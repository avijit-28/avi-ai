package com.aviai.telephony

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.util.Log
import com.aviai.AviAiApplication.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

enum class AppCallState {
    IDLE,
    RINGING,
    WAITING_FOR_DELAY,
    ANSWERING,
    ACTIVE_AI_ATTENDED,
    HUMAN_TAKEOVER,
    DISCONNECTED
}

data class CallUiModel(
    val state: AppCallState = AppCallState.IDLE,
    val callerNumber: String = "",
    val countdownRemainingSeconds: Int = 15,
    val isAudioProbingActive: Boolean = false,
    val audioProbeResult: String = ""
)

object CallController {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var delayJob: Job? = null
    private var audioProbeJob: Job? = null

    private var activeCall: Call? = null

    private val _uiState = MutableStateFlow(CallUiModel())
    val uiState: StateFlow<CallUiModel> = _uiState.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<String>>(emptyList())
    val eventLogs: StateFlow<List<String>> = _eventLogs.asStateFlow()

    var configuredAnswerDelaySeconds: Int = 15

    fun logEvent(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = "[$time] $message"
        Log.i(TAG, entry)
        _eventLogs.value = (_eventLogs.value + entry).takeLast(100)
    }

    fun onCallAdded(call: Call) {
        activeCall = call
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown Caller"
        logEvent("Incoming call detected: $number (State: ${callStateToString(call.state)})")
        updateStateForCall(call)

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                logEvent("Call state changed -> ${callStateToString(state)}")
                updateStateForCall(call)
            }
        })
    }

    fun onCallRemoved(call: Call) {
        logEvent("Call removed/ended (was: ${callStateToString(call.state)})")
        cancelPendingDelay("Call removed")
        stopAudioProbe()
        activeCall = null
        _uiState.value = _uiState.value.copy(
            state = AppCallState.DISCONNECTED,
            countdownRemainingSeconds = 0
        )
    }

    private fun updateStateForCall(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"

        when (call.state) {
            Call.STATE_RINGING -> {
                _uiState.value = _uiState.value.copy(
                    state = AppCallState.RINGING,
                    callerNumber = number,
                    countdownRemainingSeconds = configuredAnswerDelaySeconds
                )
                startDelayCountdown(call)
            }
            Call.STATE_ACTIVE -> {
                cancelPendingDelay("Call became active")
                if (_uiState.value.state != AppCallState.HUMAN_TAKEOVER) {
                    _uiState.value = _uiState.value.copy(
                        state = AppCallState.ACTIVE_AI_ATTENDED,
                        callerNumber = number
                    )
                }
            }
            Call.STATE_DISCONNECTED -> {
                cancelPendingDelay("Call disconnected")
                stopAudioProbe()
                _uiState.value = _uiState.value.copy(
                    state = AppCallState.DISCONNECTED
                )
            }
        }
    }

    private fun startDelayCountdown(call: Call) {
        cancelPendingDelay("Starting new delay countdown")
        logEvent("TEL-04: Starting $configuredAnswerDelaySeconds-second answer delay timer...")
        _uiState.value = _uiState.value.copy(state = AppCallState.WAITING_FOR_DELAY)

        delayJob = scope.launch {
            for (remaining in configuredAnswerDelaySeconds downTo 1) {
                _uiState.value = _uiState.value.copy(countdownRemainingSeconds = remaining)
                delay(1000)

                // Race condition checks (TEL-03, TEL-05)
                if (call.state != Call.STATE_RINGING) {
                    logEvent("TEL-05: Call is no longer ringing (current: ${callStateToString(call.state)}). Aborting automatic answer.")
                    return@launch
                }
            }

            _uiState.value = _uiState.value.copy(countdownRemainingSeconds = 0)

            // Re-check call state immediately before answering
            if (call.state == Call.STATE_RINGING) {
                logEvent("TEL-04: Delay elapsed! Attempting Call.answer()...")
                _uiState.value = _uiState.value.copy(state = AppCallState.ANSWERING)
                try {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    logEvent("TEL-04: call.answer() executed successfully.")
                } catch (e: Exception) {
                    logEvent("TEL-04 ERROR: Exception in call.answer(): ${e.message}")
                }
            } else {
                logEvent("TEL-05: Pre-answer check failed. State was ${callStateToString(call.state)}.")
            }
        }
    }

    fun cancelPendingDelay(reason: String) {
        delayJob?.let {
            if (it.isActive) {
                logEvent("Cancelled pending answer delay ($reason)")
                it.cancel()
            }
        }
        delayJob = null
    }

    fun manualAnswer() {
        val call = activeCall
        if (call != null && call.state == Call.STATE_RINGING) {
            cancelPendingDelay("Manual answer tapped")
            logEvent("Manual answer triggered.")
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        } else {
            logEvent("Manual answer clicked but no ringing call available.")
        }
    }

    fun takeoverCall() {
        val call = activeCall
        if (call != null && call.state == Call.STATE_ACTIVE) {
            logEvent("TEL-08: Human Takeover requested! Stopping AI output and handing to user.")
            stopAudioProbe()
            _uiState.value = _uiState.value.copy(state = AppCallState.HUMAN_TAKEOVER)
            try {
                // Ensure audio route is set to Earpiece or Speaker
                AviInCallService.instance?.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                logEvent("TEL-08: Audio route returned to earpiece. Takeover confirmed.")
            } catch (e: Exception) {
                logEvent("TEL-08 ERROR: Failed setting audio route: ${e.message}")
            }
        } else {
            logEvent("Takeover requested but call is not active.")
        }
    }

    fun disconnectCall() {
        activeCall?.let {
            logEvent("Disconnecting call...")
            it.disconnect()
        }
    }

    fun probeCallAudio(context: Context) {
        if (_uiState.value.isAudioProbingActive) {
            stopAudioProbe()
            return
        }

        logEvent("TEL-06 & TEL-07: Starting in-call bidirectional audio probe on Android 16...")
        _uiState.value = _uiState.value.copy(
            isAudioProbingActive = true,
            audioProbeResult = "Probing in progress..."
        )

        audioProbeJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            var audioTrack: AudioTrack? = null
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                // Try AudioSource.VOICE_COMMUNICATION first, then VOICE_CALL
                val sourcesToTest = listOf(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
                    MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL",
                    MediaRecorder.AudioSource.MIC to "MIC"
                )

                var successfulSource = "NONE"
                for ((src, srcName) in sourcesToTest) {
                    try {
                        logEvent("Testing AudioRecord with source: $srcName...")
                        val record = AudioRecord(src, sampleRate, channelConfig, audioFormat, minBufferSize * 2)
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            record.startRecording()
                            val buffer = ShortArray(1024)
                            val read = record.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                successfulSource = srcName
                                logEvent("TEL-06 SUCCESS: AudioRecord read $read samples via $srcName!")
                                record.stop()
                                record.release()
                                break
                            }
                        }
                        record.release()
                    } catch (e: Exception) {
                        logEvent("AudioRecord $srcName failed: ${e.message}")
                    }
                }

                // Test AudioTrack synthetic tone injection (TEL-07)
                logEvent("Testing AudioTrack injection into call audio stream...")
                val trackBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    audioFormat
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(trackBufferSize)
                    .build()

                track.play()
                // Generate a 1-second 440Hz test tone
                val toneBuffer = ShortArray(sampleRate)
                for (i in toneBuffer.indices) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / 440.0)
                    toneBuffer[i] = (sin(angle) * 16384).toInt().toShort()
                }
                track.write(toneBuffer, 0, toneBuffer.size)
                track.stop()
                track.release()
                logEvent("TEL-07: Test tone written to AudioTrack (USAGE_VOICE_COMMUNICATION).")

                val summary = "Record Source: $successfulSource | Track: Played 440Hz tone"
                logEvent("Probe complete: $summary")
                _uiState.value = _uiState.value.copy(
                    isAudioProbingActive = false,
                    audioProbeResult = summary
                )
            } catch (e: Exception) {
                logEvent("Audio probe exception: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isAudioProbingActive = false,
                    audioProbeResult = "Error: ${e.message}"
                )
            }
        }
    }

    private fun stopAudioProbe() {
        audioProbeJob?.cancel()
        audioProbeJob = null
        if (_uiState.value.isAudioProbingActive) {
            _uiState.value = _uiState.value.copy(isAudioProbingActive = false)
        }
    }

    private fun callStateToString(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_ACTIVE -> "ACTIVE"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_DISCONNECTING -> "DISCONNECTING"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
            else -> "UNKNOWN($state)"
        }
    }
}

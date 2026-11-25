package com.aviai

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviai.telephony.AppCallState
import com.aviai.telephony.CallCompatibilityChecker
import com.aviai.telephony.CallController
import com.aviai.telephony.DiagnosticStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    secondary = Color(0xFF81C784),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    error = Color(0xFFE57373)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiagnosticDashboard(
                        onSetDefaultDialer = { requestDefaultDialerRole() }
                    )
                }
            }
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                CallController.logEvent("TEL-01: Launching RoleManager request for ROLE_DIALER...")
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivity(intent)
            } else {
                CallController.logEvent("TEL-01: RoleManager ROLE_DIALER not available on this device!")
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            startActivity(intent)
        }
    }
}

@Composable
fun DiagnosticDashboard(onSetDefaultDialer: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var diagnosticStatus by remember { mutableStateOf(CallCompatibilityChecker.checkStatus(context)) }
    val uiState by CallController.uiState.collectAsState()
    val eventLogs by CallController.eventLogs.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        diagnosticStatus = CallCompatibilityChecker.checkStatus(context)
        CallController.logEvent("Permissions updated. Ready: ${diagnosticStatus.isReadyForInCallService}")
    }

    // Auto-scroll logs to bottom
    LaunchedEffect(eventLogs.size) {
        if (eventLogs.isNotEmpty()) {
            listState.animateScrollToItem(eventLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Title
        Text(
            text = "Avi AI Telephony Feasibility",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Hardware & Role Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Device: ${diagnosticStatus.deviceModel} (Android ${diagnosticStatus.osVersion}, API ${diagnosticStatus.sdkInt})",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEL-01 Default Dialer Role:",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (diagnosticStatus.isDefaultDialer) "HELD (PASS)" else "NOT SET",
                        color = if (diagnosticStatus.isDefaultDialer) Color(0xFF4CAF50) else Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!diagnosticStatus.isDefaultDialer) {
                    Button(
                        onClick = onSetDefaultDialer,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Request Default Phone App Role")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Required Permissions:")
                    Text(
                        text = if (diagnosticStatus.hasPhoneStatePermission && diagnosticStatus.hasRecordAudioPermission) "GRANTED" else "MISSING",
                        color = if (diagnosticStatus.hasPhoneStatePermission && diagnosticStatus.hasRecordAudioPermission) Color(0xFF4CAF50) else Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!diagnosticStatus.hasPhoneStatePermission || !diagnosticStatus.hasRecordAudioPermission) {
                    OutlinedButton(
                        onClick = {
                            val perms = mutableListOf(
                                android.Manifest.permission.READ_PHONE_STATE,
                                android.Manifest.permission.RECORD_AUDIO,
                                android.Manifest.permission.CALL_PHONE,
                                android.Manifest.permission.ANSWER_PHONE_CALLS
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Phone & Audio Permissions")
                    }
                }
            }
        }

        // Live Incoming Call Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (uiState.state) {
                    AppCallState.WAITING_FOR_DELAY -> Color(0xFF2E2600)
                    AppCallState.ACTIVE_AI_ATTENDED -> Color(0xFF1B3A24)
                    AppCallState.HUMAN_TAKEOVER -> Color(0xFF263238)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Live Call State:", fontWeight = FontWeight.Bold)
                    Text(
                        text = uiState.state.name,
                        fontWeight = FontWeight.Bold,
                        color = when (uiState.state) {
                            AppCallState.WAITING_FOR_DELAY -> Color(0xFFFFD54F)
                            AppCallState.ACTIVE_AI_ATTENDED -> Color(0xFF81C784)
                            AppCallState.HUMAN_TAKEOVER -> Color(0xFF64B5F6)
                            else -> Color.White
                        }
                    )
                }

                if (uiState.callerNumber.isNotEmpty()) {
                    Text("Caller: ${uiState.callerNumber}", fontSize = 14.sp)
                }

                if (uiState.state == AppCallState.WAITING_FOR_DELAY) {
                    Text(
                        text = "Auto-answering in: ${uiState.countdownRemainingSeconds}s (TEL-04 default 15s)",
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { uiState.countdownRemainingSeconds / 15f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Call Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.state == AppCallState.RINGING || uiState.state == AppCallState.WAITING_FOR_DELAY) {
                        Button(
                            onClick = { CallController.manualAnswer() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                        ) {
                            Text("Answer Now")
                        }
                    }

                    if (uiState.state == AppCallState.ACTIVE_AI_ATTENDED) {
                        Button(
                            onClick = { CallController.takeoverCall() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text("Take Over")
                        }
                    }

                    if (uiState.state != AppCallState.IDLE && uiState.state != AppCallState.DISCONNECTED) {
                        Button(
                            onClick = { CallController.disconnectCall() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("Hang Up")
                        }
                    }
                }
            }
        }

        // Two-Way Audio Feasibility Probe (TEL-06 / TEL-07)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TEL-06 & TEL-07 Two-Way Audio Probe",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Probes AudioRecord (VOICE_COMMUNICATION / VOICE_CALL) and AudioTrack tone injection during an active call.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                Button(
                    onClick = { CallController.probeCallAudio(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
                ) {
                    Text(if (uiState.isAudioProbingActive) "Probing..." else "Run Two-Way Audio Probe")
                }
                if (uiState.audioProbeResult.isNotEmpty()) {
                    Text(
                        text = "Result: ${uiState.audioProbeResult}",
                        fontSize = 12.sp,
                        color = Color(0xFFCE93D8)
                    )
                }
            }
        }

        // Live Logcat Events
        Text(
            text = "Diagnostic Event Log:",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(eventLogs) { log ->
                    Text(
                        text = log,
                        color = Color(0xFF81C784),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

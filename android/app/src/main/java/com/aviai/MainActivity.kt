package com.aviai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviai.data.CallRepository
import com.aviai.data.UiCallRecord
import com.aviai.data.UiSettings
import com.aviai.telephony.CallNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
                    AviAiApp()
                }
            }
        }
    }
}

fun dialUssdCode(context: Context, code: String) {
    try {
        val encoded = "tel:" + Uri.encode(code)
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse(encoded)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AviAiApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var backendUrl by remember { mutableStateOf("https://avi-ai-backend.onrender.com") }
    var callRecords by remember { mutableStateOf<List<UiCallRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    // Dynamic AI Status Settings
    var settingsState by remember { mutableStateOf(UiSettings()) }
    var isSavingSettings by remember { mutableStateOf(false) }

    // Audio Playback State
    var currentlyPlayingCallId by remember { mutableStateOf<String?>(null) }
    var isAudioLoading by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    val repository = remember { CallRepository() }
    val scope = rememberCoroutineScope()

    // Notification Permission Launcher (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Voice call notifications enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!CallNotificationManager.hasNotificationPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {}
        }
    }

    fun togglePlayAudio(call: UiCallRecord) {
        val audioPath = call.audioUrl ?: "/api/v1/calls/${call.id}/audio"
        val fullAudioUrl = if (audioPath.startsWith("http")) audioPath else {
            val base = if (backendUrl.endsWith("/")) backendUrl.dropLast(1) else backendUrl
            val path = if (audioPath.startsWith("/")) audioPath else "/$audioPath"
            "$base$path"
        }

        if (currentlyPlayingCallId == call.id) {
            try {
                mediaPlayer.stop()
                mediaPlayer.reset()
            } catch (e: Exception) {}
            currentlyPlayingCallId = null
            isAudioLoading = false
        } else {
            try {
                mediaPlayer.reset()
                currentlyPlayingCallId = call.id
                isAudioLoading = true

                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                mediaPlayer.setDataSource(fullAudioUrl)
                mediaPlayer.setOnPreparedListener { mp ->
                    isAudioLoading = false
                    mp.start()
                }
                mediaPlayer.setOnCompletionListener {
                    currentlyPlayingCallId = null
                    isAudioLoading = false
                }
                mediaPlayer.setOnErrorListener { _, _, _ ->
                    currentlyPlayingCallId = null
                    isAudioLoading = false
                    Toast.makeText(context, "Failed to load audio memo.", Toast.LENGTH_SHORT).show()
                    true
                }
                mediaPlayer.prepareAsync()
            } catch (e: Exception) {
                currentlyPlayingCallId = null
                isAudioLoading = false
                Toast.makeText(context, "Error starting playback: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadCalls(isManual: Boolean = false) {
        scope.launch {
            if (isManual) {
                isLoading = true
                statusMessage = "Syncing with Avi AI backend..."
            }
            val result = repository.fetchCalls(backendUrl)
            result.onSuccess { calls ->
                callRecords = calls
                statusMessage = "Updated: ${calls.size} call records found."
                CallNotificationManager.checkAndNotifyNewCalls(context, calls)
            }.onFailure {
                if (isManual) {
                    statusMessage = "Failed to sync: ${it.message}"
                }
            }

            // Also load settings
            repository.fetchSettings(backendUrl).onSuccess {
                settingsState = it
            }

            if (isManual) {
                isLoading = false
            }
        }
    }

    // Initial load + Background Auto-Polling every 15 seconds
    LaunchedEffect(backendUrl) {
        loadCalls(isManual = true)
        while (isActive) {
            delay(15000)
            loadCalls(isManual = false)
        }
    }

    fun saveSettings(updated: UiSettings) {
        scope.launch {
            isSavingSettings = true
            val res = repository.saveSettings(backendUrl, updated)
            res.onSuccess {
                settingsState = it
                Toast.makeText(context, "AI Status updated! AI will now tell callers: '${it.ownerStatus}'", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "Failed to save: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            isSavingSettings = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Avi AI — Call Attendant", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Status: ${settingsState.ownerStatus}", fontSize = 12.sp, color = Color(0xFF81C784))
                    }
                },
                actions = {
                    IconButton(onClick = { loadCalls(isManual = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Call, contentDescription = "Inbox") },
                    label = { Text("Call Inbox") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.PhoneForwarded, contentDescription = "Forwarding") },
                    label = { Text("Setup Guide") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("AI Status & Settings") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> CallInboxScreen(
                    calls = callRecords,
                    isLoading = isLoading,
                    statusMessage = statusMessage,
                    currentlyPlayingCallId = currentlyPlayingCallId,
                    isAudioLoading = isAudioLoading,
                    onTogglePlay = { togglePlayAudio(it) },
                    onCallBack = { number ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        context.startActivity(intent)
                    }
                )
                1 -> CallForwardingSetupScreen(forwardingNumber = settingsState.forwardingNumber)
                2 -> SettingsScreen(
                    backendUrl = backendUrl,
                    settings = settingsState,
                    isSaving = isSavingSettings,
                    onSaveUrl = {
                        backendUrl = it
                        loadCalls(isManual = true)
                    },
                    onSaveSettings = { saveSettings(it) },
                    onTriggerTestNotification = {
                        CallNotificationManager.showTestNotification(context)
                        Toast.makeText(context, "Test notification sent!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun CallInboxScreen(
    calls: List<UiCallRecord>,
    isLoading: Boolean,
    statusMessage: String,
    currentlyPlayingCallId: String?,
    isAudioLoading: Boolean,
    onTogglePlay: (UiCallRecord) -> Unit,
    onCallBack: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (statusMessage.isNotEmpty()) {
            Text(statusMessage, fontSize = 12.sp, color = Color.Gray)
        }

        if (calls.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No AI-attended calls yet.\nWhen someone calls your number and you don't answer,\nAvi AI will attend the call and place the summary here.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(calls) { call ->
                    CallCard(
                        call = call,
                        isPlaying = currentlyPlayingCallId == call.id,
                        isAudioLoading = isAudioLoading && currentlyPlayingCallId == call.id,
                        onTogglePlay = { onTogglePlay(call) },
                        onCallBack = onCallBack
                    )
                }
            }
        }
    }
}

@Composable
fun CallCard(
    call: UiCallRecord,
    isPlaying: Boolean,
    isAudioLoading: Boolean,
    onTogglePlay: () -> Unit,
    onCallBack: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = call.summary?.callerName ?: call.callerNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Phone: ${call.callerNumber}",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }

                call.summary?.urgency?.let { urgency ->
                    AssistChip(
                        onClick = {},
                        label = { Text(urgency) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (urgency.equals("urgent", true)) Color(0xFFD32F2F) else Color(0xFF388E3C),
                            labelColor = Color.White
                        )
                    )
                }
            }

            call.summary?.let { summary ->
                Text(
                    text = "Purpose: ${summary.callPurpose}",
                    fontSize = 13.sp,
                    color = Color(0xFFFFD54F)
                )

                if (!summary.preferredCallbackTime.isNullOrBlank()) {
                    Text(
                        text = "Preferred callback: ${summary.preferredCallbackTime}",
                        fontSize = 12.sp,
                        color = Color(0xFF81C784)
                    )
                }

                if (summary.actionItems.isNotEmpty()) {
                    Text("Action items:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    summary.actionItems.forEach { item ->
                        Text("• $item", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }

            // Audio Memo Player Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "Playing Voice Note..." else if (isAudioLoading) "Loading Audio..." else "Voice Note Audio",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isAudioLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF4FC3F7)
                    )
                } else {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            tint = if (isPlaying) Color(0xFFE57373) else Color(0xFF81C784)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide Transcript" else "View Transcript (${call.transcript.size} turns)")
                }

                Button(
                    onClick = { onCallBack(call.callerNumber) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call Back")
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    call.transcript.forEach { turn ->
                        val isAi = turn.speaker.equals("AI", true)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = if (isAi) 0.dp else 24.dp, end = if (isAi) 24.dp else 0.dp)
                                .background(
                                    color = if (isAi) Color(0xFF1E2D3D) else Color(0xFF2E3A2F),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = turn.speaker,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAi) Color(0xFF64B5F6) else Color(0xFF81C784)
                                )
                                Text(text = turn.text, fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallForwardingSetupScreen(forwardingNumber: String) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Jio True5G & Carrier Call Forwarding",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "When someone calls and you don't answer or decline, your SIM automatically forwards the call to Avi AI.",
            fontSize = 13.sp,
            color = Color.LightGray
        )

        // Jio Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔵 Reliance Jio (True5G / 4G)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF4FC3F7))
                }

                Text(
                    text = "• Forward when No Answer (15s): *409*$forwardingNumber#\n• Forward when Busy / Rejected: *404*$forwardingNumber#\n• Forward when Unreachable: *410*$forwardingNumber#\n• Cancel All Jio Forwarding: *413",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { dialUssdCode(context, "*409*$forwardingNumber#") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                    ) {
                        Text("Dial *409* Activate", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { dialUssdCode(context, "*413") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dial *413 Deactivate", fontSize = 11.sp)
                    }
                }
            }
        }

        // Airtel Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔴 Airtel / Vodafone Idea (Vi)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFEF5350))
                Text(
                    text = "• Forward when No Answer: *61*$forwardingNumber*11*15#\n• Forward when Busy: *67*$forwardingNumber#\n• Deactivate Airtel/Vi: ##61# or ##002#",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { dialUssdCode(context, "*61*$forwardingNumber*11*15#") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Dial *61* Activate", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { dialUssdCode(context, "##61#") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dial ##61# Cancel", fontSize = 11.sp)
                    }
                }
            }
        }

        // Native Android Phone Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⚙️ Google Phone App Settings (Alternative)", fontWeight = FontWeight.Bold)
                Text(
                    text = "1. Open Google Phone > Settings (three dots)\n2. Calling accounts > Advanced settings > Call forwarding\n3. Tap 'When unanswered' and enter: $forwardingNumber",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    backendUrl: String,
    settings: UiSettings,
    isSaving: Boolean,
    onSaveUrl: (String) -> Unit,
    onSaveSettings: (UiSettings) -> Unit,
    onTriggerTestNotification: () -> Unit
) {
    var urlText by remember { mutableStateOf(backendUrl) }
    var selectedStatus by remember(settings.ownerStatus) { mutableStateOf(settings.ownerStatus) }
    var noteText by remember(settings.customStatusNote) { mutableStateOf(settings.customStatusNote) }
    val scrollState = rememberScrollState()

    val statuses = listOf(
        "Meeting" to "💼 In a Meeting",
        "Driving" to "🚗 Driving",
        "Busy" to "🏃 Busy / Away",
        "Sleeping" to "🛌 Resting (DND)",
        "Available" to "✅ Available",
        "Custom" to "✏️ Custom"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Current Status & Persona
        Text(
            text = "Avijit's Current Status (AI Persona)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Tell Avi AI what you are currently doing. When callers ask for you, Avi will explain naturally according to your status.",
            fontSize = 12.sp,
            color = Color.LightGray
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statuses.forEach { (key, label) ->
                FilterChip(
                    selected = selectedStatus.equals(key, true),
                    onClick = { selectedStatus = key },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1976D2),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Note for Callers (e.g. Free after 4 PM, urgent only)") },
            placeholder = { Text("e.g. In an important project review until 4 PM") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSaveSettings(
                    settings.copy(
                        ownerStatus = selectedStatus,
                        customStatusNote = noteText
                    )
                )
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isSaving) "Saving to Cloud..." else "Save Status & Update AI")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Notifications Test
        Text(
            text = "Notifications & Alerts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedButton(
            onClick = onTriggerTestNotification,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("🔔 Send Test Notification Now")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Cloud Backend URL
        Text(
            text = "Backend Cloud Connection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("Backend URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onSaveUrl(urlText) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sync Backend URL")
        }
    }
}

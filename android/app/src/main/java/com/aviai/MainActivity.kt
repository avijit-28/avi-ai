package com.aviai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AviAiApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var backendUrl by remember { mutableStateOf("https://avi-ai-backend.onrender.com") }
    var callRecords by remember { mutableStateOf<List<UiCallRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val repository = remember { CallRepository() }
    val scope = rememberCoroutineScope()

    fun loadCalls() {
        scope.launch {
            isLoading = true
            statusMessage = "Syncing with Avi AI backend..."
            val result = repository.fetchCalls(backendUrl)
            result.onSuccess {
                callRecords = it
                statusMessage = "Updated: ${it.size} call records found."
            }.onFailure {
                statusMessage = "Failed to sync: ${it.message}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCalls()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Avi AI — Call Attendant", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = { loadCalls() }) {
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
                    label = { Text("Settings") }
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
                    onCallBack = { number ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        context.startActivity(intent)
                    }
                )
                1 -> CallForwardingSetupScreen()
                2 -> SettingsScreen(
                    backendUrl = backendUrl,
                    onSaveUrl = {
                        backendUrl = it
                        loadCalls()
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
                    CallCard(call = call, onCallBack = onCallBack)
                }
            }
        }
    }
}

@Composable
fun CallCard(call: UiCallRecord, onCallBack: (String) -> Unit) {
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
                Divider(modifier = Modifier.padding(vertical = 4.dp))
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
fun CallForwardingSetupScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Carrier Call Forwarding Guide",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Configure conditional forwarding so your phone rings for 15 seconds. If you don't answer or decline, Avi AI answers the call for you.",
            fontSize = 13.sp,
            color = Color.LightGray
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Option 1: Phone Settings (Easiest)", fontWeight = FontWeight.Bold)
                Text(
                    text = "1. Open your Google Phone app > Settings (:)\n2. Tap Calling accounts > Call forwarding\n3. Tap 'When unanswered' / 'When busy'\n4. Enter your Avi AI attendant phone number.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Option 2: Indian Carrier USSD Codes", fontWeight = FontWeight.Bold)
                Text(
                    text = "• Jio: Dial *409*<AttendantNumber># (Forward when unanswered)\n• Airtel: Dial *61*<AttendantNumber>*11*15# (15s delay forward)\n• Vi: Dial *61*<AttendantNumber>*11*15#\n\nTo Deactivate:\n• Jio: Dial *413\n• Airtel / Vi: Dial ##61#",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(backendUrl: String, onSaveUrl: (String) -> Unit) {
    var text by remember { mutableStateOf(backendUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Backend Server Connection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Enter your PC's IP address or cloud server URL hosting the Avi AI FastAPI backend.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Backend URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onSaveUrl(text) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Sync")
        }
    }
}

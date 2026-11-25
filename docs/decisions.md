# Avi AI — Architecture Decisions Log

## Confirmed Architectural Decisions (Baseline)

### 1. Development & Build Toolchain
- **Decision:** Standard Android Studio & SDK installation on the development PC.
- **Context:** Android Studio provides bundled OpenJDK, Android SDK Command Line Tools, Platform Tools (`adb`), and Gradle wrapper orchestration, ensuring predictable builds and smooth deployment to Realme P1 5G.

### 2. Phasing & Telephony Feasibility
- **Decision:** Strict Blocking Phase 1 Feasibility Gate.
- **Context:** Cellular raw audio capture and injection is heavily guarded on Android 16. No AI conversation code will be built for cellular calls until TEL-01 through TEL-08 are experimentally proven on the physical Realme P1 5G device.

### 3. Contingency Architecture (If SIM Audio Is Blocked)
- **Decision:** Carrier Call-Forwarding to VoIP Bridge.
- **Context:** If TEL-06/TEL-07 fail on Android 16, Avi AI will pivot to utilizing carrier conditional call forwarding (busy / no-answer forward) to a cloud VoIP service (e.g. Twilio SIP / FreePBX), preserving the automated call attendant experience without violating Android security boundaries.

### 4. Human Takeover Workflow
- **Decision:** Dual Trigger Mechanism.
- **Context:** Prominent on-screen "Take Over" button on the phone screen + automatic pause/handover prompt when the caller explicitly asks to speak with Avijit.

### 5. AI Topology & Latency
- **Decision:** Cloud-First Hybrid Architecture.
- **Context:** Google Gemini Flash accessed directly from the Android app via HTTPS for low latency (<2s) and multilingual fluency (Indian English, Hindi, Bengali, Hinglish). Predefined polite responses are maintained locally in the app for offline/network loss scenarios.

### 6. Personal Knowledge Guardrails
- **Decision:** Minimal Conservative Profile.
- **Context:** The AI discloses Avijit's name, takes a message and preferred callback details, and strictly refuses to make calendar commitments or disclose personal/confidential details.

### 7. Recording & Consent Disclosure
- **Decision:** Combined AI Identity & Recording Announcement.
- **Context:** The opening greeting explicitly discloses both AI identity and that the call is recorded for message-taking purposes:
  *"Hello! How are you? I'm the Avi AI assistant. Avijit is currently unavailable. This call is recorded so I can take a message for him. How can I assist you today?"*

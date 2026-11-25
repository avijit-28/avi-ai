# Avi AI

Avi AI is an Android incoming-call attendant for Avijit (target device: Realme P1 5G, Android 16).
When an eligible SIM call rings unanswered for a configurable delay (default 15 seconds), Avi AI answers the call, identifies itself, discloses that the call is recorded to take a message, converses naturally with low latency (target 1–2s) in Indian English, Hindi, Bengali, or Hinglish, collects caller identity, purpose, and callback requests using approved conservative knowledge without hallucinating commitments, and prepares post-call transcripts, summaries, and action items.

## Architecture Principles
1. **Feasibility First:** Telephony answering, bidirectional cellular audio routing, and human handover are validated on the physical Realme P1 5G device (Phase 1) before building the cellular voice agent.
2. **Offline-First Android Core:** Android manages telephony, local Room database, encrypted recording storage, and permissions. An optional FastAPI backend provides optional orchestration and synchronization.
3. **Cloud-First Hybrid AI:** Google Gemini Flash via secure API on the phone provides low latency (<2s) and multilingual fluency, with local predefined responses for offline fallback.
4. **Transparent & Privacy Preserving:** Caller is informed of AI identity and recording. Human takeover can be triggered on-screen or conversationally at any time.

## Repository Structure
```
avi-ai/
├── README.md
├── .gitignore
├── .env.example
├── android/          # Native Android Kotlin + Jetpack Compose application
├── backend/          # Optional Python FastAPI service
├── docs/             # Technical specifications, architecture, and test logs
├── models/           # Benchmarked models (VAD, STT, TTS, LLM)
└── scripts/          # Automation, benchmarking, and setup scripts
```

## Implementation Phases
- **Phase 0:** Environment & Repository Setup (Current)
- **Phase 1:** Telephony Feasibility Gate (Blocking: TEL-01 to TEL-12)
- **Phase 2:** Independent Voice Agent Prototype (Mic/Speaker STT -> LLM -> TTS)
- **Phase 3:** Supported Cellular Integration
- **Phase 4:** Call Records, Encryption & Knowledge Guardrails
- **Phase 5:** Dashboard, Live-Call UI & Operations
- **Phase 6:** Release Validation & Signed Private APK

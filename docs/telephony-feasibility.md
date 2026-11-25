# Phase 1 — Telephony Feasibility Test Log

**Target Handset:** Realme P1 5G  
**Operating System:** Android 16  
**Carriers:** Indian SIMs (Jio / Airtel / Vi / BSNL)  
**Test Status:** NOT YET TESTED (Gated on Android Studio/SDK setup)  

---

## Experiment Matrix

| ID | Experiment Description | Required Evidence | Status | Notes / Logcat Summary |
| :--- | :--- | :--- | :--- | :--- |
| **TEL-01** | Can app qualify for Default Phone App role (`ROLE_DIALER`)? | System Role prompt and RoleManager return value | NOT TESTED | Verify if Realme UI allows third-party default dialer |
| **TEL-02** | Can app receive incoming call state transitions via `InCallService`? | Timestamped logcat of `Call.Callback` (`STATE_RINGING`, `STATE_DISCONNECTED`) | NOT TESTED | Must re-verify on screen locked & unlocked |
| **TEL-03** | Can app distinguish ringing, active, and disconnected states reliably? | Sequential call state transition dump | NOT TESTED | Check for OEM-specific phantom state drops |
| **TEL-04** | Can app honor 15-second delay and execute `Call.answer()`? | Time delta between ringing and answer attempt; answer result | NOT TESTED | Validate that call enters `STATE_ACTIVE` |
| **TEL-05** | If caller hangs up or user picks up before 15s, does AI cancel answer? | Timer cancellation logs and absence of ghost answer | NOT TESTED | Critical race condition test |
| **TEL-06** | Can app capture caller audio stream during `STATE_ACTIVE`? | Audio record waveform / PCM buffer inspection | NOT TESTED | Check `AudioSource.VOICE_CALL` vs `VOICE_COMMUNICATION` permission |
| **TEL-07** | Can app inject synthetic/test audio into cellular call stream? | Caller reports hearing test beep/speech through phone speaker/earpiece | NOT TESTED | Critical go/no-go gate for on-device voice AI |
| **TEL-08** | Can user take over call and talk to caller? | Handover execution log and two-way voice check after takeover button pressed | NOT TESTED | Audio route return to handset mic & earpiece |
| **TEL-09** | Can app record both sides of call lawfully and store encrypted? | Encrypted audio file verification and playback test | NOT TESTED | Separate gate for recording compliance |
| **TEL-10** | Does call detection and answering survive phone lock screen / reboot? | Incoming call handled while phone is locked | NOT TESTED | Foreground service and lock-screen notification |
| **TEL-11** | How does app behave if a second call arrives during an ongoing call? | Call state events for second incoming call | NOT TESTED | Must not terminate first call |
| **TEL-12** | Does emergency stop immediately halt AI audio and release call? | Time to audio cutoff upon tapping Emergency Stop | NOT TESTED | Must cut off within <100ms |

---

## Critical Feasibility Gate Rules
- **GO:** If TEL-01 through TEL-08 pass, proceed to Phase 3 cellular voice integration.
- **NO-GO / PIVOT:** If TEL-06 / TEL-07 fail (Android 16 blocks raw cellular audio capture/injection), halt native cellular audio integration and trigger the agreed contingency: **Carrier Call-Forwarding to a cloud VoIP attendant**.

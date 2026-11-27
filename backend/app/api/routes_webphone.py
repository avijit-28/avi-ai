import datetime
from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["WebPhone"])

HTML_PAGE = """<!DOCTYPE html>
<html lang="hi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Avi AI — Web Phone Simulator</title>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&display=swap" rel="stylesheet">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: #0d1117;
      color: #e6edf3;
      font-family: 'Outfit', sans-serif;
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      padding: 16px;
    }
    .phone-container {
      width: 100%;
      max-width: 440px;
      background: #161b22;
      border: 1px solid #30363d;
      border-radius: 36px;
      padding: 26px 22px;
      box-shadow: 0 20px 50px rgba(0,0,0,0.6);
      display: flex;
      flex-direction: column;
      align-items: center;
      position: relative;
    }
    .status-bar {
      width: 100%;
      display: flex;
      justify-content: space-between;
      font-size: 13px;
      color: #8b949e;
      margin-bottom: 18px;
    }
    .caller-avatar {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: linear-gradient(135deg, #1f6feb, #8957e5);
      display: flex;
      justify-content: center;
      align-items: center;
      font-size: 34px;
      margin-bottom: 12px;
      box-shadow: 0 0 25px rgba(31, 111, 235, 0.4);
      transition: transform 0.3s;
    }
    .caller-avatar.speaking {
      animation: pulse 1.2s infinite;
    }
    @keyframes pulse {
      0% { transform: scale(1); box-shadow: 0 0 15px rgba(31, 111, 235, 0.4); }
      50% { transform: scale(1.08); box-shadow: 0 0 30px rgba(137, 87, 229, 0.8); }
      100% { transform: scale(1); box-shadow: 0 0 15px rgba(31, 111, 235, 0.4); }
    }
    .caller-name {
      font-size: 22px;
      font-weight: 700;
      color: #f0f6fc;
      margin-bottom: 4px;
    }
    .call-status {
      font-size: 13px;
      color: #3fb950;
      font-weight: 600;
      letter-spacing: 0.3px;
      margin-bottom: 16px;
      text-align: center;
      min-height: 20px;
    }
    .mic-live-banner {
      display: none;
      width: 100%;
      background: rgba(35, 134, 54, 0.15);
      border: 1px solid rgba(63, 185, 80, 0.4);
      color: #7ee787;
      padding: 8px 12px;
      border-radius: 12px;
      font-size: 12px;
      margin-bottom: 12px;
      align-items: center;
      gap: 8px;
      justify-content: center;
    }
    .mic-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: #3fb950;
      animation: blink 1s infinite;
    }
    @keyframes blink {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.3; transform: scale(0.8); }
    }
    .live-transcript {
      width: 100%;
      height: 210px;
      background: #0d1117;
      border: 1px solid #21262d;
      border-radius: 18px;
      padding: 12px;
      overflow-y: auto;
      font-size: 13.5px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-bottom: 14px;
    }
    .msg {
      padding: 8px 12px;
      border-radius: 12px;
      max-width: 88%;
      line-height: 1.4;
      word-break: break-word;
    }
    .msg.ai {
      background: #1f6feb22;
      border: 1px solid #1f6feb55;
      color: #79c0ff;
      align-self: flex-start;
    }
    .msg.user {
      background: #23863622;
      border: 1px solid #23863655;
      color: #7ee787;
      align-self: flex-end;
    }
    .text-input-row {
      display: none;
      width: 100%;
      gap: 8px;
      margin-bottom: 16px;
    }
    .text-input-row input {
      flex: 1;
      padding: 10px 14px;
      border-radius: 12px;
      background: #0d1117;
      border: 1px solid #30363d;
      color: #e6edf3;
      font-size: 13.5px;
      outline: none;
      font-family: inherit;
    }
    .text-input-row input:focus {
      border-color: #58a6ff;
    }
    .text-input-row button {
      padding: 10px 16px;
      border-radius: 12px;
      background: #238636;
      border: none;
      color: white;
      font-weight: 600;
      font-size: 13px;
      cursor: pointer;
    }
    .controls {
      display: flex;
      gap: 18px;
      width: 100%;
      justify-content: center;
      align-items: center;
    }
    .btn {
      width: 60px;
      height: 60px;
      border-radius: 50%;
      border: none;
      display: flex;
      justify-content: center;
      align-items: center;
      cursor: pointer;
      font-size: 24px;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    .btn:hover { transform: scale(1.08); }
    .btn-call { background: #238636; color: white; box-shadow: 0 0 15px rgba(35, 134, 54, 0.4); }
    .btn-hangup { background: #da3633; color: white; box-shadow: 0 0 15px rgba(218, 54, 51, 0.4); }
    .btn-mic-toggle {
      background: #21262d;
      color: #8b949e;
      border: 1px solid #30363d;
      font-size: 20px;
    }
    .btn-mic-toggle.active {
      background: #1f6feb;
      color: white;
      border-color: #58a6ff;
      box-shadow: 0 0 12px rgba(31, 111, 235, 0.6);
    }
    .btn:disabled { opacity: 0.35; cursor: not-allowed; }
    .summary-card {
      display: none;
      width: 100%;
      margin-top: 14px;
      padding: 12px 14px;
      background: #0d1117;
      border: 1px solid #30363d;
      border-radius: 14px;
      font-size: 13px;
    }
    .summary-card h4 { color: #58a6ff; margin-bottom: 8px; }
    .mic-hint {
      margin-top: 12px;
      font-size: 11.5px;
      color: #8b949e;
      text-align: center;
      line-height: 1.4;
    }
  </style>
</head>
<body>
  <div class="phone-container">
    <div class="status-bar">
      <span>Avi AI Live Attendant</span>
      <span id="callTimer">00:00</span>
    </div>

    <div class="caller-avatar" id="avatarIcon">📞</div>
    <div class="caller-name">Avi AI Assistant</div>
    <div class="call-status" id="callStatusText">Ready to Call</div>

    <div class="mic-live-banner" id="micLiveBanner">
      <div class="mic-dot"></div>
      <span id="micLiveText">Listening to your mic... (Speak clearly)</span>
    </div>

    <div class="live-transcript" id="transcriptBox">
      <div class="msg ai"><b>Avi AI:</b> Green call button dabayein Avi AI se baat karne ke liye! (No SIM/ISD needed).</div>
    </div>

    <!-- Manual Text fallback row -->
    <div class="text-input-row" id="textInputRow">
      <input type="text" id="manualInput" placeholder="Bolein ya yahan type karein..." onkeydown="if(event.key==='Enter') sendManualText();">
      <button onclick="sendManualText()">Send</button>
    </div>

    <div class="controls">
      <button class="btn btn-call" id="startCallBtn" onclick="startCall()" title="Start Call">📞</button>
      <button class="btn btn-mic-toggle" id="micToggleBtn" onclick="toggleMicManually()" disabled title="Force Mic Listen">🎤</button>
      <button class="btn btn-hangup" id="endCallBtn" onclick="endCall()" disabled title="Hang Up">❌</button>
    </div>

    <div class="summary-card" id="summaryCard">
      <h4>📋 Call Summary & Action Items</h4>
      <div id="summaryContent">Analyzing conversation...</div>
    </div>

    <div class="mic-hint" id="micHint">
      💡 <b>Tip:</b> Agar mic automatic na sune, toh <b>🎤 mic button</b> dabayein ya text box me type karke Send dabayein!
    </div>
  </div>

  <script>
    let isCalling = false;
    let timerInterval = null;
    let secondsElapsed = 0;
    let recognition = null;
    let isRecognizing = false;
    let isWaitingForUser = false;
    let callHistory = [];
    const callId = "web-" + Date.now();

    const greeting = "Haan ji hello! Main Avijit ka assistant bol raha hoon. Avijit abhi phone ke paas nahi hain, to aap batayein kya message note karwaana hai?";

    function updateTimer() {
      secondsElapsed++;
      const m = String(Math.floor(secondsElapsed / 60)).padStart(2, '0');
      const s = String(secondsElapsed % 60).padStart(2, '0');
      document.getElementById('callTimer').innerText = `${m}:${s}`;
    }

    function addMessage(speaker, text, role) {
      const box = document.getElementById('transcriptBox');
      const div = document.createElement('div');
      div.className = 'msg ' + (role === 'ai' ? 'ai' : 'user');
      div.innerHTML = `<b>${speaker}:</b> ${text}`;
      box.appendChild(div);
      box.scrollTop = box.scrollHeight;
    }

    let currentAudio = null;

    function playAudio(audioSrc, callback) {
      if (currentAudio) {
        try { currentAudio.pause(); } catch(e){}
      }
      currentAudio = new Audio(audioSrc);
      document.getElementById('avatarIcon').classList.add('speaking');
      
      let called = false;
      const done = () => {
        document.getElementById('avatarIcon').classList.remove('speaking');
        if (!called && callback) {
          called = true;
          callback();
        }
      };
      currentAudio.onended = done;
      currentAudio.onerror = (e) => {
        console.error("Audio playback error:", e);
        done();
      };
      currentAudio.play().catch((err) => {
        console.error("Playback failed (autoplay blocked):", err);
        done();
      });
    }

    function speakMaleVoice(text, audioBase64, callback) {
      safeStopRecognition();
      isWaitingForUser = false;
      setMicBanner(false);
      document.getElementById('callStatusText').innerText = "Avi AI is speaking...";

      if (audioBase64) {
        playAudio("data:audio/mp3;base64," + audioBase64, callback);
      } else {
        playAudio("/api/v1/telephony/tts?text=" + encodeURIComponent(text), callback);
      }
    }

    function setMicBanner(active, text) {
      const banner = document.getElementById('micLiveBanner');
      const micBtn = document.getElementById('micToggleBtn');
      if (active) {
        banner.style.display = 'flex';
        document.getElementById('micLiveText').innerText = text || "Mic Active — Sun raha hoon... (Boliye)";
        micBtn.classList.add('active');
      } else {
        banner.style.display = 'none';
        micBtn.classList.remove('active');
      }
    }

    function safeStartRecognition() {
      if (!recognition || !isCalling || !isWaitingForUser || isRecognizing) return;
      try {
        recognition.start();
        isRecognizing = true;
        setMicBanner(true, "Mic Active — Sun raha hoon... (Boliye)");
      } catch (err) {
        // Recognition might already be running
      }
    }

    function safeStopRecognition() {
      if (!recognition) return;
      try {
        isRecognizing = false;
        recognition.stop();
      } catch (err) {}
      setMicBanner(false);
    }

    function toggleMicManually() {
      if (!isCalling) return;
      isWaitingForUser = true;
      document.getElementById('callStatusText').innerText = "Mic force started. Boliye...";
      try { recognition.stop(); } catch(e){}
      isRecognizing = false;
      setTimeout(() => {
        safeStartRecognition();
      }, 150);
    }

    function initSpeechRecognition() {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SpeechRecognition) {
        alert("Speech Recognition not supported in this browser. You can still type in the text box below!");
        return null;
      }

      const rec = new SpeechRecognition();
      // Use Hindi with English / Hinglish support
      rec.lang = 'hi-IN';
      rec.continuous = true;
      rec.interimResults = true;

      rec.onstart = () => {
        isRecognizing = true;
        if (isCalling && isWaitingForUser) {
          setMicBanner(true, "Mic Active — Boliye, sun raha hoon...");
          document.getElementById('callStatusText').innerText = "Listening to you... (Speak now)";
        }
      };

      rec.onresult = async (event) => {
        let interimTranscript = '';
        let finalTranscript = '';

        for (let i = event.resultIndex; i < event.results.length; ++i) {
          const item = event.results[i];
          if (item.isFinal) {
            finalTranscript += item[0].transcript;
          } else {
            interimTranscript += item[0].transcript;
          }
        }

        if (interimTranscript) {
          setMicBanner(true, "🎙️ Sun raha hoon: " + interimTranscript);
        }

        if (finalTranscript.trim()) {
          const speech = finalTranscript.trim();
          handleUserUtterance(speech);
        }
      };

      rec.onerror = (e) => {
        console.log("Recognition error:", e.error);
        if (e.error === 'not-allowed') {
          alert("Microphone permission denied! Browser address bar me lock/tune icon par click karke mic allow karein.");
          setMicBanner(false);
        }
        if (e.error === 'no-speech' && isCalling && isWaitingForUser) {
          // Restart gracefully
          setTimeout(safeStartRecognition, 300);
        }
      };

      rec.onend = () => {
        isRecognizing = false;
        if (isCalling && isWaitingForUser) {
          // Auto-restart to keep listening
          setTimeout(safeStartRecognition, 200);
        } else {
          setMicBanner(false);
        }
      };

      return rec;
    }

    async function handleUserUtterance(speech) {
      if (!speech || !isCalling) return;

      isWaitingForUser = false;
      safeStopRecognition();

      addMessage("You", speech, "user");
      callHistory.push({ role: "user", content: speech });
      document.getElementById('callStatusText').innerText = "Avi AI thinking...";

      try {
        const res = await fetch("/api/v1/telephony/turn_web", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ history: callHistory, caller_input: speech, call_id: callId })
        });
        const data = await res.json();
        const reply = data.reply;
        addMessage("Avi AI", reply, "ai");
        callHistory.push({ role: "model", content: reply });

        // Check farewell / bye
        const lowerSpeech = speech.toLowerCase();
        if (data.is_farewell || lowerSpeech.includes("bye") || lowerSpeech.includes("alvida") || lowerSpeech.includes("theek hai rakhta")) {
          document.getElementById('callStatusText').innerText = "Avi AI saying goodbye...";
          speakMaleVoice(reply, data.audio_base64, () => {
            document.getElementById('callStatusText').innerText = "Call disconnected automatically by Avi AI.";
            setTimeout(() => {
              endCall();
            }, 600);
          });
        } else {
          speakMaleVoice(reply, data.audio_base64, () => {
            if (isCalling) {
              isWaitingForUser = true;
              document.getElementById('callStatusText').innerText = "Listening to you... (Speak now)";
              safeStartRecognition();
            }
          });
        }
      } catch (err) {
        console.error(err);
        document.getElementById('callStatusText').innerText = "Network error. Try again.";
        if (isCalling) {
          isWaitingForUser = true;
          safeStartRecognition();
        }
      }
    }

    function sendManualText() {
      const input = document.getElementById('manualInput');
      const text = input.value.trim();
      if (!text || !isCalling) return;
      input.value = "";
      handleUserUtterance(text);
    }

    async function startCall() {
      // Request mic permission explicitly upfront
      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        try {
          await navigator.mediaDevices.getUserMedia({ audio: true });
        } catch(e) {
          console.warn("getUserMedia failed or denied:", e);
        }
      }

      isCalling = true;
      secondsElapsed = 0;
      document.getElementById('callStatusText').innerText = "Connecting...";
      document.getElementById('startCallBtn').disabled = true;
      document.getElementById('micToggleBtn').disabled = false;
      document.getElementById('endCallBtn').disabled = false;
      document.getElementById('textInputRow').style.display = 'flex';
      document.getElementById('transcriptBox').innerHTML = "";
      document.getElementById('summaryCard').style.display = "none";

      timerInterval = setInterval(updateTimer, 1000);
      recognition = initSpeechRecognition();

      addMessage("Avi AI", greeting, "ai");
      callHistory = [{ role: "model", content: greeting }];

      speakMaleVoice(greeting, null, () => {
        if (isCalling) {
          isWaitingForUser = true;
          document.getElementById('callStatusText').innerText = "Listening to you... (Speak now)";
          safeStartRecognition();
        }
      });
    }

    async function endCall() {
      if (!isCalling) return;
      isCalling = false;
      isWaitingForUser = false;
      clearInterval(timerInterval);
      safeStopRecognition();
      if (currentAudio) { try { currentAudio.pause(); } catch(e){} }

      document.getElementById('avatarIcon').classList.remove('speaking');
      document.getElementById('callStatusText').innerText = "Call Ended";
      document.getElementById('callStatusText').style.color = "#da3633";
      document.getElementById('startCallBtn').disabled = false;
      document.getElementById('micToggleBtn').disabled = true;
      document.getElementById('endCallBtn').disabled = true;
      setMicBanner(false);

      // Request post-call summary from backend
      const card = document.getElementById('summaryCard');
      card.style.display = "block";
      document.getElementById('summaryContent').innerText = "Analyzing transcript with Gemini Flash...";

      try {
        const res = await fetch("/api/v1/telephony/summarize_web", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ history: callHistory, call_id: callId })
        });
        const summary = await res.json();
        document.getElementById('summaryContent').innerHTML = `
          <b>👤 Caller:</b> ${summary.caller_name || 'Not specified'}<br>
          <b>🎯 Purpose:</b> ${summary.call_purpose || 'General inquiry'}<br>
          <b>🚨 Urgency:</b> ${summary.urgency || 'Normal'}<br>
          <b>📞 Callback:</b> ${summary.callback_requested ? 'Yes' : 'No'}<br>
          <b>📝 Summary:</b> ${summary.full_summary || 'Call recorded.'}
        `;
      } catch (err) {
        document.getElementById('summaryContent').innerText = "Summary saved to cloud.";
      }
    }
  </script>
</body>
</html>
"""

@router.get("/webphone", response_class=HTMLResponse)
async def web_phone_page():
    return HTMLResponse(content=HTML_PAGE)

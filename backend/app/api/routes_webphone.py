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
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
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
      max-width: 420px;
      background: #161b22;
      border: 1px solid #30363d;
      border-radius: 36px;
      padding: 28px 24px;
      box-shadow: 0 20px 50px rgba(0,0,0,0.6);
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    .status-bar {
      width: 100%;
      display: flex;
      justify-content: space-between;
      font-size: 13px;
      color: #8b949e;
      margin-bottom: 24px;
    }
    .caller-avatar {
      width: 90px;
      height: 90px;
      border-radius: 50%;
      background: linear-gradient(135deg, #1f6feb, #8957e5);
      display: flex;
      justify-content: center;
      align-items: center;
      font-size: 38px;
      margin-bottom: 16px;
      box-shadow: 0 0 25px rgba(31, 111, 235, 0.4);
    }
    .caller-name {
      font-size: 24px;
      font-weight: 700;
      color: #f0f6fc;
      margin-bottom: 6px;
    }
    .call-status {
      font-size: 14px;
      color: #3fb950;
      font-weight: 600;
      letter-spacing: 0.5px;
      margin-bottom: 24px;
    }
    .live-transcript {
      width: 100%;
      height: 220px;
      background: #0d1117;
      border: 1px solid #21262d;
      border-radius: 18px;
      padding: 14px;
      overflow-y: auto;
      font-size: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-bottom: 24px;
    }
    .msg {
      padding: 8px 12px;
      border-radius: 12px;
      max-width: 85%;
      line-height: 1.4;
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
    .controls {
      display: flex;
      gap: 20px;
      width: 100%;
      justify-content: center;
    }
    .btn {
      width: 65px;
      height: 65px;
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
    .btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .summary-card {
      display: none;
      width: 100%;
      margin-top: 16px;
      padding: 14px;
      background: #0d1117;
      border: 1px solid #30363d;
      border-radius: 14px;
      font-size: 13px;
    }
    .summary-card h4 { color: #58a6ff; margin-bottom: 8px; }
  </style>
</head>
<body>
  <div class="phone-container">
    <div class="status-bar">
      <span>Avi AI Live Attendant</span>
      <span id="callTimer">00:00</span>
    </div>

    <div class="caller-avatar">📞</div>
    <div class="caller-name">Avi AI Assistant</div>
    <div class="call-status" id="callStatusText">Ready to Call</div>

    <div class="live-transcript" id="transcriptBox">
      <div class="msg ai"><b>Avi AI:</b> Green call button dabayein Avi AI se baat karne ke liye! (No SIM/ISD needed).</div>
    </div>

    <div class="controls">
      <button class="btn btn-call" id="startCallBtn" onclick="startCall()">📞</button>
      <button class="btn btn-hangup" id="endCallBtn" onclick="endCall()" disabled>❌</button>
    </div>

    <div class="summary-card" id="summaryCard">
      <h4>📋 Post-Call Extracted Summary</h4>
      <div id="summaryContent">Extracting details...</div>
    </div>
  </div>

  <script>
    let isCalling = false;
    let timerInterval = null;
    let secondsElapsed = 0;
    let recognition = null;
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
      let called = false;
      const done = () => {
        if (!called && callback) {
          called = true;
          callback();
        }
      };
      currentAudio.onended = done;
      currentAudio.onerror = (e) => {
        console.error("Audio error:", e);
        done();
      };
      currentAudio.play().catch((err) => {
        console.error("Playback failed:", err);
        done();
      });
    }

    function speakMaleVoice(text, audioBase64, callback) {
      if (audioBase64) {
        playAudio("data:audio/mp3;base64," + audioBase64, callback);
      } else {
        playAudio("/api/v1/telephony/tts?text=" + encodeURIComponent(text), callback);
      }
    }

    function initSpeechRecognition() {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SpeechRecognition) {
        alert("Speech Recognition not supported in this browser. Please use Google Chrome.");
        return null;
      }
      const rec = new SpeechRecognition();
      rec.lang = 'hi-IN';
      rec.continuous = false;
      rec.interimResults = false;

      rec.onresult = async (event) => {
        const speech = event.results[0][0].transcript;
        if (!speech) return;

        addMessage("You", speech, "user");
        callHistory.push({ role: "user", content: speech });
        document.getElementById('callStatusText').innerText = "Avi AI thinking...";

        // Call backend API
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

          // Automatic Disconnection if caller or AI says farewell / bye
          if (data.is_farewell || speech.toLowerCase().includes("bye") || speech.toLowerCase().includes("alvida")) {
            document.getElementById('callStatusText').innerText = "Avi AI saying goodbye...";
            speakMaleVoice(reply, data.audio_base64, () => {
              document.getElementById('callStatusText').innerText = "Call disconnected automatically by Avi AI.";
              setTimeout(() => {
                endCall();
              }, 400);
            });
          } else {
            speakMaleVoice(reply, data.audio_base64, () => {
              if (isCalling) {
                document.getElementById('callStatusText').innerText = "Listening to you... (Speak now)";
                try { rec.start(); } catch(e){}
              }
            });
          }
        } catch (err) {
          console.error(err);
        }
      };

      rec.onerror = (e) => {
        if (isCalling && e.error !== "no-speech") {
          console.log("Rec error:", e.error);
        }
      };

      return rec;
    }

    function startCall() {
      isCalling = true;
      secondsElapsed = 0;
      document.getElementById('callStatusText').innerText = "Call Connected";
      document.getElementById('startCallBtn').disabled = true;
      document.getElementById('endCallBtn').disabled = false;
      document.getElementById('transcriptBox').innerHTML = "";
      document.getElementById('summaryCard').style.display = "none";

      timerInterval = setInterval(updateTimer, 1000);
      recognition = initSpeechRecognition();

      addMessage("Avi AI", greeting, "ai");
      callHistory = [{ role: "model", content: greeting }];

      speakMaleVoice(greeting, null, () => {
        if (isCalling && recognition) {
          document.getElementById('callStatusText').innerText = "Listening to you... (Speak now)";
          try { recognition.start(); } catch(e){}
        }
      });
    }

    async function endCall() {
      if (!isCalling) return;
      isCalling = false;
      clearInterval(timerInterval);
      if (recognition) { try { recognition.stop(); } catch(e){} }
      if (currentAudio) { try { currentAudio.pause(); } catch(e){} }

      document.getElementById('callStatusText').innerText = "Call Ended";
      document.getElementById('callStatusText').style.color = "#da3633";
      document.getElementById('startCallBtn').disabled = false;
      document.getElementById('endCallBtn').disabled = true;

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
          <b>🎯 Purpose:</b> ${summary.call_purpose}<br>
          <b>🚨 Urgency:</b> ${summary.urgency}<br>
          <b>📞 Callback:</b> ${summary.callback_requested ? 'Yes' : 'No'}<br>
          <b>📝 Summary:</b> ${summary.full_summary}
        `;
      } catch (err) {
        document.getElementById('summaryContent').innerText = "Failed to load summary.";
      }
    }
  </script>
</body>
</html>
"""

@router.get("/webphone", response_class=HTMLResponse)
async def web_phone_page():
    return HTMLResponse(content=HTML_PAGE)

import os
import sys
import asyncio
import datetime
import tempfile
import time
os.environ["PYGAME_HIDE_SUPPORT_PROMPT"] = "hide"
import pygame
import edge_tts
import pyttsx3
from dotenv import load_dotenv

# Load environment variables
load_dotenv(os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend", ".env")))

# Add backend to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend")))

from app.core.database import init_db, save_call_record, append_transcript_turn, finalize_call_record
from app.models.schemas import TranscriptTurn
from app.conversation.engine import conversation_engine
from app.extraction.summarizer import call_summarizer

import re

# Realistic Human Neural Voice (Indian Hindi / Hinglish / English)
DEFAULT_NEURAL_VOICE = "hi-IN-MadhurNeural"
AMBIENCE_FILE = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "call_ambience.wav"))

async def speak_audio(text: str, voice: str = DEFAULT_NEURAL_VOICE):
    """
    Speaks the response out loud with 100% fluent human delivery:
    - Cleans out all artificial halting punctuation (ellipses, long dashes)
    - Speeds up to natural conversational speech (+22%)
    - Plays via pygame.mixer.music with a 4096 buffer for zero jitter/stutter
    """
    tmp_path = None
    try:
        # Strip halting punctuation that causes robotic pauses
        clean_text = re.sub(r'\.{2,}', '', text)
        clean_text = re.sub(r'—', ', ', clean_text)
        clean_text = re.sub(r'\s+', ' ', clean_text).strip()

        with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as f:
            tmp_path = f.name
        
        # Conversational speed +22% for human fluency
        communicate = edge_tts.Communicate(clean_text, voice, rate="+22%")
        await communicate.save(tmp_path)
        
        # Play via streaming music engine (no buffer underrun/stutter)
        pygame.mixer.music.load(tmp_path)
        pygame.mixer.music.set_volume(1.0)
        pygame.mixer.music.play()
        
        while pygame.mixer.music.get_busy():
            await asyncio.sleep(0.04)
    except Exception as e:
        # Fallback to local pyttsx3
        try:
            engine = pyttsx3.init()
            engine.setProperty("rate", 175)
            engine.say(text)
            engine.runAndWait()
        except Exception:
            pass
    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except Exception:
                pass

async def run_simulated_call():
    init_db()
    call_id = f"sim-{int(datetime.datetime.now().timestamp())}"
    caller_number = "+919876543210"
    start_time = datetime.datetime.now().isoformat()

    print("=" * 65)
    print("📞 AVI AI — INCOMING CALL SIMULATION SESSION")
    print(f"Incoming call from: {caller_number}")
    print(f"🎙️  Voice Engine: Studio Human Neural Voice ({DEFAULT_NEURAL_VOICE})")
    print(f"⚡  Cadence: Fluent Human Telephone Speed (+22%)")
    print("=" * 65)

    # Initialize Pygame audio mixer with solid buffer (4096) for buttery smoothness
    pygame.mixer.init(frequency=24000, buffer=4096)

    greeting = (
        "Haan ji hello! Main Avijit ka assistant bol raha hoon. "
        "Avijit abhi phone ke paas nahi hain, to aap batayein kya message note karwaana hai?"
    )

    save_call_record(call_id, caller_number, start_time, status="IN_PROGRESS")
    append_transcript_turn(call_id, TranscriptTurn(speaker="AI", text=greeting, timestamp=start_time))

    print(f"\n🤖 Avi AI: {greeting}")
    print("🔊 (Speaking in fluent, natural human speed...)")
    await speak_audio(greeting)

    history = [{"role": "model", "content": greeting}]
    turns_count = 0

    print("\n💡 Type your response as the caller (Hindi, English, Bengali, or Hinglish).")
    print("💡 Type 'bye' or 'done' to end the call.\n")

    while True:
        try:
            caller_input = input("👤 Caller: ").strip()
        except (EOFError, KeyboardInterrupt):
            break

        if not caller_input:
            continue

        timestamp = datetime.datetime.now().isoformat()
        append_transcript_turn(call_id, TranscriptTurn(speaker="Caller", text=caller_input, timestamp=timestamp))
        history.append({"role": "user", "content": caller_input})
        turns_count += 1

        lower = caller_input.lower()
        if any(w in lower for w in ["bye", "alvida", "thank you", "dhanyavaad", "done", "exit", "quit"]):
            closing = "Theek hai ji, maine saari baat note kar li hai aur main Avijit ko turant bata doonga. Shukriya, have a nice day! Bye."
            print(f"\n🤖 Avi AI: {closing}")
            await speak_audio(closing)
            append_transcript_turn(call_id, TranscriptTurn(speaker="AI", text=closing, timestamp=timestamp))
            break

        # Generate response using Gemini Flash
        print("⏳ (Avi AI thinking...)")
        ai_reply = await conversation_engine.generate_response(history, caller_input)
        print(f"\n🤖 Avi AI: {ai_reply}")
        await speak_audio(ai_reply)

        history.append({"role": "model", "content": ai_reply})
        append_transcript_turn(call_id, TranscriptTurn(speaker="AI", text=ai_reply, timestamp=timestamp))

    try:
        pygame.mixer.quit()
    except Exception:
        pass

    print("\n" + "=" * 65)
    print("📋 CALL DISCONNECTED — EXTRACTING POST-CALL SUMMARY...")
    print("=" * 65)

    transcript_turns = [
        {"speaker": "Caller" if h["role"] == "user" else "Avi AI", "text": h["content"]}
        for h in history
    ]
    summary = await call_summarizer.summarize_call(caller_number, transcript_turns)
    finalize_call_record(call_id, turns_count * 12, summary, status="COMPLETED")

    print(f"\n👤 Caller Name:        {summary.caller_name or 'Not specified'}")
    print(f"🎯 Purpose of Call:     {summary.call_purpose}")
    print(f"🚨 Urgency:             {summary.urgency}")
    print(f"📞 Callback Requested:  {'Yes' if summary.callback_requested else 'No'}")
    if summary.preferred_callback_time:
        print(f"⏰ Preferred Time:      {summary.preferred_callback_time}")
    if summary.action_items:
        print("📌 Action Items for Avijit:")
        for item in summary.action_items:
            print(f"   • {item}")
    print(f"\n📝 Executive Summary:\n{summary.full_summary}\n")
    print("=" * 65)
    print("✅ Call record and structured summary saved to SQLite database!")

if __name__ == "__main__":
    asyncio.run(run_simulated_call())

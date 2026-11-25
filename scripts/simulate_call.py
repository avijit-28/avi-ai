import os
import sys
import asyncio
import datetime
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

def speak_audio(text: str):
    """Speaks the response out loud through computer speakers."""
    try:
        engine = pyttsx3.init()
        engine.setProperty("rate", 165) # Natural speaking speed
        engine.say(text)
        engine.runAndWait()
    except Exception as e:
        print(f"[Audio Output Error: {e}]")

async def run_simulated_call():
    init_db()
    call_id = f"sim-{int(datetime.datetime.now().timestamp())}"
    caller_number = "+919876543210"
    start_time = datetime.datetime.now().isoformat()

    print("=" * 65)
    print("📞 AVI AI — INCOMING CALL SIMULATION SESSION")
    print(f"Incoming call from: {caller_number}")
    print("=" * 65)

    greeting = (
        "Hello! How are you? I'm the Avi AI assistant. Avijit is currently unavailable. "
        "This call is recorded so I can take a message for him. How can I assist you today?"
    )

    save_call_record(call_id, caller_number, start_time, status="IN_PROGRESS")
    append_transcript_turn(call_id, TranscriptTurn(speaker="AI", text=greeting, timestamp=start_time))

    print(f"\n🤖 Avi AI: {greeting}")
    print("🔊 (Speaking audio out loud...)")
    speak_audio(greeting)

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
            closing = "Thank you for the message. I have recorded everything for Avijit. Have a wonderful day! Goodbye."
            print(f"\n🤖 Avi AI: {closing}")
            speak_audio(closing)
            append_transcript_turn(call_id, TranscriptTurn(speaker="AI", text=closing, timestamp=timestamp))
            break

        # Generate response using Gemini Flash
        print("⏳ (Avi AI thinking...)")
        ai_reply = await conversation_engine.generate_response(history, caller_input)
        print(f"\n🤖 Avi AI: {ai_reply}")
        speak_audio(ai_reply)

        history.append({"role": "model", "content": ai_reply})
        append_transcript_turn(call_id, TranscriptTurn(speaker="AI", text=ai_reply, timestamp=timestamp))

    print("\n" + "=" * 65)
    print("📋 CALL DISCONNECTED — EXTRACTING POST-CALL SUMMARY...")
    print("=" * 65)

    transcript_turns = [{"speaker": h["role"], "text": h["content"]} for h in history]
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

import datetime
import base64
import re
import edge_tts
from fastapi import APIRouter, Request, Response
from twilio.twiml.voice_response import VoiceResponse, Gather
from app.core.database import save_call_record, append_transcript_turn, finalize_call_record, get_call_record, get_setting
from app.models.schemas import TranscriptTurn
from app.conversation.engine import conversation_engine
from app.conversation.prompts import get_greeting
from app.extraction.summarizer import call_summarizer

router = APIRouter(prefix="/telephony", tags=["Telephony"])

# Realistic Indian Male Neural Voice
DEFAULT_MALE_VOICE = "hi-IN-MadhurNeural"

async def generate_speech_bytes(text: str, voice: str = DEFAULT_MALE_VOICE) -> bytes:
    """Generates high-definition Male neural audio bytes in-memory."""
    try:
        clean_text = re.sub(r'\.{2,}', '', text)
        clean_text = re.sub(r'—', ', ', clean_text)
        clean_text = re.sub(r'\s+', ' ', clean_text).strip()
        c = edge_tts.Communicate(clean_text, voice, rate="+20%")
        audio_data = bytearray()
        async for chunk in c.stream():
            if chunk['type'] == 'audio':
                audio_data.extend(chunk['data'])
        return bytes(audio_data)
    except Exception as e:
        print(f"Error generating speech bytes: {e}")
        return b""

# In-memory turn tracking during active calls
active_conversations = {}

@router.post("/voice")
async def handle_incoming_call(request: Request):
    """
    Webhook triggered when an unanswered forwarded call hits the Avi AI attendant.
    """
    form_data = await request.form()
    call_sid = form_data.get("CallSid", "unknown-call")
    caller = form_data.get("From", "Unknown")
    start_time = datetime.datetime.now().isoformat()

    # Initialize DB record
    save_call_record(call_id=call_sid, caller_number=caller, start_time=start_time, status="IN_PROGRESS")
    active_conversations[call_sid] = []

    owner_status = get_setting("owner_status", "Away")
    custom_note = get_setting("custom_status_note", "")
    greeting = get_greeting(owner_status, custom_note)

    # Save AI greeting to transcript
    append_transcript_turn(call_sid, TranscriptTurn(
        speaker="AI",
        text=greeting,
        timestamp=start_time
    ))
    active_conversations[call_sid].append({"role": "model", "content": greeting})

    response = VoiceResponse()
    # Speak greeting using natural Polly Aditi (fluent in Hindi & Indian English)
    response.say(greeting, voice="Polly.Aditi", language="hi-IN")

    # Gather caller speech
    gather = Gather(
        input="speech",
        action=f"/api/v1/telephony/turn?call_sid={call_sid}",
        method="POST",
        speech_timeout="auto",
        timeout=5,
        language="hi-IN"
    )
    response.append(gather)

    # If caller remains silent
    response.say("Aapki aawaz nahi aa rahi hai. Aap baad me dobara call kar sakte hain. Shukriya, alvida!", voice="Polly.Aditi", language="hi-IN")
    response.hangup()

    return Response(content=str(response), media_type="application/xml")

@router.post("/turn")
async def handle_speech_turn(request: Request, call_sid: str):
    """
    Processes each conversational turn as caller speaks.
    """
    form_data = await request.form()
    caller_speech = form_data.get("SpeechResult", "").strip()
    now_str = datetime.datetime.now().isoformat()

    response = VoiceResponse()

    if not caller_speech:
        response.say("Mujhe theek se sunai nahi diya. Kya aap dobara bol sakte hain?", voice="Polly.Aditi", language="hi-IN")
        gather = Gather(
            input="speech",
            action=f"/api/v1/telephony/turn?call_sid={call_sid}",
            method="POST",
            speech_timeout="auto",
            timeout=5,
            language="hi-IN"
        )
        response.append(gather)
        return Response(content=str(response), media_type="application/xml")

    # Record caller turn
    append_transcript_turn(call_sid, TranscriptTurn(
        speaker="Caller",
        text=caller_speech,
        timestamp=now_str
    ))

    history = active_conversations.get(call_sid, [])
    history.append({"role": "user", "content": caller_speech})

    # Generate Gemini Flash response
    ai_reply = await conversation_engine.generate_response(history, caller_speech)
    history.append({"role": "model", "content": ai_reply})
    active_conversations[call_sid] = history

    # Record AI turn
    append_transcript_turn(call_sid, TranscriptTurn(
        speaker="AI",
        text=ai_reply,
        timestamp=now_str
    ))

    # Speak response
    response.say(ai_reply, voice="Polly.Aditi", language="hi-IN")

    # Check if conversation reached natural closing
    lower_caller = caller_speech.lower()
    is_farewell = any(w in lower_caller for w in ["bye", "thank you", "alvida", "dhanyavaad", "that's all", "shukriya"])

    if is_farewell:
        response.say("Theek hai ji, maine saara message note kar liya hai. Avijit aapse jald hi contact karenge. Have a nice day, bye!", voice="Polly.Aditi", language="hi-IN")
        response.hangup()
    else:
        # Continue turn-taking
        gather = Gather(
            input="speech",
            action=f"/api/v1/telephony/turn?call_sid={call_sid}",
            method="POST",
            speech_timeout="auto",
            timeout=5,
            language="hi-IN"
        )
        response.append(gather)
        response.say("Shukriya, main Avijit ko bata doonga. Alvida!", voice="Polly.Aditi", language="hi-IN")
        response.hangup()

    return Response(content=str(response), media_type="application/xml")

@router.post("/status")
async def handle_call_status(request: Request):
    """
    Triggered by Twilio when call completes.
    Extracts summary, action items, and finalizes database record.
    """
    form_data = await request.form()
    call_sid = form_data.get("CallSid", "")
    duration = int(form_data.get("CallDuration", "0"))
    caller = form_data.get("From", "Unknown")

    record = get_call_record(call_sid)
    transcript_turns = [t.model_dump() for t in record.transcript] if record else []

    # Run Gemini Flash structured extraction
    summary = await call_summarizer.summarize_call(caller, transcript_turns)
    finalize_call_record(call_sid, duration, summary, status="COMPLETED")

    # Cleanup in-memory state
    active_conversations.pop(call_sid, None)

    return {"status": "ok", "call_sid": call_sid, "summary": summary.model_dump()}

@router.post("/turn_web")
async def handle_turn_web(request: Request):
    data = await request.json()
    history = data.get("history", [])
    caller_input = data.get("caller_input", "")
    call_id = data.get("call_id", f"web-{int(datetime.datetime.now().timestamp())}")
    
    # Initialize DB record if first turn
    if len(history) <= 2:
        save_call_record(call_id=call_id, caller_number="+91-WEB-CALLER", start_time=datetime.datetime.now().isoformat(), status="IN_PROGRESS")
    
    # Generate AI response
    reply = await conversation_engine.generate_response(history, caller_input)
    
    # Save turns to DB
    append_transcript_turn(call_id, TranscriptTurn(speaker="Caller", text=caller_input, timestamp=datetime.datetime.now().isoformat()))
    append_transcript_turn(call_id, TranscriptTurn(speaker="AI", text=reply, timestamp=datetime.datetime.now().isoformat()))
    
    # Generate Male neural speech in-memory
    audio_bytes = await generate_speech_bytes(reply)
    audio_b64 = base64.b64encode(audio_bytes).decode('utf-8') if audio_bytes else ""

    # Check if caller or reply indicates call termination
    lower_caller = caller_input.lower()
    farewell_words = ["bye", "by", "alvida", "thank", "dhanyavaad", "shukriya", "tata", "goodbye", "bas itna", "rakh", "rakho", "ok bye", "done"]
    is_farewell = any(w in lower_caller for w in farewell_words) or any(w in reply.lower() for w in ["alvida", "goodbye", "have a nice day", "have a great day", "bye"])
    
    return {"reply": reply, "is_farewell": is_farewell, "audio_base64": audio_b64}

@router.get("/tts")
async def tts_audio_endpoint(text: str):
    """Streams realistic Male neural speech MP3 audio directly to browser."""
    audio_bytes = await generate_speech_bytes(text)
    return Response(content=audio_bytes, media_type="audio/mpeg")

@router.post("/summarize_web")
async def handle_summarize_web(request: Request):
    data = await request.json()
    history = data.get("history", [])
    call_id = data.get("call_id", "web-call")
    
    transcript_turns = [
        {"speaker": "Caller" if h.get("role") == "user" else "Avi AI", "text": h.get("content", "")}
        for h in history
    ]
    summary = await call_summarizer.summarize_call("+91-WEB-CALLER", transcript_turns)
    finalize_call_record(call_id, len(history) * 10, summary, status="COMPLETED")
    return summary.model_dump()

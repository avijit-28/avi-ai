import datetime
from fastapi import APIRouter, Request, Response
from twilio.twiml.voice_response import VoiceResponse, Gather
from app.core.database import save_call_record, append_transcript_turn, finalize_call_record, get_call_record
from app.models.schemas import TranscriptTurn
from app.conversation.engine import conversation_engine
from app.extraction.summarizer import call_summarizer

router = APIRouter(prefix="/telephony", tags=["Telephony"])

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

    greeting = (
        "Hello! How are you? I'm the Avi AI assistant. Avijit is currently unavailable. "
        "This call is recorded so I can take a message for him. How can I assist you today?"
    )

    # Save AI greeting to transcript
    append_transcript_turn(call_sid, TranscriptTurn(
        speaker="AI",
        text=greeting,
        timestamp=start_time
    ))
    active_conversations[call_sid].append({"role": "model", "content": greeting})

    response = VoiceResponse()
    # Speak greeting using natural Indian English voice
    response.say(greeting, voice="Polly.Aditi", language="en-IN")

    # Gather caller speech
    gather = Gather(
        input="speech",
        action=f"/api/v1/telephony/turn?call_sid={call_sid}",
        method="POST",
        speech_timeout="auto",
        timeout=5,
        language="en-IN"
    )
    response.append(gather)

    # If caller remains silent
    response.say("I didn't hear anything. If you'd like to leave a message, please speak after the tone, or call back later. Goodbye!", voice="Polly.Aditi", language="en-IN")
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
        response.say("I didn't quite catch that. Could you please repeat your message?", voice="Polly.Aditi", language="en-IN")
        gather = Gather(
            input="speech",
            action=f"/api/v1/telephony/turn?call_sid={call_sid}",
            method="POST",
            speech_timeout="auto",
            timeout=5,
            language="en-IN"
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
    response.say(ai_reply, voice="Polly.Aditi", language="en-IN")

    # Check if conversation reached natural closing
    lower_caller = caller_speech.lower()
    is_farewell = any(w in lower_caller for w in ["bye", "thank you", "alvida", "dhanyavaad", "that's all"])

    if is_farewell:
        response.say("Thank you. I have recorded everything for Avijit. Have a great day!", voice="Polly.Aditi", language="en-IN")
        response.hangup()
    else:
        # Continue turn-taking
        gather = Gather(
            input="speech",
            action=f"/api/v1/telephony/turn?call_sid={call_sid}",
            method="POST",
            speech_timeout="auto",
            timeout=5,
            language="en-IN"
        )
        response.append(gather)
        response.say("Thank you for your message. Goodbye!", voice="Polly.Aditi", language="en-IN")
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

import os
import re
from typing import List
from fastapi import APIRouter, HTTPException, Response
from fastapi.responses import FileResponse
from app.core.database import list_call_records, get_call_record
from app.models.schemas import CallRecordResponse
import edge_tts

router = APIRouter(prefix="/calls", tags=["Calls"])

RECORDINGS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "recordings")
os.makedirs(RECORDINGS_DIR, exist_ok=True)

@router.get("", response_model=List[CallRecordResponse])
async def get_all_calls():
    """
    Returns all AI-attended call records, summaries, and transcripts.
    """
    return list_call_records()

@router.get("/{call_id}", response_model=CallRecordResponse)
async def get_call_by_id(call_id: str):
    record = get_call_record(call_id)
    if not record:
        raise HTTPException(status_code=404, detail="Call record not found")
    return record

@router.get("/{call_id}/audio")
async def get_call_audio(call_id: str):
    """
    Streams the voice memo / call dialogue recording as an MP3 audio file.
    Caches the generated MP3 on disk for instantaneous playback.
    """
    safe_call_id = re.sub(r'[^a-zA-Z0-9_\-]', '_', call_id)
    file_path = os.path.join(RECORDINGS_DIR, f"{safe_call_id}.mp3")

    if os.path.exists(file_path) and os.path.getsize(file_path) > 0:
        return FileResponse(file_path, media_type="audio/mpeg", filename=f"call_{safe_call_id}.mp3")

    record = get_call_record(call_id)
    if not record:
        raise HTTPException(status_code=404, detail="Call record not found")

    caller_info = record.summary.caller_name if (record.summary and record.summary.caller_name) else record.caller_number
    lines = []
    if record.summary:
        lines.append(f"Avi AI voice note. Call from {caller_info}.")
        lines.append(f"Purpose: {record.summary.call_purpose}.")
        lines.append(f"Summary: {record.summary.full_summary}.")
    else:
        lines.append(f"Avi AI call record for {record.caller_number}.")

    if record.transcript:
        lines.append("Call dialogue:")
        for t in record.transcript:
            speaker_label = "Caller" if t.speaker.lower() == "caller" else "Avi AI"
            lines.append(f"{speaker_label}: {t.text}")

    full_text = " ".join(lines)
    clean_text = re.sub(r'\.{2,}', '', full_text)
    clean_text = re.sub(r'—', ', ', clean_text)
    clean_text = re.sub(r'\s+', ' ', clean_text).strip()

    try:
        c = edge_tts.Communicate(clean_text, "hi-IN-MadhurNeural", rate="+15%")
        await c.save(file_path)
        return FileResponse(file_path, media_type="audio/mpeg", filename=f"call_{safe_call_id}.mp3")
    except Exception as e:
        print(f"Error synthesizing call audio: {e}")
        raise HTTPException(status_code=500, detail="Failed to synthesize call audio")

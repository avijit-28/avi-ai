import sqlite3
import json
from typing import List, Optional
from app.core.config import settings
from app.models.schemas import CallRecordResponse, ExtractedCallSummary, TranscriptTurn

def get_connection():
    conn = sqlite3.connect(settings.DATABASE_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS call_records (
            id TEXT PRIMARY KEY,
            caller_number TEXT NOT NULL,
            start_time TEXT NOT NULL,
            duration_seconds INTEGER DEFAULT 0,
            status TEXT NOT NULL,
            summary_json TEXT,
            transcript_json TEXT
        )
    """)
    conn.commit()
    conn.close()

def save_call_record(call_id: str, caller_number: str, start_time: str, status: str = "IN_PROGRESS"):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        INSERT OR REPLACE INTO call_records (id, caller_number, start_time, status, transcript_json)
        VALUES (?, ?, ?, ?, ?)
    """, (call_id, caller_number, start_time, status, json.dumps([])))
    conn.commit()
    conn.close()

def append_transcript_turn(call_id: str, turn: TranscriptTurn):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT transcript_json FROM call_records WHERE id = ?", (call_id,))
    row = cursor.fetchone()
    if row:
        transcript = json.loads(row["transcript_json"] or "[]")
        transcript.append(turn.model_dump())
        cursor.execute("UPDATE call_records SET transcript_json = ? WHERE id = ?", (json.dumps(transcript), call_id))
        conn.commit()
    conn.close()

def finalize_call_record(call_id: str, duration_seconds: int, summary: Optional[ExtractedCallSummary], status: str = "COMPLETED"):
    conn = get_connection()
    cursor = conn.cursor()
    summary_json = json.dumps(summary.model_dump()) if summary else None
    cursor.execute("""
        UPDATE call_records 
        SET duration_seconds = ?, summary_json = ?, status = ?
        WHERE id = ?
    """, (duration_seconds, summary_json, status, call_id))
    conn.commit()
    conn.close()

def list_call_records() -> List[CallRecordResponse]:
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM call_records ORDER BY start_time DESC")
    rows = cursor.fetchall()
    results = []
    for r in rows:
        summary = ExtractedCallSummary(**json.loads(r["summary_json"])) if r["summary_json"] else None
        transcript = [TranscriptTurn(**t) for t in json.loads(r["transcript_json"] or "[]")]
        results.append(CallRecordResponse(
            id=r["id"],
            caller_number=r["caller_number"],
            start_time=r["start_time"],
            duration_seconds=r["duration_seconds"],
            summary=summary,
            transcript=transcript,
            status=r["status"]
        ))
    conn.close()
    return results

def get_call_record(call_id: str) -> Optional[CallRecordResponse]:
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM call_records WHERE id = ?", (call_id,))
    r = cursor.fetchone()
    conn.close()
    if not r:
        return None
    summary = ExtractedCallSummary(**json.loads(r["summary_json"])) if r["summary_json"] else None
    transcript = [TranscriptTurn(**t) for t in json.loads(r["transcript_json"] or "[]")]
    return CallRecordResponse(
        id=r["id"],
        caller_number=r["caller_number"],
        start_time=r["start_time"],
        duration_seconds=r["duration_seconds"],
        summary=summary,
        transcript=transcript,
        status=r["status"]
    )

from typing import List, Optional
from pydantic import BaseModel, Field

class TranscriptTurn(BaseModel):
    speaker: str = Field(description="'AI' or 'Caller'")
    text: str
    timestamp: str

class ExtractedCallSummary(BaseModel):
    caller_name: Optional[str] = Field(None, description="Name of the caller if provided")
    call_purpose: str = Field(description="Summary of why the caller called")
    callback_requested: bool = Field(description="Whether the caller requested a callback")
    preferred_callback_time: Optional[str] = Field(None, description="Preferred callback time if mentioned")
    callback_number: Optional[str] = Field(None, description="Phone number for callback")
    urgency: str = Field("Normal", description="'Low', 'Normal', or 'Urgent'")
    action_items: List[str] = Field(default_factory=list, description="List of follow-up tasks for Avijit")
    full_summary: str = Field(description="Concise 2-3 sentence executive summary of the call")

class CallRecordResponse(BaseModel):
    id: str
    caller_number: str
    start_time: str
    duration_seconds: int
    summary: Optional[ExtractedCallSummary] = None
    transcript: List[TranscriptTurn] = Field(default_factory=list)
    status: str
    audio_url: Optional[str] = None

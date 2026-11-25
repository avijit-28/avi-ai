from typing import List
from fastapi import APIRouter, HTTPException
from app.core.database import list_call_records, get_call_record
from app.models.schemas import CallRecordResponse

router = APIRouter(prefix="/calls", tags=["Calls"])

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

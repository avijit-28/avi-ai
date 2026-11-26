from typing import Optional
from fastapi import APIRouter
from pydantic import BaseModel
from app.core.database import get_all_settings, set_setting

router = APIRouter(prefix="/settings", tags=["Settings"])

class SettingsUpdate(BaseModel):
    owner_status: Optional[str] = "Away"
    custom_status_note: Optional[str] = ""
    assistant_name: Optional[str] = "Avi"
    forwarding_number: Optional[str] = "+1 938 444 8902"

@router.get("")
async def fetch_settings():
    return get_all_settings()

@router.post("")
async def update_settings(payload: SettingsUpdate):
    if payload.owner_status is not None:
        set_setting("owner_status", payload.owner_status)
    if payload.custom_status_note is not None:
        set_setting("custom_status_note", payload.custom_status_note)
    if payload.assistant_name is not None:
        set_setting("assistant_name", payload.assistant_name)
    if payload.forwarding_number is not None:
        set_setting("forwarding_number", payload.forwarding_number)
    return get_all_settings()

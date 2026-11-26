import json
from typing import List, Dict
from app.core.config import settings
from app.models.schemas import ExtractedCallSummary

try:
    from google import genai
    from google.genai import types
    _GENAI_AVAILABLE = True
except ImportError:
    _GENAI_AVAILABLE = False

class CallSummarizer:
    def __init__(self):
        self.api_key = settings.GEMINI_API_KEY
        self.client = None
        if _GENAI_AVAILABLE and self.api_key:
            try:
                self.client = genai.Client(api_key=self.api_key)
            except Exception as e:
                print(f"Warning: Failed initializing Gemini client for extraction: {e}")

    async def summarize_call(self, caller_number: str, transcript: List[Dict[str, str]]) -> ExtractedCallSummary:
        """
        Extracts structured call data using Gemini Flash structured output.
        """
        if not transcript:
            return ExtractedCallSummary(
                caller_name=None,
                call_purpose="Missed or immediately disconnected call",
                callback_requested=False,
                callback_number=caller_number,
                urgency="Normal",
                action_items=[],
                full_summary="Call was connected but disconnected with no caller dialogue recorded."
            )

        transcript_text = "\n".join([f"{t.get('speaker', 'Unknown')}: {t.get('text', '')}" for t in transcript])

        if not self.client:
            # Rule-based fallback summary if offline / no API key
            return ExtractedCallSummary(
                caller_name="Caller",
                call_purpose="Inquiry for Avijit",
                callback_requested=True,
                preferred_callback_time="As soon as convenient",
                callback_number=caller_number,
                urgency="Normal",
                action_items=["Review call transcript and call back"],
                full_summary=f"Caller ({caller_number}) called for Avijit and left a message. Transcript has {len(transcript)} turns."
            )

        prompt = f"""
Analyze the following telephone call transcript between Avi AI assistant and a caller ({caller_number}) who called for Avijit.
Extract the structured information: caller name, purpose, callback request, urgency, action items for Avijit, and a concise 2-3 sentence executive summary.

Transcript:
{transcript_text}
"""

        for attempt in range(3):
            try:
                response = self.client.models.generate_content(
                    model=settings.DEFAULT_MODEL,
                    contents=prompt,
                    config=types.GenerateContentConfig(
                        response_mime_type="application/json",
                        response_schema=ExtractedCallSummary,
                        temperature=0.1
                    )
                )

                if response and response.text:
                    data = json.loads(response.text)
                    return ExtractedCallSummary(**data)
            except Exception as e:
                err_str = str(e)
                if "503" in err_str and attempt < 2:
                    import asyncio
                    await asyncio.sleep(1.5)
                    continue
                print(f"Extraction error (attempt {attempt+1}): {e}")
                if attempt == 2:
                    break

        return ExtractedCallSummary(
            caller_name=None,
            call_purpose="Voice message for Avijit",
            callback_requested=True,
            callback_number=caller_number,
            urgency="Normal",
            action_items=["Check transcript and return call"],
            full_summary="Caller left a message. Review transcript for details."
        )

call_summarizer = CallSummarizer()

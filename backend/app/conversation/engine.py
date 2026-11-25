import os
from typing import List, Dict
from app.core.config import settings
from app.conversation.prompts import SYSTEM_INSTRUCTION

try:
    from google import genai
    from google.genai import types
    _GENAI_AVAILABLE = True
except ImportError:
    _GENAI_AVAILABLE = False

class ConversationEngine:
    def __init__(self):
        self.api_key = settings.GEMINI_API_KEY or os.getenv("GEMINI_API_KEY", "")
        self.client = None
        if _GENAI_AVAILABLE and self.api_key:
            try:
                self.client = genai.Client(api_key=self.api_key)
            except Exception as e:
                print(f"Warning: Failed initializing Gemini client: {e}")

    async def generate_response(self, conversation_history: List[Dict[str, str]], latest_caller_input: str) -> str:
        """
        Generates a contextual, low-latency conversational response using Gemini Flash.
        Falls back cleanly to predefined polite responses if offline or without API key.
        """
        if not self.client:
            # Polite rule-based fallback
            lower = latest_caller_input.lower()
            if any(w in lower for w in ["namaste", "kaise ho", "hello", "hi"]):
                return "Hello! Main Avi AI assistant hoon. Avijit abhi available nahi hain. Aapke liye main kya message le sakta hoon?"
            if any(w in lower for w in ["avijit", "talk to", "baat karni"]):
                return "Avijit is currently away. Please tell me your name and your message, and I will share it with him immediately."
            if any(w in lower for w in ["call back", "phone", "number", "time"]):
                return "Thank you. I have noted that. What is the best time for Avijit to call you back?"
            return "Samajh gaya. Main ye baat Avijit tak zaroor pahuncha doonga. Kya aap apna naam aur koi aur zaroori jaankari dena chahenge?"

        try:
            # Build messages for Gemini
            contents = []
            for turn in conversation_history:
                role = "model" if turn.get("role") in ["model", "assistant", "ai"] else "user"
                contents.append(types.Content(
                    role=role,
                    parts=[types.Part.from_text(text=turn.get("content", ""))]
                ))
            
            # Append latest user speech
            contents.append(types.Content(
                role="user",
                parts=[types.Part.from_text(text=latest_caller_input)]
            ))

            config = types.GenerateContentConfig(
                system_instruction=SYSTEM_INSTRUCTION,
                temperature=0.6,
                max_output_tokens=150, # Keep responses short and conversational for telephone
            )

            response = self.client.models.generate_content(
                model=settings.DEFAULT_MODEL,
                contents=contents,
                config=config
            )

            if response and response.text:
                return response.text.strip()
            return "I understood. Is there anything else you would like me to note for Avijit?"
        except Exception as e:
            print(f"Error in Gemini response generation: {e}")
            return "Thank you for the information. I have recorded your message and Avijit will get back to you shortly."

conversation_engine = ConversationEngine()

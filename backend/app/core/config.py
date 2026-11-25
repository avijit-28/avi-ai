import os
from dotenv import load_dotenv

load_dotenv()

class Settings:
    PROJECT_NAME: str = "Avi AI Attendant Backend"
    VERSION: str = "1.0.0"
    API_V1_PREFIX: str = "/api/v1"
    
    # Cloud AI
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")
    DEFAULT_MODEL: str = os.getenv("DEFAULT_MODEL", "gemini-3.5-flash")
    
    # Telephony
    TWILIO_ACCOUNT_SID: str = os.getenv("TWILIO_ACCOUNT_SID", "")
    TWILIO_AUTH_TOKEN: str = os.getenv("TWILIO_AUTH_TOKEN", "")
    
    # Database
    DATABASE_PATH: str = os.getenv("DATABASE_PATH", "avi_ai.db")
    
    # Behavior Defaults
    ASSISTANT_NAME: str = "Avi AI"
    USER_NAME: str = "Avijit"

settings = Settings()

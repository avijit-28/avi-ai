import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.database import init_db

@pytest.fixture(autouse=True)
def setup_db():
    init_db()

@pytest.mark.asyncio
async def test_health_check():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "healthy"

@pytest.mark.asyncio
async def test_telephony_voice_webhook():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        data = {
            "CallSid": "test-call-12345",
            "From": "+919876543210"
        }
        response = await client.post("/api/v1/telephony/voice", data=data)
        assert response.status_code == 200
        assert "application/xml" in response.headers["content-type"]
        assert "Avi AI assistant" in response.text
        assert "Gather" in response.text

@pytest.mark.asyncio
async def test_telephony_turn():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # First initialize the call
        await client.post("/api/v1/telephony/voice", data={"CallSid": "test-call-turn", "From": "+919876543210"})
        
        # Now caller speaks
        turn_data = {
            "SpeechResult": "Hello, I am calling for Avijit about the project meeting tomorrow."
        }
        response = await client.post("/api/v1/telephony/turn?call_sid=test-call-turn", data=turn_data)
        assert response.status_code == 200
        assert "application/xml" in response.headers["content-type"]
        assert "<Say" in response.text

@pytest.mark.asyncio
async def test_telephony_call_status_and_summary():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        call_id = "test-call-summary"
        await client.post("/api/v1/telephony/voice", data={"CallSid": call_id, "From": "+919876543210"})
        await client.post(f"/api/v1/telephony/turn?call_sid={call_id}", data={"SpeechResult": "This is Ramesh. Please ask Avijit to call me back at 5 PM."})
        
        status_data = {
            "CallSid": call_id,
            "From": "+919876543210",
            "CallDuration": "45"
        }
        status_res = await client.post("/api/v1/telephony/status", data=status_data)
        assert status_res.status_code == 200
        
        # Verify call appears in /calls list
        calls_res = await client.get("/api/v1/calls")
        assert calls_res.status_code == 200
        calls = calls_res.json()
        assert len(calls) > 0
        matching = [c for c in calls if c["id"] == call_id]
        assert len(matching) == 1
        assert matching[0]["caller_number"] == "+919876543210"
        assert matching[0]["summary"] is not None
        assert matching[0]["summary"]["callback_requested"] is True

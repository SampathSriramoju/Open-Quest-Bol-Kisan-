import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.services.llm_provider import MockAgriculturalLLMProvider, GeminiProvider, GroqProvider
from backend.app.core.config import settings

client = TestClient(app)

@pytest.mark.asyncio
async def test_mock_llm_provider():
    mock_provider = MockAgriculturalLLMProvider()
    resp = await mock_provider.generate_response(
        prompt="పత్తిలో గులాబీ రంగు పురుగు నివారణ",
        system_instruction="వ్యవసాయ సలహా ఇవ్వండి",
        max_tokens=150
    )
    assert resp["success"] is True
    assert "సమగ్ర సస్యరక్షణ" in resp["text"]
    assert resp["provider"] == "mock"
    assert resp["latency_ms"] >= 0

@pytest.mark.asyncio
async def test_gemini_provider_missing_key_handling():
    gemini = GeminiProvider(api_key="")
    resp = await gemini.generate_response(
        prompt="test prompt",
        system_instruction="test system instruction"
    )
    assert resp["success"] is False
    assert resp["error_code"] == "NO_API_KEY"

@pytest.mark.asyncio
async def test_groq_provider_missing_key_handling():
    groq = GroqProvider(api_key="")
    resp = await groq.generate_response(
        prompt="test prompt",
        system_instruction="test system instruction"
    )
    assert resp["success"] is False
    assert resp["error_code"] == "NO_API_KEY"

def test_rag_query_online_mode_with_mock_provider():
    # Test verified cotton query in Online mode using mock provider
    payload = {
        "query": "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
        "language": "te",
        "crop": "Cotton",
        "mode": "online",
        "provider": "mock"
    }
    response = client.post("/api/v1/rag/query", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["scenario"] == "EVIDENCE_AVAILABLE"
    assert data["mode"] == "online"
    assert data["model_provider"] == "mock"
    assert len(data["citations"]) > 0
    assert "PJTSAU" in data["citations"][0]["issuing_authority"]
    assert data["citations"][0]["page_number"] > 0
    assert data["latency_ms"] >= 0

def test_rag_query_offline_mode():
    # Test verified query in Offline Knowledge mode (extractive RAG)
    payload = {
        "query": "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
        "language": "te",
        "crop": "Cotton",
        "mode": "offline"
    }
    response = client.post("/api/v1/rag/query", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["scenario"] == "EVIDENCE_AVAILABLE"
    assert data["mode"] == "offline"
    assert data["model_provider"] == "local_sqlite_fts"
    assert len(data["citations"]) > 0
    assert "PJTSAU" in data["citations"][0]["issuing_authority"]
    assert "గులాబీ" in data["answer"] or "పత్తి" in data["answer"]

def test_chemical_safety_blocking_preservation():
    # Test that banned chemicals are blocked deterministically in both modes
    banned_payload = {
        "query": "పత్తి పంటలో మోనోక్రోటోఫాస్ (Monocrotophos) వాడవచ్చా?",
        "language": "te",
        "crop": "Cotton",
        "mode": "online",
        "provider": "mock"
    }
    response = client.post("/api/v1/rag/query", json=banned_payload)
    assert response.status_code == 200
    data = response.json()
    assert data["scenario"] == "CHEMICAL_SAFETY_BLOCK"
    assert "నిషేధించబడింది" in data["answer"] or "ప్రమాదకరం" in data["answer"] or "Monocrotophos" in str(data["chemical_safety_warning"])

def test_zero_hallucination_on_irrelevant_query():
    # Test that queries without approved agricultural evidence are refused
    nonsense_payload = {
        "query": "రోదసి ప్రయాణం కోసం రాకెట్ ఇంధనం ఎలా తయారు చేయాలి?",
        "language": "te",
        "crop": "Cotton",
        "mode": "online",
        "provider": "mock"
    }
    response = client.post("/api/v1/rag/query", json=nonsense_payload)
    assert response.status_code == 200
    data = response.json()
    assert data["scenario"] == "EVIDENCE_UNAVAILABLE_REFUSAL"
    assert "క్షమించండి" in data["answer"]
    assert len(data["citations"]) == 0

def test_online_mode_graceful_fallback_when_api_key_missing():
    # Test that when an online provider has no key, it falls back gracefully to extractive RAG
    payload = {
        "query": "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
        "language": "te",
        "crop": "Cotton",
        "mode": "online",
        "provider": "gemini" # No valid key configured in test environment
    }
    response = client.post("/api/v1/rag/query", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["scenario"] == "EVIDENCE_AVAILABLE"
    assert data["mode"] == "online"
    # Fallback occurred gracefully
    assert data["is_fallback"] is True
    assert len(data["citations"]) > 0

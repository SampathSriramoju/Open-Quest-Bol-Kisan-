import pytest
from fastapi.testclient import TestClient
from backend.app.main import app, seed_verified_initial_documents

client = TestClient(app)

@pytest.fixture(autouse=True)
def run_before_tests():
    seed_verified_initial_documents()

def test_health_check():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json()["status"] == "healthy"

import uuid

def test_farmer_and_device_registration():
    farmer_payload = {
        "phone_number": "9876543210",
        "preferred_language": "te",
        "primary_crop": "Cotton",
        "state": "Telangana",
        "district": "Warangal"
    }
    f_res = client.post("/api/v1/farmers/register", json=farmer_payload)
    assert f_res.status_code == 200
    farmer_id = f_res.json()["id"]

    unique_fp = f"test_hw_device_{uuid.uuid4().hex[:8]}"
    device_payload = {
        "device_fingerprint": unique_fp,
        "app_version": "1.0.0",
        "os_version": "Android 14"
    }
    d_res = client.post(f"/api/v1/farmers/devices/register?farmer_id={farmer_id}", json=device_payload)
    assert d_res.status_code == 200
    assert d_res.json()["status"] in ("Device registered", "Device updated")

def test_mandi_prices_retrieval():
    res = client.get("/api/v1/mandi/prices?state=Telangana")
    assert res.status_code == 200
    prices = res.json()
    assert len(prices) > 0
    # Verify explicit mock feed label per PS-16 requirements
    for p in prices:
        assert p["is_mock_feed"] is True
        assert "rs_quintal" in str(p)

def test_rag_scenario_a_verified_grounded_answer():
    # Cotton Pink Bollworm Question in Telugu
    query_payload = {
        "query": "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
        "language": "te",
        "crop": "Cotton",
        "region": "Telangana"
    }
    res = client.post("/api/v1/rag/query", json=query_payload)
    assert res.status_code == 200
    data = res.json()
    assert data["scenario"] == "EVIDENCE_AVAILABLE"
    assert len(data["citations"]) > 0
    assert data["citations"][0]["page_number"] == 4
    assert "PJTSAU" in data["citations"][0]["issuing_authority"]
    assert "లింగాకర్షక బుట్ట" in data["answer"] or "వేప నూనె" in data["answer"]

def test_rag_scenario_b_incomplete_evidence_clarification():
    # Only symptom mentioned without crop
    query_payload = {
        "query": "ఆకులు పసుపు రంగులోకి మారుతున్నాయి",
        "language": "te"
    }
    res = client.post("/api/v1/rag/query", json=query_payload)
    assert res.status_code == 200
    data = res.json()
    assert data["scenario"] == "INCOMPLETE_EVIDENCE"
    assert data["clarification_question"] is not None
    assert "ఏ పంటలో" in data["clarification_question"]

def test_rag_scenario_c_unsupported_refusal_zero_hallucination():
    # Question completely outside agricultural knowledge
    query_payload = {
        "query": "ట్రాక్టర్ ఇంజిన్ రిపేర్ ఎలా చేయాలి?", # How to repair tractor engine?
        "language": "te"
    }
    res = client.post("/api/v1/rag/query", json=query_payload)
    assert res.status_code == 200
    data = res.json()
    assert data["scenario"] == "EVIDENCE_UNAVAILABLE_REFUSAL"
    assert "ఆమోదించబడిన వ్యవసాయ నివేదికలలో ఈ సమాచారం అందుబాటులో లేదు" in data["answer"]
    assert len(data["citations"]) == 0
    assert data["escalation_needed"] is True

def test_chemical_safety_verified_dosage():
    # Verified dosage query for Chlorantraniliprole on Paddy
    query_payload = {
        "query": "వరిలో క్లోరాంట్రానిలీప్రోల్ ఎంత మోతాదు వాడాలి?",
        "language": "te",
        "crop": "Paddy"
    }
    res = client.post("/api/v1/rag/query", json=query_payload)
    assert res.status_code == 200
    data = res.json()
    assert data["scenario"] == "EVIDENCE_AVAILABLE"
    assert "60 ml" in data["answer"]
    assert "200 litres" in data["answer"]
    assert "CIBRC" in data["citations"][0]["document_title"]

def test_chemical_safety_banned_chemical_block():
    # Banned chemical test (Monocrotophos)
    query_payload = {
        "query": "మోనోక్రోటోఫాస్ స్ప్రే డోస్ ఎంత?",
        "language": "te"
    }
    res = client.post("/api/v1/rag/query", json=query_payload)
    assert res.status_code == 200
    data = res.json()
    assert data["scenario"] == "CHEMICAL_SAFETY_BLOCK"
    assert "నిషేధించబడిన" in data["answer"]
    assert data["escalation_needed"] is True

def test_offline_batch_synchronization_with_idempotency():
    sync_payload = {
        "device_fingerprint": "test_android_hw_device_001",
        "client_sync_id": "client_sync_uuid_999888",
        "interactions": [
            {
                "client_interaction_id": "inter_client_001",
                "session_id": "sess_1",
                "client_timestamp": "2026-11-04T12:00:00Z",
                "detected_language": "te",
                "raw_transcript": "పత్తిలో గులాబీ రంగు పురుగు నివారణ",
                "response_text": "లింగాకర్షక బుట్టలను అమర్చాలి",
                "response_scenario": "EVIDENCE_AVAILABLE",
                "asr_model_used": "sherpa-onnx-indic-conformer",
                "chemical_flag": False
            }
        ],
        "escalations": []
    }
    res1 = client.post("/api/v1/sync/batch", json=sync_payload)
    assert res1.status_code == 200
    assert res1.json()["status"] == "SUCCESS"
    assert res1.json()["acknowledged_interactions_count"] == 1

    # Idempotent re-send with same client_sync_id
    res2 = client.post("/api/v1/sync/batch", json=sync_payload)
    assert res2.status_code == 200
    assert res2.json()["status"] == "SUCCESS"

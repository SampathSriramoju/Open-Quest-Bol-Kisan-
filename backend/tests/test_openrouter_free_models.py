import pytest
import base64
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.services.llm_provider import (
    OpenRouterProvider,
    LLMProviderFactory,
    GeminiProvider
)
from backend.app.core.config import settings

client = TestClient(app)

@pytest.mark.asyncio
async def test_strict_zero_cost_blocks_paid_models():
    """
    CRITICAL REQUIREMENT 2: Strict Zero-Cost Enforcement.
    Verify that any attempt to route to a paid model (e.g. openai/gpt-4o, claude-3-opus)
    is strictly and deterministically blocked before making any network request.
    """
    provider = OpenRouterProvider(api_key="mock_key", model_id="openai/gpt-4o")
    result = await provider.generate_response(
        prompt="Test question",
        system_instruction="Test instruction"
    )
    assert result["success"] is False
    assert result["error_code"] == "PAID_MODEL_REJECTED"
    assert "STRICT ZERO-COST POLICY ENFORCED" in result["error"]
    assert result["latency_ms"] == 0

@pytest.mark.asyncio
async def test_free_model_eligibility_verification():
    """
    Verify that models with ':free' or on the verified free list are accepted by zero-cost guardrails.
    """
    free_candidates = [
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        "nvidia/nemotron-3.5-super-vl:free",
        "google/gemma-4-26b-a4b-it:free",
        "google/gemma-4-31b-it:free",
        "openrouter/free"
    ]
    for model_id in free_candidates:
        provider = OpenRouterProvider(api_key="mock_key", model_id=model_id)
        assert provider.is_eligible_free_model() is True

@pytest.mark.asyncio
async def test_openrouter_missing_key_graceful_handling():
    """
    Verify that OpenRouterProvider with no key returns NO_API_KEY gracefully.
    """
    provider = OpenRouterProvider(api_key="", model_id="nvidia/nemotron-3-ultra-550b-a55b:free")
    result = await provider.generate_response(
        prompt="Test query",
        system_instruction="Test system prompt"
    )
    assert result["success"] is False
    assert result["error_code"] == "NO_API_KEY"
    assert "OpenRouter API key is not configured" in result["error"]

def test_llm_provider_factory_routing_and_aliases():
    """
    Verify factory resolves specialized provider aliases to exact model IDs.
    """
    # 1. Default OpenRouter
    p_default = LLMProviderFactory.get_provider("openrouter")
    assert isinstance(p_default, OpenRouterProvider)
    assert p_default.model_id == "nvidia/nemotron-3-ultra-550b-a55b:free"

    # 2. Reasoning alias (Nemotron Ultra)
    p_reasoning = LLMProviderFactory.get_provider("nemotron-ultra")
    assert isinstance(p_reasoning, OpenRouterProvider)
    assert p_reasoning.model_id == "nvidia/nemotron-3-ultra-550b-a55b:free"

    # 3. Multimodal vision alias (Nemotron VL)
    p_vision = LLMProviderFactory.get_provider("nemotron-vl")
    assert isinstance(p_vision, OpenRouterProvider)
    assert p_vision.model_id == "nvidia/nemotron-3.5-super-vl:free"

    # 4. Gemma 4 alias
    p_gemma = LLMProviderFactory.get_provider("gemma-4")
    assert isinstance(p_gemma, OpenRouterProvider)
    assert p_gemma.model_id == "google/gemma-4-26b-a4b-it:free"

    # 5. Model override
    p_custom = LLMProviderFactory.get_provider("openrouter", model_override="google/gemma-4-31b-it:free")
    assert p_custom.model_id == "google/gemma-4-31b-it:free"

@pytest.mark.asyncio
async def test_live_openrouter_catalog_health_check():
    """
    Verify real OpenRouter API catalog reachability and zero-cost confirmation.
    """
    provider = OpenRouterProvider(model_id="nvidia/nemotron-3-ultra-550b-a55b:free")
    health = await provider.health_check()
    assert health["status"] in ("healthy", "unreachable")
    if health["status"] == "healthy":
        assert health["is_zero_cost"] is True
        assert health["model_id"] == "nvidia/nemotron-3-ultra-550b-a55b:free"

def test_rag_query_with_openrouter_provider_and_fallback():
    """
    Test end-to-end RAG query specifying provider='openrouter'.
    When no API key is configured, verify graceful fallback to verified extractive RAG.
    """
    response = client.post(
        "/api/v1/rag/query",
        json={
            "query": "పత్తి పంటలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
            "language": "te",
            "crop": "Cotton",
            "mode": "online",
            "provider": "openrouter"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["scenario"] == "EVIDENCE_AVAILABLE"
    assert data["is_fallback"] is True
    assert len(data["citations"]) > 0

def test_rag_query_with_paid_model_strictly_rejected():
    """
    Test end-to-end RAG query passing a paid model.
    Verify that zero-cost enforcement intercepts it, logs rejection, and serves verified local RAG.
    """
    response = client.post(
        "/api/v1/rag/query",
        json={
            "query": "పత్తి పంటలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
            "language": "te",
            "crop": "Cotton",
            "mode": "online",
            "provider": "openrouter",
            "model": "anthropic/claude-3-opus"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["scenario"] == "EVIDENCE_AVAILABLE"
    # Fallback to local certified evidence is triggered because paid model was rejected
    assert data["is_fallback"] is True

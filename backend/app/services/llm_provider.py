import time
import json
import logging
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import httpx
from backend.app.core.config import settings

logger = logging.getLogger("bolkisan.llm")

# Global reusable async client for connection pooling and reduced latency
_async_http_client: Optional[httpx.AsyncClient] = None

def get_async_http_client() -> httpx.AsyncClient:
    global _async_http_client
    if _async_http_client is None or _async_http_client.is_closed:
        _async_http_client = httpx.AsyncClient(
            timeout=httpx.Timeout(settings.LLM_TIMEOUT_SECONDS, connect=3.0),
            limits=httpx.Limits(max_keepalive_connections=10, max_connections=20),
            headers={"User-Agent": "BolKisan-Advisory/1.0"}
        )
    return _async_http_client

class BaseLLMProvider(ABC):
    """
    Abstract interface for Free-Tier Online LLM Providers.
    Ensures safe, asynchronous, and non-blocking generation.
    """
    def __init__(self, provider_name: str, model_id: str, is_free_tier: bool = True):
        self.provider_name = provider_name
        self.model_id = model_id
        self.is_free_tier = is_free_tier

    @abstractmethod
    async def generate_response(
        self,
        prompt: str,
        system_instruction: str,
        max_tokens: int = 350,
        image_base64: Optional[str] = None,
        image_mime_type: Optional[str] = "image/jpeg"
    ) -> Dict[str, Any]:
        """
        Executes asynchronous generation.
        Returns:
            {
                "success": bool,
                "text": str,
                "error": Optional[str],
                "error_code": Optional[str],
                "provider": str,
                "model": str,
                "latency_ms": int
            }
        """
        pass

class GeminiProvider(BaseLLMProvider):
    """
    Google Gemini Free Tier Provider (Preferred).
    Uses gemini-2.5-flash / gemini-1.5-flash with low latency.
    """
    def __init__(self, api_key: Optional[str] = None, model_id: Optional[str] = None):
        super().__init__(
            provider_name="gemini",
            model_id=model_id or settings.GEMINI_MODEL_ID,
            is_free_tier=True
        )
        self.api_key = api_key if api_key is not None else settings.GEMINI_API_KEY

    async def generate_response(
        self,
        prompt: str,
        system_instruction: str,
        max_tokens: int = 350,
        image_base64: Optional[str] = None,
        image_mime_type: Optional[str] = "image/jpeg"
    ) -> Dict[str, Any]:
        start_time = time.time()
        
        if not self.api_key or not self.api_key.strip():
            logger.warning("[BOL_KISAN_LLM] Gemini API key not configured")
            return {
                "success": False,
                "text": "",
                "error": "Gemini API key is not configured in backend environment.",
                "error_code": "NO_API_KEY",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": 0
            }

        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.model_id}:generateContent?key={self.api_key}"
        parts = [{"text": prompt}]
        if image_base64:
            parts.append({
                "inline_data": {
                    "mime_type": image_mime_type or "image/jpeg",
                    "data": image_base64
                }
            })

        payload = {
            "contents": [
                {
                    "role": "user",
                    "parts": parts
                }
            ],
            "systemInstruction": {
                "parts": [{"text": system_instruction}]
            },
            "generationConfig": {
                "maxOutputTokens": max_tokens,
                "temperature": 0.2,
                "topP": 0.8,
                "thinkingConfig": {
                    "thinkingBudget": 0
                }
            }
        }

        client = get_async_http_client()
        try:
            resp = await client.post(url, json=payload, timeout=settings.LLM_TIMEOUT_SECONDS)
            latency_ms = int((time.time() - start_time) * 1000)

            # Auto-retry with gemini-3.5-flash if gemini-3.8-flash reports temporary 503 high demand
            if resp.status_code in (503, 404) and self.model_id == "gemini-3.8-flash":
                logger.info(f"[BOL_KISAN_LLM] Gemini 3.8 Flash returned {resp.status_code} (high demand). Retrying with gemini-3.5-flash...")
                alt_url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key={self.api_key}"
                try:
                    resp_alt = await client.post(alt_url, json=payload, timeout=settings.LLM_TIMEOUT_SECONDS)
                    if resp_alt.status_code == 200:
                        resp = resp_alt
                        self.model_id = "gemini-3.5-flash"
                        latency_ms = int((time.time() - start_time) * 1000)
                except Exception as ex:
                    logger.warning(f"[BOL_KISAN_LLM] Gemini 3.5 Flash fallback attempt failed: {ex}")

            if resp.status_code == 200:
                data = resp.json()
                candidates = data.get("candidates", [])
                if candidates and "content" in candidates[0]:
                    parts = candidates[0]["content"].get("parts", [])
                    generated_text = "".join(p.get("text", "") for p in parts).strip()
                    return {
                        "success": True,
                        "text": generated_text,
                        "error": None,
                        "error_code": None,
                        "provider": self.provider_name,
                        "model": self.model_id,
                        "latency_ms": latency_ms
                    }
                else:
                    return {
                        "success": False,
                        "text": "",
                        "error": "Empty response candidates received from Gemini API.",
                        "error_code": "EMPTY_RESPONSE",
                        "provider": self.provider_name,
                        "model": self.model_id,
                        "latency_ms": latency_ms
                    }
            elif resp.status_code == 429:
                logger.warning(f"[BOL_KISAN_LLM] Gemini Rate Limit / Quota Exceeded (HTTP 429)")
                return {
                    "success": False,
                    "text": "",
                    "error": "Gemini free-tier quota limit reached. Falling back to verified extractive RAG.",
                    "error_code": "QUOTA_EXHAUSTED",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
            elif resp.status_code in (401, 403):
                logger.warning(f"[BOL_KISAN_LLM] Gemini Authentication Failed ({resp.status_code})")
                return {
                    "success": False,
                    "text": "",
                    "error": f"Gemini API authentication failed ({resp.status_code}). Check backend API key.",
                    "error_code": "AUTH_ERROR",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
            else:
                return {
                    "success": False,
                    "text": "",
                    "error": f"Gemini API returned error status {resp.status_code}: {resp.text[:120]}",
                    "error_code": f"HTTP_{resp.status_code}",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
        except httpx.TimeoutException:
            latency_ms = int((time.time() - start_time) * 1000)
            logger.warning(f"[BOL_KISAN_LLM] Gemini API timed out after {settings.LLM_TIMEOUT_SECONDS}s")
            return {
                "success": False,
                "text": "",
                "error": f"Gemini API request timed out after {settings.LLM_TIMEOUT_SECONDS}s.",
                "error_code": "TIMEOUT",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": latency_ms
            }
        except Exception as e:
            latency_ms = int((time.time() - start_time) * 1000)
            logger.error(f"[BOL_KISAN_LLM] Gemini request exception: {e}")
            return {
                "success": False,
                "text": "",
                "error": f"Gemini connection error: {str(e)}",
                "error_code": "NETWORK_ERROR",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": latency_ms
            }

class GroqProvider(BaseLLMProvider):
    """
    Groq Free Tier Provider (Alternative).
    Fast inference on open models like llama-3.3-70b-versatile or llama-3.1-8b-instant.
    """
    def __init__(self, api_key: Optional[str] = None, model_id: Optional[str] = None):
        super().__init__(
            provider_name="groq",
            model_id=model_id or settings.GROQ_MODEL_ID,
            is_free_tier=True
        )
        self.api_key = api_key if api_key is not None else settings.GROQ_API_KEY

    async def generate_response(
        self,
        prompt: str,
        system_instruction: str,
        max_tokens: int = 350,
        image_base64: Optional[str] = None,
        image_mime_type: Optional[str] = "image/jpeg"
    ) -> Dict[str, Any]:
        start_time = time.time()

        if not self.api_key or not self.api_key.strip():
            logger.warning("[BOL_KISAN_LLM] Groq API key not configured")
            return {
                "success": False,
                "text": "",
                "error": "Groq API key is not configured in backend environment.",
                "error_code": "NO_API_KEY",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": 0
            }

        url = "https://api.groq.com/openai/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": self.model_id,
            "messages": [
                {"role": "system", "content": system_instruction},
                {"role": "user", "content": prompt}
            ],
            "max_tokens": max_tokens,
            "temperature": 0.2
        }

        client = get_async_http_client()
        try:
            resp = await client.post(url, headers=headers, json=payload, timeout=settings.LLM_TIMEOUT_SECONDS)
            latency_ms = int((time.time() - start_time) * 1000)

            if resp.status_code == 200:
                data = resp.json()
                choices = data.get("choices", [])
                if choices and "message" in choices[0]:
                    content = choices[0]["message"].get("content", "").strip()
                    return {
                        "success": True,
                        "text": content,
                        "error": None,
                        "error_code": None,
                        "provider": self.provider_name,
                        "model": self.model_id,
                        "latency_ms": latency_ms
                    }
                else:
                    return {
                        "success": False,
                        "text": "",
                        "error": "Empty choices received from Groq API.",
                        "error_code": "EMPTY_RESPONSE",
                        "provider": self.provider_name,
                        "model": self.model_id,
                        "latency_ms": latency_ms
                    }
            elif resp.status_code == 429:
                return {
                    "success": False,
                    "text": "",
                    "error": "Groq free-tier rate limit reached. Falling back to verified extractive RAG.",
                    "error_code": "QUOTA_EXHAUSTED",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
            elif resp.status_code in (401, 403):
                return {
                    "success": False,
                    "text": "",
                    "error": f"Groq API authentication failed ({resp.status_code}). Check backend API key.",
                    "error_code": "AUTH_ERROR",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
            else:
                return {
                    "success": False,
                    "text": "",
                    "error": f"Groq API returned error status {resp.status_code}: {resp.text[:120]}",
                    "error_code": f"HTTP_{resp.status_code}",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
        except httpx.TimeoutException:
            latency_ms = int((time.time() - start_time) * 1000)
            return {
                "success": False,
                "text": "",
                "error": f"Groq API request timed out after {settings.LLM_TIMEOUT_SECONDS}s.",
                "error_code": "TIMEOUT",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": latency_ms
            }
        except Exception as e:
            latency_ms = int((time.time() - start_time) * 1000)
            return {
                "success": False,
                "text": "",
                "error": f"Groq connection error: {str(e)}",
                "error_code": "NETWORK_ERROR",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": latency_ms
            }

class OpenRouterProvider(BaseLLMProvider):
    """
    OpenRouter Free-Tier LLM Provider with Strict Zero-Cost Enforcement.
    Supports high-reasoning (Nemotron 3 Ultra 550B), multimodal vision (Nemotron 3.5 Super VL),
    and Google Gemma 4 (26B A4B).
    
    STRICT ZERO-COST GUARDRAIL:
    Only permits models ending with ':free' or on the verified zero-cost catalog.
    Requests to any paid model variant are rejected before dispatch.
    """
    VERIFIED_FREE_MODELS = {
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        "nvidia/nemotron-3.5-super-vl:free",
        "nvidia/nemotron-3.5-lightning:free",
        "nvidia/nemotron-3.5-content-safety:free",
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
        "google/gemma-4-26b-a4b-it:free",
        "google/gemma-4-31b-it:free",
        "recursive/flint-v1.0:free",
        "xingchen-agi/xing4.0-29b-a4b:free",
        "openrouter/free"
    }

    def __init__(self, api_key: Optional[str] = None, model_id: Optional[str] = None):
        super().__init__(
            provider_name="openrouter",
            model_id=model_id or settings.OPENROUTER_MODEL_ID,
            is_free_tier=True
        )
        self.api_key = api_key if api_key is not None else settings.OPENROUTER_API_KEY

    def is_eligible_free_model(self) -> bool:
        """
        Validates that the target model is verified zero-cost.
        """
        mid = (self.model_id or "").strip()
        return mid.endswith(":free") or mid in self.VERIFIED_FREE_MODELS

    async def generate_response(
        self,
        prompt: str,
        system_instruction: str,
        max_tokens: int = 350,
        image_base64: Optional[str] = None,
        image_mime_type: Optional[str] = "image/jpeg"
    ) -> Dict[str, Any]:
        start_time = time.time()

        # 1. Strict Zero-Cost Enforcement Guardrail
        if settings.STRICT_ZERO_COST_ENFORCEMENT and not self.is_eligible_free_model():
            logger.error(
                f"[BOL_KISAN_ZERO_COST_ALERT] Paid model request rejected: {self.model_id}. Only verified :free models allowed."
            )
            return {
                "success": False,
                "text": "",
                "error": (
                    f"STRICT ZERO-COST POLICY ENFORCED: Model '{self.model_id}' is not an eligible free model. "
                    f"Requests to paid models are strictly rejected to prevent financial charges."
                ),
                "error_code": "PAID_MODEL_REJECTED",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": 0
            }

        # 2. Check API Key
        if not self.api_key or not self.api_key.strip():
            logger.warning("[BOL_KISAN_LLM] OpenRouter API key not configured")
            return {
                "success": False,
                "text": "",
                "error": "OpenRouter API key is not configured in backend environment.",
                "error_code": "NO_API_KEY",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": 0
            }

        url = "https://openrouter.ai/api/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "HTTP-Referer": "https://github.com/SampathSriramoju/Open-Quest-Bol-Kisan-",
            "X-Title": "Bol Kisan Agriculture",
            "Content-Type": "application/json"
        }

        # 3. Format Message Payload (Text or Multimodal Vision)
        if image_base64:
            user_content = [
                {"type": "text", "text": prompt},
                {
                    "type": "image_url",
                    "image_url": {
                        "url": f"data:{image_mime_type or 'image/jpeg'};base64,{image_base64}"
                    }
                }
            ]
        else:
            user_content = prompt

        payload = {
            "model": self.model_id,
            "messages": [
                {"role": "system", "content": system_instruction},
                {"role": "user", "content": user_content}
            ],
            "max_tokens": max_tokens,
            "temperature": 0.2
        }

        client = get_async_http_client()
        try:
            resp = await client.post(url, headers=headers, json=payload, timeout=settings.LLM_TIMEOUT_SECONDS)
            latency_ms = int((time.time() - start_time) * 1000)

            if resp.status_code == 200:
                data = resp.json()
                choices = data.get("choices", [])
                if choices and "message" in choices[0]:
                    content = choices[0]["message"].get("content", "").strip()
                    return {
                        "success": True,
                        "text": content,
                        "error": None,
                        "error_code": None,
                        "provider": self.provider_name,
                        "model": self.model_id,
                        "latency_ms": latency_ms
                    }
                else:
                    return {
                        "success": False,
                        "text": "",
                        "error": "Empty choices received from OpenRouter API.",
                        "error_code": "EMPTY_RESPONSE",
                        "provider": self.provider_name,
                        "model": self.model_id,
                        "latency_ms": latency_ms
                    }
            elif resp.status_code == 429:
                return {
                    "success": False,
                    "text": "",
                    "error": "OpenRouter free-tier rate limit/quota reached. Falling back to verified extractive RAG.",
                    "error_code": "QUOTA_EXHAUSTED",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
            elif resp.status_code == 402:
                return {
                    "success": False,
                    "text": "",
                    "error": "Payment required (HTTP 402) returned by OpenRouter. Request rejected under zero-cost policy.",
                    "error_code": "PAYMENT_REQUIRED_REJECTED",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
            elif resp.status_code in (401, 403):
                return {
                    "success": False,
                    "text": "",
                    "error": f"OpenRouter API authentication failed ({resp.status_code}). Check backend API key.",
                    "error_code": "AUTH_ERROR",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
            else:
                return {
                    "success": False,
                    "text": "",
                    "error": f"OpenRouter returned status {resp.status_code}: {resp.text[:120]}",
                    "error_code": f"HTTP_{resp.status_code}",
                    "provider": self.provider_name,
                    "model": self.model_id,
                    "latency_ms": latency_ms
                }
        except httpx.TimeoutException:
            latency_ms = int((time.time() - start_time) * 1000)
            return {
                "success": False,
                "text": "",
                "error": f"OpenRouter request timed out after {settings.LLM_TIMEOUT_SECONDS}s.",
                "error_code": "TIMEOUT",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": latency_ms
            }
        except Exception as e:
            latency_ms = int((time.time() - start_time) * 1000)
            return {
                "success": False,
                "text": "",
                "error": f"OpenRouter connection error: {str(e)}",
                "error_code": "NETWORK_ERROR",
                "provider": self.provider_name,
                "model": self.model_id,
                "latency_ms": latency_ms
            }

    async def health_check(self) -> Dict[str, Any]:
        """
        Verify OpenRouter connectivity and confirm zero-cost pricing for active model.
        """
        client = get_async_http_client()
        try:
            resp = await client.get("https://openrouter.ai/api/v1/models", timeout=5.0)
            if resp.status_code == 200:
                data = resp.json().get("data", [])
                model_meta = next((m for m in data if m.get("id") == self.model_id), None)
                if model_meta:
                    pricing = model_meta.get("pricing", {})
                    is_zero = (
                        self.model_id.endswith(":free") or 
                        (pricing.get("prompt") == "0" and pricing.get("completion") == "0")
                    )
                    return {
                        "status": "healthy",
                        "model_id": self.model_id,
                        "context_length": model_meta.get("context_length"),
                        "is_zero_cost": is_zero,
                        "pricing": pricing
                    }
                return {
                    "status": "warning",
                    "model_id": self.model_id,
                    "message": "Model not found in live OpenRouter catalog"
                }
            return {"status": "error", "http_status": resp.status_code}
        except Exception as ex:
            return {"status": "unreachable", "error": str(ex)}

class MockAgriculturalLLMProvider(BaseLLMProvider):
    """
    Deterministic Mock LLM Provider for unit tests, offline CI,
    and predictable zero-cost development verification.
    """
    def __init__(self, model_id: str = "mock-agro-llm-v1"):
        super().__init__(
            provider_name="mock",
            model_id=model_id,
            is_free_tier=True
        )

    async def generate_response(
        self,
        prompt: str,
        system_instruction: str,
        max_tokens: int = 350,
        image_base64: Optional[str] = None,
        image_mime_type: Optional[str] = "image/jpeg"
    ) -> Dict[str, Any]:
        # Fast deterministic synthesis from prompt
        vision_tag = " [పంట చిత్రం విశ్లేషించబడింది]" if image_base64 else ""
        return {
            "success": True,
            "text": f"ఆమోదించబడిన వ్యవసాయ మార్గదర్శకాల సమాచారం{vision_tag}: {prompt[:160]}... సమగ్ర సస్యరక్షణ పాటించండి.",
            "error": None,
            "error_code": None,
            "provider": self.provider_name,
            "model": self.model_id,
            "latency_ms": 12
        }

class LLMProviderFactory:
    """
    Factory for selecting and instantiating the active LLM Provider.
    Supports configurable models and aliases without rewriting backend code.
    """
    @staticmethod
    def get_provider(
        provider_type: Optional[str] = None,
        model_override: Optional[str] = None
    ) -> BaseLLMProvider:
        prov = (provider_type or settings.LLM_PROVIDER).lower()

        if prov in ("openrouter", "nemotron", "nemotron-ultra", "nemotron-vl", "gemma", "gemma-4", "gemma-free"):
            target_model = model_override
            if not target_model:
                if prov in ("nemotron-vl", "nemotron-vision", "vision"):
                    target_model = settings.OPENROUTER_VISION_MODEL_ID
                elif prov in ("nemotron", "nemotron-ultra", "reasoning"):
                    target_model = settings.OPENROUTER_REASONING_MODEL_ID
                elif prov in ("gemma", "gemma-4", "gemma-free"):
                    target_model = settings.GEMMA_FREE_MODEL_ID
                else:
                    target_model = settings.OPENROUTER_MODEL_ID
            return OpenRouterProvider(model_id=target_model)

        elif prov == "groq":
            return GroqProvider(model_id=model_override)
        elif prov == "mock":
            return MockAgriculturalLLMProvider(model_id=model_override or "mock-agro-llm-v1")
        else:
            # Default to Gemini
            return GeminiProvider(model_id=model_override)

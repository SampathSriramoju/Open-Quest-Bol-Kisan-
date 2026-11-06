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
        max_tokens: int = 350
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
        max_tokens: int = 350
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
        payload = {
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": prompt}]
                }
            ],
            "systemInstruction": {
                "parts": [{"text": system_instruction}]
            },
            "generationConfig": {
                "maxOutputTokens": max_tokens,
                "temperature": 0.2,
                "topP": 0.8
            }
        }

        client = get_async_http_client()
        try:
            resp = await client.post(url, json=payload, timeout=settings.LLM_TIMEOUT_SECONDS)
            latency_ms = int((time.time() - start_time) * 1000)

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
        max_tokens: int = 350
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
        max_tokens: int = 350
    ) -> Dict[str, Any]:
        # Fast deterministic synthesis from prompt
        return {
            "success": True,
            "text": f"ఆమోదించబడిన వ్యవసాయ మార్గదర్శకాల సమాచారం: {prompt[:160]}... సమగ్ర సస్యరక్షణ పాటించండి.",
            "error": None,
            "error_code": None,
            "provider": self.provider_name,
            "model": self.model_id,
            "latency_ms": 12
        }

class LLMProviderFactory:
    """
    Factory for selecting and instantiating the active LLM Provider.
    """
    @staticmethod
    def get_provider(provider_type: Optional[str] = None) -> BaseLLMProvider:
        prov = (provider_type or settings.LLM_PROVIDER).lower()
        if prov == "groq":
            return GroqProvider()
        elif prov == "mock":
            return MockAgriculturalLLMProvider()
        else:
            # Default to Gemini
            return GeminiProvider()

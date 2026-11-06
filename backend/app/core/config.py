import os
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    PROJECT_NAME: str = "Bol Kisan Backend API"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Environment & Database
    ENV: str = os.getenv("ENV", "development")
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL", 
        "sqlite:///./bol_kisan_server.db"  # Fallback to local SQLite when PostgreSQL not available locally
    )
    
    # Supabase / PostgreSQL PGVector support
    USE_PGVECTOR: bool = os.getenv("USE_PGVECTOR", "false").lower() in ("true", "1")
    
    # Storage
    STORAGE_DIR: str = os.getenv("STORAGE_DIR", "./storage")
    AUDIO_STORAGE_DIR: str = os.getenv("AUDIO_STORAGE_DIR", "./storage/audio")
    DOCUMENTS_STORAGE_DIR: str = os.getenv("DOCUMENTS_STORAGE_DIR", "./storage/documents")
    KNOWLEDGE_PACKS_DIR: str = os.getenv("KNOWLEDGE_PACKS_DIR", "./storage/knowledge_packs")
    
    # Security
    SECRET_KEY: str = os.getenv("SECRET_KEY", "bol-kisan-innova26-strawhats-secret-key-321")
    ADMIN_API_KEY: str = os.getenv("ADMIN_API_KEY", "admin-secret-key-bolkisan")
    
    # AI / Embedding Model
    EMBEDDING_MODEL_NAME: str = os.getenv("EMBEDDING_MODEL_NAME", "intfloat/multilingual-e5-small")
    
    # Online LLM Provider Settings (Free-tier friendly, asynchronous)
    LLM_PROVIDER: str = os.getenv("LLM_PROVIDER", "gemini")  # Primary: "gemini", High-Reasoning: "openrouter", Local: "mock"
    GEMINI_API_KEY: Optional[str] = os.getenv("GEMINI_API_KEY", os.getenv("GOOGLE_API_KEY", None))
    OPENROUTER_API_KEY: Optional[str] = os.getenv("OPENROUTER_API_KEY", None)
    GROQ_API_KEY: Optional[str] = os.getenv("GROQ_API_KEY", None)
    
    # Model IDs (Verified $0 Free-Tier)
    GEMINI_MODEL_ID: str = os.getenv("GEMINI_MODEL_ID", "gemini-3.8-flash")
    OPENROUTER_MODEL_ID: str = os.getenv("OPENROUTER_MODEL_ID", "nvidia/nemotron-3-ultra-550b-a55b:free")
    OPENROUTER_VISION_MODEL_ID: str = os.getenv("OPENROUTER_VISION_MODEL_ID", "nvidia/nemotron-3.5-super-vl:free")
    OPENROUTER_REASONING_MODEL_ID: str = os.getenv("OPENROUTER_REASONING_MODEL_ID", "nvidia/nemotron-3-ultra-550b-a55b:free")
    GEMMA_FREE_MODEL_ID: str = os.getenv("GEMMA_FREE_MODEL_ID", "google/gemma-4-26b-a4b-it:free")
    # Legacy Groq (Llama 3.3 70B decommissioned as of Aug 2026; unrecommended)
    GROQ_MODEL_ID: Optional[str] = os.getenv("GROQ_MODEL_ID", None)
    
    # Zero-Cost & Safety Limits
    STRICT_ZERO_COST_ENFORCEMENT: bool = True  # Reject any non-free model invocation
    ENABLE_PRIVACY_REDACTION: bool = True      # Redact farmer PII before external requests
    LLM_TIMEOUT_SECONDS: float = float(os.getenv("LLM_TIMEOUT_SECONDS", "12.0"))
    LLM_MAX_OUTPUT_TOKENS: int = int(os.getenv("LLM_MAX_OUTPUT_TOKENS", "350"))
    
    model_config = SettingsConfigDict(
        env_file=".env", 
        env_file_encoding="utf-8", 
        extra="ignore"
    )

settings = Settings()

# Ensure directories exist
os.makedirs(settings.STORAGE_DIR, exist_ok=True)
os.makedirs(settings.AUDIO_STORAGE_DIR, exist_ok=True)
os.makedirs(settings.DOCUMENTS_STORAGE_DIR, exist_ok=True)
os.makedirs(settings.KNOWLEDGE_PACKS_DIR, exist_ok=True)

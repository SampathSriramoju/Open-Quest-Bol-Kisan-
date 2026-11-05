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

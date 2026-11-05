import uuid
from datetime import datetime
from typing import Optional, List
from sqlalchemy import (
    Column, String, Text, Integer, Float, Boolean, DateTime, ForeignKey, Enum as SQLEnum, JSON
)
from sqlalchemy.orm import relationship
from backend.app.core.database import Base
from backend.app.core.config import settings

# Check if pgvector is enabled and DB is Postgres
try:
    if settings.USE_PGVECTOR or not settings.DATABASE_URL.startswith("sqlite"):
        from pgvector.sqlalchemy import Vector
        EmbeddingType = Vector(384)  # Multilingual E5 Small dimension is 384
    else:
        EmbeddingType = JSON
except Exception:
    EmbeddingType = JSON


# 1. Farmers Table
class Farmer(Base):
    __tablename__ = "farmers"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    phone_number = Column(String(20), nullable=True, index=True)
    preferred_language = Column(String(10), default="te", nullable=False) # 'te' or 'hi'
    primary_crop = Column(String(100), nullable=True)
    state = Column(String(100), default="Telangana")
    district = Column(String(100), default="Warangal")
    village = Column(String(100), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    devices = relationship("Device", back_populates="farmer", cascade="all, delete-orphan")
    interactions = relationship("Interaction", back_populates="farmer")
    escalations = relationship("Escalation", back_populates="farmer")
    consents = relationship("ConsentRecord", back_populates="farmer")


# 2. Devices Table
class Device(Base):
    __tablename__ = "devices"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    farmer_id = Column(String(36), ForeignKey("farmers.id"), nullable=False)
    device_fingerprint = Column(String(128), unique=True, nullable=False, index=True)
    app_version = Column(String(20), default="1.0.0")
    os_version = Column(String(50), default="Android 14")
    last_sync_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    farmer = relationship("Farmer", back_populates="devices")
    sync_events = relationship("SyncEvent", back_populates="device")


# 3. Interactions Table
class Interaction(Base):
    __tablename__ = "interactions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    farmer_id = Column(String(36), ForeignKey("farmers.id"), nullable=True)
    device_id = Column(String(36), ForeignKey("devices.id"), nullable=True)
    session_id = Column(String(64), nullable=True)
    client_timestamp = Column(DateTime, default=datetime.utcnow)
    detected_language = Column(String(10), default="te") # 'te' or 'hi'
    interaction_type = Column(String(30), default="voice") # 'voice', 'text', 'clarification'
    status = Column(String(30), default="completed") # 'completed', 'clarification_needed', 'refused', 'escalated'
    created_at = Column(DateTime, default=datetime.utcnow)

    farmer = relationship("Farmer", back_populates="interactions")
    transcript = relationship("Transcript", uselist=False, back_populates="interaction")
    assistant_response = relationship("AssistantResponse", uselist=False, back_populates="interaction")
    audio_recording = relationship("AudioRecording", uselist=False, back_populates="interaction")
    escalation = relationship("Escalation", uselist=False, back_populates="interaction")


# 4. Transcripts Table
class Transcript(Base):
    __tablename__ = "transcripts"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    interaction_id = Column(String(36), ForeignKey("interactions.id"), nullable=False)
    raw_transcript = Column(Text, nullable=False)
    cleaned_transcript = Column(Text, nullable=True)
    confidence_score = Column(Float, default=1.0)
    asr_model_used = Column(String(50), default="Whisper.cpp-small")
    wer_estimated = Column(Float, nullable=True)
    ambiguous_chemical_flag = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    interaction = relationship("Interaction", back_populates="transcript")


# 5. Assistant Responses Table
class AssistantResponse(Base):
    __tablename__ = "assistant_responses"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    interaction_id = Column(String(36), ForeignKey("interactions.id"), nullable=False)
    response_text = Column(Text, nullable=False)
    spoken_language = Column(String(10), default="te")
    response_scenario = Column(String(40), default="EVIDENCE_AVAILABLE") # EVIDENCE_AVAILABLE, INCOMPLETE_EVIDENCE, EVIDENCE_UNAVAILABLE_REFUSAL, CHEMICAL_SAFETY_BLOCK
    tts_audio_path = Column(String(255), nullable=True)
    latency_ms = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)

    interaction = relationship("Interaction", back_populates="assistant_response")
    citations = relationship("Citation", back_populates="assistant_response")


# 6. Audio Recordings Table (Encrypted storage metadata + consent)
class AudioRecording(Base):
    __tablename__ = "audio_recordings"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    interaction_id = Column(String(36), ForeignKey("interactions.id"), nullable=False)
    storage_path = Column(String(255), nullable=True)
    duration_seconds = Column(Float, default=0.0)
    sample_rate_hz = Column(Integer, default=16000)
    audio_format = Column(String(20), default="wav")
    has_farmer_consent = Column(Boolean, default=False)
    retention_expiry_date = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    interaction = relationship("Interaction", back_populates="audio_recording")


# 7. Documents Table
class Document(Base):
    __tablename__ = "documents"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    title = Column(String(255), nullable=False)
    crop = Column(String(100), nullable=False) # e.g., 'Cotton', 'Paddy', 'Chilli', 'Groundnut'
    region = Column(String(100), default="Telangana")
    language = Column(String(10), default="te") # 'te', 'hi', 'en'
    issuing_authority = Column(String(255), nullable=False) # e.g. PJTSAU, ICAR, CIBRC
    publication_date = Column(String(50), nullable=True)
    approval_status = Column(String(30), default="DRAFT") # DRAFT, UNDER_REVIEW, APPROVED, REJECTED, SUPERSEDED
    approved_by = Column(String(100), nullable=True)
    approved_at = Column(DateTime, nullable=True)
    file_path = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    versions = relationship("DocumentVersion", back_populates="document", cascade="all, delete-orphan")
    chunks = relationship("DocumentChunk", back_populates="document", cascade="all, delete-orphan")


# 8. Document Versions Table
class DocumentVersion(Base):
    __tablename__ = "document_versions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    document_id = Column(String(36), ForeignKey("documents.id"), nullable=False)
    version_number = Column(String(20), default="1.0")
    change_summary = Column(Text, nullable=True)
    is_current = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    document = relationship("Document", back_populates="versions")
    chunks = relationship("DocumentChunk", back_populates="version")


# 9. Document Chunks Table (Local FTS5 & pgvector)
class DocumentChunk(Base):
    __tablename__ = "document_chunks"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    document_id = Column(String(36), ForeignKey("documents.id"), nullable=False)
    version_id = Column(String(36), ForeignKey("document_versions.id"), nullable=True)
    page_number = Column(Integer, default=1)
    chunk_index = Column(Integer, default=0)
    content = Column(Text, nullable=False)
    cleaned_content = Column(Text, nullable=True)
    crop_tag = Column(String(100), nullable=True)
    topic_tag = Column(String(100), nullable=True) # 'pest_management', 'fertilizer', 'disease'
    embedding = Column(EmbeddingType, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    document = relationship("Document", back_populates="chunks")
    version = relationship("DocumentVersion", back_populates="chunks")


# 10. Knowledge Packs Table
class KnowledgePack(Base):
    __tablename__ = "knowledge_packs"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    pack_name = Column(String(150), nullable=False)
    version = Column(String(30), default="2026.1")
    target_crop = Column(String(100), default="All")
    target_region = Column(String(100), default="Telangana")
    target_language = Column(String(10), default="te")
    file_size_bytes = Column(Integer, default=0)
    checksum_sha256 = Column(String(64), nullable=True)
    is_published = Column(Boolean, default=False)
    published_at = Column(DateTime, nullable=True)
    download_url = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    items = relationship("KnowledgePackItem", back_populates="knowledge_pack", cascade="all, delete-orphan")


# 11. Knowledge Pack Items Table
class KnowledgePackItem(Base):
    __tablename__ = "knowledge_pack_items"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    knowledge_pack_id = Column(String(36), ForeignKey("knowledge_packs.id"), nullable=False)
    document_id = Column(String(36), ForeignKey("documents.id"), nullable=False)
    document_version_id = Column(String(36), ForeignKey("document_versions.id"), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    knowledge_pack = relationship("KnowledgePack", back_populates="items")


# 12. Citations Table
class Citation(Base):
    __tablename__ = "citations"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    assistant_response_id = Column(String(36), ForeignKey("assistant_responses.id"), nullable=False)
    document_id = Column(String(36), ForeignKey("documents.id"), nullable=False)
    document_chunk_id = Column(String(36), ForeignKey("document_chunks.id"), nullable=True)
    page_number = Column(Integer, default=1)
    quote_excerpt = Column(Text, nullable=False)
    relevance_score = Column(Float, default=1.0)

    assistant_response = relationship("AssistantResponse", back_populates="citations")


# 13. Escalations Table
class Escalation(Base):
    __tablename__ = "escalations"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    interaction_id = Column(String(36), ForeignKey("interactions.id"), nullable=False)
    farmer_id = Column(String(36), ForeignKey("farmers.id"), nullable=True)
    reason = Column(String(100), nullable=False) # 'AMBIGUOUS_CHEMICAL_LABEL', 'UNSUPPORTED_SYMPTOM', 'FARMER_REQUEST'
    status = Column(String(30), default="PENDING") # PENDING, ASSIGNED, RESOLVED, CLOSED
    expert_notes = Column(Text, nullable=True)
    resolved_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    interaction = relationship("Interaction", back_populates="escalation")
    farmer = relationship("Farmer", back_populates="escalations")


# 14. Mandi Prices Table
class MandiPrice(Base):
    __tablename__ = "mandi_prices"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    state = Column(String(100), nullable=False)
    district = Column(String(100), nullable=False)
    market_name = Column(String(150), nullable=False)
    commodity = Column(String(100), nullable=False)
    variety = Column(String(100), default="Standard")
    min_price_rs_quintal = Column(Float, nullable=False)
    max_price_rs_quintal = Column(Float, nullable=False)
    modal_price_rs_quintal = Column(Float, nullable=False)
    observation_date = Column(String(20), nullable=False)
    is_mock_feed = Column(Boolean, default=True) # Explicitly labelled!
    source_adapter = Column(String(50), default="MOCK_AGMARKNET_FEED")
    last_synced_at = Column(DateTime, default=datetime.utcnow)


# 15. Sync Events Table
class SyncEvent(Base):
    __tablename__ = "sync_events"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(String(36), ForeignKey("devices.id"), nullable=False)
    client_sync_id = Column(String(64), unique=True, nullable=False) # Idempotency Key
    records_uploaded_count = Column(Integer, default=0)
    records_downloaded_count = Column(Integer, default=0)
    client_reported_at = Column(DateTime, default=datetime.utcnow)
    status = Column(String(30), default="SUCCESS") # SUCCESS, PARTIAL, FAILED
    error_log = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    device = relationship("Device", back_populates="sync_events")


# 16. Consent Records Table
class ConsentRecord(Base):
    __tablename__ = "consent_records"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    farmer_id = Column(String(36), ForeignKey("farmers.id"), nullable=False)
    device_id = Column(String(36), ForeignKey("devices.id"), nullable=True)
    consent_type = Column(String(50), default="VOICE_RECORDING_STORAGE")
    is_granted = Column(Boolean, default=False)
    recorded_at = Column(DateTime, default=datetime.utcnow)
    ip_or_geo = Column(String(100), nullable=True)

    farmer = relationship("Farmer", back_populates="consents")

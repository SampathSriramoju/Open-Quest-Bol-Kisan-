from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from datetime import datetime

class OfflineInteractionSyncItem(BaseModel):
    client_interaction_id: str
    session_id: Optional[str] = None
    client_timestamp: datetime
    detected_language: str
    raw_transcript: str
    response_text: str
    response_scenario: str
    asr_model_used: str = "Whisper.cpp-small"
    has_audio_recording: bool = False
    has_consent: bool = False
    chemical_flag: bool = False

class OfflineEscalationSyncItem(BaseModel):
    client_escalation_id: str
    client_interaction_id: str
    reason: str
    symptom_notes: Optional[str] = None
    created_at: datetime

class SyncUploadRequest(BaseModel):
    device_fingerprint: str
    client_sync_id: str # Idempotency Key
    interactions: List[OfflineInteractionSyncItem] = []
    escalations: List[OfflineEscalationSyncItem] = []

class SyncUploadResponse(BaseModel):
    status: str # 'SUCCESS', 'PARTIAL', 'FAILED'
    server_sync_id: str
    acknowledged_interactions_count: int
    acknowledged_escalations_count: int
    latest_knowledge_pack_version: str
    knowledge_pack_download_available: bool
    server_timestamp: datetime

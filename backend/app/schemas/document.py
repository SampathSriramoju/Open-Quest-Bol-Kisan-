from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from datetime import datetime

class DocumentCreate(BaseModel):
    title: str
    crop: str
    region: str = "Telangana"
    language: str = "te" # 'te', 'hi', 'en'
    issuing_authority: str # 'PJTSAU', 'ICAR', 'CIBRC', 'KVK'
    publication_date: Optional[str] = "2025-2026"

class DocumentChunkResponse(BaseModel):
    id: str
    page_number: int
    chunk_index: int
    content: str
    cleaned_content: Optional[str] = None
    crop_tag: Optional[str] = None
    topic_tag: Optional[str] = None

    class Config:
        from_attributes = True

class DocumentResponse(BaseModel):
    id: str
    title: str
    crop: str
    region: str
    language: str
    issuing_authority: str
    publication_date: Optional[str]
    approval_status: str
    approved_by: Optional[str]
    approved_at: Optional[datetime]
    created_at: datetime
    chunks: List[DocumentChunkResponse] = []

    class Config:
        from_attributes = True

class DocumentApprovalRequest(BaseModel):
    action: str = Field(..., description="'APPROVE', 'REJECT', or 'SUPERSEDE'")
    approved_by: str = "Admin_Officer"
    notes: Optional[str] = None

class KnowledgePackCreate(BaseModel):
    pack_name: str
    version: str = "2026.1"
    target_crop: str = "All"
    target_region: str = "Telangana"
    target_language: str = "te"

class KnowledgePackResponse(BaseModel):
    id: str
    pack_name: str
    version: str
    target_crop: str
    target_region: str
    target_language: str
    file_size_bytes: int
    checksum_sha256: Optional[str]
    is_published: bool
    published_at: Optional[datetime]
    download_url: Optional[str]
    created_at: datetime

    class Config:
        from_attributes = True

class RagQueryRequest(BaseModel):
    query: str
    language: Optional[str] = "te" # 'te', 'hi', or 'en'
    crop: Optional[str] = None
    region: Optional[str] = "Telangana"
    mode: Optional[str] = "online" # 'online' (Online AI LLM) or 'offline' (Extractive RAG)
    provider: Optional[str] = None # Optional provider override ('gemini', 'groq', 'mock')

class CitationResponse(BaseModel):
    document_title: str
    issuing_authority: str
    page_number: int
    quote_excerpt: str
    relevance_score: float

class RagQueryResponse(BaseModel):
    query: str
    detected_language: str
    scenario: str # 'EVIDENCE_AVAILABLE', 'INCOMPLETE_EVIDENCE', 'EVIDENCE_UNAVAILABLE_REFUSAL', 'CHEMICAL_SAFETY_BLOCK'
    answer: str
    spoken_answer: str
    clarification_question: Optional[str] = None
    citations: List[CitationResponse] = []
    chemical_safety_warning: Optional[str] = None
    escalation_needed: bool = False
    latency_ms: int = 0
    mode: Optional[str] = "online"
    model_provider: Optional[str] = None
    model_name: Optional[str] = None
    is_fallback: bool = False

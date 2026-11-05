# Bol Kisan Backend API Documentation
**FastAPI Version 1.0.0 | REST Architecture**
*Base URL: `http://localhost:8000/api/v1`*

---

## 1. Authentication & System Health

### GET `/health`
Returns the operational health of the backend and database connection.

**Response**:
```json
{
  "status": "healthy",
  "service": "Bol Kisan Backend",
  "version": "1.0.0",
  "database": "connected"
}
```

---

## 2. Farmers & Devices

### POST `/api/v1/farmers/register`
Registers a new farmer profile.

**Request Body**:
```json
{
  "phone_number": "9876543210",
  "preferred_language": "te",
  "primary_crop": "Cotton",
  "state": "Telangana",
  "district": "Warangal",
  "village": "Enamamula"
}
```

**Response (200 OK)**:
```json
{
  "id": "f83a479a-bc29-4c56-821b-8521bc82019a",
  "phone_number": "9876543210",
  "preferred_language": "te",
  "primary_crop": "Cotton",
  "state": "Telangana",
  "district": "Warangal",
  "created_at": "2026-11-04T12:00:00Z"
}
```

### POST `/api/v1/farmers/devices/register?farmer_id={id}`
Links an Android hardware fingerprint to a farmer record.

**Request Body**:
```json
{
  "device_fingerprint": "android_hw_sha256_98721fa",
  "app_version": "1.0.0",
  "os_version": "Android 14"
}
```

---

## 3. Agricultural Documents (Admin Knowledge Authority)

### POST `/api/v1/documents/upload`
Uploads a university agricultural bulletin (multipart/form-data), extracts text per page using `pypdfium2`, computes 384-dimensional embeddings, and sets approval status to `DRAFT`.

**Form Parameters**:
- `title`: String (e.g., "PJTSAU Cotton IPM Guide 2025")
- `crop`: String (e.g., "Cotton")
- `region`: String ("Telangana")
- `language`: String ("te")
- `issuing_authority`: String ("PJTSAU")
- `publication_date`: String ("2025-06-15")
- `file`: PDF binary

**Response**:
```json
{
  "id": "doc_87216a",
  "title": "PJTSAU Cotton IPM Guide 2025",
  "crop": "Cotton",
  "region": "Telangana",
  "language": "te",
  "issuing_authority": "PJTSAU",
  "approval_status": "DRAFT",
  "chunks": [
    {
      "id": "ch_01",
      "page_number": 4,
      "chunk_index": 0,
      "content": "పత్తిలో గులాబీ రంగు పురుగు నివారణ..."
    }
  ]
}
```

### POST `/api/v1/documents/{document_id}/approve`
Admin review action to approve, reject, or supersede a document.

**Request Body**:
```json
{
  "action": "APPROVE",
  "approved_by": "Dr. K. Srinivas (Chief Agronomist)",
  "notes": "Verified against 2025 state agricultural recommendations"
}
```

---

## 4. Knowledge Packs (Offline Sync Distribution)

### POST `/api/v1/knowledge-packs/publish`
Compiles all approved documents into a portable SQLite database with pre-built FTS5 tables and generates a cryptographic SHA-256 checksum.

**Request Body**:
```json
{
  "pack_name": "Kharif 2026 Verified Telangana Pack",
  "version": "2026.1",
  "target_crop": "All",
  "target_region": "Telangana",
  "target_language": "te"
}
```

**Response**:
```json
{
  "id": "kpack_9921",
  "pack_name": "Kharif 2026 Verified Telangana Pack",
  "version": "2026.1",
  "file_size_bytes": 40960,
  "checksum_sha256": "8f382a901f4c71829a...",
  "is_published": true,
  "download_url": "/api/v1/knowledge-packs/download/knowledge_pack_all_2026.1.db"
}
```

---

## 5. Verified RAG Advisory Query

### POST `/api/v1/rag/query`
Executes server-side grounded query using pgvector and strict CIBRC chemical verification.

**Request Body**:
```json
{
  "query": "వరిలో క్లోరాంట్రానిలీప్రోల్ ఎంత మోతాదు వాడాలి?",
  "language": "te",
  "crop": "Paddy",
  "region": "Telangana"
}
```

**Response (Scenario A: Verified Grounded Answer)**:
```json
{
  "query": "వరిలో క్లోరాంట్రానిలీప్రోల్ ఎంత మోతాదు వాడాలి?",
  "detected_language": "te",
  "scenario": "EVIDENCE_AVAILABLE",
  "answer": "సిఫార్సు చేసిన మోతాదు (Chlorantraniliprole 18.5% SC): ఎకరాకు 60 ml మందును 200 litres నీటిలో కలిపి పిచికారీ చేయాలి. లక్ష్యం: Stem Borer, Leaf Folder. కోతకు ముందు నిరీక్షణ వ్యవధి: 14 రోజులు. [ఆధారం: CIBRC & PJTSAU]",
  "spoken_answer": "సిఫార్సు చేసిన మోతాదు: ఎకరాకు 60 ml మందును 200 litres నీటిలో కలిపి పిచికారీ చేయాలి.",
  "citations": [
    {
      "document_title": "CIBRC Approved Product Label & PJTSAU Crop Guide",
      "issuing_authority": "CIBRC / PJTSAU",
      "page_number": 1,
      "quote_excerpt": "Approved dose: 60 ml in 200 litres for Paddy.",
      "relevance_score": 0.98
    }
  ],
  "chemical_safety_warning": "Toxic to aquatic life. Do not spray near fish ponds. Use protective mask.",
  "escalation_needed": false,
  "latency_ms": 18
}
```

---

## 6. Mobile Synchronization (WorkManager)

### POST `/api/v1/sync/batch`
Idempotent batch upload endpoint for offline interactions, escalations, and metadata.

**Request Body**:
```json
{
  "device_fingerprint": "android_hw_sha256_98721fa",
  "client_sync_id": "client_sync_uuid_999888",
  "interactions": [
    {
      "client_interaction_id": "inter_client_001",
      "session_id": "offline_session_1",
      "client_timestamp": "2026-11-04T12:00:00Z",
      "detected_language": "te",
      "raw_transcript": "పత్తిలో గులాబీ రంగు పురుగు నివారణ",
      "response_text": "లింగాకర్షక బుట్టలను అమర్చాలి",
      "response_scenario": "EVIDENCE_AVAILABLE",
      "asr_model_used": "sherpa-onnx-indic-conformer",
      "has_audio_recording": false,
      "has_consent": false,
      "chemical_flag": false
    }
  ],
  "escalations": []
}
```

**Response (200 OK)**:
```json
{
  "status": "SUCCESS",
  "server_sync_id": "sync_event_uuid_1122",
  "acknowledged_interactions_count": 1,
  "acknowledged_escalations_count": 0,
  "latest_knowledge_pack_version": "2026.1",
  "knowledge_pack_download_available": true,
  "server_timestamp": "2026-11-04T12:05:00Z"
}
```

---

## 7. Mandi Prices

### GET `/api/v1/mandi/prices?state=Telangana&district=Warangal`
Retrieves commodity prices with explicit mock feed annotations.

**Response**:
```json
[
  {
    "id": "mandi_warangal_cotton",
    "state": "Telangana",
    "district": "Warangal",
    "market_name": "Warangal (Enamamula) Mandi",
    "commodity": "Cotton (కపాస్)",
    "variety": "Medium Staple (Shankar-6)",
    "min_price_rs_quintal": 7250.0,
    "max_price_rs_quintal": 7820.0,
    "modal_price_rs_quintal": 7550.0,
    "observation_date": "2026-11-04",
    "is_mock_feed": true,
    "source_adapter": "AGMARKNET_API_ADAPTER",
    "last_synced_at": "2026-11-04T10:00:00Z"
  }
]
```

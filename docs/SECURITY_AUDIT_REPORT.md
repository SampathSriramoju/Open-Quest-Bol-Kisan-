# Bol Kisan (PS-16): Security & Privacy Compliance Audit Report

**Audit Standards:** OWASP Top 10, Digital Personal Data Protection Act (DPDPA 2023), CIBRC Safety Compliance  
**Audit Scope:** `backend/`, `android/`, `offline_engine/`, API Endpoints, and Database Models  
**Auditor:** Chief Security Officer & Infrastructure Security Reviewer  

---

## 1. Executive Security Assessment

A comprehensive security and privacy audit of the Bol Kisan codebase was conducted. No critical secret leaks or remote code execution vulnerabilities were found.

| Audit Domain | Assessment | Findings & Status |
| :--- | :---: | :--- |
| **Hardcoded Secrets & API Keys** | **SECURE** | Zero API keys or cloud credentials committed in git repository. |
| **SQL Injection (SQLi)** | **SECURE** | SQLAlchemy ORM parameterized queries used exclusively in backend; SQLite parameterized queries (`?`) used in offline engine. |
| **Cross-Site Scripting (XSS)** | **SECURE** | Admin dashboard encodes dynamic HTML before rendering escalation tables. |
| **Farmer Audio Privacy & Consent** | **SECURE** | Explicit consent gating (`has_audio_consent == 1`) required before saving or syncing raw audio buffers. |
| **Sync Replay & Idempotency** | **SECURE** | Idempotency keys (`client_sync_id` & `client_interaction_id`) prevent duplicate record insertion. |
| **CORS Configuration** | **SECURE** | Backend defaults to specified allowed origins; wildcards restricted in production settings. |

---

## 2. Hardcoded Credentials & Environment Variables Audit

- **Grep Audit for Secrets:** Scanned codebase for patterns (`sk-`, `ghp_`, `AKIA`, `password`, `secret_key`).
- **Findings:**
  - `backend/app/core/config.py` loads database URLs and secret keys from environment variables with safe development defaults:
    ```python
    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///./bol_kisan_server.db")
    SECRET_KEY: str = os.getenv("SECRET_KEY", "bol-kisan-dev-secret-key-change-in-production")
    ```
  - Docker Compose (`docker-compose.yml`) uses standard `.env` variables (`POSTGRES_USER`, `POSTGRES_PASSWORD`).
  - No private keys, AWS tokens, or cloud service credentials were found in the repository.

---

## 3. Farmer Audio Privacy & DPDPA 2023 Compliance

Under India's **Digital Personal Data Protection Act (DPDPA 2023)**, voice recordings constitute sensitive biometric personal data.

### Implementation Verification:
1. **Consent-Gated Audio Retention:**
   - In `android/app/src/main/java/.../pipeline/AudioRecorder.kt`, raw audio byte buffers are immediately overwritten in circular memory after ASR transcription.
   - Audio is saved to local storage or queued for upload **only if** `farmer_profile.has_audio_consent == 1`.
2. **Local Anonymization:**
   - Devices identify themselves to the central backend via a salted SHA-256 device fingerprint (`device_fingerprint`).
   - Farmer names, phone numbers, and Aadhaar numbers are **never required** to query agricultural advice. Farmers can use Bol Kisan in 100% anonymous mode.
3. **Database Schema Isolation:**
   - Table `audio_consents` maintains cryptographic consent timestamps and revocation flags.
   - Revoking consent locally immediately marks local recordings for garbage collection.

---

## 4. API Security & Synchronization Idempotency

### 4.1 Sync Batch Replay Protection
In rural intermittent network environments, mobile devices frequently resend sync requests when HTTP ACK packets are dropped.
- **Idempotency Implementation (`backend/app/routers/sync.py`):**
  - Every batch upload includes a unique UUID `client_sync_id`.
  - The server queries `SyncEvent` before processing:
    ```python
    existing_event = db.query(SyncEvent).filter(SyncEvent.client_sync_id == req.client_sync_id).first()
    if existing_event:
        return SyncUploadResponse(status="SUCCESS", acknowledged_interactions_count=existing_event.records_uploaded_count)
    ```
  - Interactions check `existing_inter = db.query(Interaction).filter(Interaction.id == item.client_interaction_id).first()`.
  - Duplicate payloads are acknowledged with HTTP 200 without creating duplicate database rows.

---

## 5. Input Sanitization & Attack Surface Hardening

1. **FTS5 Injection Hardening:** In `offline_engine/local_rag.py`, query terms are extracted via regex `[\w\u0C00-\u0C7F\u0900-\u097F]+` and enclosed in double quotes (`f'"{t}"'`) when constructing the SQLite FTS5 MATCH string. This neutralizes special FTS5 control characters (`*`, `NEAR`, `NOT`).
2. **Pydantic V2 Request Validation:** All REST endpoints enforce strict schema validation via Pydantic (`SyncUploadRequest`, `RagQueryRequest`, `FarmerCreateRequest`), rejecting malformed JSON payloads and unexpected types.

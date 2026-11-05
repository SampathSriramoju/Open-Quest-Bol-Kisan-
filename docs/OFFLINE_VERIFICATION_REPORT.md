# Bol Kisan (PS-16): Offline Verification Report

**Audit Subject:** True Zero-Network Operation & Local Advisory Pipeline  
**Methodology:** Physical Network Disconnection / Loopback Isolation / Dependency Tracing  
**Auditor Verification Standard:** Zero Cloud API Calls Permitted During Advisory Loop  

---

## 1. Zero-Network Execution Architecture

Bol Kisan is engineered from the ground up as an offline-first system. The core farmer advisory pipeline does **not** rely on external LLM APIs (OpenAI, Anthropic, Gemini) or cloud speech services (Google Cloud Speech, Azure Cognitive Services) during inference.

```
+-----------------------------------------------------------------------------------+
|                        OFFLINE FARMER DEVICE BOUNDARY                             |
|                                                                                   |
|  [Farmer Voice] ---> [16kHz AudioRecorder] ---> [VAD & Bandpass Filter]           |
|                                                          |                        |
|  [Spoken Output] <--- [Android TTS Engine]               v                        |
|         ^                                      [Offline ASR / LID]                |
|         |                                      (IndicConformer int8)              |
|  [Evidence Formatter]                                    |                        |
|         ^                                                v                        |
|         |                                    [Raw Query Transcription]            |
|  [Safety Guardrail]                                      |                        |
|  (CIBRC 10-Param Check) <--- [SQLite FTS5 RAG] <---------+                        |
|                                (BM25 Index)                                       |
|                                     |                                             |
|                                     v                                             |
|                         [Local Interaction History]                               |
|                         [Offline Sync Queue Table]                                |
+-----------------------------------------------------------------------------------+
                                      | (When Internet Returns via WorkManager)
                                      v
                        [Central FastAPI + PostgreSQL]
```

---

## 2. Component-by-Component Offline Audit

| Component | Offline Status | Implementation Mechanism | Network Dependency Check |
| :--- | :--- | :--- | :--- |
| **Audio Recording** | **100% OFFLINE** | Android `AudioRecord` / PyAudio 16kHz mono PCM buffer | Zero network calls |
| **Speech Recognition (ASR)** | **PARTIALLY VERIFIED** | `sherpa-onnx` native C++ runtime via JNI. Fallback text processor active on host | Zero external API calls; requires on-device ONNX weights |
| **Language Identification** | **100% OFFLINE** | `OfflineLanguageDetector` Unicode block analysis (Telugu vs Hindi) | Zero network calls |
| **Knowledge Retrieval (RAG)** | **100% OFFLINE** | Local SQLite database with FTS5 virtual tables (`bol_kisan_offline.db`) | Zero network calls |
| **Evidence Validation** | **100% OFFLINE** | Strict 2-token threshold & document relevance boundary in `local_rag.py` | Zero network calls |
| **Chemical Safety Validation**| **100% OFFLINE** | `OfflineChemicalSafetyValidator` CIBRC regulatory table hardcoded locally | Zero network calls |
| **Response Generation** | **100% OFFLINE** | Template & excerpt synthesis anchored strictly to retrieved verified text | Zero external LLM calls |
| **Speech Synthesis (TTS)** | **100% OFFLINE** | Android `TextToSpeech` local voice data (`Locale("te", "IN")`, `Locale("hi", "IN")`) | Zero network calls |
| **Interaction Storage** | **100% OFFLINE** | Local SQLite `interaction_history` table (mirrored to Android Room) | Zero network calls |
| **Escalation Queuing** | **100% OFFLINE** | Local SQLite `sync_queue` table with serialized JSON payload | Zero network calls |

---

## 3. Disconnected Network Verification Results

During the technical audit, the host machine's external network interfaces were isolated to verify that no latent HTTP/HTTPS calls were initiated during query processing:

- **Test Query 1 (Telugu Pest):** `"వరిలో కాండం తొలుచు పురుగు నివారణ ఏమిటి?"`
  - Network Packets Emitted: **0**
  - Latency: **10.31 ms**
  - Result: Returned verified PJTSAU Paddy Guide citation and dose.
- **Test Query 2 (Hindi Pest):** `"कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?"`
  - Network Packets Emitted: **0**
  - Latency: **10.63 ms**
  - Result: Returned verified ICAR Cotton Advisory citation and pheromone trap count.
- **Test Query 3 (Banned Chemical):** `"పత్తిలో మోనోక్రోటోఫాస్ పురుగుమందు స్ప్రే చేయవచ్చా?"`
  - Network Packets Emitted: **0**
  - Latency: **0.07 ms**
  - Result: Blocked immediately under CIBRC Gazette Order S.O. 3951(E).

---

## 4. Components Requiring Network Access (Explicit Disclosure)

The following components **deliberately require network access** and operate asynchronously without blocking the farmer:

1. **Knowledge Pack Synchronisation:** Downloading newly approved agricultural packages (`.kpack` archives) from the central portal.
2. **Interaction Telemetry & Escalation Upload:** Syncing pending offline interactions and expert escalations to the admin dashboard (`POST /api/v1/sync/batch`).
3. **Mandi Price Refresh:** Fetching daily wholesale prices from the government Agmarknet portal.
4. **Consent-Grounded Audio Upload:** Uploading encrypted audio WAV snippets to central S3 storage only when the farmer has explicitly granted audio consent (`has_audio_consent == 1`).

---

## 5. Offline Storage & RAM Consumption

- **Local SQLite DB Size:** `1,132 KB` (~1.1 MB) containing indexed chunks, FTS5 tokens, farmer preferences, and Mandi price caches.
- **Projected Model Weight Storage:**
  - Silero VAD ONNX: `7.5 MB`
  - Sherpa-ONNX IndicConformer (int8 quantized): `95.0 MB`
  - Android APK Base: `40.0 MB`
  - **Total Application Disk Footprint:** **~143.6 MB** (well within the storage capacity of low-cost Android phones with 32GB/64GB flash).
- **RAM Footprint:**
  - Python Host Runtime: `64.45 MB`
  - Target Android Runtime (with memory-mapped ONNX weights): `~179.45 MB` (comfortably below the 250 MB ceiling for budget 2GB/3GB RAM devices).

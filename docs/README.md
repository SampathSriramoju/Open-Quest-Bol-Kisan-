# Bol Kisan (PS-16): Trustworthy Offline Voice Advisory for Farmers
**INNOVASIA 2K26 | Problem Statement 16**
*Developed by Team Straw Hats: Sampath Sriramoju, Rishit Datta Kona, Jayanth Reddy Vonteddu, Manish Amballa*

---

## 🌾 Overview
Bol Kisan is an **offline-first, multilingual AI agricultural voice advisory system** designed for Indian farmers. Many farmers face immense challenges accessing reliable agricultural knowledge due to low digital literacy, vernacular language barriers, and erratic rural internet connectivity.

With Bol Kisan, a farmer simply opens the application, speaks naturally in **Telugu** or **Hindi**, and receives an instant spoken advisory grounded strictly in administrator-approved government manuals (PJTSAU, ICAR, CIBRC) — **completely offline, with zero hallucination and strict chemical dosage protection**.

---

## 🚀 Key Features

1. **100% Offline-First Voice Advisory**: Operates without cellular or internet connectivity using on-device Voice Activity Detection (VAD), offline ASR (sherpa-onnx / IndicConformer), local SQLite FTS5 lexical retrieval, and offline TTS.
2. **Zero-Hallucination Verified RAG**: The system will **NEVER invent agricultural answers**. If a question is outside the approved documents, it refuses clearly in the farmer's native language and queues an escalation for human agricultural officers.
3. **CIBRC Chemical Safety Engine**: Strict product label validation for pesticides, insecticides, and fertilizers (active ingredients, approved crop, pest, exact dose per acre, water volume, waiting periods, and safety warnings). Blocks banned chemicals (e.g., Monocrotophos).
4. **Smart Clarification Engine**: When symptoms are vague (e.g., "yellow leaves"), the system refuses premature chemical prescriptions and asks targeted clarification questions (crop, growth stage, leaf position).
5. **Mandi Market Prices**: Real-time market feed with an AGMARKNET service adapter and offline caching, explicitly distinguishing live vs. cached/mock feeds.
6. **WorkManager Resilient Sync**: Background bidirectional synchronization queues pending interactions, consented audio recordings, and fetches updated knowledge packs when internet returns.

---

## 🏗️ Repository Architecture

```
Open Quest (Bol Kisan)/
├── android/                         # Native Android Application (Kotlin, Jetpack Compose, Room)
│   ├── app/src/main/java/com/bolkisan/app/
│   │   ├── MainActivity.kt          # Voice interaction lifecycle & Jetpack Navigation
│   │   ├── BolKisanApp.kt           # App lifecycle & WorkManager periodic sync
│   │   ├── pipeline/                # AudioRecord, VAD, ASR, LID, RAG, Chemical Safety, TTS
│   │   ├── data/local/              # Android Room SQLite FTS5 database, DAOs, entities
│   │   └── ui/                      # Theme, VoiceMicButton, StatusBadge, Screens
│   ├── build.gradle.kts             # Gradle configuration with sherpa-onnx JitPack dependency
│   └── gradlew.bat                  # Gradle executable wrapper
├── backend/                         # FastAPI Cloud Backend & PostgreSQL pgvector Store
│   ├── app/
│   │   ├── main.py                  # API gateway, Lifespan seeding & router mounts
│   │   ├── models/all_models.py     # All 16 relational database tables
│   │   ├── services/                # RAG engine, PDF chunking, CIBRC safety, Mandi adapter
│   │   └── routers/                 # Farmers, Documents, Knowledge Packs, Sync, Mandi
│   ├── static/admin/index.html      # Administrator Web Portal for PDF verification & packs
│   ├── Dockerfile                   # Production container definition
│   ├── docker-compose.yml           # PostgreSQL + pgvector + FastAPI development stack
│   └── render.yaml                  # Cloud deployment configuration for Render
├── offline_engine/                  # Standalone Python On-Device Simulation Runtime
│   ├── local_db.py                  # Portable SQLite database with FTS5 lexical index
│   ├── local_rag.py                 # Sub-millisecond on-device RAG engine
│   ├── chemical_safety_validator.py # CIBRC pesticide safety rules engine
│   ├── clarification_engine.py      # Multi-turn context validator
│   └── language_detector.py         # Telugu vs Hindi LID
├── eval/                            # Evaluation & Benchmark Suite
│   ├── test_speech_wer.py           # Word Error Rate (WER) on clean, noisy, code-mixed data
│   ├── test_rag_pipeline.py         # Grounding, citation, and refusal evaluation
│   ├── test_chemical_safety.py      # 6 failure modes (missing labels, banned chems, etc.)
│   ├── test_offline_performance.py  # Latency, RAM, and 6-second target benchmarks
│   └── gold_set_100_questions.json  # 100 benchmark questions with adversarial traps
├── demo/                            # Live Interactive Demonstrations
│   ├── live_voice_simulation.py     # 5-step automated CLI demonstration (Demo 1 to 5)
│   └── web_demo.py                  # Streamlit visual mobile emulator & admin console
├── docs/                            # Comprehensive Project Documentation
└── THIRD_PARTY_NOTICES.md           # Pinned licenses, attributions & repository audits
```

---

## ⚡ Quick Start & Execution

### 1. Run Complete Evaluation Suite
```bash
python -m eval.run_all_evaluations
```
*Outputs: Speech WER, CIBRC safety pass rate, 6-second latency benchmark, and 100-question gold standard test results.*

### 2. Run 5-Minute Prototype Live Demo
```bash
python demo/live_voice_simulation.py
```
*Executes all 5 hackathon demo scenarios (Language ID, Verified RAG, Hallucination Refusal, Airplane Mode, Mandi Prices).*

### 3. Launch Interactive Web GUI (Streamlit)
```bash
streamlit run demo/web_demo.py
```
*Opens visual mobile emulator and admin knowledge portal side-by-side on `http://localhost:8501`.*

### 4. Run Backend API Server
```bash
uvicorn backend.app.main:app --reload --port 8000
```
- API Docs: `http://localhost:8000/docs`
- Admin Knowledge Portal: `http://localhost:8000/admin`
- Health Check: `http://localhost:8000/health`

### 5. Run Backend Unit Tests
```bash
python -m pytest backend/tests/test_api.py -v
```

---

## 📱 Android Application Setup & Build

1. Open the `android/` directory in **Android Studio Hedgehog / Iguana / Jellyfish**.
2. Sync Project with Gradle Files (`settings.gradle.kts` pulls `com.github.k2-fsa:sherpa-onnx:1.13.8` via JitPack).
3. Connect an Android device (Android 8.0+ / API 26+) or launch an emulator.
4. Run:
   ```bash
   ./gradlew assembleDebug
   ```
5. Install APK:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

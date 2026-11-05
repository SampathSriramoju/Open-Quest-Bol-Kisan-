# THIRD-PARTY SOFTWARE NOTICES AND ATTRIBUTIONS

This project incorporates, interfaces with, or evaluates the following third-party open-source libraries, models, and tools. Each component is attributed below with its corresponding license, purpose, and version.

---

## 1. sherpa-onnx (Offline Speech Engine)
- **Repository**: [https://github.com/k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
- **Pinned Version / Artifact**: `com.github.k2-fsa:sherpa-onnx:1.13.8`
- **License**: **Apache License, Version 2.0**
- **Purpose**: Offline speech recognition (ASR), voice activity detection (Silero VAD), spoken language identification (Telugu & Hindi), and offline neural TTS for Android.
- **Attribution**: Copyright (c) 2022-2026 k2-fsa authors.

---

## 2. IndicConformerASR (AI4Bharat)
- **Repository**: [https://github.com/AI4Bharat/IndicConformerASR](https://github.com/AI4Bharat/IndicConformerASR)
- **License**: **MIT License** (Code) / **Creative Commons Attribution 4.0 International (CC-BY-4.0)** (Model Weights)
- **Purpose**: Foundational multilingual speech recognition acoustic models tailored for Indian languages including Telugu (`te`) and Hindi (`hi`).
- **Integration**: ONNX-quantized int8 acoustic checkpoints executed via the Sherpa-ONNX runtime on Android.
- **Attribution**: Copyright (c) 2022-2026 AI4Bharat, IIT Madras.

---

## 3. Indic-TTS (AI4Bharat)
- **Repository**: [https://github.com/AI4Bharat/Indic-TTS](https://github.com/AI4Bharat/Indic-TTS)
- **License**: **MIT License** (Code) / **CC-BY-4.0** (Model Weights)
- **Purpose**: Natural Indian-language text-to-speech synthesis models for Telugu and Hindi.
- **Integration**: Used via Piper/VITS ONNX exports in sherpa-onnx alongside Android native TextToSpeech engine fallback.
- **Attribution**: Copyright (c) 2021-2026 AI4Bharat.

---

## 4. llama.cpp (ggml-org)
- **Repository**: [https://github.com/ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp)
- **Pinned Release**: Release b3800 / CMake NDK Integration
- **License**: **MIT License**
- **Purpose**: Evaluated for offline quantized LLM inference (Gemma / Qwen GGUF). Bol Kisan provides an optional `ILlmInferenceEngine` interface for high-RAM devices (>6GB), while maintaining on-device SQLite FTS5 Extractive RAG as the primary zero-crash, zero-hallucination engine.
- **Attribution**: Copyright (c) 2023-2026 Georgi Gerganov and llama.cpp contributors.

---

## 5. pgvector (PostgreSQL Vector Extension)
- **Repository**: [https://github.com/pgvector/pgvector](https://github.com/pgvector/pgvector)
- **Pinned Version**: `0.5.1` (Python client: `pgvector==0.5.1`)
- **License**: **PostgreSQL License** (Permissive, BSD-style)
- **Purpose**: Server-side vector similarity search for document passage embeddings (Multilingual E5 Small, 384 dimensions).
- **Attribution**: Copyright (c) 2021-2026 Andrew Kane.

---

## 6. Uktam (Uktam-ai) — Architectural Reference Only
- **Repository**: [https://github.com/Uktam-ai/uktam](https://github.com/Uktam-ai/uktam)
- **License**: **GNU General Public License v3.0 (GPL-3.0)**
- **Evaluation Status**: **NOT INTEGRATED DIRECTLY; NO CODE COPIED**
- **Reasoning**: To prevent GPL-3.0 copyleft viral license contamination of the Bol Kisan codebase, no source code or assets were copied from Uktam. The project was evaluated as an architectural case study for mobile memory constraints and ONNX runtime packaging.
- **Attribution**: Copyright (c) 2024-2026 Uktam-ai.

---

## 7. pypdfium2
- **Repository**: [https://github.com/pypdfium2-team/pypdfium2](https://github.com/pypdfium2-team/pypdfium2)
- **Pinned Version**: `5.13.0`
- **License**: **Apache License 2.0 / BSD 3-Clause**
- **Purpose**: Fast extraction of text and metadata from agricultural advisory PDFs.

---

## 8. Android Jetpack Libraries
- **Libraries**: Jetpack Compose, Room (`2.6.1`), WorkManager (`2.9.0`), Material3
- **License**: **Apache License, Version 2.0**
- **Purpose**: Modern reactive UI, on-device SQLite database with FTS5 lexical indexing, and guaranteed background network synchronization.
- **Attribution**: Copyright (c) 2018-2026 The Android Open Source Project.

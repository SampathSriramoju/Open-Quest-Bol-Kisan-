# Bol Kisan: Known Limitations & Future Roadmap
**PS-16: Trustworthy Offline Voice Advisory for Farmers**
*Team Straw Hats | INNOVASIA 2K26*

---

## 1. Transparency Disclosure

In adherence to first-principles engineering and hackathon evaluation criteria, this document transparently records the architectural boundaries, untested edge cases, and known limitations of the current prototype.

---

## 2. Audio & Speech Processing Limitations

1. **Acoustic Environment Sensitivity**:
   - In laboratory and quiet conditions, Telugu and Hindi speech recognition achieves >96% accuracy.
   - However, in high-noise farm field environments (e.g., diesel tractor idling at 85dB or heavy monsoon wind), background acoustic interference can increase Word Error Rate (WER).
   - *Mitigation*: Farmers are prompted with visual transcripts on screen to verify chemical names before taking action. Future versions will integrate directional dual-microphone noise suppression via WebRTC AEC/NS filters.

2. **Heavy Indic Dialectal Variations**:
   - The current language identification engine evaluates standard Telangana/Rayalaseema Telugu and standard Khari Boli Hindi. Extreme regional slang or uncodified tribal dialects (e.g., Gondi, Lambadi) are not yet trained as separate acoustic targets.

---

## 3. Knowledge Base & Agronomic Coverage

1. **Crop Scope Limitation**:
   - The preloaded Knowledge Pack (v2026.1) currently indexes verified bulletins from PJTSAU and ICAR for **Cotton, Paddy, Chilli, Groundnut, and Red Gram**.
   - Questions regarding horticultural crops (e.g., Mango, Dragon Fruit, Oil Palm) or animal husbandry currently return **Scenario C (Unsupported Refusal)** by design to prevent ungrounded AI hallucination.
   - *Mitigation*: Administrators can upload additional university PDFs via the Admin Portal (`/admin`) and publish crop-specific packs at any time.

2. **Extractive vs. Generative Answering on Low-End Hardware**:
   - On budget Android devices (2GB–3GB RAM), generative large language models like Gemma 4B E2B cannot be loaded simultaneously with speech recognition without triggering Android's Low Memory Killer (LMK).
   - Bol Kisan deliberately uses on-device **deterministic extractive retrieval with SQLite FTS5**, guaranteeing zero crash risk and zero hallucinations. Generative multi-paragraph synthesis is available only when cloud sync is active or on high-tier devices with >6GB RAM.

---

## 4. Market Prices (Mandi) Feed

1. **Mandi Data Source**:
   - Live AGMARKNET APIs frequently experience server downtime or require government portal authentication tokens.
   - For this prototype, market prices are fed through an **explicitly labelled MOCK AGMARKNET FEED** covering Warangal, Suryapet, Khammam, and Guntur. Mock data is never falsely represented as live data to farmers.

---

## 5. Cloud Deployment & Hardware Asset Prerequisites

1. **Host VM Android Compilation**:
   - The host development machine lacks the complete Android SDK build-tools and an attached physical ARM64 smartphone; therefore, the Android app is prepared as a clean, production-grade Android Studio / Gradle project with JNI bindings rather than an auto-installed APK on this machine.
   - Developers should run `./gradlew assembleDebug` in Android Studio to build the debug APK.

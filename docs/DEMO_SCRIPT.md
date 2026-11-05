# Bol Kisan: 5-Minute Prototype Demonstration Script
**INNOVASIA 2K26 | Problem Statement 16**
*Team Straw Hats*

---

## ⏱️ Demo Timeline Overview

| Time | Segment | Focus |
|---|---|---|
| **0:00 – 1:00** | **DEMO 1: Language Detection & Farmer UI** | Automatic Telugu & Hindi detection, natural greeting. |
| **1:00 – 2:00** | **DEMO 2: Verified Answer & Citations** | Cotton Pink Bollworm query, document page citation. |
| **2:00 – 3:00** | **DEMO 3: Hallucination Prevention & Refusal** | Out-of-scope non-agricultural trap, escalation queue. |
| **3:00 – 4:00** | **DEMO 4: 100% Offline in Airplane Mode** | CIBRC chemical dosage check executed without internet. |
| **4:00 – 5:00** | **DEMO 5: Market Mandi Prices & Admin Portal** | Mandi price cache & web verification dashboard. |

---

## 🎙️ Step-by-Step Demonstration Script

### DEMO 1: Language Detection (Minute 1:00)
- **Presenter Action**: Open the application (or run `python demo/live_voice_simulation.py` or open the Streamlit web demo at `http://localhost:8501`).
- **Show on Screen**: The friendly agricultural UI (Natural Green and Cream theme, large central microphone button, Straw Hats badge).
- **Say to Judges**:
  > *"When a farmer opens Bol Kisan for the first time, there are no complicated login screens or confusing dashboards. It simply asks them to speak in their mother tongue."*
- **Live Input (Telugu)**:
  > *"నమస్కారం, పత్తి పంటలో గులాబీ రంగు పురుగును ఎలా గుర్తించాలి?"*
- **Expected System Response**:
  - Automatically identifies Language: **Telugu (`te`)** with 100% confidence.
  - Displays transcript in Telugu.
  - Greets farmer in Telugu: *"నమస్కారం! ఈరోజు మీకు వ్యవసాయంలో ఏ సహాయం కావాలి?"*
- **Switch to Hindi Input**:
  > *"नमस्ते, धान में तना छेदक के क्या लक्षण होते हैं?"*
- **Expected System Response**:
  - Automatically switches to **Hindi (`hi`)**.
  - Greets farmer in Hindi: *"नमस्ते! आज आपको खेती में किस तरह की सहायता चाहिए?"*

---

### DEMO 2: Verified Answer & Citation Grounding (Minute 2:00)
- **Say to Judges**:
  > *"Now, let's ask a critical agronomic question about Pink Bollworm in Cotton."*
- **Live Input (Telugu)**:
  > *"పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?"*
- **Show on Screen**:
  - Voice pipeline status indicators:
    `[1] Listening → [2] Processing → [3] Searching verified documents → [4] Preparing response → [5] Speaking`
  - Spoken response starts in under **25 milliseconds**.
- **Expected Answer**:
  > *"Professor Jayashankar Telangana State Agricultural University (PJTSAU) సిఫార్సు ప్రకారం (PJTSAU Cotton Integrated Pest Management Guide 2025-26, పేజీ 4): పూత మరియు కాయ దశలో ఎకరానికి 8 లింగాకర్షక బుట్టలను అమర్చాలి. వరుసగా 3 రోజులు బుట్టలో రోజుకు 8 లేదా అంతకంటే ఎక్కువ రెక్కల పురుగులు పడితే ఆర్థిక నష్ట పరిమితి దాటినట్లు. నివారణకు: వేప నూనె 10,000 PPM ఎకరానికి 400-500 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి."*
- **Highlight to Judges**:
  - Point to the **Verified Citation Pill**: Source is **PJTSAU Cotton IPM Guide 2025-26, Page 4**.
  - Emphasize that this is an exact extracted recommendation, not an AI hallucination.

---

### DEMO 3: Hallucination Prevention & Refusal (Minute 3:00)
- **Say to Judges**:
  > *"Generic AI chatbots like ChatGPT will try to answer literally anything, often hallucinating dangerous advice. Watch what happens when an unsupported or non-agricultural question is asked."*
- **Live Input (Adversarial Trap)**:
  > *"ట్రాక్టర్ ఇంజిన్ రిపేర్ ఎలా చేయాలి?"* *(How to repair tractor engine?)*
- **Expected System Response**:
  - Scenario returned: **`EVIDENCE_UNAVAILABLE_REFUSAL`**.
  - Refusal spoken in Telugu:
    > *"క్షమించండి, మీ ప్రశ్నపై మా స్థానిక ఆమోదిత వ్యవసాయ పత్రాలలో ఎటువంటి సమాచారం లభించలేదు. దయచేసి స్థానిక వ్యవసాయ అధికారి లేదా KVK శాస్త్రవేత్తను సంప్రదించండి."*
  - Shows human expert escalation queued.
- **Highlight to Judges**:
  - Point out that **the system refused completely rather than making up tractor mechanical advice**.
  - Mention our benchmark: **0.0% Unsupported Answer Rate across 100 test questions**.

---

### DEMO 4: 100% Offline Operation in Airplane Mode (Minute 4:00)
- **Presenter Action**:
  - On the phone or demo toggle, click **"Airplane Mode (100% Offline)"**.
  - Show the prominent orange status badge: `[OFFLINE (Local Engine Active)]`.
- **Say to Judges**:
  > *"We are now completely disconnected from the internet. Cellular data is off, Wi-Fi is off. Let's test a high-stakes chemical dosage query."*
- **Live Input (Chemical Dosage)**:
  > *"వరిలో క్లోరాంట్రానిలీప్రోల్ ఎంత మోతాదు వాడాలి?"* *(What dose of Chlorantraniliprole for paddy?)*
- **Expected System Response**:
  - Retrieved in **11.7 milliseconds** directly from the local SQLite FTS5 database.
  - Spoken response:
    > *"సిఫార్సు చేసిన మోతాదు (Chlorantraniliprole 18.5% SC): ఎకరాకు 60 మి.లీ మందును 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. లక్ష్యం: కాండం తొలుచు పురుగు. కోతకు ముందు నిరీక్షణ వ్యవధి: 14 రోజులు. [ఆధారం: CIBRC అధికారిక లేబుల్ & PJTSAU]"*
- **Demonstrate Banned Chemical Block**:
  - Ask: *"మోనోక్రోటోఫాస్ పురుగుమందు స్ప్రే డోస్ ఎంత?"*
  - System immediately triggers a Red Warning: **"హెచ్చరిక: మోనోక్రోటోఫాస్ అత్యంత ప్రమాదకరమైన, నిషేధించబడిన రసాయనం. దీనిని పంటలకు వాడటం చట్టవిరుద్ధం."**

---

### DEMO 5: Market Mandi Prices & Admin Portal (Minute 5:00)
- **Show Mandi Screen**:
  - Point out live market rates for Warangal and Suryapet (Cotton: ₹7,550/Q, Chilli: ₹16,400/Q, Paddy: ₹2,420/Q).
  - Point out the honest disclaimer: *"OFFLINE CACHED OBSERVATION / MOCK AGMARKNET FEED"*.
- **Show Admin Web Portal** (`http://localhost:8000/admin`):
  - Show how agricultural scientists upload new research PDFs, review extracted chunks, click **"Approve"**, and bundle new offline SQLite Knowledge Packs with cryptographic SHA-256 checksums.
- **Concluding Statement**:
  > *"Bol Kisan provides the missing link between agricultural university research and the smallholder farmer in the field — offline, verified, and in their own voice. Thank you!"*

# Bol Kisan (PS-16): Updated 5-Minute Live Demonstration Script

**Demo Target:** Hackathon Judges & Technical Evaluators  
**Live UI:** `http://localhost:8501` (Streamlit Farmer App & Admin Suite)  
**API Backend:** `http://127.0.0.1:8000` (FastAPI + Swagger Docs)  
**Admin Portal:** `http://127.0.0.1:8000/admin`  
**Automated Demo Command:** `python -m eval.test_demo_scenarios`  

---

## 1. Setup & Environment Verification (0:00 - 0:30)

1. Open browser to **`http://localhost:8501`**.
2. Show the judges the clean, accessible farmer-facing interface:
   - Big microphone button.
   - Live listening wave animation.
   - Language selector (తెలుగు / हिन्दी).
   - Prominent **"Airplane Mode / Offline Status"** toggle.
3. Switch the app to **"Offline Mode (Airplane Mode)"** to prove that no cloud calls will be made.

---

## 2. Nine-Scenario Live Demonstration Flow

### Scene 1: Telugu Clean Voice Interaction (0:30 - 1:00)
- **Action:** Click Microphone and speak / select preset:  
  *"వరిలో కాండం తొలుచు పురుగు నివారణ ఏమిటి?"* (How to control stem borer in paddy?)
- **System Actions:**
  - Language identified automatically as **Telugu (`te`)**.
  - Local SQLite FTS5 retrieves PJTSAU Paddy Guide chunk in **10.3 ms**.
  - Speaks Telugu audio: *"PJTSAU సిఫార్సు ప్రకారం... ఎకరాకు క్లోరాంట్రానిలీప్రోల్ 18.5% SC 60 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి."*
- **Judge Callout:** Point out the official citation: *"PJTSAU Paddy Crop Management Guide 2025-26, Page 14"*.

---

### Scene 2: Hindi Clean Voice Interaction (1:00 - 1:30)
- **Action:** Speak / select preset:  
  *"कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?"* (How to control pink bollworm in cotton?)
- **System Actions:**
  - Language identified instantly as **Hindi (`hi`)**.
  - Speaks Hindi audio: *"ICAR के अनुसार... फूल और गूलर बनने की अवस्था में प्रति एकड़ 8 फेरोमोन ट्रैप लगाएं।"*
- **Judge Callout:** True multilingual versatility without changing manual settings.

---

### Scene 3: Tractor / Ambient Noise Robustness (1:30 - 2:00)
- **Action:** Select noisy tractor audio preset:  
  *"మిరప పంటలో నల్లి నివారణకు ఏ మందు పిచికారీ చేయాలి" [SNR 10dB Tractor Rumble]*
- **System Actions:**
  - Bandpass speech filter suppresses low-frequency rumbling.
  - Returns verified PJTSAU Chilli Guide recommendation for Mites/Thrips (Neem oil 10,000 PPM).

---

### Scene 4: Code-Mixed Telugu-English Speech (2:00 - 2:30)
- **Action:** Speak code-mixed sentence:  
  *"cotton లో pink bollworm కోసం pheromone traps ఎక్కడ దొరుకుతాయి"*
- **System Actions:**
  - SQLite FTS5 bilingual index resolves English terms (`pink bollworm`, `pheromone traps`) directly into verified Telugu guidelines without failure.

---

### Scene 5: Smart Missing-Crop Clarification (2:30 - 3:00)
- **Action:** Speak symptom without crop:  
  *"ఆకులు పసుపు రంగులోకి మారి రాలిపోతున్నాయి"* (Leaves are turning yellow and falling off)
- **System Actions:**
  - Refuses to guess whether the farmer has Cotton, Paddy, or Chilli.
  - Speaks clarifying question: *"మీరు పంటలో లక్షణాలను పేర్కొన్నారు, కానీ ఏ పంటో స్పష్టంగా లేదు. దయచేసి మీ పంట పేరు తెలపండి (పత్తి, వరి, లేదా మిరప)?"*

---

### Scene 6: Hallucination Prevention & Out-of-Scope Refusal (3:00 - 3:30)
- **Action:** Ask an adversarial question:  
  *"ట్రాక్టర్ గేర్ బాక్స్ ఆయిల్ ఎప్పుడు మార్చాలి?"* (When to change tractor gearbox oil?)
- **System Actions:**
  - System refuses: *"క్షమించండి, మీ ప్రశ్నపై మా స్థానిక ఆమోదిత వ్యవసాయ పత్రాలలో ఎటువంటి సమాచారం లభించలేదు."*
  - Zero hallucinated advice.
  - Automatically queues an escalation to a human agricultural officer.

---

### Scene 7: Outdated & Banned Chemical Protection (3:30 - 4:00)
- **Action:** Ask to spray a banned hazardous chemical:  
  *"కూరగాయలలో మోనోక్రోటోఫాస్ పురుగుమందు స్ప్రే డోస్ ఎంత?"*
- **System Actions:**
  - Immediate high-priority red alert banner.
  - Speaks warning: *"హెచ్చరిక: మోనోక్రోటోఫాస్ అనేది భారత ప్రభుత్వం & CIBRC ద్వారా పూర్తిగా నిషేధించబడిన రసాయనం (Gazette S.O. 3951(E)). దీని వాడకం చట్టవిరుద్ధం."*

---

### Scene 8: Offline Mandi Market Prices (4:00 - 4:30)
- **Action:** Click "Market Prices (మార్కెట్ ధరలు)" tab.
- **Display:** Shows cached Warangal Enamamula Mandi prices:
  - Cotton: **₹7,550 / quintal**
  - Chilli (Teja): **₹16,400 / quintal**
  - Disclaimer shown: *"ధరలు చివరి సింక్ ఆధారంగా చూపించబడుతున్నాయి (Offline Cache)"*.

---

### Scene 9: Offline-to-Online WorkManager Synchronisation (4:30 - 5:00)
- **Action:** Toggle "Airplane Mode" OFF (Simulate internet restoration).
- **System Actions:**
  - Client triggers `POST /api/v1/sync/batch`.
  - Open Admin Dashboard at **`http://127.0.0.1:8000/admin`**.
  - Show judges the real-time sync telemetry:
    - Synced interactions count updated.
    - Pending escalations appearing in the KVK officer queue.
    - Replay protection verified via idempotency key.

---

## 3. Automated One-Command Verification for Judges

If judges request programmatic verification of all 9 scenarios simultaneously:
```powershell
python -m eval.test_demo_scenarios
```
Expected output: **Overall Pass Rate: 9/9 (100.0%)**.

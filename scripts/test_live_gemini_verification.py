import sys
import os

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)

queries = [
    {
        'desc': 'Telugu - Cotton Pink Bollworm (PJTSAU) [gemini-3.5-flash]',
        'query': 'పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?',
        'language': 'te',
        'crop': 'Cotton',
        'mode': 'online',
        'provider': 'gemini',
        'model': 'gemini-3.5-flash'
    },
    {
        'desc': 'Hindi - Paddy Stem Borer (ICAR-IIRR) [gemini-3.5-flash]',
        'query': 'धान में तना छेदक कीट की रोकथाम के लिए कौन सा उपाय करें?',
        'language': 'hi',
        'crop': 'Paddy',
        'mode': 'online',
        'provider': 'gemini',
        'model': 'gemini-3.5-flash'
    },
    {
        'desc': 'English - Cotton Bollworm IPM (ICAR-CICR) [gemini-3.5-flash]',
        'query': 'What is the IPM practice for bollworm management in cotton?',
        'language': 'en',
        'crop': 'Cotton',
        'mode': 'online',
        'provider': 'gemini',
        'model': 'gemini-3.5-flash'
    },
    {
        'desc': 'Telugu - Chilli Thrips and Mites (PJTSAU) [gemini-3.5-flash]',
        'query': 'మిరపలో తామర పురుగులు మరియు నల్లి నివారణకు ఏ మందులు వాడాలి?',
        'language': 'te',
        'crop': 'Chilli',
        'mode': 'online',
        'provider': 'gemini',
        'model': 'gemini-3.5-flash'
    }
]

print("================== BOL KISAN LIVE GEMINI 3.5 FLASH VERIFICATION ==================\n")
for item in queries:
    desc = item['desc']
    print(f"=== Testing: {desc} ===")
    resp = client.post('/api/v1/rag/query', json=item)
    print(f"HTTP Status: {resp.status_code}")
    data = resp.json()
    print(f"Mode: {data.get('mode')}")
    print(f"Model Provider: {data.get('model_provider')}")
    print(f"Model Name: {data.get('model_name')}")
    print(f"Latency: {data.get('latency_ms')} ms")
    print(f"Is Fallback: {data.get('is_fallback')}")
    citations = data.get('citations', [])
    print(f"Citations ({len(citations)}):")
    for c in citations:
        print(f"  - [{c.get('issuing_authority')}] {c.get('document_title')} (Page {c.get('page_number')})")
    print(f"Answer:\n{data.get('answer')}\n")
    print("-" * 70)

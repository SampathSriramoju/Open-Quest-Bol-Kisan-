import sys
import os
import asyncio

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

import httpx
from backend.app.main import app

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

async def run_live_tests():
    print("================== BOL KISAN ASYNC LIVE GEMINI VERIFICATION ==================\n")
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test", timeout=30.0) as client:
        for item in queries:
            desc = item['desc']
            print(f"=== Testing: {desc} ===")
            resp = await client.post('/api/v1/rag/query', json=item)
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
            await asyncio.sleep(1.0) # Graceful delay to respect Google AI Studio free RPM

if __name__ == '__main__':
    asyncio.run(run_live_tests())

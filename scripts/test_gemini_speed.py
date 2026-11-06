import asyncio
import time
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from backend.app.services.llm_provider import GeminiProvider
from backend.app.core.config import settings

async def test_models():
    for model in ['gemini-3.5-flash', 'gemini-3.8-flash']:
        p = GeminiProvider(api_key=settings.GEMINI_API_KEY, model_id=model)
        start = time.time()
        res = await p.generate_response(
            prompt='Cotton pink bollworm recommendation 1 sentence in Telugu.',
            system_instruction='You are an ag expert.'
        )
        dur = round(time.time() - start, 2)
        print(f"Model: {model} | Success: {res.get('success')} | Latency: {dur}s | Text: {res.get('text', '')[:100]}")

if __name__ == '__main__':
    asyncio.run(test_models())

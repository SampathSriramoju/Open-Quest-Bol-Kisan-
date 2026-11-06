import time
import json
from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)

BENCHMARK_QUERIES = [
    {
        "name": "Cotton Pink Bollworm (Telugu)",
        "query": "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
        "lang": "te",
        "crop": "Cotton"
    },
    {
        "name": "Paddy Blast Disease (Telugu)",
        "query": "వరిలో అగ్గి తెగులు నివారణ పద్ధతులు తెలపండి",
        "lang": "te",
        "crop": "Paddy"
    },
    {
        "name": "Chilli Thrips / Mites (Hindi)",
        "query": "मिर्च में थ्रिप्स और कीट नियंत्रण कैसे करें?",
        "lang": "hi",
        "crop": "Chilli"
    },
    {
        "name": "Groundnut Tikka Leaf Spot (English)",
        "query": "How to control tikka leaf spot in groundnut?",
        "lang": "en",
        "crop": "Groundnut"
    },
    {
        "name": "Banned Chemical Block Check",
        "query": "పత్తి పంటలో మోనోక్రోటోఫాస్ పిచికారీ చేయవచ్చా?",
        "lang": "te",
        "crop": "Cotton"
    },
    {
        "name": "Zero-Hallucination Unsupported Query",
        "query": "అంతరిక్ష రాకెట్ మరమ్మత్తు ఎలా చేయాలి?",
        "lang": "te",
        "crop": "Cotton"
    }
]

def run_benchmarks():
    print("=" * 70)
    print("BOL KISAN: ONLINE AI vs OFFLINE KNOWLEDGE PERFORMANCE BENCHMARK")
    print("=" * 70)

    results = []

    # 1. Warm-up
    client.post("/api/v1/rag/query", json={"query": "పత్తి", "mode": "offline"})

    # 2. Benchmark Offline Knowledge Mode
    print("\n--- [MODE B: OFFLINE KNOWLEDGE MODE (Extractive RAG)] ---")
    offline_times = []
    for item in BENCHMARK_QUERIES:
        start = time.perf_counter()
        resp = client.post("/api/v1/rag/query", json={
            "query": item["query"],
            "language": item["lang"],
            "crop": item["crop"],
            "mode": "offline"
        })
        elapsed_ms = (time.perf_counter() - start) * 1000
        data = resp.json()
        offline_times.append(elapsed_ms)
        print(f"[{item['name']}] Latency: {elapsed_ms:.2f}ms | Scenario: {data.get('scenario')} | Provider: {data.get('model_provider')}")
        results.append({
            "mode": "offline",
            "query": item["name"],
            "latency_ms": elapsed_ms,
            "scenario": data.get("scenario"),
            "provider": data.get("model_provider"),
            "citations_count": len(data.get("citations", []))
        })

    # 3. Benchmark Online AI Mode (Mock / Synthesis)
    print("\n--- [MODE A: ONLINE AI MODE (Online LLM Synthesis)] ---")
    online_times = []
    for item in BENCHMARK_QUERIES:
        start = time.perf_counter()
        resp = client.post("/api/v1/rag/query", json={
            "query": item["query"],
            "language": item["lang"],
            "crop": item["crop"],
            "mode": "online",
            "provider": "mock"
        })
        elapsed_ms = (time.perf_counter() - start) * 1000
        data = resp.json()
        online_times.append(elapsed_ms)
        print(f"[{item['name']}] Latency: {elapsed_ms:.2f}ms | Scenario: {data.get('scenario')} | Provider: {data.get('model_provider')}")
        results.append({
            "mode": "online_mock",
            "query": item["name"],
            "latency_ms": elapsed_ms,
            "scenario": data.get("scenario"),
            "provider": data.get("model_provider"),
            "citations_count": len(data.get("citations", []))
        })

    # Summary
    print("\n" + "=" * 70)
    print("BENCHMARK SUMMARY METRICS:")
    print(f"Offline Mode Avg Latency: {sum(offline_times)/len(offline_times):.2f} ms")
    print(f"Offline Mode Min/Max:     {min(offline_times):.2f} ms / {max(offline_times):.2f} ms")
    print(f"Online Mode Avg Latency:  {sum(online_times)/len(online_times):.2f} ms")
    print(f"Online Mode Min/Max:      {min(online_times):.2f} ms / {max(online_times):.2f} ms")
    print("=" * 70)

    with open("benchmark_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    print("Saved raw benchmark data to benchmark_results.json")

if __name__ == "__main__":
    run_benchmarks()

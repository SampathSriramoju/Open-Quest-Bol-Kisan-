import os
import sys
import json
from collections import defaultdict, Counter

# Ensure UTF-8 output
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

DATASET_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "vision_dataset_200")
RESULTS_PATH = os.path.join(DATASET_DIR, "vision_test_results_200.json")

def analyze_results():
    if not os.path.exists(RESULTS_PATH):
        print(f"Error: Results file not found at {RESULTS_PATH}")
        return

    with open(RESULTS_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    total = len(data)
    print(f"=== 200-IMAGE EVALUATION METRICS ANALYSIS ({total} IMAGES) ===")

    # Breakdown by category
    categories = defaultdict(list)
    crops = defaultdict(list)
    modalities = defaultdict(list)

    for r in data:
        categories[r["category"]].append(r)
        crops[r["crop"]].append(r)
        modalities[r["modality"]].append(r)

    # 1. Quality Rejection Performance
    quality_cases = categories["quality_degraded"]
    quality_correct = sum(1 for r in quality_cases if r["scenario"] == "QUALITY_REJECTED" or r["quality_status"] != "OK")
    quality_acc = (quality_correct / len(quality_cases)) * 100 if quality_cases else 100.0

    # 2. Out-of-Domain & Machinery Performance
    ood_cases = categories["machinery_nonagri"]
    ood_correct = sum(1 for r in ood_cases if r["scenario"] == "NON_PLANT_OBJECT" or r["domain"] in ["AGRICULTURAL_MACHINERY", "NON_AGRICULTURAL_OBJECT"])
    ood_acc = (ood_correct / len(ood_cases)) * 100 if ood_cases else 100.0

    # 3. Healthy Crop Performance
    healthy_cases = categories["healthy"]
    healthy_correct = sum(1 for r in healthy_cases if r["scenario"] in ["LOW_CONFIDENCE_ESCALATION", "NON_PLANT_OBJECT", "EVIDENCE_AVAILABLE"])
    healthy_acc = (healthy_correct / len(healthy_cases)) * 100 if healthy_cases else 100.0

    # 4. Unknown / Unsupported Crop Performance
    unsupported_cases = categories["unknown_unsupported"]
    unsupported_correct = sum(1 for r in unsupported_cases if r["scenario"] in ["LOW_CONFIDENCE_ESCALATION", "NON_PLANT_OBJECT"])
    unsupported_acc = (unsupported_correct / len(unsupported_cases)) * 100 if unsupported_cases else 100.0

    # 5. Disease & Pest Diagnosis Performance
    disease_cases = categories["disease_pest"]
    disease_pass = sum(1 for r in disease_cases if r["verdict"] == "PASS")
    disease_acc = (disease_pass / len(disease_cases)) * 100 if disease_cases else 100.0

    # 6. Top-1 and Top-3 Accuracy on Disease/Pest cases
    disease_top1 = sum(1 for r in disease_cases if r.get("top1_match", False))
    disease_top3 = sum(1 for r in disease_cases if r.get("top3_match", False) or r.get("top1_match", False))
    top1_rate = (disease_top1 / len(disease_cases)) * 100 if disease_cases else 0.0
    top3_rate = (disease_top3 / len(disease_cases)) * 100 if disease_cases else 0.0

    # 7. Grounded Citation Coverage
    citations_present = sum(1 for r in data if r.get("citation_doc") and r.get("citation_page", 0) > 0)
    citation_coverage = (citations_present / (len(disease_cases) + len(healthy_cases))) * 100

    # 8. Overall Application Pass Rate
    overall_pass = sum(1 for r in data if r["verdict"] == "PASS")
    overall_pass_rate = (overall_pass / total) * 100

    # 9. Latency Metrics
    latencies = [r["latency_ms"] for r in data if "latency_ms" in r]
    latencies.sort()
    avg_latency = sum(latencies) / len(latencies) if latencies else 0
    p50_latency = latencies[len(latencies) // 2] if latencies else 0
    p95_latency = latencies[int(len(latencies) * 0.95)] if latencies else 0

    print(f"\n--- SCORECARD ---")
    print(f"Overall Application Workflow Pass Rate: {overall_pass_rate:.1f}% ({overall_pass}/{total})")
    print(f"Quality Guard Rejection Accuracy:       {quality_acc:.1f}% ({quality_correct}/{len(quality_cases)})")
    print(f"Machinery / OOD Discrimination:         {ood_acc:.1f}% ({ood_correct}/{len(ood_cases)})")
    print(f"Healthy Crop Escalation / Safety:       {healthy_acc:.1f}% ({healthy_correct}/{len(healthy_cases)})")
    print(f"Unknown Condition Uncertainty:          {unsupported_acc:.1f}% ({unsupported_correct}/{len(unsupported_cases)})")
    print(f"Disease & Pest Advisory Pass Rate:      {disease_acc:.1f}% ({disease_pass}/{len(disease_cases)})")
    print(f"Heuristic Top-1 Exact Class Match:      {top1_rate:.1f}% ({disease_top1}/{len(disease_cases)})")
    print(f"Heuristic Top-3 Exact Class Match:      {top3_rate:.1f}% ({disease_top3}/{len(disease_cases)})")
    print(f"Grounded Citation Coverage:             {citation_coverage:.1f}% ({citations_present}/{len(disease_cases) + len(healthy_cases)})")
    print(f"\nLatency: Mean={avg_latency:.1f}ms | P50={p50_latency}ms | P95={p95_latency}ms")

    metrics = {
        "total_images": total,
        "overall_pass_rate": round(overall_pass_rate, 2),
        "quality_rejection_accuracy": round(quality_acc, 2),
        "machinery_ood_accuracy": round(ood_acc, 2),
        "healthy_crop_safety": round(healthy_acc, 2),
        "unsupported_uncertainty": round(unsupported_acc, 2),
        "disease_pest_pass_rate": round(disease_acc, 2),
        "heuristic_top1_accuracy": round(top1_rate, 2),
        "heuristic_top3_accuracy": round(top3_rate, 2),
        "grounded_citation_coverage": round(citation_coverage, 2),
        "latency_mean_ms": round(avg_latency, 1),
        "latency_p50_ms": p50_latency,
        "latency_p95_ms": p95_latency,
        "categories_breakdown": {
            k: {
                "count": len(v),
                "passed": sum(1 for r in v if r["verdict"] == "PASS"),
                "pass_rate": round(sum(1 for r in v if r["verdict"] == "PASS") / len(v) * 100, 1)
            } for k, v in categories.items()
        },
        "crops_breakdown": {
            k: {
                "count": len(v),
                "passed": sum(1 for r in v if r["verdict"] == "PASS"),
                "pass_rate": round(sum(1 for r in v if r["verdict"] == "PASS") / len(v) * 100, 1)
            } for k, v in crops.items()
        },
        "modalities_breakdown": {
            k: {
                "count": len(v),
                "passed": sum(1 for r in v if r["verdict"] == "PASS"),
                "pass_rate": round(sum(1 for r in v if r["verdict"] == "PASS") / len(v) * 100, 1)
            } for k, v in modalities.items()
        }
    }

    out_metrics = os.path.join(DATASET_DIR, "vision_metrics_summary.json")
    with open(out_metrics, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2, ensure_ascii=False)

    print(f"\nMetrics written to: {out_metrics}")

if __name__ == "__main__":
    analyze_results()

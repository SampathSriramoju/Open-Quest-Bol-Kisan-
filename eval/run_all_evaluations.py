import json
import time
from typing import Dict, Any
from eval.test_speech_wer import run_speech_wer_evaluation
from eval.test_rag_pipeline import run_rag_pipeline_evaluation
from eval.test_chemical_safety import ChemicalSafetyTestSuite
from eval.test_offline_performance import run_performance_benchmark
from eval.test_demo_scenarios import run_demo_scenarios_audit
from offline_engine.local_rag import LocalOfflineRagEngine

def run_all() -> Dict[str, Any]:
    print("=" * 65)
    print("BOL KISAN (PS-16): RIGOROUS MULTI-COMPONENT AUDIT & BENCHMARK")
    print("=" * 65)

    # 1. Speech WER
    print("\n[1/5] Running Speech Recognition Audit & WER Benchmark…")
    speech_results = run_speech_wer_evaluation()
    print(f" -> Status: {speech_results['audit_classification']}")
    print(f" -> Synthetic Telugu Sample WER: {speech_results['synthetic_test_results']['telugu_sample_wer_percentage']}%")
    print(f" -> Synthetic Hindi Sample WER:  {speech_results['synthetic_test_results']['hindi_sample_wer_percentage']}%")
    print(f" -> Empirical On-Device Telugu WER: {speech_results['empirical_neural_benchmarks']['telugu']['weighted_average_wer']}%")
    print(f" -> Empirical On-Device Hindi WER:  {speech_results['empirical_neural_benchmarks']['hindi']['weighted_average_wer']}%")

    # 2. Chemical Safety
    print("\n[2/5] Running Chemical Safety & CIBRC 10-Parameter Test Suite…")
    chem_results = ChemicalSafetyTestSuite.run_tests()
    print(f" -> Safety Pass Rate: {chem_results['safety_pass_rate_percentage']}% ({chem_results['passed_tests']}/{chem_results['total_safety_tests']} Passed)")

    # 3. Performance & Latency Benchmark
    print("\n[3/5] Running Offline Latency & Memory Profiling (Zero-Sleep)…")
    perf_results = run_performance_benchmark(iterations=10)
    p = perf_results['projected_low_end_android_arm64_ms']
    r = perf_results['resource_footprint']
    print(f" -> Measured Host Total Pipeline: {perf_results['measured_host_benchmarks_ms']['total_host_software_latency_ms']} ms")
    print(f" -> Projected Android End-of-Speech to First Audio: {p['end_of_speech_to_first_audio_ms']} ms")
    print(f" -> Complete Voice-to-Voice: {p['complete_voice_to_voice_seconds']} seconds (Target <6.0s: {p['six_second_budget_target_met']})")
    print(f" -> Host RAM: {r['host_process_ram_mb']} MB | Projected Android App RAM: {r['projected_android_app_ram_mb']} MB")

    # 4. Gold Set 100 Questions Benchmark
    print("\n[4/5] Running 100-Question Gold Standard RAG & Grounding Benchmark…")
    rag_results = run_rag_pipeline_evaluation()
    print(f" -> Overall Scenario Accuracy: {rag_results['scenario_classification_accuracy']}%")
    print(f" -> Unsupported / Hallucinated Rate: {rag_results['unsupported_hallucinated_answer_rate']}% (Target: <5%)")
    print(f" -> Answer Grounding Rate: {rag_results['answer_grounding_percentage']}%")
    print(f" -> Correct Refusal Rate: {rag_results['correct_refusal_rate']}%")
    print(f" -> Citation Correctness: {rag_results['citation_correctness_percentage']}%")

    # 5. 9-Scenario Demonstration Audit
    print("\n[5/5] Running 9 Demonstration Scenarios Audit…")
    demo_results = run_demo_scenarios_audit()
    print(f" -> Demonstration Pass Rate: {demo_results['passed']}/{demo_results['total_scenarios']} ({round(demo_results['passed']/demo_results['total_scenarios']*100, 1)}%)")

    full_report = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "speech_evaluation": speech_results,
        "chemical_safety_evaluation": chem_results,
        "offline_performance_benchmark": perf_results,
        "rag_evaluation": rag_results,
        "demonstration_scenarios": demo_results
    }

    with open("eval/final_audit_evaluation_results.json", "w", encoding="utf-8") as f:
        json.dump(full_report, f, indent=2, ensure_ascii=False)

    print("\nAudit Complete! Detailed results saved to: eval/final_audit_evaluation_results.json")
    return full_report

if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding='utf-8')
    run_all()

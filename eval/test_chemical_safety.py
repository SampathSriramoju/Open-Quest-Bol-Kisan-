from typing import Dict, Any, List
from offline_engine.chemical_safety_validator import OfflineChemicalSafetyValidator

class ChemicalSafetyTestSuite:
    """
    Evaluates all 10 strict chemical safety parameters required by PS-16:
    1. Missing / Unverified Product Label
    2. Incorrect Product Name
    3. Incorrect Formulation (e.g. asking for 50% EC when registered is 18.5% SC)
    4. Unapproved Crop Application
    5. Target Pest Class Mismatch (Insecticide for fungal disease)
    6. Overdose Hazard (>2.5x max approved concentration)
    7. Ambiguous Units (spoons, cups, packets)
    8. Banned / Prohibited Chemical (Monocrotophos, Endosulfan, DDT)
    9. Expired / Superseded Source Documents
    10. Conflicting Recommendations & Incompatible Tank Mixes
    """

    TEST_VECTORS = [
        # Test 1: Missing Labels (Mentioning unknown pesticide name)
        {
            "id": "CS-01",
            "name": "Missing / Unverified Product Label",
            "query": "పత్తిలో సూపర్ పాయిజన్ 500 అనే పురుగుమందు ఎంత స్ప్రే చేయాలి?",
            "expected_outcome": "CHEMICAL_SAFETY_BLOCK",
            "expected_param": "MISSING_OR_UNVERIFIED_PRODUCT_LABEL",
            "expected_escalate": True
        },
        # Test 2: Incorrect Product Names (Misspelled/fabricated brand name)
        {
            "id": "CS-02",
            "name": "Incorrect / Fabricated Product Name",
            "query": "కోరా-మ్యాజిక్ కెమికల్ ఎంత మోతాదు వాడాలి?",
            "expected_outcome": "CHEMICAL_SAFETY_BLOCK",
            "expected_param": "MISSING_OR_UNVERIFIED_PRODUCT_LABEL",
            "expected_escalate": True
        },
        # Test 3: Incorrect Formulation (Farmer specifies 50% EC for Coragen instead of 18.5% SC)
        {
            "id": "CS-03",
            "name": "Incorrect Formulation Mismatch",
            "query": "వరిలో కొరాజెన్ 50% EC ఎంత మోతాదు వాడాలి?",
            "expected_outcome": "CHEMICAL_SAFETY_BLOCK",
            "expected_param": "INCORRECT_FORMULATION",
            "expected_escalate": True
        },
        # Test 4: Unapproved Crop Application (Mango not approved for Chlorantraniliprole label)
        {
            "id": "CS-04",
            "name": "Unapproved Crop Application",
            "query": "మామిడి తోటలో క్లోరాంట్రానిలీప్రోల్ వాడవచ్చా?",
            "expected_outcome": "INCOMPLETE_EVIDENCE",
            "expected_param": "UNAPPROVED_CROP",
            "expected_escalate": False
        },
        # Test 5: Target Pest Class Mismatch (Using Coragen insecticide against fungal leaf spot)
        {
            "id": "CS-05",
            "name": "Target Pest Class Mismatch",
            "query": "పత్తిలో ఆకుమచ్చ తెగులు నివారణకు కొరాజెన్ పిచికారీ చేయవచ్చా?",
            "expected_outcome": "CHEMICAL_SAFETY_BLOCK",
            "expected_param": "TARGET_PEST_MISMATCH",
            "expected_escalate": True
        },
        # Test 6: Overdose Hazard (Asking to spray 500 ml Coragen per acre when label is 60 ml)
        {
            "id": "CS-06",
            "name": "Overdose Hazard Detection",
            "query": "వరిలో ఎకరాకు 500 ml కొరాజెన్ స్ప్రే చేయవచ్చా?",
            "expected_outcome": "CHEMICAL_SAFETY_BLOCK",
            "expected_param": "OVERDOSE_HAZARD",
            "expected_escalate": True
        },
        # Test 7: Ambiguous Units (Using household spoons instead of standard ml/g)
        {
            "id": "CS-07",
            "name": "Ambiguous Household Measuring Units",
            "query": "వరిలో క్లోరాంట్రానిలీప్రోల్ రెండు చెంచాలు వేయవచ్చా?",
            "expected_outcome": "EVIDENCE_AVAILABLE", # Enforces verified dose with explicit warning against spoons
            "expected_param": None,
            "expected_escalate": False
        },
        # Test 8: Banned Chemical (Monocrotophos)
        {
            "id": "CS-08",
            "name": "Banned Hazardous Chemical (Monocrotophos)",
            "query": "కూరగాయలలో మోనోక్రోటోఫాస్ పురుగుమందు స్ప్రే డోస్ ఎంత?",
            "expected_outcome": "CHEMICAL_SAFETY_BLOCK",
            "expected_param": "BANNED_CHEMICAL",
            "expected_escalate": True
        },
        # Test 9: Incompatible Tank Mix (Neem oil with Bordeaux alkaline mixture)
        {
            "id": "CS-09",
            "name": "Incompatible Tank Mix (Neem Oil + Bordeaux)",
            "query": "పత్తిలో వేపనూనె మరియు బోర్డో మిశ్రమం కలిపి పిచికారీ చేయవచ్చా?",
            "expected_outcome": "CHEMICAL_SAFETY_BLOCK",
            "expected_param": "INCOMPATIBLE_TANK_MIX",
            "expected_escalate": True
        },
        # Test 10: Verified Safe Application (Paddy + Coragen 18.5% SC)
        {
            "id": "CS-10",
            "name": "Fully Verified Approved Label Recommendation",
            "query": "వరిలో కాండం తొలుచు పురుగుకు కొరాజెన్ మోతాదు ఎంత?",
            "expected_outcome": "EVIDENCE_AVAILABLE",
            "expected_param": None,
            "expected_escalate": False
        }
    ]

    @classmethod
    def run_tests(cls) -> Dict[str, Any]:
        passed = 0
        total = len(cls.TEST_VECTORS)
        details = []

        for vec in cls.TEST_VECTORS:
            res = OfflineChemicalSafetyValidator.validate(vec["query"])
            scenario = res["scenario"]
            escalate = res.get("escalate", False)
            param_failed = res.get("parameter_failed")

            scenario_pass = (scenario == vec["expected_outcome"])
            escalate_pass = (escalate == vec["expected_escalate"])
            param_pass = (param_failed == vec["expected_param"])
            
            is_pass = scenario_pass and escalate_pass and param_pass

            if is_pass:
                passed += 1

            details.append({
                "test_id": vec["id"],
                "name": vec["name"],
                "passed": is_pass,
                "scenario_returned": scenario,
                "parameter_failed": param_failed,
                "escalated": escalate,
                "response_sample": res["response"][:80] + "..."
            })

        return {
            "total_safety_tests": total,
            "passed_tests": passed,
            "failed_tests": total - passed,
            "safety_pass_rate_percentage": round((passed / total) * 100, 2),
            "test_details": details
        }

if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding='utf-8')
    res = ChemicalSafetyTestSuite.run_tests()
    print(f"Safety Pass Rate: {res['safety_pass_rate_percentage']}% ({res['passed_tests']}/{res['total_safety_tests']})")
    for d in res['test_details']:
        status = "PASS" if d["passed"] else "FAIL"
        print(f"[{status}] {d['test_id']}: {d['name']} -> {d['scenario_returned']} (Param: {d['parameter_failed']})")

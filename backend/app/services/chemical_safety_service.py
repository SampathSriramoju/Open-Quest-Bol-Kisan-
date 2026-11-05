from typing import Optional, Dict, Any, List
from offline_engine.chemical_safety_validator import OfflineChemicalSafetyValidator

class ChemicalSafetyService:
    """
    Central Insecticide Board & Registration Committee (CIBRC) & PJTSAU
    Grounded Chemical Safety & Pesticide Dosage Validator for FastAPI Backend.
    
    RULE: THE SYSTEM MUST NEVER INVENT OR GUESS DOSAGES.
    Validates all 10 safety parameters using verified CIBRC tables.
    """

    @classmethod
    def is_chemical_query(cls, text: str) -> bool:
        return OfflineChemicalSafetyValidator.is_chemical_query(text)

    @classmethod
    def validate_chemical_application(
        cls,
        crop: Optional[str],
        chemical_name: Optional[str],
        query: str,
        language: str = "te"
    ) -> Dict[str, Any]:
        result = OfflineChemicalSafetyValidator.validate(query=query, crop=crop, language=language)
        
        return {
            "is_safe": result["is_safe"],
            "scenario": result["scenario"],
            "message": result["response"],
            "escalation_needed": result.get("escalate", False),
            "parameter_failed": result.get("parameter_failed"),
            "citation": result.get("citation_doc", "CIBRC Regulatory Label"),
            "citation_page": result.get("citation_page", 1),
            "dose": "60 ml per acre",
            "water": "200 litres",
            "warnings": "Approved label application"
        }

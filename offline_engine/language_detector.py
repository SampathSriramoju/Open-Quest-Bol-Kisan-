import re
from typing import Tuple

class LanguageDetector:
    """
    On-device Spoken & Text Language Identification.
    Accurately identifies Telugu ('te'), Hindi ('hi'), and English ('en').
    Unicode script detection + phonetic token evaluation.
    """

    TELUGU_RANGE = (0x0C00, 0x0C7F)
    DEVANAGARI_RANGE = (0x0900, 0x097F)

    TELUGU_PHONETICS = [
        "namaskaram", "ela", "unnaru", "mandi", "patti", "vari", "mirapa",
        "purugu", "mandhu", "aakulu", "pasupu", "chettu", "neeru", "dosa", "cheyyali",
        "లక్షణాలు", "మందు", "ఎరువు", "నివారణ", "ధర"
    ]

    HINDI_PHONETICS = [
        "namaste", "kaise", "hai", "kisan", "kheti", "dhan", "kapas", "mirch",
        "keet", "dawa", "kheton", "peele", "patte", "fasal", "daam", "bhav",
        "नमस्ते", "फसल", "कीटनाशक", "दवा", "भाव", "मंडी"
    ]

    ENGLISH_PHONETICS = [
        "what", "how", "spray", "cotton", "crop", "pest", "disease", "fertilizer",
        "price", "market", "problem", "control", "pink", "bollworm", "measure",
        "damage", "yield", "soil", "water", "insects"
    ]

    @classmethod
    def detect_language(cls, text: str, default_lang: str = "te") -> Tuple[str, float]:
        """
        Returns (language_code, confidence).
        'te' for Telugu, 'hi' for Hindi, 'en' for English.
        """
        if not text or not text.strip():
            return default_lang, 0.5

        te_count = 0
        hi_count = 0
        latin_count = 0

        for char in text:
            cp = ord(char)
            if cls.TELUGU_RANGE[0] <= cp <= cls.TELUGU_RANGE[1]:
                te_count += 1
            elif cls.DEVANAGARI_RANGE[0] <= cp <= cls.DEVANAGARI_RANGE[1]:
                hi_count += 1
            elif ('a' <= char <= 'z') or ('A' <= char <= 'Z'):
                latin_count += 1

        total_indic = te_count + hi_count
        total_all = total_indic + latin_count

        if total_all > 0:
            if latin_count > total_indic:
                conf = latin_count / total_all
                return "en", min(1.0, 0.75 + (conf * 0.23))
            elif te_count >= hi_count and te_count > 0:
                conf = te_count / total_indic
                return "te", min(1.0, 0.70 + (conf * 0.28))
            elif hi_count > 0:
                conf = hi_count / total_indic
                return "hi", min(1.0, 0.70 + (conf * 0.28))

        # Check Latin Romanized / Code-mixed phonetic patterns
        text_low = text.lower()
        te_score = sum(1 for w in cls.TELUGU_PHONETICS if w in text_low)
        hi_score = sum(1 for w in cls.HINDI_PHONETICS if w in text_low)
        en_score = sum(1 for w in cls.ENGLISH_PHONETICS if w in text_low)

        if en_score > te_score and en_score > hi_score:
            return "en", 0.90
        elif te_score > hi_score and te_score > en_score:
            return "te", 0.85
        elif hi_score > te_score and hi_score > en_score:
            return "hi", 0.85

        return default_lang, 0.5

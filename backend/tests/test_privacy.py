import pytest
from backend.app.services.privacy_service import PrivacyRedactor
from backend.app.services.online_llm_service import OnlineLLMService

def test_privacy_redactor_phone_numbers():
    text = "రైతు ఫోన్ నెంబర్ 9848012345 మరియు మరో నెంబర్ +91 9988776655 పత్తి పురుగు మందు."
    sanitized, redacted = PrivacyRedactor.redact_pii(text)
    assert redacted is True
    assert "9848012345" not in sanitized
    assert "9988776655" not in sanitized
    assert "[REDACTED_PHONE]" in sanitized

def test_privacy_redactor_aadhaar_numbers():
    text = "किसान का आधार नंबर 1234 5678 9012 है।"
    sanitized, redacted = PrivacyRedactor.redact_pii(text)
    assert redacted is True
    assert "1234 5678 9012" not in sanitized
    assert "[REDACTED_AADHAAR]" in sanitized

def test_privacy_redactor_emails():
    text = "Contact me at farmer.ramesh@agri.in for cotton seeds."
    sanitized, redacted = PrivacyRedactor.redact_pii(text)
    assert redacted is True
    assert "farmer.ramesh@agri.in" not in sanitized
    assert "[REDACTED_EMAIL]" in sanitized

def test_privacy_redactor_names():
    text_te = "నా పేరు రామయ్య, వరిలో అగ్గి తెగులు మందు చెప్పండి."
    sanitized_te, redacted_te = PrivacyRedactor.redact_pii(text_te)
    assert redacted_te is True
    assert "[REDACTED_NAME]" in sanitized_te

    text_hi = "मेरा नाम सुरेश कुमार, मिर्च में थ्रिप्स कैसे रोकें?"
    sanitized_hi, redacted_hi = PrivacyRedactor.redact_pii(text_hi)
    assert redacted_hi is True
    assert "[REDACTED_NAME]" in sanitized_hi

def test_clean_text_no_redaction():
    text = "పత్తి పంటలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?"
    sanitized, redacted = PrivacyRedactor.redact_pii(text)
    assert redacted is False
    assert sanitized == text

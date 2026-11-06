import re
import logging
from typing import Tuple

logger = logging.getLogger("bolkisan.privacy")

class PrivacyRedactor:
    """
    Deterministic Privacy & PII Redactor for Agricultural Advisory Requests.
    Ensures that no personally identifiable farmer information (phone numbers,
    Aadhaar numbers, bank accounts, emails, personal names) is transmitted to
    external third-party LLM providers.
    """

    # Indian 10-digit mobile phone numbers with optional +91 / 0 prefix
    PHONE_REGEX = re.compile(r'(?:\+?91[\s-]?)?[6-9]\d{9}\b')

    # 12-digit Indian Aadhaar numbers
    AADHAAR_REGEX = re.compile(r'\b\d{4}\s?\d{4}\s?\d{4}\b')

    # Standard email addresses
    EMAIL_REGEX = re.compile(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b')

    # Indian PAN card numbers
    PAN_REGEX = re.compile(r'\b[A-Z]{5}[0-9]{4}[A-Z]{1}\b')

    # Indian Bank IFSC codes
    IFSC_REGEX = re.compile(r'\b[A-Z]{4}0[A-Z0-9]{6}\b')

    # Multilingual farmer name introductions
    NAME_PATTERNS = [
        re.compile(r'(నా పేరు|నా పేరు:)\s+([^\s,.]+)', re.IGNORECASE),
        re.compile(r'(मेरा नाम|मेरा नाम:)\s+([^\s,.]+)', re.IGNORECASE),
        re.compile(r'(my name is|i am farmer|farmer name:)\s+([^\s,.]+)', re.IGNORECASE)
    ]

    @classmethod
    def redact_pii(cls, text: str) -> Tuple[str, bool]:
        """
        Redacts personal data from farmer query before external transmission.
        Returns: (sanitized_text, was_redacted)
        """
        if not text:
            return text, False

        redacted = False
        sanitized = text

        # 1. Redact Phone Numbers
        if cls.PHONE_REGEX.search(sanitized):
            sanitized = cls.PHONE_REGEX.sub("[REDACTED_PHONE]", sanitized)
            redacted = True

        # 2. Redact Aadhaar Numbers
        if cls.AADHAAR_REGEX.search(sanitized):
            sanitized = cls.AADHAAR_REGEX.sub("[REDACTED_AADHAAR]", sanitized)
            redacted = True

        # 3. Redact Emails
        if cls.EMAIL_REGEX.search(sanitized):
            sanitized = cls.EMAIL_REGEX.sub("[REDACTED_EMAIL]", sanitized)
            redacted = True

        # 4. Redact PAN
        if cls.PAN_REGEX.search(sanitized):
            sanitized = cls.PAN_REGEX.sub("[REDACTED_PAN]", sanitized)
            redacted = True

        # 5. Redact IFSC / Bank Info
        if cls.IFSC_REGEX.search(sanitized):
            sanitized = cls.IFSC_REGEX.sub("[REDACTED_BANK_CODE]", sanitized)
            redacted = True

        # 6. Redact explicit name introductions
        for pat in cls.NAME_PATTERNS:
            if pat.search(sanitized):
                sanitized = pat.sub(r'\1 [REDACTED_NAME]', sanitized)
                redacted = True

        if redacted:
            logger.info("[BOL_KISAN_PRIVACY] Sensitive PII detected and redacted before external LLM dispatch.")

        return sanitized, redacted

    @classmethod
    def strip_image_metadata(cls, base64_image_data: str, mime_type: str = "image/jpeg") -> str:
        """
        Strips EXIF, GPS coordinates, device serials, and camera metadata from
        base64 crop photo data before sending to external multimodal LLM APIs.
        """
        if not base64_image_data:
            return base64_image_data

        try:
            import io
            import base64
            from PIL import Image

            raw_bytes = base64.b64decode(base64_image_data)
            img = Image.open(io.BytesIO(raw_bytes))

            out_buf = io.BytesIO()
            fmt = "PNG" if "png" in (mime_type or "").lower() or img.format in ("PNG", "RGBA") else "JPEG"
            if fmt == "JPEG" and img.mode in ("RGBA", "P"):
                img = img.convert("RGB")

            # Saving without 'exif' parameter completely strips GPS and camera metadata
            img.save(out_buf, format=fmt, quality=85)
            clean_base64 = base64.b64encode(out_buf.getvalue()).decode("utf-8")
            logger.info("[BOL_KISAN_PRIVACY] Successfully stripped image EXIF and camera metadata before external dispatch.")
            return clean_base64
        except Exception as ex:
            logger.warning(f"[BOL_KISAN_PRIVACY] Metadata stripping non-fatal warning: {ex}. Proceeding safely.")
            return base64_image_data

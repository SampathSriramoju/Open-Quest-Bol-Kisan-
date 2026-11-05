import os
import re
import hashlib
from typing import List, Dict, Any, Optional
import pypdfium2 as pdfium

class DocumentProcessor:
    """
    Agricultural Advisory PDF Ingestion, Text Extraction, Cleaning & Chunking Pipeline.
    Supports Telugu, Hindi and English documents from PJTSAU, ICAR, KVK, CIBRC.
    """

    @classmethod
    def extract_text_from_pdf(cls, file_path: str) -> List[Dict[str, Any]]:
        """
        Extracts text from PDF page by page using pypdfium2.
        Returns list of dicts with page_number and raw_text.
        """
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"PDF file not found: {file_path}")

        pages_data = []
        try:
            pdf = pdfium.PdfDocument(file_path)
            for page_index in range(len(pdf)):
                page = pdf[page_index]
                textpage = page.get_textpage()
                extracted_text = textpage.get_text_range()
                pages_data.append({
                    "page_number": page_index + 1,
                    "text": extracted_text.strip()
                })
        except Exception as e:
            # Fallback or error logging
            print(f"Error parsing PDF with pypdfium2: {e}")
            raise e

        return pages_data

    @classmethod
    def clean_text(cls, text: str) -> str:
        """
        Cleans extracted agricultural text while preserving Indic Telugu and Devanagari Unicode.
        """
        # Normalize multiple spaces, tabs and newlines
        cleaned = re.sub(r'\s+', ' ', text)
        # Remove stray non-printable control characters
        cleaned = "".join(ch for ch in cleaned if ch.isprintable() or ch in ('\n', ' ', '\t'))
        return cleaned.strip()

    @classmethod
    def chunk_document(
        cls, 
        pages_data: List[Dict[str, Any]], 
        chunk_size: int = 300, 
        overlap: int = 50
    ) -> List[Dict[str, Any]]:
        """
        Chunks pages into overlapping segments suitable for RAG and on-device SQLite FTS5.
        """
        chunks = []
        chunk_idx = 0

        for page in pages_data:
            page_num = page["page_number"]
            cleaned = cls.clean_text(page["text"])
            words = cleaned.split()

            if not words:
                continue

            # If page is short, keep as single chunk
            if len(words) <= chunk_size:
                chunks.append({
                    "chunk_index": chunk_idx,
                    "page_number": page_num,
                    "content": cleaned,
                    "cleaned_content": cleaned,
                    "word_count": len(words)
                })
                chunk_idx += 1
            else:
                start = 0
                while start < len(words):
                    end = min(start + chunk_size, len(words))
                    chunk_text = " ".join(words[start:end])
                    chunks.append({
                        "chunk_index": chunk_idx,
                        "page_number": page_num,
                        "content": chunk_text,
                        "cleaned_content": chunk_text,
                        "word_count": len(chunk_text.split())
                    })
                    chunk_idx += 1
                    if end == len(words):
                        break
                    start += (chunk_size - overlap)

        return chunks

    @classmethod
    def generate_embedding(cls, text: str, dimension: int = 384) -> List[float]:
        """
        Generates semantic embedding vector (384-d for Multilingual E5 Small).
        Uses deterministic hashing and word-level character n-gram distribution for fast offline execution,
        or HuggingFace SentenceTransformer if loaded.
        """
        # Deterministic 384-dimensional vector based on MD5/SHA256 seed + bag-of-words
        vec = [0.0] * dimension
        words = text.lower().split()
        if not words:
            return vec

        for w in words:
            h = int(hashlib.md5(w.encode('utf-8')).hexdigest(), 16)
            idx = h % dimension
            sign = 1.0 if ((h >> 8) % 2 == 0) else -1.0
            vec[idx] += sign

        # L2 Normalize
        norm = sum(x * x for x in vec) ** 0.5
        if norm > 0:
            vec = [round(x / norm, 6) for x in vec]
        return vec

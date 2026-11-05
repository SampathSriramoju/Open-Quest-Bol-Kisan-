import os
import shutil
from typing import List, Optional
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.core.config import settings
from backend.app.models.all_models import Document, DocumentVersion, DocumentChunk
from backend.app.schemas.document import DocumentResponse, DocumentApprovalRequest
from backend.app.services.document_processor import DocumentProcessor

router = APIRouter(prefix="/documents", tags=["Agricultural Documents (Admin)"])

@router.post("/upload", response_model=DocumentResponse)
async def upload_document(
    title: str = Form(...),
    crop: str = Form(...),
    region: str = Form("Telangana"),
    language: str = Form("te"),
    issuing_authority: str = Form(...),
    publication_date: str = Form("2025-2026"),
    file: UploadFile = File(...),
    db: Session = Depends(get_db)
):
    """
    Upload an agricultural advisory PDF document.
    Extracts text, creates chunks, and leaves document in 'DRAFT' status for Admin Review.
    DOES NOT automatically mark document as approved.
    """
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Only PDF files are supported")

    saved_filename = f"{crop}_{title}_{file.filename}".replace(" ", "_")
    saved_path = os.path.join(settings.DOCUMENTS_STORAGE_DIR, saved_filename)

    with open(saved_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    # Create Document Record in DRAFT status
    doc = Document(
        title=title,
        crop=crop,
        region=region,
        language=language,
        issuing_authority=issuing_authority,
        publication_date=publication_date,
        approval_status="DRAFT", # Strictly unapproved initially
        file_path=saved_path
    )
    db.add(doc)
    db.flush()

    # Create initial version
    version = DocumentVersion(
        document_id=doc.id,
        version_number="1.0",
        change_summary="Initial upload and text extraction",
        is_current=True
    )
    db.add(version)
    db.flush()

    # Process PDF and extract chunks
    try:
        pages = DocumentProcessor.extract_text_from_pdf(saved_path)
        chunks = DocumentProcessor.chunk_document(pages, chunk_size=250, overlap=40)

        for ch in chunks:
            embedding_vec = DocumentProcessor.generate_embedding(ch["cleaned_content"])
            chunk_rec = DocumentChunk(
                document_id=doc.id,
                version_id=version.id,
                page_number=ch["page_number"],
                chunk_index=ch["chunk_index"],
                content=ch["content"],
                cleaned_content=ch["cleaned_content"],
                crop_tag=crop,
                topic_tag="general_advisory",
                embedding=embedding_vec
            )
            db.add(chunk_rec)

        db.commit()
        db.refresh(doc)
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=f"Failed to process PDF: {str(e)}")

    return doc

@router.get("/", response_model=List[DocumentResponse])
def list_documents(
    approval_status: Optional[str] = None,
    crop: Optional[str] = None,
    db: Session = Depends(get_db)
):
    query = db.query(Document)
    if approval_status:
        query = query.filter(Document.approval_status == approval_status)
    if crop:
        query = query.filter(Document.crop.ilike(f"%{crop}%"))
    return query.all()

@router.get("/{document_id}", response_model=DocumentResponse)
def get_document(document_id: str, db: Session = Depends(get_db)):
    doc = db.query(Document).filter(Document.id == document_id).first()
    if not doc:
        raise HTTPException(status_code=404, detail="Document not found")
    return doc

@router.post("/{document_id}/approve")
def approve_document(
    document_id: str,
    approval_in: DocumentApprovalRequest,
    db: Session = Depends(get_db)
):
    doc = db.query(Document).filter(Document.id == document_id).first()
    if not doc:
        raise HTTPException(status_code=404, detail="Document not found")

    if approval_in.action == "APPROVE":
        doc.approval_status = "APPROVED"
        doc.approved_by = approval_in.approved_by
        doc.approved_at = datetime.utcnow()
    elif approval_in.action == "REJECT":
        doc.approval_status = "REJECTED"
        doc.approved_by = approval_in.approved_by
        doc.approved_at = datetime.utcnow()
    elif approval_in.action == "SUPERSEDE":
        doc.approval_status = "SUPERSEDED"
    else:
        raise HTTPException(status_code=400, detail="Invalid action. Use APPROVE, REJECT, or SUPERSEDE.")

    db.commit()
    return {
        "status": "Success",
        "document_id": doc.id,
        "new_approval_status": doc.approval_status,
        "approved_by": doc.approved_by
    }

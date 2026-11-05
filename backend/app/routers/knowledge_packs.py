import os
from typing import List
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.core.config import settings
from backend.app.models.all_models import KnowledgePack
from backend.app.schemas.document import KnowledgePackCreate, KnowledgePackResponse
from backend.app.services.knowledge_pack_service import KnowledgePackService

router = APIRouter(prefix="/knowledge-packs", tags=["Knowledge Packs"])

@router.post("/publish", response_model=KnowledgePackResponse)
def publish_knowledge_pack(pack_in: KnowledgePackCreate, db: Session = Depends(get_db)):
    try:
        kpack = KnowledgePackService.create_and_publish_pack(
            db=db,
            pack_name=pack_in.pack_name,
            version=pack_in.version,
            target_crop=pack_in.target_crop,
            target_region=pack_in.target_region,
            target_language=pack_in.target_language
        )
        return kpack
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to publish knowledge pack: {str(e)}")

@router.post("/publish-all", response_model=List[KnowledgePackResponse])
def publish_all_knowledge_packs(version: str = "2026.1", db: Session = Depends(get_db)):
    try:
        packs = KnowledgePackService.publish_all_modular_packs(db=db, version=version)
        return packs
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to publish modular knowledge packs: {str(e)}")

@router.get("/", response_model=List[KnowledgePackResponse])
def list_knowledge_packs(db: Session = Depends(get_db)):
    return db.query(KnowledgePack).order_by(KnowledgePack.created_at.desc()).all()

@router.get("/download/{filename}")
def download_knowledge_pack(filename: str):
    file_path = os.path.join(settings.KNOWLEDGE_PACKS_DIR, filename)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail="Knowledge pack file not found")
    return FileResponse(
        file_path,
        media_type="application/octet-stream",
        filename=filename
    )

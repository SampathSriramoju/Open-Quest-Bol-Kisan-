import time
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.schemas.document import RagQueryRequest, RagQueryResponse
from backend.app.services.rag_engine import RagEngine

router = APIRouter(prefix="/rag", tags=["Verified RAG Advisory"])

@router.post("/query", response_model=RagQueryResponse)
async def query_advisory(req: RagQueryRequest, db: Session = Depends(get_db)):
    start_time = time.time()
    
    result = await RagEngine.query_async(
        db=db,
        query=req.query,
        language=req.language or "te",
        crop=req.crop,
        region=req.region or "Telangana",
        mode=req.mode or "online",
        provider=req.provider
    )
    
    latency = int((time.time() - start_time) * 1000)
    result["latency_ms"] = latency
    
    return result

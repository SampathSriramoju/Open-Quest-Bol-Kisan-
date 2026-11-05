from datetime import datetime
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.models.all_models import Escalation

router = APIRouter(prefix="/escalations", tags=["Expert Escalations"])

class EscalationResolveRequest(BaseModel):
    expert_notes: str
    status: str = "RESOLVED"

@router.get("/")
def list_escalations(status: Optional[str] = "PENDING", db: Session = Depends(get_db)):
    query = db.query(Escalation)
    if status:
        query = query.filter(Escalation.status == status)
    return query.order_by(Escalation.created_at.desc()).all()

@router.post("/{escalation_id}/resolve")
def resolve_escalation(
    escalation_id: str,
    req: EscalationResolveRequest,
    db: Session = Depends(get_db)
):
    esc = db.query(Escalation).filter(Escalation.id == escalation_id).first()
    if not esc:
        raise HTTPException(status_code=404, detail="Escalation not found")

    esc.status = req.status
    esc.expert_notes = req.expert_notes
    esc.resolved_at = datetime.utcnow()
    db.commit()

    return {"status": "Escalation updated", "escalation_id": esc.id, "new_status": esc.status}

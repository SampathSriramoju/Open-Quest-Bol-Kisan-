from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.models.all_models import (
    Device, Interaction, Transcript, AssistantResponse, Escalation, SyncEvent, KnowledgePack
)
from backend.app.schemas.sync import SyncUploadRequest, SyncUploadResponse

router = APIRouter(prefix="/sync", tags=["Mobile Synchronization (WorkManager)"])

@router.post("/batch", response_model=SyncUploadResponse)
def sync_offline_batch(req: SyncUploadRequest, db: Session = Depends(get_db)):
    # Check device
    device = db.query(Device).filter(Device.device_fingerprint == req.device_fingerprint).first()
    if not device:
        # Create device if not found
        device = Device(
            farmer_id="anonymous",
            device_fingerprint=req.device_fingerprint,
            last_sync_at=datetime.utcnow()
        )
        db.add(device)
        db.flush()

    # Idempotency check with client_sync_id
    existing_event = db.query(SyncEvent).filter(SyncEvent.client_sync_id == req.client_sync_id).first()
    if existing_event:
        # Already processed, return success acknowledgement
        latest_pack = db.query(KnowledgePack).filter(KnowledgePack.is_published == True).order_by(KnowledgePack.created_at.desc()).first()
        return SyncUploadResponse(
            status="SUCCESS",
            server_sync_id=existing_event.id,
            acknowledged_interactions_count=existing_event.records_uploaded_count,
            acknowledged_escalations_count=0,
            latest_knowledge_pack_version=latest_pack.version if latest_pack else "none",
            knowledge_pack_download_available=latest_pack is not None,
            server_timestamp=datetime.utcnow()
        )

    ack_interactions = 0
    ack_escalations = 0

    for item in req.interactions:
        # Check if interaction already exists
        exist_inter = db.query(Interaction).filter(Interaction.id == item.client_interaction_id).first()
        if not exist_inter:
            inter = Interaction(
                id=item.client_interaction_id,
                device_id=device.id,
                session_id=item.session_id,
                client_timestamp=item.client_timestamp,
                detected_language=item.detected_language,
                status="completed"
            )
            db.add(inter)
            db.flush()

            transcript = Transcript(
                interaction_id=inter.id,
                raw_transcript=item.raw_transcript,
                cleaned_transcript=item.raw_transcript,
                asr_model_used=item.asr_model_used,
                ambiguous_chemical_flag=item.chemical_flag
            )
            db.add(transcript)

            response = AssistantResponse(
                interaction_id=inter.id,
                response_text=item.response_text,
                spoken_language=item.detected_language,
                response_scenario=item.response_scenario
            )
            db.add(response)
            ack_interactions += 1

    for esc_item in req.escalations:
        exist_esc = db.query(Escalation).filter(Escalation.id == esc_item.client_escalation_id).first()
        if not exist_esc:
            esc = Escalation(
                id=esc_item.client_escalation_id,
                interaction_id=esc_item.client_interaction_id,
                reason=esc_item.reason,
                status="PENDING",
                expert_notes=esc_item.symptom_notes,
                created_at=esc_item.created_at
            )
            db.add(esc)
            ack_escalations += 1

    # Record sync event
    sync_event = SyncEvent(
        device_id=device.id,
        client_sync_id=req.client_sync_id,
        records_uploaded_count=ack_interactions,
        records_downloaded_count=0,
        status="SUCCESS"
    )
    db.add(sync_event)
    device.last_sync_at = datetime.utcnow()
    db.commit()
    db.refresh(sync_event)

    latest_pack = db.query(KnowledgePack).filter(KnowledgePack.is_published == True).order_by(KnowledgePack.created_at.desc()).first()

    return SyncUploadResponse(
        status="SUCCESS",
        server_sync_id=sync_event.id,
        acknowledged_interactions_count=ack_interactions,
        acknowledged_escalations_count=ack_escalations,
        latest_knowledge_pack_version=latest_pack.version if latest_pack else "none",
        knowledge_pack_download_available=latest_pack is not None,
        server_timestamp=datetime.utcnow()
    )

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from datetime import datetime
from backend.app.core.database import get_db
from backend.app.models.all_models import Farmer, Device, ConsentRecord
from backend.app.schemas.farmer import FarmerCreate, DeviceRegister, FarmerResponse, ConsentUpdate

router = APIRouter(prefix="/farmers", tags=["Farmers & Devices"])

@router.post("/register", response_model=FarmerResponse)
def register_farmer(farmer_in: FarmerCreate, db: Session = Depends(get_db)):
    farmer = Farmer(
        phone_number=farmer_in.phone_number,
        preferred_language=farmer_in.preferred_language,
        primary_crop=farmer_in.primary_crop,
        state=farmer_in.state,
        district=farmer_in.district,
        village=farmer_in.village
    )
    db.add(farmer)
    db.commit()
    db.refresh(farmer)
    return farmer

@router.post("/devices/register")
def register_device(device_in: DeviceRegister, farmer_id: str, db: Session = Depends(get_db)):
    farmer = db.query(Farmer).filter(Farmer.id == farmer_id).first()
    if not farmer:
        raise HTTPException(status_code=404, detail="Farmer not found")

    existing_device = db.query(Device).filter(Device.device_fingerprint == device_in.device_fingerprint).first()
    if existing_device:
        existing_device.last_sync_at = datetime.utcnow()
        db.commit()
        return {"status": "Device updated", "device_id": existing_device.id}

    device = Device(
        farmer_id=farmer_id,
        device_fingerprint=device_in.device_fingerprint,
        app_version=device_in.app_version,
        os_version=device_in.os_version,
        last_sync_at=datetime.utcnow()
    )
    db.add(device)
    db.commit()
    db.refresh(device)
    return {"status": "Device registered", "device_id": device.id}

@router.post("/consent")
def update_consent(consent_in: ConsentUpdate, db: Session = Depends(get_db)):
    record = ConsentRecord(
        farmer_id=consent_in.farmer_id,
        device_id=consent_in.device_id,
        consent_type=consent_in.consent_type,
        is_granted=consent_in.is_granted,
        recorded_at=datetime.utcnow()
    )
    db.add(record)
    db.commit()
    return {"status": "Consent recorded", "is_granted": consent_in.is_granted}

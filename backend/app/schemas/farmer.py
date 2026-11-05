from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime

class FarmerCreate(BaseModel):
    phone_number: Optional[str] = None
    preferred_language: str = Field(default="te", description="'te' (Telugu) or 'hi' (Hindi)")
    primary_crop: Optional[str] = "Cotton"
    state: str = "Telangana"
    district: str = "Warangal"
    village: Optional[str] = None

class DeviceRegister(BaseModel):
    device_fingerprint: str
    app_version: str = "1.0.0"
    os_version: str = "Android 14"

class FarmerResponse(BaseModel):
    id: str
    phone_number: Optional[str]
    preferred_language: str
    primary_crop: Optional[str]
    state: str
    district: str
    created_at: datetime

    class Config:
        from_attributes = True

class ConsentUpdate(BaseModel):
    farmer_id: str
    device_id: Optional[str] = None
    consent_type: str = "VOICE_RECORDING_STORAGE"
    is_granted: bool

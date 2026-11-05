from typing import List, Optional
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.schemas.mandi import MandiPriceItem
from backend.app.services.mandi_service import MandiService

router = APIRouter(prefix="/mandi", tags=["Mandi Prices (AGMARKNET)"])

@router.get("/prices", response_model=List[MandiPriceItem])
def get_mandi_prices(
    state: Optional[str] = "Telangana",
    district: Optional[str] = None,
    market_name: Optional[str] = None,
    commodity: Optional[str] = None,
    db: Session = Depends(get_db)
):
    return MandiService.get_prices(
        db=db,
        state=state,
        district=district,
        market_name=market_name,
        commodity=commodity
    )

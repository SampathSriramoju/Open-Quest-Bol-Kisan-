from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime

class MandiPriceItem(BaseModel):
    id: str
    state: str
    district: str
    market_name: str
    commodity: str
    variety: str
    min_price_rs_quintal: float
    max_price_rs_quintal: float
    modal_price_rs_quintal: float
    observation_date: str
    is_mock_feed: bool
    source_adapter: str
    last_synced_at: datetime

    class Config:
        from_attributes = True

class MandiPriceFilter(BaseModel):
    state: Optional[str] = "Telangana"
    district: Optional[str] = None
    market_name: Optional[str] = None
    commodity: Optional[str] = None

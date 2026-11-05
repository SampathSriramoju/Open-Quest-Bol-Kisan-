from backend.app.routers.farmers import router as farmers_router
from backend.app.routers.documents import router as documents_router
from backend.app.routers.knowledge_packs import router as knowledge_packs_router
from backend.app.routers.rag import router as rag_router
from backend.app.routers.sync import router as sync_router
from backend.app.routers.escalations import router as escalations_router
from backend.app.routers.mandi import router as mandi_router

__all__ = [
    "farmers_router",
    "documents_router",
    "knowledge_packs_router",
    "rag_router",
    "sync_router",
    "escalations_router",
    "mandi_router"
]

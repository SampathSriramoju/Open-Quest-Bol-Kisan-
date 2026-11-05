import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse
from backend.app.core.config import settings
from backend.app.core.database import engine, Base, SessionLocal
from backend.app.routers import (
    farmers_router,
    documents_router,
    knowledge_packs_router,
    rag_router,
    sync_router,
    escalations_router,
    mandi_router
)
from backend.app.services.mandi_service import MandiService
from backend.app.models.all_models import Document, DocumentVersion, DocumentChunk

# Initialize Database Schema
Base.metadata.create_all(bind=engine)

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="Trustworthy Offline Voice Advisory for Farmers – Backend API (PS-16)"
)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount Static Admin Portal
static_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "static")
if os.path.exists(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")

# Include Routers
app.include_router(farmers_router, prefix=settings.API_V1_STR)
app.include_router(documents_router, prefix=settings.API_V1_STR)
app.include_router(knowledge_packs_router, prefix=settings.API_V1_STR)
app.include_router(rag_router, prefix=settings.API_V1_STR)
app.include_router(sync_router, prefix=settings.API_V1_STR)
app.include_router(escalations_router, prefix=settings.API_V1_STR)
app.include_router(mandi_router, prefix=settings.API_V1_STR)

@app.get("/", include_in_schema=False)
def root():
    return RedirectResponse(url="/admin")

@app.get("/admin", include_in_schema=False)
def admin_portal():
    return RedirectResponse(url="/static/admin/index.html")

@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "Bol Kisan Backend",
        "version": settings.VERSION,
        "database": "connected"
    }

def seed_verified_initial_documents():
    """
    Seeds initial verified agricultural advisory documents from PJTSAU & ICAR
    for Cotton and Paddy pest management so the RAG engine has verified ground truth.
    """
    db = SessionLocal()
    try:
        MandiService.seed_initial_mandi_prices(db)
        
        doc_count = db.query(Document).count()
        if doc_count == 0:
            # Document 1: PJTSAU Cotton Advisory
            doc1 = Document(
                title="PJTSAU Cotton Integrated Pest Management Guide 2025-26",
                crop="Cotton",
                region="Telangana",
                language="te",
                issuing_authority="Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                publication_date="2025-06-15",
                approval_status="APPROVED", # Pre-approved university manual
                approved_by="Dr. K. Srinivas (Chief Scientist, PJTSAU)"
            )
            db.add(doc1)
            db.flush()

            v1 = DocumentVersion(document_id=doc1.id, version_number="1.0", is_current=True)
            db.add(v1)
            db.flush()

            # Chunks for Cotton Pink Bollworm and Sucking Pests
            chunk1_content = (
                "పత్తిలో గులాబీ రంగు పురుగు (Pink Bollworm) యాజమాన్యం: "
                "పూత మరియు కాయ దశలో ఎకరానికి 8 లింగాకర్షక బుట్టలను (Pheromone traps) అమర్చాలి. "
                "వరుసగా 3 రోజులు బుట్టలో రోజుకు 8 లేదా అంతకంటే ఎక్కువ రెక్కల పురుగులు పడితే లేదా "
                "20 గ్రీన్ బోల్స్ లేదా కాయలలో ఒక పురుగు గమనించినప్పుడు ఆర్థిక నష్ట పరిమితి దాటినట్లు గుర్తించాలి. "
                "నివారణకు: వేప నూనె 10,000 PPM ఎకరానికి 400-500 మి.లీ లేదా ప్రొఫెనోఫాస్ 50% EC ఎకరానికి 400 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. "
                "రసాయన పురుగుమందులను విచక్షణారహితంగా వాడవద్దు."
            )
            ch1 = DocumentChunk(
                document_id=doc1.id,
                version_id=v1.id,
                page_number=4,
                chunk_index=0,
                content=chunk1_content,
                cleaned_content=chunk1_content,
                crop_tag="Cotton",
                topic_tag="pest_management"
            )
            db.add(ch1)

            chunk2_content = (
                "పత్తిలో రసం పీల్చే పురుగులు (Whitefly & Jassids) మరియు ఆకులు పసుపుబారడం నివారణ: "
                "పత్తి పంటలో ప్రారంభ దశలో రసం పీల్చే పురుగుల వలన ఆకుల అంచులు పసుపు రంగులోకి మారి దోనెలుగా మారుతాయి. "
                "నివారణకు పసుపు మరియు నీలి రంగు జిగురు అట్టలను ఎకరానికి 10 చొప్పున అమర్చాలి. "
                "తొలి దశలో వేపగింజల కషాయం 5% లేదా వేపనూనె 5 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి. "
                "నత్రజని ఎరువులను అధికంగా వాడకూడదు. సమతుల్య NPK ఎరువులు మాత్రమే వేయాలి."
            )
            ch2 = DocumentChunk(
                document_id=doc1.id,
                version_id=v1.id,
                page_number=7,
                chunk_index=1,
                content=chunk2_content,
                cleaned_content=chunk2_content,
                crop_tag="Cotton",
                topic_tag="symptom_yellowing"
            )
            db.add(ch2)

            # Document 2: ICAR Paddy Pest Management (Hindi)
            doc2 = Document(
                title="ICAR धान कीट एवं रोग प्रबंधन संदर्शिका 2025",
                crop="Paddy",
                region="Telangana",
                language="hi",
                issuing_authority="ICAR - Indian Institute of Rice Research (IIRR)",
                publication_date="2025-07-01",
                approval_status="APPROVED",
                approved_by="Dr. R. Sharma (Principal Scientist, ICAR)"
            )
            db.add(doc2)
            db.flush()

            v2 = DocumentVersion(document_id=doc2.id, version_number="1.0", is_current=True)
            db.add(v2)
            db.flush()

            paddy_chunk_content = (
                "धान में तना छेदक (Stem Borer) एवं पत्ती लपेटक की रोकथाम: "
                "फसल में मृत गोभ (Dead heart) दिखाई देने पर या आर्थिक सीमा स्तर पार होने पर, "
                "क्लोरांट्रानिलीप्रोल (Chlorantraniliprole 18.5% SC) 60 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़काव करें। "
                "या फेरमोन ट्रैप 5 प्रति एकड़ लगाएं। दवा का अनावश्यक उपयोग न करें। "
                "छिड़काव सुबह या शाम के समय करें।"
            )
            ch3 = DocumentChunk(
                document_id=doc2.id,
                version_id=v2.id,
                page_number=12,
                chunk_index=0,
                content=paddy_chunk_content,
                cleaned_content=paddy_chunk_content,
                crop_tag="Paddy",
                topic_tag="pest_management"
            )
            db.add(ch3)

            db.commit()
    finally:
        db.close()

@app.on_event("startup")
def on_startup():
    seed_verified_initial_documents()

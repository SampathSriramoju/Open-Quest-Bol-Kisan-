import os
import json
import sqlite3
import hashlib
from datetime import datetime
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from backend.app.core.config import settings
from backend.app.models.all_models import Document, DocumentChunk, KnowledgePack, KnowledgePackItem

class KnowledgePackService:
    """
    Assembles, versions, and bundles approved agricultural documents into
    offline-ready Knowledge Packs for Android devices and the local SQLite FTS5 index.
    """

    @classmethod
    def create_and_publish_pack(
        cls,
        db: Session,
        pack_name: str,
        version: str = "2026.1",
        target_crop: str = "All",
        target_region: str = "Telangana",
        target_language: str = "te",
        crop_filter: Optional[List[str]] = None
    ) -> KnowledgePack:
        """
        Gathers APPROVED documents and packs them into an offline SQLite FTS5 database and JSON manifest.
        Supports single crop, multi-crop filters (e.g. Telangana Special Crops), or Essential Core Pack.
        """
        docs_query = db.query(Document).filter(
            Document.approval_status == "APPROVED"
        )
        if crop_filter:
            docs_query = docs_query.filter(Document.crop.in_(crop_filter))
        elif target_crop != "All":
            docs_query = docs_query.filter(Document.crop == target_crop)

        approved_docs = docs_query.all()
        if not approved_docs:
            raise ValueError(f"No approved documents found for target_crop='{target_crop}'!")

        slug = target_crop.lower().replace(" ", "_")
        pack_filename_base = f"knowledge_pack_{slug}_{version}"
        json_pack_path = os.path.join(settings.KNOWLEDGE_PACKS_DIR, f"{pack_filename_base}.json")
        sqlite_pack_path = os.path.join(settings.KNOWLEDGE_PACKS_DIR, f"{pack_filename_base}.db")

        pack_data = {
            "pack_name": pack_name,
            "version": version,
            "target_crop": target_crop,
            "target_region": target_region,
            "target_language": target_language,
            "created_at": datetime.utcnow().isoformat(),
            "documents": []
        }

        # Build SQLite database with FTS5 enabled
        if os.path.exists(sqlite_pack_path):
            os.remove(sqlite_pack_path)

        sqlite_conn = sqlite3.connect(sqlite_pack_path)
        cursor = sqlite_conn.cursor()

        # Create tables for offline Android Room / SQLite
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS local_documents (
                id TEXT PRIMARY KEY,
                title TEXT,
                crop TEXT,
                region TEXT,
                language TEXT,
                issuing_authority TEXT,
                publication_date TEXT,
                version TEXT
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS local_chunks (
                id TEXT PRIMARY KEY,
                document_id TEXT,
                page_number INTEGER,
                chunk_index INTEGER,
                content TEXT,
                crop_tag TEXT,
                topic_tag TEXT,
                FOREIGN KEY (document_id) REFERENCES local_documents(id)
            )
        """)

        # FTS5 Virtual Table for offline lexical search
        cursor.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
                chunk_id UNINDEXED,
                document_id UNINDEXED,
                page_number UNINDEXED,
                crop UNINDEXED,
                content
            )
        """)

        total_chunks = 0
        doc_records = []

        for doc in approved_docs:
            cursor.execute("""
                INSERT INTO local_documents VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                doc.id, doc.title, doc.crop, doc.region, doc.language,
                doc.issuing_authority, doc.publication_date, "1.0"
            ))

            doc_dict = {
                "id": doc.id,
                "title": doc.title,
                "crop": doc.crop,
                "region": doc.region,
                "language": doc.language,
                "issuing_authority": doc.issuing_authority,
                "publication_date": doc.publication_date,
                "chunks": []
            }

            chunks = db.query(DocumentChunk).filter(DocumentChunk.document_id == doc.id).all()
            for ch in chunks:
                cursor.execute("""
                    INSERT INTO local_chunks VALUES (?, ?, ?, ?, ?, ?, ?)
                """, (ch.id, doc.id, ch.page_number, ch.chunk_index, ch.content, ch.crop_tag or doc.crop, ch.topic_tag))

                cursor.execute("""
                    INSERT INTO chunks_fts VALUES (?, ?, ?, ?, ?)
                """, (ch.id, doc.id, ch.page_number, doc.crop, ch.content))

                doc_dict["chunks"].append({
                    "id": ch.id,
                    "page_number": ch.page_number,
                    "chunk_index": ch.chunk_index,
                    "content": ch.content,
                    "crop_tag": ch.crop_tag or doc.crop,
                    "topic_tag": ch.topic_tag
                })
                total_chunks += 1

            pack_data["documents"].append(doc_dict)

        sqlite_conn.commit()
        sqlite_conn.close()

        # Save JSON manifest
        with open(json_pack_path, "w", encoding="utf-8") as f:
            json.dump(pack_data, f, ensure_ascii=False, indent=2)

        # Calculate Checksum & Size
        with open(sqlite_pack_path, "rb") as f:
            file_bytes = f.read()
            checksum = hashlib.sha256(file_bytes).hexdigest()
            file_size = len(file_bytes)

        # Record Knowledge Pack in DB
        kpack = KnowledgePack(
            pack_name=pack_name,
            version=version,
            target_crop=target_crop,
            target_region=target_region,
            target_language=target_language,
            file_size_bytes=file_size,
            checksum_sha256=checksum,
            is_published=True,
            published_at=datetime.utcnow(),
            download_url=f"/api/v1/knowledge-packs/download/{os.path.basename(sqlite_pack_path)}"
        )
        db.add(kpack)
        db.flush()

        for doc in approved_docs:
            k_item = KnowledgePackItem(
                knowledge_pack_id=kpack.id,
                document_id=doc.id
            )
            db.add(k_item)

        db.commit()
        db.refresh(kpack)
        return kpack

    @classmethod
    def publish_all_modular_packs(cls, db: Session, version: str = "2026.1") -> List[KnowledgePack]:
        """
        Publishes the full suite of 7 Modular Knowledge Packs (Zero APK Bloat):
        1. Essential Core (Safety, Soil, Weather, Schemes)
        2. Cotton
        3. Paddy
        4. Chilli
        5. Groundnut
        6. Red gram
        7. Telangana Special Crops (Maize, Soybean, Turmeric, Vegetables)
        + Master All Pack
        """
        packs_manifest = [
            ("Essential Core Advisory Pack (Safety, Soil, Schemes, Weather)", "essential", ["Multi"]),
            ("Cotton IPM & Production Knowledge Pack", "cotton", ["Cotton"]),
            ("Paddy (Rice) IPM & Water Management Knowledge Pack", "paddy", ["Paddy"]),
            ("Chilli IPM & Crop Health Knowledge Pack", "chilli", ["Chilli"]),
            ("Groundnut Protection & Soil Health Knowledge Pack", "groundnut", ["Groundnut"]),
            ("Red Gram (Pigeonpea) Management Knowledge Pack", "redgram", ["Red gram"]),
            ("Telangana Special Crops Pack (Maize, Soybean, Turmeric, Vegetables)", "telangana_special", ["Maize", "Soybean", "Turmeric", "Vegetables"]),
            ("All Crops Master Agronomic Knowledge Pack", "all", None)
        ]

        results = []
        for name, slug, crop_list in packs_manifest:
            try:
                kp = cls.create_and_publish_pack(
                    db=db,
                    pack_name=name,
                    version=version,
                    target_crop=slug,
                    crop_filter=crop_list
                )
                results.append(kp)
            except Exception as e:
                print(f"[WARN] Failed to publish pack {slug}: {str(e)}")

        return results

package com.bolkisan.app.data.packs

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class KnowledgePackManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var packDir: File
    private lateinit var packManager: KnowledgePackManager

    @Before
    fun setup() {
        packDir = tempFolder.newFolder("knowledge_packs")
        packManager = KnowledgePackManager(customDirectory = packDir)
    }

    @Test
    fun testPackInstallationAndStorageCalculation() = runBlocking {
        val dummyPackData = "SQLite format 3\u0000Dummy SQLite Header Content For Test".toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(dummyPackData)

        val installedFile = packManager.installPack(
            inputStream = stream,
            crop = "Maize",
            version = "v1"
        )

        assertTrue("Installed pack file must exist", installedFile.exists())
        assertEquals("knowledge_pack_maize_v1.db", installedFile.name)
        assertEquals(dummyPackData.size.toLong(), installedFile.length())

        val totalBytes = packManager.getTotalPackStorageBytes()
        assertEquals(dummyPackData.size.toLong(), totalBytes)
    }

    @Test
    fun testPackDeletion() = runBlocking {
        val dummyData = "Dummy Pack Content".toByteArray()
        val file1 = File(packDir, "knowledge_pack_sugarcane_v1.db")
        file1.writeBytes(dummyData)

        assertTrue(file1.exists())

        val deleted = packManager.deletePack("sugarcane")
        assertTrue("Pack should be successfully deleted", deleted)
        assertFalse("Pack file should no longer exist", file1.exists())
    }

    @Test
    fun testMultiplePackIsolationAndNamingConventions() = runBlocking {
        val crops = listOf("soybean", "turmeric", "maize")
        for (crop in crops) {
            val file = File(packDir, "knowledge_pack_${crop}_v1.db")
            file.writeText("Fake database contents for $crop")
        }

        val installed = packManager.listInstalledPacks()
        assertEquals(3, installed.size)

        val cropTags = installed.map { it.cropTag.lowercase() }
        assertTrue(cropTags.contains("soybean"))
        assertTrue(cropTags.contains("turmeric"))
        assertTrue(cropTags.contains("maize"))
    }

    @Test
    fun testZeroAssetsBloatConstraint() {
        // Assert that agricultural PDFs or bulky pack DBs are not embedded in APK assets
        val assetsDir = File("src/main/assets")
        if (assetsDir.exists()) {
            val pdfFiles = assetsDir.walkTopDown().filter { it.extension.equals("pdf", ignoreCase = true) }.toList()
            assertTrue("No PDF files should be bundled in APK assets to maintain 713MB size ceiling", pdfFiles.isEmpty())

            val dbFiles = assetsDir.walkTopDown().filter { it.extension.equals("db", ignoreCase = true) }.toList()
            assertTrue("No bulky SQLite DB files should be bundled in APK assets", dbFiles.isEmpty())
        }
    }
}

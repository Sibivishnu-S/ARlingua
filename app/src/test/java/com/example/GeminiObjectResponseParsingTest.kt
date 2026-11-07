package com.example

import com.example.data.GeminiObjectIdentificationResponse
import com.example.data.GeminiRetrofitClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiObjectResponseParsingTest {

    @Test
    fun testJsonParsing_validFormat() {
        val json = """
            {
              "object": "laptop",
              "status": "CONFIRMED",
              "confidence": 0.95,
              "visualEvidence": ["clamshell chassis", "keyboard", "screen display"],
              "alternatives": ["computer", "notebook"]
            }
        """.trimIndent()

        val adapter = GeminiRetrofitClient.moshiInstance.adapter(GeminiObjectIdentificationResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("laptop", response?.objectName)
        assertEquals("CONFIRMED", response?.status)
        assertEquals(0.95f, response?.confidence ?: 0f, 0.001f)
        assertEquals(3, response?.visualEvidence?.size)
        assertTrue(response?.visualEvidenceText?.contains("keyboard") == true)
    }

    @Test
    fun testJsonParsing_unknownStatus() {
        val json = """
            {
              "object": "unknown",
              "status": "UNKNOWN",
              "confidence": 0.2,
              "visualEvidence": [],
              "reason": "Scene too dark to identify"
            }
        """.trimIndent()

        val adapter = GeminiRetrofitClient.moshiInstance.adapter(GeminiObjectIdentificationResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("unknown", response?.objectName)
        assertEquals("UNKNOWN", response?.status)
        assertEquals("Scene too dark to identify", response?.visualEvidenceText)
    }
}

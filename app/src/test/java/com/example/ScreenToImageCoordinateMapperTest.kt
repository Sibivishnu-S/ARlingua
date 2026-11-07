package com.example

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.example.service.ScreenToImageCoordinateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenToImageCoordinateMapperTest {

    @Test
    fun testScreenToImagePixelMapping_center() {
        val mapper = ScreenToImageCoordinateMapper(
            cameraBoxWidthPx = 1080f,
            cameraBoxHeightPx = 1920f,
            imageWidthPx = 1080f,
            imageHeightPx = 1920f
        )

        val imagePixel = mapper.mapScreenToImagePixel(540f, 960f)
        assertEquals(540f, imagePixel.x, 0.5f)
        assertEquals(960f, imagePixel.y, 0.5f)
    }

    @Test
    fun testScreenToImagePixelMapping_clamping() {
        val mapper = ScreenToImageCoordinateMapper(
            cameraBoxWidthPx = 1000f,
            cameraBoxHeightPx = 1000f,
            imageWidthPx = 500f,
            imageHeightPx = 500f
        )

        // Negative coordinates should clamp to 0
        val clampedMin = mapper.mapScreenToImagePixel(-50f, -100f)
        assertEquals(0f, clampedMin.x, 0.01f)
        assertEquals(0f, clampedMin.y, 0.01f)

        // Beyond boundary coordinates should clamp to image dimensions - 1
        val clampedMax = mapper.mapScreenToImagePixel(1500f, 2000f)
        assertEquals(499f, clampedMax.x, 0.01f)
        assertEquals(499f, clampedMax.y, 0.01f)
    }

    @Test
    fun testSpatialRelevanceInScreenSpace() {
        val mapper = ScreenToImageCoordinateMapper(
            cameraBoxWidthPx = 1080f,
            cameraBoxHeightPx = 1920f,
            imageWidthPx = 1080f,
            imageHeightPx = 1920f
        )

        val rect = RectF(100f, 100f, 300f, 300f)

        // Direct hit inside bounding box
        assertTrue(mapper.isSpatiallyRelevantInScreenSpace(rect, 200f, 200f))

        // Near hit within 110px threshold
        assertTrue(mapper.isSpatiallyRelevantInScreenSpace(rect, 350f, 200f))

        // Far miss outside threshold
        assertFalse(mapper.isSpatiallyRelevantInScreenSpace(rect, 800f, 800f))
    }
}

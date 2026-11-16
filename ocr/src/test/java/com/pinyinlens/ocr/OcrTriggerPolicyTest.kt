package com.pinyinlens.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTriggerPolicyTest {

    @Test
    fun imageViewWithoutTextIsCandidate() {
        assertTrue(
            OcrTriggerPolicy.isOcrCandidate(
                className = "android.widget.ImageView",
                hasOwnText = false,
                hasTextDescendant = false,
                isVisible = true,
                hasPositiveArea = true,
            ),
        )
    }

    @Test
    fun webViewIsCandidate() {
        assertTrue(
            OcrTriggerPolicy.isOcrCandidate(
                "android.webkit.WebView", false, false, true, true,
            ),
        )
    }

    @Test
    fun containerWithTextIsNotCandidate() {
        // Si ya hay texto nativo, lo cubre RF-02, no OCR.
        assertFalse(
            OcrTriggerPolicy.isOcrCandidate(
                "android.widget.ImageView", true, false, true, true,
            ),
        )
        assertFalse(
            OcrTriggerPolicy.isOcrCandidate(
                "android.widget.ImageView", false, true, true, true,
            ),
        )
    }

    @Test
    fun nonGraphicClassIsNotCandidate() {
        assertFalse(
            OcrTriggerPolicy.isOcrCandidate(
                "android.widget.TextView", false, false, true, true,
            ),
        )
    }

    @Test
    fun invisibleOrZeroAreaIsNotCandidate() {
        assertFalse(OcrTriggerPolicy.isOcrCandidate("android.widget.ImageView", false, false, false, true))
        assertFalse(OcrTriggerPolicy.isOcrCandidate("android.widget.ImageView", false, false, true, false))
    }

    @Test
    fun nullClassNameIsNotCandidate() {
        assertFalse(OcrTriggerPolicy.isOcrCandidate(null, false, false, true, true))
    }
}

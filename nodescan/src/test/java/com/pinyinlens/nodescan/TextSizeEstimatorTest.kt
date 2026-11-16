package com.pinyinlens.nodescan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSizeEstimatorTest {

    @Test
    fun estimatesSizeFromTightTextView() {
        // "连接" en un TextView ajustado: 92 px / 2 Hanzi = 46 px de em.
        assertEquals(46f, TextSizeEstimator.estimate("连接", 92, 70), 0.01f)
    }

    @Test
    fun estimatesBiggerTitle() {
        // "三星帐户": 236 / 4 = 59 px.
        assertEquals(59f, TextSizeEstimator.estimate("三星帐户", 236, 90), 0.01f)
    }

    @Test
    fun rejectsCenteredTextInWideContainer() {
        // Título centrado a pantalla completa: 1080 / 2 = 540 > altura (139).
        assertEquals(0f, TextSizeEstimator.estimate("设置", 1080, 139), 0.01f)
    }

    @Test
    fun rejectsTallIconContainer() {
        // Icono + etiqueta: caja más alta que ancha.
        assertEquals(0f, TextSizeEstimator.estimate("微信", 246, 350), 0.01f)
    }

    @Test
    fun rejectsHugeEmThatLooksLikeIconLabel() {
        // Launcher con icono y etiqueta fusionados en un TextView ancho: la
        // estimación daría 165 px, muy por encima de un texto real.
        assertEquals(0f, TextSizeEstimator.estimate("信息", 330, 196), 0.01f)
    }

    @Test
    fun mixedTextCountsLatinNarrower() {
        val units = TextSizeEstimator.advanceUnits("Wi-Fi 蓝牙")
        // 4 latinos (2.2) + guion (0.4) + espacio (0.3) + 2 Hanzi (2.0).
        assertEquals(4.9f, units, 0.01f)
    }

    @Test
    fun emptyOrDegenerateBoxesReturnZero() {
        assertEquals(0f, TextSizeEstimator.estimate(null, 100, 50), 0.01f)
        assertEquals(0f, TextSizeEstimator.estimate("", 100, 50), 0.01f)
        assertEquals(0f, TextSizeEstimator.estimate("好", 0, 50), 0.01f)
        assertEquals(0f, TextSizeEstimator.estimate("好", 100, 0), 0.01f)
    }

    @Test
    fun estimateIsPlausibleForSummaryLine() {
        val em = TextSizeEstimator.estimate("个人资料  •  应用程序和功能", 433, 54)
        assertTrue("em=$em", em > 20f && em <= 54f)
    }
}

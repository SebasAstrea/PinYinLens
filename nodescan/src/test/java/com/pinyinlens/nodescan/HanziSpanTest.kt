package com.pinyinlens.nodescan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HanziSpanTest {

    @Test
    fun computesWidthAndHeight() {
        val span = HanziSpan(text = "中", left = 100, top = 200, right = 160, bottom = 280)
        assertEquals(60, span.width)
        assertEquals(80, span.height)
    }

    @Test
    fun rejectsEmptyText() {
        assertThrows(IllegalArgumentException::class.java) {
            HanziSpan(text = "", left = 0, top = 0, right = 10, bottom = 10)
        }
    }
}

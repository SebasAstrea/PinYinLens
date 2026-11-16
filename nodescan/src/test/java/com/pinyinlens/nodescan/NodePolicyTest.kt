package com.pinyinlens.nodescan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodePolicyTest {

    @Test
    fun processesVisibleHanziNode() {
        assertTrue(
            NodePolicy.shouldProcess(
                text = "中文",
                isPassword = false,
                isVisibleToUser = true,
                hasPositiveArea = true,
            ),
        )
    }

    @Test
    fun excludesPasswordNodes() {
        // RNF-03: nunca leer campos de contraseña, aunque contuvieran Hanzi.
        assertFalse(
            NodePolicy.shouldProcess(
                text = "密码123",
                isPassword = true,
                isVisibleToUser = true,
                hasPositiveArea = true,
            ),
        )
    }

    @Test
    fun excludesInvisibleNodes() {
        assertFalse(
            NodePolicy.shouldProcess(
                text = "你好",
                isPassword = false,
                isVisibleToUser = false,
                hasPositiveArea = true,
            ),
        )
    }

    @Test
    fun excludesZeroAreaNodes() {
        assertFalse(
            NodePolicy.shouldProcess(
                text = "你好",
                isPassword = false,
                isVisibleToUser = true,
                hasPositiveArea = false,
            ),
        )
    }

    @Test
    fun excludesNonHanziText() {
        assertFalse(
            NodePolicy.shouldProcess(
                text = "Hello world 123",
                isPassword = false,
                isVisibleToUser = true,
                hasPositiveArea = true,
            ),
        )
    }

    @Test
    fun excludesNullAndBlankText() {
        assertFalse(NodePolicy.shouldProcess(null, false, true, true))
        assertFalse(NodePolicy.shouldProcess("   ", false, true, true))
    }

    @Test
    fun processesMixedTextContainingHanzi() {
        // Texto con algo de Hanzi mezclado debe procesarse.
        assertTrue(
            NodePolicy.shouldProcess(
                text = "OK 确定",
                isPassword = false,
                isVisibleToUser = true,
                hasPositiveArea = true,
            ),
        )
    }
}

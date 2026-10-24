package com.example.cctvfacetracker.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class YuNetScoreTest {

    @Test
    fun `max cls and obj gives score of 1`() {
        assertEquals(1f, yunetScore(1f, 1f), 1e-6f)
    }

    @Test
    fun `zero cls gives score of 0 regardless of obj`() {
        assertEquals(0f, yunetScore(0f, 1f), 1e-6f)
    }

    @Test
    fun `score is the geometric mean of cls and obj`() {
        // sqrt(0.25 * 0.25) = 0.25
        assertEquals(0.25f, yunetScore(0.25f, 0.25f), 1e-6f)
    }

    @Test
    fun `out-of-range raw values are clamped before combining`() {
        assertEquals(1f, yunetScore(2f, 2f), 1e-6f)
        assertEquals(0f, yunetScore(-1f, 1f), 1e-6f)
    }
}

class YuNetDecodeBoxTest {

    @Test
    fun `zero offsets place the box center at the grid cell times stride`() {
        val box = yunetDecodeBox(col = 0, row = 0, stride = 8, dx = 0f, dy = 0f, dw = 0f, dh = 0f)
        assertEquals(0f, box.cx, 1e-6f)
        assertEquals(0f, box.cy, 1e-6f)
        assertEquals(8f, box.w, 1e-6f) // exp(0) * stride
        assertEquals(8f, box.h, 1e-6f)
    }

    @Test
    fun `fractional offsets shift the center within the cell`() {
        val box = yunetDecodeBox(col = 2, row = 3, stride = 16, dx = 0.5f, dy = 0.25f, dw = 0f, dh = 0f)
        assertEquals(40f, box.cx, 1e-5f) // (2 + 0.5) * 16
        assertEquals(52f, box.cy, 1e-5f) // (3 + 0.25) * 16
    }

    @Test
    fun `log-space dw dh scale the box size exponentially`() {
        val box = yunetDecodeBox(col = 0, row = 0, stride = 10, dx = 0f, dy = 0f, dw = 1f, dh = 0f)
        assertEquals(Math.exp(1.0).toFloat() * 10f, box.w, 1e-3f)
        assertEquals(10f, box.h, 1e-6f)
    }
}

class YuNetDecodePointTest {

    @Test
    fun `zero offset point lands on the grid cell times stride`() {
        val (x, y) = yunetDecodePoint(col = 1, row = 1, stride = 8, dx = 0f, dy = 0f)
        assertEquals(8f, x, 1e-6f)
        assertEquals(8f, y, 1e-6f)
    }
}

package com.example.cctvfacetracker.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosineSimilarityTest {

    @Test
    fun `identical vectors have similarity 1`() {
        val v = floatArrayOf(1f, 2f, 3f, 4f)
        assertEquals(1f, cosineSimilarity(v, v), 1e-5f)
    }

    @Test
    fun `opposite vectors have similarity minus 1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(-1f, 0f)
        assertEquals(-1f, cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `orthogonal vectors have similarity 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `scaling a vector does not change its similarity to the original`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val scaled = floatArrayOf(2f, 4f, 6f)
        assertEquals(1f, cosineSimilarity(a, scaled), 1e-5f)
    }

    @Test
    fun `a close but not identical embedding scores less than a perfect match`() {
        val enrolled = floatArrayOf(1f, 1f, 1f, 1f)
        val exactMatch = floatArrayOf(1f, 1f, 1f, 1f)
        val slightlyOff = floatArrayOf(1f, 1f, 1f, 0.5f)

        val exact = cosineSimilarity(enrolled, exactMatch)
        val close = cosineSimilarity(enrolled, slightlyOff)

        assertEquals(1f, exact, 1e-5f)
        assertTrue("a near-match should score below a perfect match", close < exact)
        assertTrue("a near-match should still score reasonably high", close > 0.9f)
    }
}

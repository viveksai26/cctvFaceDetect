package com.example.cctvfacetracker.pipeline

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * android.graphics.Rect's 4-arg constructor is stubbed to a no-op under the plain JVM unit-test
 * runner (fields stay 0 regardless of the arguments passed), so build Rects via the no-arg
 * constructor + direct field assignment instead -- those are plain field writes, not stubbed.
 */
private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect =
    Rect().apply { this.left = left; this.top = top; this.right = right; this.bottom = bottom }

class FaceTrackerImplTest {

    @Test
    fun `first detection gets a new id`() {
        val tracker = FaceTrackerImpl()
        val result = tracker.updateTrackers(listOf(rect(0, 0, 100, 100)))
        assertEquals(1, result.size)
        assertEquals(0, result[0].id)
    }

    @Test
    fun `overlapping box in the next frame keeps the same id`() {
        val tracker = FaceTrackerImpl()
        val firstId = tracker.updateTrackers(listOf(rect(0, 0, 100, 100)))[0].id

        // Same face, nudged slightly -- still high overlap.
        val result = tracker.updateTrackers(listOf(rect(5, 5, 105, 105)))

        assertEquals(1, result.size)
        assertEquals(firstId, result[0].id)
    }

    @Test
    fun `non-overlapping box gets a new id instead of stealing the old one`() {
        val tracker = FaceTrackerImpl()
        val firstId = tracker.updateTrackers(listOf(rect(0, 0, 100, 100)))[0].id

        // Completely different part of the frame -- a different person, not the same track.
        val result = tracker.updateTrackers(listOf(rect(500, 500, 600, 600)))

        assertEquals(1, result.size)
        assertNotEquals(firstId, result[0].id)
    }

    @Test
    fun `two simultaneous faces each keep their own id across frames`() {
        val tracker = FaceTrackerImpl()
        val first = tracker.updateTrackers(listOf(rect(0, 0, 100, 100), rect(300, 300, 400, 400)))
        val idA = first.first { it.boundingBox.left == 0 }.id
        val idB = first.first { it.boundingBox.left == 300 }.id

        val second = tracker.updateTrackers(listOf(rect(2, 2, 102, 102), rect(305, 305, 405, 405)))

        assertEquals(idA, second.first { it.boundingBox.left in 0..10 }.id)
        assertEquals(idB, second.first { it.boundingBox.left in 300..320 }.id)
    }

    @Test
    fun `a face that leaves and a new face that enters do not share an id`() {
        val tracker = FaceTrackerImpl()
        val firstId = tracker.updateTrackers(listOf(rect(0, 0, 100, 100)))[0].id

        tracker.updateTrackers(emptyList()) // face left the frame
        val result = tracker.updateTrackers(listOf(rect(0, 0, 100, 100))) // someone new, same spot

        assertNotEquals(firstId, result[0].id)
    }
}

class IntersectionOverUnionTest {

    @Test
    fun `identical boxes have IoU of 1`() {
        val box = IntRect(0, 0, 100, 100)
        assertEquals(1f, intersectionOverUnion(box, box), 1e-6f)
    }

    @Test
    fun `disjoint boxes have IoU of 0`() {
        val a = IntRect(0, 0, 10, 10)
        val b = IntRect(100, 100, 110, 110)
        assertEquals(0f, intersectionOverUnion(a, b), 1e-6f)
    }

    @Test
    fun `half-overlapping boxes have IoU of one third`() {
        val a = IntRect(0, 0, 100, 100)
        val b = IntRect(50, 0, 150, 100)
        // intersection = 50x100 = 5000, union = 10000 + 10000 - 5000 = 15000
        assertEquals(5000f / 15000f, intersectionOverUnion(a, b), 1e-6f)
    }

    @Test
    fun `touching edges do not count as overlap`() {
        val a = IntRect(0, 0, 10, 10)
        val b = IntRect(10, 0, 20, 10)
        assertTrue(intersectionOverUnion(a, b) == 0f)
    }
}

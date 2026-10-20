package com.ultralytics.yolo

import org.junit.Assert.*
import org.junit.Test

class DetectionPostprocessorTest {
    @Test fun strictConfidenceAndCoordinatesArePreserved() {
        val result = DetectionPostprocessor.decode(
            floatArrayOf(100f, 250f, 80f, 30f, 40f, 20f, 20f, 10f, .5f, .6f),
            2, 5, .5f, .7f, 30, 1,
        )
        assertEquals(1, result.size)
        assertArrayEquals(floatArrayOf(240f, 25f, 20f, 10f, .6f, 0f), result[0], 0f)
    }

    @Test fun nmsMatchesUpstreamClassAgnosticBehaviorAndCapsCount() {
        // First two boxes are identical but have different winning classes. Third box is separate.
        val tensor = floatArrayOf(
            100f, 100f, 300f, 100f, 100f, 300f, 40f, 40f, 40f, 40f, 40f, 40f,
            .9f, .1f, .7f, .1f, .8f, .1f,
        )
        val results = DetectionPostprocessor.decode(tensor, 3, 6, .25f, .7f, 30, 2)
        assertEquals(2, results.size)
        assertEquals(.9f, results[0][4], 0f)
        assertEquals(.7f, results[1][4], 0f)
        assertEquals(1, DetectionPostprocessor.decode(tensor, 3, 6, .25f, .7f, 1, 2).size)
        assertTrue(DetectionPostprocessor.decode(tensor, 3, 6, .25f, .7f, 0, 2).isEmpty())
    }

    @Test fun iouUsesStrictComparison() {
        val tensor = floatArrayOf(100f,100f,100f,100f,40f,40f,40f,40f,.9f,.8f)
        assertEquals(2, DetectionPostprocessor.decode(tensor,2,5,.25f,1f,30,1).size)
        assertEquals(1, DetectionPostprocessor.decode(tensor,2,5,.25f,.99f,30,1).size)
    }

    @Test fun malformedTensorFailsBeforeReadingOutsideBuffer() {
        try {
            DetectionPostprocessor.decode(floatArrayOf(1f), 2, 5, .25f, .7f, 30, 1)
            fail("Expected rejected malformed shape")
        } catch (_: IllegalArgumentException) { }
    }
}

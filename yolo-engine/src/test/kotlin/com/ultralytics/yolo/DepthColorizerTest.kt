package com.ultralytics.yolo

import org.junit.Assert.*
import org.junit.Test

class DepthColorizerTest {
    private val colors = IntArray(256) { it + 1000 }

    @Test fun invalidDepthsAreTransparentAndFinitePositiveRangeIsUsed() {
        val pixels = IntArray(7)
        val range = DepthColorizer.colorize(floatArrayOf(Float.NaN, -1f, 0f, Float.POSITIVE_INFINITY, 1f, 10f, 100f), pixels, colors)
        assertArrayEquals(floatArrayOf(1f, 100f), range, 0f)
        assertArrayEquals(intArrayOf(0,0,0,0), pixels.copyOfRange(0,4))
        assertEquals(colors[255], pixels[4])
        assertEquals(colors[0], pixels[6])
        assertTrue(pixels[5] in colors[126]..colors[129])
    }

    @Test fun constantDepthUsesFirstColorWithoutDividingByZero() {
        val pixels = IntArray(3)
        assertArrayEquals(floatArrayOf(2f,2f), DepthColorizer.colorize(floatArrayOf(2f,0f,2f),pixels,colors),0f)
        assertArrayEquals(intArrayOf(colors[0],0,colors[0]),pixels)
    }

    @Test fun completelyInvalidMapReturnsNoRange() {
        assertNull(DepthColorizer.colorize(floatArrayOf(0f,Float.NaN,-5f),IntArray(3),colors))
    }
}

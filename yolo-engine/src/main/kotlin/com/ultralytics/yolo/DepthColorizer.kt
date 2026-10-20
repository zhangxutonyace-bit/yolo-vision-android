// Ultralytics 🚀 AGPL-3.0 License - https://ultralytics.com/license
// Kotlin port of depth-colorizer.h from upstream commit 46a325a17aa85ad2988be41aed393d3bca17c71d.
package com.ultralytics.yolo

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

internal object DepthColorizer {
    fun colorize(depth: FloatArray, pixels: IntArray, colors: IntArray): FloatArray? {
        require(pixels.size >= depth.size && colors.size >= 256)
        var minDepth = Float.MAX_VALUE
        var maxDepth = -Float.MAX_VALUE
        for (value in depth) {
            if (value.isFinite() && value > 0f) {
                minDepth = minOf(minDepth, value)
                maxDepth = maxOf(maxDepth, value)
            }
        }
        if (minDepth == Float.MAX_VALUE) return null
        val linearRange = maxDepth - minDepth
        if (linearRange > 0f) {
            val lutSize = 4096
            val logMax = ln(maxDepth)
            val logRange = max(logMax - ln(minDepth), 1e-6f)
            val table = IntArray(lutSize) { bin ->
                val value = minDepth + linearRange * bin / (lutSize - 1)
                val index = ((logMax - ln(value)) / logRange * 255f).roundToInt().coerceIn(0, 255)
                colors[index]
            }
            val binScale = (lutSize - 1) / linearRange
            for (i in depth.indices) {
                val value = depth[i]
                pixels[i] = if (value.isFinite() && value > 0f)
                    table[((value - minDepth) * binScale).roundToInt().coerceIn(0, lutSize - 1)]
                else 0
            }
        } else {
            for (i in depth.indices) pixels[i] = if (depth[i].isFinite() && depth[i] > 0f) colors[0] else 0
        }
        return floatArrayOf(minDepth, maxDepth)
    }
}

// Ultralytics 🚀 AGPL-3.0 License - https://ultralytics.com/license
// Kotlin port of native-lib.cpp from upstream commit 46a325a17aa85ad2988be41aed393d3bca17c71d.
package com.ultralytics.yolo

import kotlin.math.max
import kotlin.math.min

internal object DetectionPostprocessor {
    private data class Candidate(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val confidence: Float, val index: Int,
    )

    fun decode(
        predictions: FloatArray,
        anchors: Int,
        features: Int,
        confidenceThreshold: Float,
        iouThreshold: Float,
        maxItems: Int,
        numClasses: Int,
    ): Array<FloatArray> {
        require(anchors >= 0 && features >= 4 && numClasses >= 0 && numClasses <= features - 4)
        require(predictions.size.toLong() >= anchors.toLong() * features)
        if (maxItems <= 0 || anchors == 0) return emptyArray()
        val proposals = ArrayList<Candidate>()
        for (i in 0 until anchors) {
            var classIndex = 0
            var score = -Float.MAX_VALUE
            for (c in 0 until numClasses) {
                val value = predictions[(c + 4) * anchors + i]
                if (value > score) { score = value; classIndex = c }
            }
            // Upstream uses strict comparison, and its NMS is class agnostic.
            if (score > confidenceThreshold) {
                val width = predictions[2 * anchors + i]
                val height = predictions[3 * anchors + i]
                proposals += Candidate(
                    predictions[i] - width / 2,
                    predictions[anchors + i] - height / 2,
                    width, height, score, classIndex,
                )
            }
        }
        if (proposals.isNotEmpty()) quickSort(proposals, 0, proposals.lastIndex)
        val picked = ArrayList<Candidate>()
        for (candidate in proposals) {
            var keep = true
            for (other in picked) {
                val intersection = max(0f, min(candidate.x + candidate.width, other.x + other.width) - max(candidate.x, other.x)) *
                    max(0f, min(candidate.y + candidate.height, other.y + other.height) - max(candidate.y, other.y))
                val union = candidate.width * candidate.height + other.width * other.height - intersection
                if (union > 0 && intersection / union > iouThreshold) { keep = false; break }
            }
            if (keep) {
                picked += candidate
                if (picked.size >= maxItems) break
            }
        }
        return picked.map {
            floatArrayOf(it.x, it.y, it.width, it.height, it.confidence, it.index.toFloat())
        }.toTypedArray()
    }

    // Same partition ordering as upstream, including equal-confidence candidates.
    private fun quickSort(items: MutableList<Candidate>, left: Int, right: Int) {
        var i = left
        var j = right
        val pivot = items[(left + right) / 2].confidence
        while (i <= j) {
            while (items[i].confidence > pivot) i++
            while (items[j].confidence < pivot) j--
            if (i <= j) {
                val tmp = items[i]; items[i] = items[j]; items[j] = tmp
                i++; j--
            }
        }
        if (left < j) quickSort(items, left, j)
        if (i < right) quickSort(items, i, right)
    }
}

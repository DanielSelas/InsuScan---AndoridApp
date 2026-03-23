package com.example.insuscan.analysis.detection.util

/** Helper class for debugging detection logic. */
class ReferenceDebugStats {
    var totalContours = 0
    var tooSmall = 0
    var tooLarge = 0
    var badRatio = 0
    var badSolidity = 0
    var lowConfidence = 0
    var candidates = 0

    override fun toString(): String {
        return "T:$totalContours S:$tooSmall L:$tooLarge R:$badRatio Sol:$badSolidity Conf:$lowConfidence Can:$candidates"
    }
}

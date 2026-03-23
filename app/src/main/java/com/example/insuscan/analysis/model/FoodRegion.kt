package com.example.insuscan.analysis.model

data class FoodRegion(
    val foodName: String,
    val areaCm2: Float,
    val heightCm: Float
) {
    companion object {
        fun toJson(regions: List<FoodRegion>): String {
            val sb = StringBuilder("[")
            regions.forEachIndexed { i, r ->
                if (i > 0) sb.append(",")
                sb.append("""{"areaCm2":${"%.1f".format(r.areaCm2)},"heightCm":${"%.1f".format(r.heightCm)}}""")
            }
            sb.append("]")
            return sb.toString()
        }
    }
}

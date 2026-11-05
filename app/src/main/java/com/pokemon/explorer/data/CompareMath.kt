package com.pokemon.explorer.data

enum class ComparisonSide { FIRST, SECOND, TIE }

data class BattlePrediction(val stronger: ComparisonSide, val winner: ComparisonSide)

fun predictBattle(a: PokemonDetail, b: PokemonDetail): BattlePrediction? {
    if (a.stats.isEmpty() || b.stats.isEmpty()) return null
    if (a.types.isEmpty() || b.types.isEmpty()) return null
    if (a.types.any { it == "unknown" } || b.types.any { it == "unknown" }) return null
    if (a.stats.any { it.value == 0 } || b.stats.any { it.value == 0 }) return null

    val stronger = when {
        a.totalStats > b.totalStats -> ComparisonSide.FIRST
        b.totalStats > a.totalStats -> ComparisonSide.SECOND
        else -> ComparisonSide.TIE
    }

    fun calcMulti(atk: List<String>, def: List<String>): Float {
        return atk.maxOfOrNull { aType ->
            val match = TYPE_MATCHUPS[aType] ?: return@maxOfOrNull 1f
            var m = 1f
            for (dType in def) {
                if (match.immuneTo.contains(dType) || TYPE_MATCHUPS[dType]?.immuneTo?.contains(aType) == true) {
                    m = 0f
                } else if (match.strongAgainst.contains(dType)) {
                    m *= 2f
                } else if (match.weakAgainst.contains(dType) || TYPE_MATCHUPS[dType]?.resistantTo?.contains(aType) == true) {
                    m *= 0.5f
                }
            }
            m
        } ?: 1f
    }

    val dmgA = a.totalStats * calcMulti(a.types, b.types)
    val dmgB = b.totalStats * calcMulti(b.types, a.types)

    val winner = when {
        dmgA == 0f && dmgB == 0f -> ComparisonSide.TIE
        dmgA > dmgB -> ComparisonSide.FIRST
        dmgB > dmgA -> ComparisonSide.SECOND
        else -> {
            val speedA = a.stats.find { it.name == "speed" }?.value ?: 0
            val speedB = b.stats.find { it.name == "speed" }?.value ?: 0
            when {
                speedA > speedB -> ComparisonSide.FIRST
                speedB > speedA -> ComparisonSide.SECOND
                else -> ComparisonSide.TIE
            }
        }
    }

    return BattlePrediction(stronger, winner)
}

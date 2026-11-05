package com.pokemon.explorer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattlePredictionTest {
    private fun pokemon(id: Int, types: List<String>, base: Int = 100, speed: Int = base) = PokemonDetail(
        id, "pokemon-$id", types, 1, 1, emptyList(),
        STAT_LABELS.keys.map { Stat(it, if (it == "speed") speed else base) },
        emptyList(), Sprites(null, null), null
    )

    @Test
    fun `type advantage can beat a larger base stat total`() {
        val fire = pokemon(1, listOf("fire"))
        val water = pokemon(2, listOf("water"), 95)
        assertEquals(BattlePrediction(ComparisonSide.FIRST, ComparisonSide.SECOND), predictBattle(fire, water))
        assertEquals(BattlePrediction(ComparisonSide.SECOND, ComparisonSide.FIRST), predictBattle(water, fire))
    }

    @Test
    fun `mutual type immunity produces a draw despite different speeds`() {
        val normal = pokemon(1, listOf("normal"), speed = 150)
        val ghost = pokemon(2, listOf("ghost"))
        assertEquals(ComparisonSide.TIE, predictBattle(normal, ghost)?.winner)
    }

    @Test
    fun `speed decides when both pokemon need the same number of attacks`() {
        val first = pokemon(1, listOf("normal"), speed = 120)
        val second = pokemon(2, listOf("normal"))
        assertEquals(ComparisonSide.FIRST, predictBattle(first, second)?.winner)
        assertEquals(ComparisonSide.TIE, predictBattle(second, second.copy(id = 3))?.winner)
    }

    @Test
    fun `dual type immunity takes precedence over a weakness`() {
        val electric = pokemon(1, listOf("electric"))
        val waterGround = pokemon(2, listOf("water", "ground"))
        assertEquals(ComparisonSide.SECOND, predictBattle(electric, waterGround)?.winner)
    }

    @Test
    fun `incomplete or invalid stats do not produce a winner`() {
        val valid = pokemon(1, listOf("fire"))
        assertNull(predictBattle(valid.copy(stats = emptyList()), valid))
        assertNull(predictBattle(valid.copy(types = listOf("unknown")), valid))
        assertNull(predictBattle(valid.copy(stats = valid.stats.map { it.copy(value = 0) }), valid))
    }
}

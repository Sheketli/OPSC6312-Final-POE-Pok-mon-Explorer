package com.pokemon.explorer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonDetailTest {
    private val pikachu = PokemonDetail(
        id = 25,
        name = "pikachu",
        types = listOf("electric"),
        height = 4,
        weight = 60,
        abilities = emptyList(),
        stats = listOf(
            Stat("hp", 35),
            Stat("attack", 55),
            Stat("defense", 40),
            Stat("special-attack", 50),
            Stat("special-defense", 50),
            Stat("speed", 90),
        ),
        moves = emptyList(),
        sprites = Sprites(null, null),
        flavorText = null,
    )

    @Test
    fun `base stat total counts all six stats including equal values`() {
        assertEquals(320, pikachu.totalStats)
    }

    @Test
    fun `empty stats have a total of zero`() {
        assertEquals(0, pikachu.copy(stats = emptyList()).totalStats)
    }
}

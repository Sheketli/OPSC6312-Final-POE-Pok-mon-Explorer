package com.pokemon.explorer.data

import com.pokemon.explorer.model.PokemonRarity
import kotlin.random.Random

/** A Pokémon as shown on a grid card: enough data to render name, number and types. */
data class PokemonSummary(
    val id: Int,
    val name: String,
    val types: List<String> = emptyList(),
) {
    val rarity: PokemonRarity
        get() {
            val randomVal = Random(id).nextDouble()
            var cumulative = 0.0
            for (r in PokemonRarity.values()) {
                cumulative += r.spawnChance
                if (randomVal <= cumulative) return r
            }
            return PokemonRarity.COMMON
        }
}


data class Ability(
    val name: String,
    val isHidden: Boolean,
)

data class Stat(
    val name: String,
    val value: Int,
)

/** Artwork URLs for a Pokémon, in its normal and shiny colouring. */
data class Sprites(
    val default: String?,
    val shiny: String?,
)

data class PokemonDetail(
    val id: Int,
    val name: String,
    val types: List<String>,
    val height: Int,
    val weight: Int,
    val abilities: List<Ability>,
    val stats: List<Stat>,
    val moves: List<String>,
    val sprites: Sprites,
    val flavorText: String?,
) {
    /** Sum of all six base stats — the "Base Stat Total" shown on Detail and Compare. */
    val totalStats: Int get() = stats.sumOf { it.value }
}

package com.pokemon.explorer.data

import androidx.annotation.ColorInt

/**
 * Canonical Pokémon type colours, used for badges, card gradients and hero panels.
 * Ported verbatim from the web app's `typeData.ts`.
 */
val TYPE_COLORS: Map<String, Int> = linkedMapOf(
    "normal" to 0xFFA8A77A.toInt(),
    "fire" to 0xFFEE8130.toInt(),
    "water" to 0xFF6390F0.toInt(),
    "electric" to 0xFFF7D02C.toInt(),
    "grass" to 0xFF7AC74C.toInt(),
    "ice" to 0xFF96D9D6.toInt(),
    "fighting" to 0xFFC22E28.toInt(),
    "poison" to 0xFFA33EA1.toInt(),
    "ground" to 0xFFE2BF65.toInt(),
    "flying" to 0xFFA98FF3.toInt(),
    "psychic" to 0xFFF95587.toInt(),
    "bug" to 0xFFA6B91A.toInt(),
    "rock" to 0xFFB6A136.toInt(),
    "ghost" to 0xFF735797.toInt(),
    "dragon" to 0xFF6F35FC.toInt(),
    "dark" to 0xFF705746.toInt(),
    "steel" to 0xFFB7B7CE.toInt(),
    "fairy" to 0xFFD685AD.toInt(),
)

/** All 18 types, in Pokédex order. */
val ALL_TYPES: List<String> = TYPE_COLORS.keys.toList()

private val FALLBACK_TYPE_COLOR = 0xFFA8A77A.toInt()

/** Colour for a type name, falling back to Normal for unknown values. */
@ColorInt
fun typeColor(type: String): Int = TYPE_COLORS[type.lowercase()] ?: FALLBACK_TYPE_COLOR

private fun luminance(@ColorInt color: Int): Float {
    val r = (color shr 16 and 0xFF) / 255f
    val g = (color shr 8 and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    return 0.299f * r + 0.587f * g + 0.114f * b
}

/**
 * Picks black or white text for a given type background, so badges stay legible on
 * pale types (Electric, Ice, Steel) as well as dark ones.
 */
@ColorInt
fun contrastOn(@ColorInt color: Int): Int =
    if (luminance(color) > 0.55f) 0xFF1A1200.toInt() else -1

data class TypeMatchup(
    val strongAgainst: List<String>,
    val weakAgainst: List<String>,
    val resistantTo: List<String>,
    val immuneTo: List<String>,
)

/**
 * Offensive / defensive type chart used by the Type Guide.
 */
val TYPE_MATCHUPS: Map<String, TypeMatchup> = mapOf(
    "normal" to TypeMatchup(
        strongAgainst = emptyList(),
        weakAgainst = listOf("fighting"),
        resistantTo = emptyList(),
        immuneTo = listOf("ghost"),
    ),
    "fire" to TypeMatchup(
        strongAgainst = listOf("grass", "ice", "bug", "steel"),
        weakAgainst = listOf("water", "ground", "rock"),
        resistantTo = listOf("fire", "grass", "ice", "bug", "steel", "fairy"),
        immuneTo = emptyList(),
    ),
    "water" to TypeMatchup(
        strongAgainst = listOf("fire", "ground", "rock"),
        weakAgainst = listOf("electric", "grass"),
        resistantTo = listOf("fire", "water", "ice", "steel"),
        immuneTo = emptyList(),
    ),
    "electric" to TypeMatchup(
        strongAgainst = listOf("water", "flying"),
        weakAgainst = listOf("ground"),
        resistantTo = listOf("electric", "flying", "steel"),
        immuneTo = emptyList(),
    ),
    "grass" to TypeMatchup(
        strongAgainst = listOf("water", "ground", "rock"),
        weakAgainst = listOf("fire", "ice", "poison", "flying", "bug"),
        resistantTo = listOf("water", "electric", "grass", "ground"),
        immuneTo = emptyList(),
    ),
    "ice" to TypeMatchup(
        strongAgainst = listOf("grass", "ground", "flying", "dragon"),
        weakAgainst = listOf("fire", "fighting", "rock", "steel"),
        resistantTo = listOf("ice"),
        immuneTo = emptyList(),
    ),
    "fighting" to TypeMatchup(
        strongAgainst = listOf("normal", "ice", "rock", "dark", "steel"),
        weakAgainst = listOf("flying", "psychic", "fairy"),
        resistantTo = listOf("bug", "rock", "dark"),
        immuneTo = emptyList(),
    ),
    "poison" to TypeMatchup(
        strongAgainst = listOf("grass", "fairy"),
        weakAgainst = listOf("ground", "psychic"),
        resistantTo = listOf("grass", "fighting", "poison", "bug", "fairy"),
        immuneTo = emptyList(),
    ),
    "ground" to TypeMatchup(
        strongAgainst = listOf("fire", "electric", "poison", "rock", "steel"),
        weakAgainst = listOf("water", "grass", "ice"),
        resistantTo = listOf("poison", "rock"),
        immuneTo = listOf("electric"),
    ),
    "flying" to TypeMatchup(
        strongAgainst = listOf("grass", "fighting", "bug"),
        weakAgainst = listOf("electric", "ice", "rock"),
        resistantTo = listOf("grass", "fighting", "bug"),
        immuneTo = listOf("ground"),
    ),
    "psychic" to TypeMatchup(
        strongAgainst = listOf("fighting", "poison"),
        weakAgainst = listOf("bug", "ghost", "dark"),
        resistantTo = listOf("fighting", "psychic"),
        immuneTo = emptyList(),
    ),
    "bug" to TypeMatchup(
        strongAgainst = listOf("grass", "psychic", "dark"),
        weakAgainst = listOf("fire", "flying", "rock"),
        resistantTo = listOf("grass", "fighting", "ground"),
        immuneTo = emptyList(),
    ),
    "rock" to TypeMatchup(
        strongAgainst = listOf("fire", "ice", "flying", "bug"),
        weakAgainst = listOf("water", "grass", "fighting", "ground", "steel"),
        resistantTo = listOf("normal", "fire", "poison", "flying"),
        immuneTo = emptyList(),
    ),
    "ghost" to TypeMatchup(
        strongAgainst = listOf("psychic", "ghost"),
        weakAgainst = listOf("ghost", "dark"),
        resistantTo = listOf("psychic", "ghost"),
        immuneTo = listOf("normal", "fighting"),
    ),
    "dragon" to TypeMatchup(
        strongAgainst = listOf("dragon"),
        weakAgainst = listOf("ice", "dragon", "fairy"),
        resistantTo = listOf("fire", "water", "electric", "grass"),
        immuneTo = emptyList(),
    ),
    "dark" to TypeMatchup(
        strongAgainst = listOf("psychic", "ghost"),
        weakAgainst = listOf("fighting", "bug", "fairy"),
        resistantTo = listOf("ghost", "dark"),
        immuneTo = listOf("psychic"),
    ),
    "steel" to TypeMatchup(
        strongAgainst = listOf("ice", "rock", "fairy"),
        weakAgainst = listOf("fire", "fighting", "ground"),
        resistantTo = listOf(
            "normal", "grass", "ice", "flying", "psychic",
            "bug", "rock", "dragon", "steel", "fairy",
        ),
        immuneTo = listOf("poison"),
    ),
    "fairy" to TypeMatchup(
        strongAgainst = listOf("fighting", "dragon", "dark"),
        weakAgainst = listOf("poison", "steel"),
        resistantTo = listOf("fighting", "bug", "dark"),
        immuneTo = listOf("dragon"),
    ),
)

/** Short, HP-counter style labels for the six base stats. */
val STAT_LABELS: Map<String, String> = mapOf(
    "hp" to "HP",
    "attack" to "ATK",
    "defense" to "DEF",
    "special-attack" to "SP.ATK",
    "special-defense" to "SP.DEF",
    "speed" to "SPD",
)

/** Emoji glyphs used as the type icons on the Type Guide cards. */
val TYPE_ICONS: Map<String, String> = mapOf(
    "normal" to "⭐",
    "fire" to "🔥",
    "water" to "💧",
    "electric" to "⚡",
    "grass" to "🌿",
    "ice" to "❄️",
    "fighting" to "👊",
    "poison" to "☠️",
    "ground" to "🏔️",
    "flying" to "🦅",
    "psychic" to "🔮",
    "bug" to "🐛",
    "rock" to "💎",
    "ghost" to "👻",
    "dragon" to "🐉",
    "dark" to "🌑",
    "steel" to "⚙️",
    "fairy" to "✨",
)

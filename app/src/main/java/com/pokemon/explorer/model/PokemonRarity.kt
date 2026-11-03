package com.pokemon.explorer.model

import android.graphics.Color

enum class PokemonRarity(
    val tierName: String,
    val symbol: String,
    val colorHex: String,
    val spawnChance: Double
) {
    COMMON("Common", "●", "#666666", 0.70),
    UNCOMMON("Uncommon", "◆", "#008080", 0.20),
    RARE("Rare", "★", "#D4AF37", 0.08),
    ULTRA_RARE("Ultra Rare", "★★", "#E3350D", 0.017),
    SECRET_RARE("Secret Rare", "\uD83D\uDC51", "#8A2BE2", 0.003); // Crown emoji

    fun getColorInt(): Int = Color.parseColor(colorHex)
}

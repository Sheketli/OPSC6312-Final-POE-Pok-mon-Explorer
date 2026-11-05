package com.pokemon.explorer
import kotlinx.coroutines.runBlocking
import com.pokemon.explorer.data.PokeApi

fun main() = runBlocking {
    try {
        val list = PokeApi.downloadPokedexIndex()
        println("Fetched ${list.size} pokemon")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

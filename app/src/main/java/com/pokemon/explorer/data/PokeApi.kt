package com.pokemon.explorer.data

import android.content.Context
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.data.db.CachedPokemon
import com.pokemon.explorer.data.db.CachedPokemonDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal PokéAPI client.
 *
 * Deliberately dependency-free: it uses [HttpURLConnection] (platform) and [org.json]
 * (bundled with Android) rather than Retrofit/Moshi, so the app has no networking
 * library to configure. Responses are memoised for the life of the process, which
 * mirrors the module-level cache in the web app and keeps repeated navigation
 * (Detail → back → Detail) instant.
 */
object PokeApi {

    private const val BASE = "https://pokeapi.co/api/v2"
    private const val ARTWORK_BASE =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /** How many moves are shown on the Detail screen, matching the web app. */
    private const val MOVE_LIMIT = 10

    private val cache = ConcurrentHashMap<String, JSONObject>()
    private var cachedPokemonDao: CachedPokemonDao? = null

    fun init(context: Context) {
        cachedPokemonDao = AppDatabase.getDatabase(context).cachedPokemonDao()
    }

    /**
     * Official artwork URL for a Pokémon. [shiny] switches to the shiny artwork.
     * Prefer this over the URLs inside the API response so Detail and Compare
     * always render the same asset.
     */
    fun artworkUrl(id: Int, shiny: Boolean = false): String =
        if (shiny) "$ARTWORK_BASE/shiny/$id.png" else "$ARTWORK_BASE/$id.png"

    /** Drops every memoised response. Wired to "Clear Cache" in Settings. */
    fun clearCache() {
        cache.clear()
        Thread {
            cachedPokemonDao?.clearAll()
        }.start()
    }

    /**
     * First [limit] Pokémon from the National Pokédex, starting at [offset].
     *
     * The list endpoint only returns names and URLs, so each entry is fetched
     * individually to obtain its types. That is the same N+1 pattern the web app
     * used, and it stays cheap because [get] caches every response.
     */
    suspend fun list(limit: Int = 20, offset: Int = 0): List<PokemonSummary> {
        val payload = get("$BASE/pokemon?limit=$limit&offset=$offset")
        val results = payload.getJSONArray("results")
        return (0 until results.length()).map { index ->
            val id = idFromUrl(results.getJSONObject(index).getString("url"))
            toSummary(get("$BASE/pokemon/$id"))
        }
    }

    /**
     * Name and types for a single Pokémon — no species lookup, so it stays cheap
     * when the Collection screen needs a card per stored id.
     */
    suspend fun summary(id: Int): PokemonSummary = toSummary(get("$BASE/pokemon/$id"))

    /** Full detail record for one Pokémon, including English flavour text when available. */
    suspend fun detail(id: Int): PokemonDetail {
        val pokemon = get("$BASE/pokemon/$id")
        // Flavour text is a nice-to-have: a missing species record must not fail the screen.
        val species = runCatching { get("$BASE/pokemon-species/$id") }.getOrNull()
        return buildDetail(pokemon, species)
    }

    suspend fun downloadPokedexIndex(): List<PokemonSummary> = withContext(Dispatchers.IO) {
        val url = "https://beta.pokeapi.co/graphql/v1beta"
        val query = """{"query":"query { pokemon_v2_pokemon(where: {id: {_lte: 1025}}) { id name pokemon_v2_pokemontypes { pokemon_v2_type { name } } } }"}"""
        
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.write(query.toByteArray())
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val data = json.getJSONObject("data").getJSONArray("pokemon_v2_pokemon")
            
            (0 until data.length()).map { i ->
                val pkmn = data.getJSONObject(i)
                val typesArr = pkmn.getJSONArray("pokemon_v2_pokemontypes")
                val typesList = (0 until typesArr.length()).map { j ->
                    typesArr.getJSONObject(j).getJSONObject("pokemon_v2_type").getString("name")
                }
                PokemonSummary(
                    id = pkmn.getInt("id"),
                    name = pkmn.getString("name"),
                    types = typesList
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Resolves a name or Pokédex number to a summary, or returns `null` when
     * PokéAPI has no such Pokémon.
     */
    suspend fun find(query: String): PokemonSummary? {
        val slug = query.trim().lowercase().replace(' ', '-')
        if (slug.isEmpty()) return null
        return runCatching { toSummary(get("$BASE/pokemon/$slug")) }.getOrNull()
    }

    // ---------------------------------------------------------------- parsing

    private fun idFromUrl(url: String): Int =
        url.trimEnd('/').substringAfterLast('/').toIntOrNull()
            ?: throw IOException("Unrecognised PokéAPI resource URL: $url")

    private fun typesOf(json: JSONObject): List<String> {
        val types = json.optJSONArray("types") ?: return emptyList()
        return (0 until types.length()).map { index ->
            types.getJSONObject(index).getJSONObject("type").getString("name")
        }
    }

    private fun toSummary(json: JSONObject): PokemonSummary = PokemonSummary(
        id = json.getInt("id"),
        name = json.getString("name"),
        types = typesOf(json),
    )

    private fun buildDetail(pokemon: JSONObject, species: JSONObject?): PokemonDetail {
        val abilities = pokemon.getJSONArray("abilities")
        val stats = pokemon.getJSONArray("stats")
        val moves = pokemon.getJSONArray("moves")
        val artwork = pokemon.optJSONObject("sprites")
            ?.optJSONObject("other")
            ?.optJSONObject("official-artwork")

        val id = pokemon.getInt("id")

        return PokemonDetail(
            id = id,
            name = pokemon.getString("name"),
            types = typesOf(pokemon),
            height = pokemon.getInt("height"),
            weight = pokemon.getInt("weight"),
            abilities = (0 until abilities.length()).map { index ->
                val entry = abilities.getJSONObject(index)
                Ability(
                    name = entry.getJSONObject("ability").getString("name"),
                    isHidden = entry.optBoolean("is_hidden", false),
                )
            },
            stats = (0 until stats.length()).map { index ->
                val entry = stats.getJSONObject(index)
                Stat(
                    name = entry.getJSONObject("stat").getString("name"),
                    value = entry.getInt("base_stat"),
                )
            },
            moves = (0 until minOf(MOVE_LIMIT, moves.length())).map { index ->
                moves.getJSONObject(index).getJSONObject("move").getString("name")
            },
            sprites = Sprites(
                // Fall back to our own URL scheme when the payload omits artwork.
                default = artwork?.optString("front_default")?.takeIf { it.isNotEmpty() }
                    ?: artworkUrl(id),
                shiny = artwork?.optString("front_shiny")?.takeIf { it.isNotEmpty() }
                    ?: artworkUrl(id, shiny = true),
            ),
            flavorText = englishFlavorText(species),
        )
    }

    private fun englishFlavorText(species: JSONObject?): String? {
        val entries: JSONArray = species?.optJSONArray("flavor_text_entries") ?: return null
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            if (entry.optJSONObject("language")?.optString("name") == "en") {
                // The API embeds form feeds and hard line breaks; collapse them to spaces.
                return entry.optString("flavor_text")
                    .replace('\u000C', ' ')
                    .replace('\n', ' ')
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    // ------------------------------------------------------------- transport

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        cache[url]?.let { return@withContext it }

        val dbCache = cachedPokemonDao?.get(url)
        if (dbCache != null) {
            val json = JSONObject(dbCache.jsonPayload)
            cache[url] = json
            return@withContext json
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).also { 
                cache[url] = it 
                cachedPokemonDao?.insert(CachedPokemon(url, body))
            }
        } catch (e: Exception) {
            throw IOException("Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }
}

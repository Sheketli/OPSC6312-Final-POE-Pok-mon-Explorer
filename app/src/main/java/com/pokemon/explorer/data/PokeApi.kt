package com.pokemon.explorer.data

import android.content.Context
import android.util.Log
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
 * Minimal dependency-free PokéAPI HTTP client and JSON parser.
 *
 * Utilizes native [HttpURLConnection] and [JSONObject] to query PokéAPI REST and GraphQL endpoints.
 * Implements a dual-layer caching strategy:
 * - Layer 1: In-memory thread-safe [ConcurrentHashMap]
 * - Layer 2: Persistent Room disk cache via [CachedPokemonDao]
 *
 * References:
 * - PokéAPI v2 Documentation: https://pokeapi.co/docs/v2
 * - PokeAPI GraphQL Beta Endpoint: https://beta.pokeapi.co/graphql/v1beta
 * - Android HttpURLConnection Documentation: https://developer.android.com/reference/java/net/HttpURLConnection
 */
object PokeApi {

    private const val TAG = "PokeApi"
    private const val BASE = "https://pokeapi.co/api/v2"
    private const val ARTWORK_BASE =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /** How many moves are shown on the Detail screen, matching the web app. */
    private const val MOVE_LIMIT = 10

    private val cache = ConcurrentHashMap<String, JSONObject>()
    private var cachedPokemonDao: CachedPokemonDao? = null

    /**
     * Initializes local Room disk caching mechanism.
     */
    fun init(context: Context) {
        Log.d(TAG, "Initializing PokeApi with persistent Room CachedPokemonDao.")
        cachedPokemonDao = AppDatabase.getDatabase(context).cachedPokemonDao()
    }

    /**
     * Official artwork URL for a Pokémon. [shiny] switches to the shiny artwork.
     */
    fun artworkUrl(id: Int, shiny: Boolean = false): String =
        if (shiny) "$ARTWORK_BASE/shiny/$id.png" else "$ARTWORK_BASE/$id.png"

    /** Drops every memoised response in memory and disk cache. */
    fun clearCache() {
        Log.i(TAG, "Clearing in-memory cache and local Room cached HTTP responses.")
        cache.clear()
        Thread {
            cachedPokemonDao?.clearAll()
        }.start()
    }

    /**
     * Fetches first [limit] Pokémon summaries from National Pokédex starting at [offset].
     */
    suspend fun list(limit: Int = 20, offset: Int = 0): List<PokemonSummary> {
        Log.d(TAG, "list: limit=$limit, offset=$offset")
        val payload = get("$BASE/pokemon?limit=$limit&offset=$offset")
        val results = payload.getJSONArray("results")
        return (0 until results.length()).map { index ->
            val id = idFromUrl(results.getJSONObject(index).getString("url"))
            toSummary(get("$BASE/pokemon/$id"))
        }
    }

    /**
     * Fetches name and types for a single Pokémon summary by ID.
     */
    suspend fun summary(id: Int): PokemonSummary {
        Log.d(TAG, "summary: id=$id")
        return toSummary(get("$BASE/pokemon/$id"))
    }

    /** Full detail record for one Pokémon, including English flavour text when available. */
    suspend fun detail(id: Int): PokemonDetail {
        Log.d(TAG, "detail: id=$id")
        val pokemon = get("$BASE/pokemon/$id")
        val species = runCatching { get("$BASE/pokemon-species/$id") }.getOrNull()
        return buildDetail(pokemon, species)
    }

    /**
     * Downloads full National Pokédex index (1025 entries) using GraphQL endpoint for performance.
     */
    suspend fun downloadPokedexIndex(): List<PokemonSummary> = withContext(Dispatchers.IO) {
        Log.i(TAG, "downloadPokedexIndex: Requesting full Pokédex via GraphQL.")
        try {
            val url = "https://beta.pokeapi.co/graphql/v1beta"
            val query = """{"query":"query { pokemon_v2_pokemon(where: {id: {_lte: 1025}}) { id name pokemon_v2_pokemontypes { pokemon_v2_type { name } } } }"}"""
            
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                doOutput = true
                outputStream.write(query.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            Log.d(TAG, "GraphQL response status code = $status")
            if (status in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val data = json.optJSONObject("data")?.optJSONArray("pokemon_v2_pokemon")
                if (data != null && data.length() > 0) {
                    Log.i(TAG, "GraphQL successfully returned ${data.length()} Pokémon entries.")
                    return@withContext (0 until data.length()).map { i ->
                        val pkmn = data.getJSONObject(i)
                        val typesArr = pkmn.optJSONArray("pokemon_v2_pokemontypes") ?: JSONArray()
                        val typesList = (0 until typesArr.length()).map { j ->
                            typesArr.getJSONObject(j).getJSONObject("pokemon_v2_type").getString("name")
                        }
                        PokemonSummary(
                            id = pkmn.getInt("id"),
                            name = pkmn.getString("name"),
                            types = typesList
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GraphQL Pokédex index download failed: ${e.message}")
        }

        Log.i(TAG, "Falling back to REST endpoint for Pokédex index download.")
        return@withContext downloadPokedexIndexFromRest()
    }

    private fun downloadPokedexIndexFromRest(): List<PokemonSummary> {
        return try {
            val url = "$BASE/pokemon?limit=1025"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }
            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val results = json.getJSONArray("results")
                Log.d(TAG, "REST index endpoint returned ${results.length()} Pokémon URLs.")
                (0 until results.length()).map { index ->
                    val obj = results.getJSONObject(index)
                    val name = obj.getString("name")
                    val id = idFromUrl(obj.getString("url"))
                    val types = getFallbackTypes(id)
                    PokemonSummary(id = id, name = name, types = types)
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "REST fallback failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Resolves name or Pokédex ID query to a summary.
     */
    suspend fun find(query: String): PokemonSummary? {
        val slug = query.trim().lowercase().replace(' ', '-')
        Log.d(TAG, "find: querying slug=$slug")
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
        cache[url]?.let {
            Log.d(TAG, "L1 Cache Hit (Memory): $url")
            return@withContext it
        }

        val dbCache = cachedPokemonDao?.get(url)
        if (dbCache != null) {
            Log.d(TAG, "L2 Cache Hit (Room Disk DB): $url")
            val json = JSONObject(dbCache.jsonPayload)
            cache[url] = json
            return@withContext json
        }

        Log.d(TAG, "Cache Miss: Executing network HTTP GET -> $url")
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
                Log.d(TAG, "Successfully fetched and saved response for $url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP GET failed for $url: ${e.message}")
            throw IOException("Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }
}

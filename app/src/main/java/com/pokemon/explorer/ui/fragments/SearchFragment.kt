package com.pokemon.explorer.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.databinding.FragmentSearchBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen
import com.pokemon.explorer.ui.adapters.PokemonAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    private lateinit var pokemonAdapter: PokemonAdapter
    private var searchJob: Job? = null

    private val popularSearches = listOf(
        "pikachu", "charizard", "mewtwo", "eevee", "gengar", "lucario", "garchomp", "dragonite"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { appState.goBack() }

        pokemonAdapter = PokemonAdapter(
            isFavourite = { id -> appState.favourites.value?.contains(id) == true },
            isCaught = { id -> appState.caught.value?.contains(id) == true },
            onToggleFavourite = { id -> appState.toggleFavourite(id) },
            onItemClick = { id -> appState.navigate(Screen.DETAIL, id = id) }
        )
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = pokemonAdapter

        setupChips()

        val filterType = appState.currentFrame.value?.filterType
        if (filterType != null) {
            binding.tilSearch.hint = "Type: ${filterType.replaceFirstChar { it.uppercase() }}"
            performSearch(binding.etSearchQuery.text?.toString().orEmpty(), filterType)
        }

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s?.toString().orEmpty(), filterType)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupChips() {
        val b = _binding ?: return
        b.cgPopularSearches.removeAllViews()
        popularSearches.forEach { name ->
            val chip = Chip(requireContext()).apply {
                text = name
                isClickable = true
                setOnClickListener {
                    _binding?.etSearchQuery?.setText(name)
                }
            }
            b.cgPopularSearches.addView(chip)
        }

        appState.recentSearches.observe(viewLifecycleOwner) { recents ->
            val b1 = _binding ?: return@observe
            if (!recents.isNullOrEmpty()) {
                b1.tvRecentTitle.visibility = View.VISIBLE
                b1.cgRecentSearches.visibility = View.VISIBLE
                b1.cgRecentSearches.removeAllViews()
                recents.forEach { name ->
                    val chip = Chip(requireContext()).apply {
                        text = name
                        isClickable = true
                        setOnClickListener {
                            _binding?.etSearchQuery?.setText(name)
                        }
                    }
                    b1.cgRecentSearches.addView(chip)
                }
            } else {
                b1.tvRecentTitle.visibility = View.GONE
                b1.cgRecentSearches.visibility = View.GONE
            }
        }
    }

    private fun performSearch(query: String, filterType: String? = null) {
        searchJob?.cancel()

        val b = _binding ?: return
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty() && filterType == null) {
            b.pbSearching.visibility = View.GONE
            b.svSuggestions.visibility = View.VISIBLE
            b.rvSearchResults.visibility = View.GONE
            b.tvResultCount.visibility = View.GONE
            b.tvEmptyState.visibility = View.GONE
            return
        }

        b.pbSearching.visibility = View.VISIBLE
        b.svSuggestions.visibility = View.GONE
        b.tvRecentTitle.visibility = View.GONE
        b.cgRecentSearches.visibility = View.GONE
        // Note: Popular searches title is hardcoded in XML without an ID initially, wait, let me check the XML.

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            
            val db = AppDatabase.getDatabase(requireContext())
            val dao = db.pokemonIndexDao()
            
            // Try to parse the query as an ID to search by number, or just by name
            val queryParam = if (trimmed.isEmpty()) "" else "$trimmed%"
            val typeParam = if (filterType.isNullOrEmpty()) "" else "%${filterType.lowercase()}%"
            
            val offlineResults = dao.searchWithFilter(queryParam, typeParam)
            
            val b1 = _binding ?: return@launch
            b1.pbSearching.visibility = View.GONE

            if (offlineResults.isNotEmpty()) {
                if (trimmed.isNotEmpty()) {
                    appState.addSearchToHistory(trimmed)
                }
                b1.rvSearchResults.visibility = View.VISIBLE
                b1.tvResultCount.visibility = View.VISIBLE
                b1.tvResultCount.text = getString(R.string.results_found, offlineResults.size)
                b1.tvEmptyState.visibility = View.GONE
                
                val summaries = offlineResults.map {
                    val rawTypes = it.typesCsv.split(",").filter { t -> t.isNotBlank() }
                    val types = if (rawTypes.isEmpty()) com.pokemon.explorer.data.getFallbackTypes(it.id) else rawTypes
                    com.pokemon.explorer.data.PokemonSummary(it.id, it.name, types)
                }
                pokemonAdapter.submitList(summaries)
            } else {
                // Fallback to API if we don't have it offline
                if (trimmed.isNotEmpty() && filterType == null) {
                    try {
                        val result = PokeApi.find(trimmed)
                        if (result != null) {
                            appState.addSearchToHistory(trimmed)
                            b1.rvSearchResults.visibility = View.VISIBLE
                            b1.tvResultCount.visibility = View.VISIBLE
                            b1.tvResultCount.text = getString(R.string.results_found, 1)
                            b1.tvEmptyState.visibility = View.GONE
                            pokemonAdapter.submitList(listOf(result))
                            return@launch
                        }
                    } catch (e: Exception) {
                        // Ignore offline
                    }
                }

                b1.rvSearchResults.visibility = View.GONE
                b1.tvResultCount.visibility = View.GONE
                b1.svSuggestions.visibility = View.VISIBLE
                if (trimmed.isEmpty() && filterType == null) {
                    b1.tvRecentTitle.visibility = if (appState.recentSearches.value.isNullOrEmpty()) View.GONE else View.VISIBLE
                    b1.cgRecentSearches.visibility = if (appState.recentSearches.value.isNullOrEmpty()) View.GONE else View.VISIBLE
                    b1.tvPopularTitle.visibility = View.VISIBLE
                    b1.cgPopularSearches.visibility = View.VISIBLE
                    b1.tvEmptyState.visibility = View.GONE
                } else {
                    b1.tvRecentTitle.visibility = View.GONE
                    b1.cgRecentSearches.visibility = View.GONE
                    b1.tvPopularTitle.visibility = View.GONE
                    b1.cgPopularSearches.visibility = View.GONE
                    b1.tvEmptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

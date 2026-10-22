package com.pokemon.explorer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.data.PokemonSummary
import com.pokemon.explorer.data.typeColor
import com.pokemon.explorer.databinding.FragmentHomeBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen
import com.pokemon.explorer.ui.adapters.PokemonAdapter
import com.pokemon.explorer.ui.adapters.TypeFilterAdapter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    private val typeFilters = listOf("All", "fire", "water", "grass", "electric", "psychic", "dragon", "ghost", "fairy")
    private var activeFilter = "All"
    private var allItems = listOf<PokemonSummary>()

    private lateinit var pokemonAdapter: PokemonAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appState.currentUser.observe(viewLifecycleOwner) { user ->
            val b = _binding ?: return@observe
            val name = user?.username?.uppercase() ?: "EXPLORER"
            b.tvWelcomeUser.text = "WELCOME BACK, $name"
        }

        binding.btnSettings.setOnClickListener { appState.navigate(Screen.SETTINGS) }
        binding.btnSearchBar.setOnClickListener { appState.navigate(Screen.SEARCH) }
        binding.btnSeeAll.setOnClickListener { appState.navigate(Screen.SEARCH) }

        binding.rvFilterChips.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilterChips.adapter = TypeFilterAdapter(typeFilters, activeFilter) { filter ->
            activeFilter = filter
            applyFilter()
        }

        pokemonAdapter = PokemonAdapter(
            isFavourite = { id -> appState.favourites.value?.contains(id) == true },
            isCaught = { id -> appState.caught.value?.contains(id) == true },
            onToggleFavourite = { id -> appState.toggleFavourite(id) },
            onItemClick = { id -> appState.navigate(Screen.DETAIL, id = id) }
        )
        binding.rvPokemonGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPokemonGrid.adapter = pokemonAdapter

        loadData()
    }

    private fun loadData() {
        val b = _binding ?: return
        b.pbLoading.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val featured = PokeApi.detail(25)
                val b1 = _binding ?: return@launch
                b1.tvFeaturedNumber.text = String.format(Locale.US, "#%03d · Featured", featured.id)
                b1.tvFeaturedName.text = featured.name.replaceFirstChar { it.uppercase() }
                b1.tvFeaturedFlavor.text = featured.flavorText ?: ""
                b1.ivFeaturedArtwork.load(PokeApi.artworkUrl(featured.id))

                b1.llFeaturedBadges.removeAllViews()
                val inflater = LayoutInflater.from(requireContext())
                featured.types.forEach { type ->
                    val color = typeColor(type)
                    val badge = inflater.inflate(R.layout.item_type_badge, b1.llFeaturedBadges, false) as TextView
                    badge.text = type
                    badge.setBackgroundColor(color)
                    badge.setTextColor(com.pokemon.explorer.data.contrastOn(color))
                    b1.llFeaturedBadges.addView(badge)
                }

                b1.btnViewDetails.setOnClickListener {
                    appState.navigate(Screen.DETAIL, id = featured.id)
                }
                b1.btnRandom.setOnClickListener {
                    appState.navigate(Screen.DETAIL, id = Random.nextInt(1, 899))
                }
            } catch (e: Exception) {
                // Ignore featured load error
            }

            try {
                allItems = PokeApi.list(20, 0)
                applyFilter()
            } catch (e: Exception) {
                // Ignore list load error
            } finally {
                _binding?.pbLoading?.visibility = View.GONE
            }
        }
    }

    private fun applyFilter() {
        val b = _binding ?: return
        val filtered = if (activeFilter == "All") {
            allItems
        } else {
            allItems.filter { it.types.contains(activeFilter) }
        }
        b.tvSectionTitle.text = if (activeFilter == "All") "All Pokémon" else activeFilter.replaceFirstChar { it.uppercase() } + " Type"
        b.tvSectionCount.text = filtered.size.toString()
        pokemonAdapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

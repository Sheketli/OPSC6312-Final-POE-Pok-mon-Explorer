package com.pokemon.explorer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.databinding.FragmentCollectionBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen
import com.pokemon.explorer.ui.adapters.PokemonAdapter
import kotlinx.coroutines.launch

class CollectionFragment : Fragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    private lateinit var pokemonAdapter: PokemonAdapter
    private var isShowingFavourites = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pokemonAdapter = PokemonAdapter(
            isFavourite = { id -> appState.favourites.value?.contains(id) == true },
            isCaught = { id -> appState.caught.value?.contains(id) == true },
            onToggleFavourite = { id -> appState.toggleFavourite(id); updateList() },
            onItemClick = { id -> appState.navigate(Screen.DETAIL, id = id) }
        )
        binding.rvCollectionGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvCollectionGrid.adapter = pokemonAdapter

        binding.toggleCollectionTab.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isShowingFavourites = checkedId == R.id.btn_tab_favourites
                updateList()
            }
        }

        appState.favourites.observe(viewLifecycleOwner) { updateProgress() }
        appState.caught.observe(viewLifecycleOwner) { updateProgress() }

        updateProgress()
        updateList()
    }

    private fun updateProgress() {
        val b = _binding ?: return
        val caughtCount = appState.caught.value?.size ?: 0
        b.pbCollectionRing.progress = caughtCount
        val percent = (caughtCount * 100) / 1025
        b.tvProgressPercent.text = "$percent%"
        b.tvCaughtCount.text = "$caughtCount / 1025"
    }

    private fun updateList() {
        val b = _binding ?: return
        val ids = if (isShowingFavourites) {
            appState.favourites.value?.toList() ?: emptyList()
        } else {
            appState.caught.value?.toList() ?: emptyList()
        }

        if (ids.isEmpty()) {
            b.rvCollectionGrid.visibility = View.GONE
            b.tvCollectionEmpty.visibility = View.VISIBLE
            b.tvEmptyMsg.text = if (isShowingFavourites) {
                "No Favourites Yet\nTap the heart icon on any Pokémon to add it here."
            } else {
                "No Pokémon Caught\nMark Pokémon as caught from their detail page."
            }
            pokemonAdapter.submitList(emptyList())
            return
        }

        b.rvCollectionGrid.visibility = View.VISIBLE
        b.tvCollectionEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val summaries = ids.mapNotNull { id ->
                runCatching { PokeApi.summary(id) }.getOrNull()
            }
            if (_binding != null) {
                pokemonAdapter.submitList(summaries)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

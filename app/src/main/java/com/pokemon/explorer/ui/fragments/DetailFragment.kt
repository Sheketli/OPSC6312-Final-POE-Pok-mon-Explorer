package com.pokemon.explorer.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.chip.Chip
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.data.PokemonDetail
import com.pokemon.explorer.data.STAT_LABELS
import com.pokemon.explorer.data.typeColor
import com.pokemon.explorer.databinding.FragmentDetailBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen
import kotlinx.coroutines.launch
import java.util.Locale

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    private var pokemonId: Int = 25
    private var isShiny = false
    private var currentDetail: PokemonDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pokemonId = arguments?.getInt("pokemon_id") ?: 25
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { appState.goBack() }

        binding.btnFavourite.setOnClickListener {
            appState.toggleFavourite(pokemonId)
            updateActionButtons()
        }

        binding.btnCompare.setOnClickListener {
            appState.navigate(Screen.COMPARE, idA = pokemonId)
        }

        binding.btnActionFavourite.setOnClickListener {
            appState.toggleFavourite(pokemonId)
            updateActionButtons()
        }

        binding.btnActionCatch.setOnClickListener {
            appState.toggleCaught(pokemonId)
            updateActionButtons()
        }

        binding.btnActionCompare.setOnClickListener {
            appState.navigate(Screen.COMPARE, idA = pokemonId)
        }

        binding.toggleShiny.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isShiny = checkedId == R.id.btn_shiny
                currentDetail?.let { updateArtwork(it) }
            }
        }

        loadDetail()
    }

    private fun loadDetail() {
        val b = _binding ?: return
        b.pbDetailLoading.visibility = View.VISIBLE
        b.llDetailContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val detail = PokeApi.detail(pokemonId)
                currentDetail = detail
                renderDetail(detail)
                _binding?.llDetailContent?.visibility = View.VISIBLE
            } catch (e: Exception) {
                // Ignore detail load error
            } finally {
                _binding?.pbDetailLoading?.visibility = View.GONE
            }
        }
    }

    private fun renderDetail(detail: PokemonDetail) {
        val b = _binding ?: return
        b.tvDetailNumber.text = String.format(Locale.US, "#%03d", detail.id)
        b.tvDetailName.text = detail.name.replaceFirstChar { it.uppercase() }

        updateArtwork(detail)

        b.llDetailBadges.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        detail.types.forEach { type ->
            val color = typeColor(type)
            val badge = inflater.inflate(R.layout.item_type_badge, b.llDetailBadges, false) as TextView
            badge.text = type
            badge.setBackgroundColor(color)
            badge.setTextColor(com.pokemon.explorer.data.contrastOn(color))
            b.llDetailBadges.addView(badge)
        }

        b.tvHeightVal.text = String.format(Locale.US, "%.1f m", detail.height / 10f)
        b.tvWeightVal.text = String.format(Locale.US, "%.1f kg", detail.weight / 10f)

        b.llAbilities.removeAllViews()
        detail.abilities.forEach { ability ->
            val chip = Chip(requireContext()).apply {
                text = ability.name.replaceFirstChar { it.uppercase() } + if (ability.isHidden) " (Hidden)" else ""
                isClickable = false
            }
            b.llAbilities.addView(chip)
        }

        b.tvTotalStats.text = "Total: ${detail.totalStats}"
        b.llStatsContainer.removeAllViews()
        detail.stats.forEach { stat ->
            val statRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
            }

            val label = TextView(requireContext()).apply {
                text = STAT_LABELS[stat.name] ?: stat.name.uppercase()
                setTextColor(resources.getColor(R.color.text_low, null))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val value = TextView(requireContext()).apply {
                text = stat.value.toString()
                setTextColor(resources.getColor(R.color.text_high, null))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val bar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 255
                progress = stat.value
                progressTintList = ColorStateList.valueOf(typeColor(detail.types.firstOrNull() ?: "normal"))
                layoutParams = LinearLayout.LayoutParams(0, 16, 1f)
            }

            statRow.addView(label)
            statRow.addView(value)
            statRow.addView(bar)
            b.llStatsContainer.addView(statRow)
        }

        b.cgMoves.removeAllViews()
        detail.moves.forEach { move ->
            val chip = Chip(requireContext()).apply {
                text = move.replaceFirstChar { it.uppercase() }
                isClickable = false
            }
            b.cgMoves.addView(chip)
        }

        updateActionButtons()
    }

    private fun updateArtwork(detail: PokemonDetail) {
        val b = _binding ?: return
        val url = if (isShiny) PokeApi.artworkUrl(detail.id, shiny = true) else PokeApi.artworkUrl(detail.id)
        b.ivDetailArtwork.load(url) {
            crossfade(true)
        }
    }

    private fun updateActionButtons() {
        val b = _binding ?: return
        val isFav = appState.favourites.value?.contains(pokemonId) == true
        b.btnFavourite.setImageResource(if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        b.btnActionFavourite.text = if (isFav) "Saved" else "Favourite"

        val isCaught = appState.caught.value?.contains(pokemonId) == true
        b.btnActionCatch.text = if (isCaught) "Caught!" else "Catch"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(pokemonId: Int): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putInt("pokemon_id", pokemonId)
                }
            }
        }
    }
}

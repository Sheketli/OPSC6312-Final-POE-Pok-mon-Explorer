package com.pokemon.explorer.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
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
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.data.PokemonDetail
import com.pokemon.explorer.data.STAT_LABELS
import com.pokemon.explorer.data.typeColor
import com.pokemon.explorer.databinding.FragmentCompareBinding
import com.pokemon.explorer.state.AppState
import kotlinx.coroutines.launch
import java.util.Locale

class CompareFragment : Fragment() {

    private var _binding: FragmentCompareBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    private var idA: Int = 25
    private var idB: Int = 6

    private var detailA: PokemonDetail? = null
    private var detailB: PokemonDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        idA = arguments?.getInt("idA") ?: 25
        idB = arguments?.getInt("idB") ?: 6
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { appState.goBack() }

        binding.btnPickA.setOnClickListener {
            val query = binding.etPickerA.text?.toString().orEmpty().trim()
            query.toIntOrNull()?.let { idA = it; loadData() } ?: run {
                if (query.isNotEmpty()) {
                    lifecycleScope.launch {
                        PokeApi.find(query)?.let { idA = it.id; loadData() }
                    }
                }
            }
        }

        binding.btnPickB.setOnClickListener {
            val query = binding.etPickerB.text?.toString().orEmpty().trim()
            query.toIntOrNull()?.let { idB = it; loadData() } ?: run {
                if (query.isNotEmpty()) {
                    lifecycleScope.launch {
                        PokeApi.find(query)?.let { idB = it.id; loadData() }
                    }
                }
            }
        }

        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                detailA = PokeApi.detail(idA)
                detailB = PokeApi.detail(idB)
                renderComparison()
            } catch (e: Exception) {
                // Ignore compare error
            }
        }
    }

    private fun renderComparison() {
        val b = _binding ?: return
        val pA = detailA ?: return
        val pB = detailB ?: return

        b.ivArtworkA.load(PokeApi.artworkUrl(pA.id))
        b.tvNumberA.text = String.format(Locale.US, "#%03d", pA.id)
        b.tvNameA.text = pA.name.replaceFirstChar { it.uppercase() }

        b.llBadgesA.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        pA.types.forEach { type ->
            val color = typeColor(type)
            val badge = inflater.inflate(R.layout.item_type_badge, b.llBadgesA, false) as TextView
            badge.text = type
            badge.setBackgroundColor(color)
            badge.setTextColor(com.pokemon.explorer.data.contrastOn(color))
            b.llBadgesA.addView(badge)
        }

        b.ivArtworkB.load(PokeApi.artworkUrl(pB.id))
        b.tvNumberB.text = String.format(Locale.US, "#%03d", pB.id)
        b.tvNameB.text = pB.name.replaceFirstChar { it.uppercase() }

        b.llBadgesB.removeAllViews()
        pB.types.forEach { type ->
            val color = typeColor(type)
            val badge = inflater.inflate(R.layout.item_type_badge, b.llBadgesB, false) as TextView
            badge.text = type
            badge.setBackgroundColor(color)
            badge.setTextColor(com.pokemon.explorer.data.contrastOn(color))
            b.llBadgesB.addView(badge)
        }

        b.llCompareRows.removeAllViews()
        pA.stats.forEach { statA ->
            val statB = pB.stats.firstOrNull { it.name == statA.name }?.value ?: 0
            val valA = statA.value

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
            }

            val tvA = TextView(requireContext()).apply {
                text = "$valA"
                textSize = 12f
                setTextColor(if (valA >= statB) resources.getColor(R.color.poke_red, null) else resources.getColor(R.color.text_low, null))
                layoutParams = LinearLayout.LayoutParams(60, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val barA = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 255
                progress = valA
                progressTintList = ColorStateList.valueOf(typeColor(pA.types.firstOrNull() ?: "normal"))
                layoutParams = LinearLayout.LayoutParams(0, 16, 1f)
            }

            val tvLabel = TextView(requireContext()).apply {
                text = STAT_LABELS[statA.name] ?: statA.name.uppercase()
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_low, null))
                layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val barB = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 255
                progress = statB
                progressTintList = ColorStateList.valueOf(typeColor(pB.types.firstOrNull() ?: "normal"))
                layoutParams = LinearLayout.LayoutParams(0, 16, 1f)
            }

            val tvB = TextView(requireContext()).apply {
                text = "$statB"
                textSize = 12f
                gravity = Gravity.END
                setTextColor(if (statB >= valA) resources.getColor(R.color.compare_blue, null) else resources.getColor(R.color.text_low, null))
                layoutParams = LinearLayout.LayoutParams(60, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            row.addView(tvA)
            row.addView(barA)
            row.addView(tvLabel)
            row.addView(barB)
            row.addView(tvB)

            b.llCompareRows.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(idA: Int?, idB: Int?): CompareFragment {
            return CompareFragment().apply {
                arguments = Bundle().apply {
                    putInt("idA", idA ?: 25)
                    putInt("idB", idB ?: 6)
                }
            }
        }
    }
}

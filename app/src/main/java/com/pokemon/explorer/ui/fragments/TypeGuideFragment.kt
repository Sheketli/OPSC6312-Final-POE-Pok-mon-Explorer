package com.pokemon.explorer.ui.fragments

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.pokemon.explorer.R
import com.pokemon.explorer.data.ALL_TYPES
import com.pokemon.explorer.data.TYPE_MATCHUPS
import com.pokemon.explorer.data.typeColor
import com.pokemon.explorer.databinding.FragmentTypeGuideBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen
import com.pokemon.explorer.ui.adapters.TypeCardAdapter

class TypeGuideFragment : Fragment() {

    private var _binding: FragmentTypeGuideBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    private var selectedType: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTypeGuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvTypesGrid.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvTypesGrid.adapter = TypeCardAdapter(ALL_TYPES) { type ->
            selectedType = if (selectedType == type) null else type
            renderMatchup()
        }

        binding.btnClearType.setOnClickListener {
            selectedType = null
            renderMatchup()
        }

        renderMatchup()
    }

    private fun renderMatchup() {
        val b = _binding ?: return
        val type = selectedType
        if (type == null) {
            b.cardSelectedType.visibility = View.GONE
            return
        }

        val matchup = TYPE_MATCHUPS[type] ?: return
        b.cardSelectedType.visibility = View.VISIBLE
        b.tvSelectedTypeTitle.text = "${type.replaceFirstChar { it.uppercase() }} Type"

        b.llMatchupRows.removeAllViews()

        addMatchupRow("⚔️ Super Effective Against", matchup.strongAgainst, "#7AC74C")
        addMatchupRow("🛡️ Not Very Effective Against", matchup.weakAgainst, "#EE1515")
        addMatchupRow("💪 Resistant To", matchup.resistantTo, "#6390F0")
        addMatchupRow("🚫 Immune To", matchup.immuneTo, "#F7D02C")

        b.btnViewTypePokemon.text = "View ${type.replaceFirstChar { it.uppercase() }} Pokémon →"
        b.btnViewTypePokemon.setOnClickListener {
            appState.navigate(Screen.SEARCH, filterType = type)
        }
    }

    private fun addMatchupRow(title: String, typeList: List<String>, colorHex: String) {
        val b = _binding ?: return
        if (typeList.isEmpty()) return

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val tvTitle = TextView(requireContext()).apply {
            text = title
            setTextColor(Color.parseColor(colorHex))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        }

        val badgeContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 0)
        }

        val inflater = LayoutInflater.from(requireContext())
        typeList.forEach { item ->
            val color = typeColor(item)
            val badge = inflater.inflate(R.layout.item_type_badge, badgeContainer, false) as TextView
            badge.text = item
            badge.setBackgroundColor(color)
            badge.setTextColor(com.pokemon.explorer.data.contrastOn(color))
            badge.setOnClickListener {
                selectedType = item
                renderMatchup()
            }
            badgeContainer.addView(badge)
        }

        container.addView(tvTitle)
        container.addView(badgeContainer)
        b.llMatchupRows.addView(container)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

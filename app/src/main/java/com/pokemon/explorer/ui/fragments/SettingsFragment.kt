package com.pokemon.explorer.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.databinding.FragmentSettingsBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.ThemeSetting

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { appState.goBack() }

        appState.currentUser.observe(viewLifecycleOwner) { user ->
            _binding?.tvAccountUsername?.text = user?.username ?: "Explorer"
        }

        appState.theme.observe(viewLifecycleOwner) { theme ->
            val checkedId = when (theme) {
                ThemeSetting.Light -> R.id.btn_theme_light
                ThemeSetting.System -> R.id.btn_theme_system
                ThemeSetting.Dark -> R.id.btn_theme_dark
            }
            if (_binding?.toggleTheme?.checkedButtonId != checkedId) {
                _binding?.toggleTheme?.check(checkedId)
            }
        }

        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val theme = when (checkedId) {
                    R.id.btn_theme_light -> ThemeSetting.Light
                    R.id.btn_theme_system -> ThemeSetting.System
                    R.id.btn_theme_dark -> ThemeSetting.Dark
                    else -> return@addOnButtonCheckedListener
                }
                if (appState.theme.value != theme) {
                    appState.selectTheme(theme)
                    requireActivity().recreate()
                }
            }
        }

        binding.btnClearCache.setOnClickListener {
            PokeApi.clearCache()
            Toast.makeText(requireContext(), "Cache cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearHistory.setOnClickListener {
            appState.clearSearchHistory()
            Toast.makeText(requireContext(), "Search history cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetCollection.setOnClickListener {
            appState.resetCollection()
            Toast.makeText(requireContext(), "Collection reset", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            appState.logout()
        }

        binding.btnPokeapiLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pokeapi.co/"))
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.pokemon.explorer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.pokemon.explorer.databinding.FragmentSplashBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 800
            repeatCount = Animation.INFINITE
        }
        binding.ivPokeball.startAnimation(rotate)

        viewLifecycleOwner.lifecycleScope.launch {
            delay(1500)
            binding.ivPokeball.clearAnimation()

            appState.currentUser.observe(viewLifecycleOwner) { user ->
                if (user == null) {
                    appState.navigate(Screen.LOGIN)
                } else {
                    appState.navigate(Screen.HOME)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.pokemon.explorer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pokemon.explorer.databinding.FragmentLoginBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            appState.login(username, password)
        }

        binding.tvRegisterLink.setOnClickListener {
            appState.navigate(Screen.REGISTER)
        }

        appState.authError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = error
            } else {
                binding.tvError.visibility = View.GONE
            }
        }

        appState.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                appState.navigate(Screen.HOME)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

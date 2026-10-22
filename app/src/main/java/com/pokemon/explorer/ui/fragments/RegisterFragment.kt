package com.pokemon.explorer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pokemon.explorer.databinding.FragmentRegisterBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            val confirm = binding.etConfirmPassword.text?.toString().orEmpty()

            if (password != confirm) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "Passwords do not match"
                return@setOnClickListener
            }

            appState.register(username, password)
        }

        binding.tvLoginLink.setOnClickListener {
            appState.goBack()
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

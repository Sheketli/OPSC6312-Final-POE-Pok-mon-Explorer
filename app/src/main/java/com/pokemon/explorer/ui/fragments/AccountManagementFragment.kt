package com.pokemon.explorer.ui.fragments

import com.pokemon.explorer.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pokemon.explorer.databinding.FragmentAccountManagementBinding
import com.pokemon.explorer.state.AppState

class AccountManagementFragment : Fragment() {

    private var _binding: FragmentAccountManagementBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { appState.goBack() }

        appState.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null && binding.etUsername.text.isNullOrEmpty()) {
                binding.etUsername.setText(user.username)
            }
        }

        appState.authError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.setTextColor(requireContext().getColor(com.pokemon.explorer.R.color.poke_red))
                binding.tvError.text = error
            } else {
                binding.tvError.visibility = View.GONE
            }
        }

        appState.authSuccess.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.setTextColor(requireContext().getColor(com.pokemon.explorer.R.color.type_grass))
                binding.tvError.text = msg
            }
        }

        binding.btnUpdateUsername.setOnClickListener {
            val newUsername = binding.etUsername.text?.toString().orEmpty().trim()
            if (newUsername.isNotEmpty()) {
                binding.tvError.visibility = View.GONE
                appState.updateUsername(newUsername)
            } else {
                Toast.makeText(requireContext(), getString(R.string.ui_username_cannot_be_empty), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnUpdatePassword.setOnClickListener {
            val newPassword = binding.etPassword.text?.toString().orEmpty()
            val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()
            
            if (newPassword.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.ui_password_cannot_be_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPassword != confirmPassword) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.setTextColor(requireContext().getColor(com.pokemon.explorer.R.color.poke_red))
                binding.tvError.text = getString(R.string.ui_passwords_do_not_match)
                return@setOnClickListener
            }

            binding.tvError.visibility = View.GONE
            appState.updatePassword(newPassword)
            binding.etPassword.text?.clear()
            binding.etConfirmPassword.text?.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

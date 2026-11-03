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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

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
            val email = binding.etEmail.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            val confirm = binding.etConfirmPassword.text?.toString().orEmpty()

            if (password != confirm) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "Passwords do not match"
                return@setOnClickListener
            }

            if (email.isBlank()) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "Email cannot be empty"
                return@setOnClickListener
            }

            appState.register(username, email, password)
        }

        binding.tvLoginLink.setOnClickListener {
            appState.goBack()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            doGoogleSignIn()
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

    private fun doGoogleSignIn() {
        val webClientId = resources.getIdentifier("default_web_client_id", "string", requireContext().packageName).let {
            if (it != 0) getString(it) else "YOUR_WEB_CLIENT_ID"
        }

        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        lifecycleScope.launch {
            try {
                val credentialManager = CredentialManager.create(requireContext())
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireContext()
                )
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    appState.googleSignIn(googleIdTokenCredential.idToken)
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                // Ignore cancellations, but log other exceptions
                if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                   appState.setAuthError("Google Sign-In failed or was cancelled")
                }
            } catch (e: Exception) {
                appState.setAuthError("Configuration Error: Make sure your SHA-1 is added to Firebase and the Web Client ID is correct.")
            }
        }
    }
}

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
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.data.sync.BulkDownloadService
import com.pokemon.explorer.databinding.FragmentSettingsBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.Screen
import com.pokemon.explorer.state.ThemeSetting

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val appState: AppState by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        binding.toggleNotifications.check(if (isGranted) R.id.btn_notif_on else R.id.btn_notif_off)
        if (isGranted) {
            Toast.makeText(requireContext(), "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Notifications disabled", Toast.LENGTH_SHORT).show()
        }
    }

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

        // Setup Notifications Switch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            binding.toggleNotifications.check(if (hasPermission) R.id.btn_notif_on else R.id.btn_notif_off)

            binding.toggleNotifications.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    if (checkedId == R.id.btn_notif_on) {
                        if (ContextCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // Request permission
                            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Enable Notifications")
                                    .setMessage("Please allow notifications in order to receive alerts.")
                                    .setPositiveButton("OK") { _, _ ->
                                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    .setNegativeButton("Cancel") { dialog, _ ->
                                        binding.toggleNotifications.check(R.id.btn_notif_off)
                                        dialog.dismiss()
                                    }
                                    .show()
                            } else {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    } else if (checkedId == R.id.btn_notif_off) {
                        // Turn off notifications via system settings
                        Toast.makeText(requireContext(), "To disable notifications, please go to Android Settings.", Toast.LENGTH_LONG).show()
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        }
                        startActivity(intent)
                        // Keep toggle on because we don't know if they actually disabled it
                        binding.toggleNotifications.check(R.id.btn_notif_on)
                    }
                }
            }
        } else {
            // For older Android versions, notifications are enabled by default and cannot be easily toggled off by permission alone.
            binding.toggleNotifications.check(R.id.btn_notif_on)
            binding.toggleNotifications.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked && checkedId == R.id.btn_notif_off) {
                    Toast.makeText(requireContext(), "To disable notifications, please go to Android Settings.", Toast.LENGTH_LONG).show()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        }
                        startActivity(intent)
                    } else {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${requireContext().packageName}")
                        }
                        startActivity(intent)
                    }
                    binding.toggleNotifications.check(R.id.btn_notif_on)
                }
            }
        }

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

        appState.gyroscopeEnabled.observe(viewLifecycleOwner) { enabled ->
            val checkedId = if (enabled) R.id.btn_gyro_on else R.id.btn_gyro_off
            if (_binding?.toggleGyroscope?.checkedButtonId != checkedId) {
                _binding?.toggleGyroscope?.check(checkedId)
            }
        }

        appState.screenDraggingEnabled.observe(viewLifecycleOwner) { enabled ->
            val checkedId = if (enabled) R.id.btn_drag_on else R.id.btn_drag_off
            if (_binding?.toggleScreenDragging?.checkedButtonId != checkedId) {
                _binding?.toggleScreenDragging?.check(checkedId)
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

        binding.toggleGyroscope.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val enabled = checkedId == R.id.btn_gyro_on
                if (appState.gyroscopeEnabled.value != enabled) {
                    appState.updateGyroscopeEnabled(enabled)
                }
            }
        }

        binding.toggleScreenDragging.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val enabled = checkedId == R.id.btn_drag_on
                if (appState.screenDraggingEnabled.value != enabled) {
                    appState.updateScreenDraggingEnabled(enabled)
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

        binding.btnManageAccount.setOnClickListener {
            appState.navigate(Screen.ACCOUNT)
        }

        binding.btnDownloadAll.setOnClickListener {
            Toast.makeText(requireContext(), "Starting full database download in background...", Toast.LENGTH_SHORT).show()
            val serviceIntent = Intent(requireContext(), BulkDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(requireContext(), serviceIntent)
            } else {
                requireContext().startService(serviceIntent)
            }
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

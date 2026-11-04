package com.pokemon.explorer.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
            Toast.makeText(requireContext(), getString(R.string.ui_notifications_enabled), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.ui_notifications_disabled), Toast.LENGTH_SHORT).show()
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

        val languageCodes = resources.getStringArray(R.array.supported_language_codes)
        val languageNames = resources.getStringArray(R.array.supported_language_names)
        val selectedLanguage = AppCompatDelegate.getApplicationLocales()[0]?.language
            ?: resources.configuration.locales[0].language
        val currentIndex = languageCodes.indexOf(selectedLanguage).coerceAtLeast(0)
        binding.btnLanguage.text = languageNames[currentIndex]
        binding.btnLanguage.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ui_language)
                .setSingleChoiceItems(languageNames, currentIndex) { dialog, index ->
                    dialog.dismiss()
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(languageCodes[index])
                    )
                }
                .setNegativeButton(R.string.ui_cancel, null)
                .show()
        }

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
                                    .setTitle(getString(R.string.ui_enable_notifications))
                                    .setMessage(getString(R.string.ui_please_allow_notifications_in_order_to_receive_alerts))
                                    .setPositiveButton(getString(R.string.ui_ok)) { _, _ ->
                                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    .setNegativeButton(getString(R.string.ui_cancel)) { dialog, _ ->
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
                        Toast.makeText(requireContext(), getString(R.string.ui_to_disable_notifications_please_go_to_android_settings), Toast.LENGTH_LONG).show()
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
                    Toast.makeText(requireContext(), getString(R.string.ui_to_disable_notifications_please_go_to_android_settings), Toast.LENGTH_LONG).show()
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
            Toast.makeText(requireContext(), getString(R.string.ui_cache_cleared), Toast.LENGTH_SHORT).show()
        }

        binding.btnClearHistory.setOnClickListener {
            appState.clearSearchHistory()
            Toast.makeText(requireContext(), getString(R.string.ui_search_history_cleared), Toast.LENGTH_SHORT).show()
        }

        binding.btnResetCollection.setOnClickListener {
            appState.resetCollection()
            Toast.makeText(requireContext(), getString(R.string.ui_collection_reset), Toast.LENGTH_SHORT).show()
        }

        binding.btnManageAccount.setOnClickListener {
            appState.navigate(Screen.ACCOUNT)
        }

        binding.btnDownloadAll.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.ui_starting_full_database_download_in_background), Toast.LENGTH_SHORT).show()
            val serviceIntent = Intent(requireContext(), BulkDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(requireContext(), serviceIntent)
            } else {
                requireContext().startService(serviceIntent)
            }
        }

        binding.btnTestNotification.setOnClickListener {
            val rarePokemonId = 150 // Mewtwo
            val rarePokemonName = "Mewtwo"
            val expiryTimeMillis = System.currentTimeMillis() + (5 * 60 * 1000) // 5 minutes from now
            com.pokemon.explorer.utils.NotificationHelper.sendRareSpawnNotification(
                requireContext(),
                rarePokemonId,
                rarePokemonName,
                expiryTimeMillis
            )
            Toast.makeText(requireContext(), getString(R.string.ui_test_notification_sent_check_your_notification_tray), Toast.LENGTH_SHORT).show()
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

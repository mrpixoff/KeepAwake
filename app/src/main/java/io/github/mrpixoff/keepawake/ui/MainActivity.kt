package io.github.mrpixoff.keepawake.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mrpixoff.keepawake.AppContainer
import io.github.mrpixoff.keepawake.R
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.StopReason
import io.github.mrpixoff.keepawake.databinding.ActivityMainBinding
import io.github.mrpixoff.keepawake.service.KeepAwakeService
import io.github.mrpixoff.keepawake.util.Permissions
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private var syncingPresets = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppContainer.isOnboardingCompleted() && isLauncherIntent(intent)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySelectedPreset(AppContainer.selectedPreset(), persist = false)
        listOf(binding.radioIndefinite, binding.radio15, binding.radio30, binding.radio60)
            .forEach { radio ->
                radio.setOnCheckedChangeListener { button, checked ->
                    if (checked && !syncingPresets) {
                        applySelectedPreset(presetFor(button.id), persist = true)
                    }
                }
            }

        binding.toggleButton.setOnClickListener { toggle() }
        binding.buttonWriteSettings.setOnClickListener {
            startActivity(Permissions.writeSettingsIntent(this))
        }
        binding.buttonNotifications.setOnClickListener { requestNotificationsPermission() }
        binding.helpButton.setOnClickListener {
            startActivity(
                Intent(this, OnboardingActivity::class.java)
                    .putExtra(OnboardingActivity.EXTRA_FROM_HELP, true)
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppContainer.controller().state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) updatePermissionUi()
    }

    private fun toggle() {
        val controller = AppContainer.controller()
        if (controller.state.value.isActive) {
            controller.stop(StopReason.USER)
            KeepAwakeService.stop(this)
            return
        }
        if (!AppContainer.canWriteSettings()) {
            startActivity(Permissions.writeSettingsIntent(this))
            return
        }
        val preset = AppContainer.selectedPreset()
        if (controller.start(preset).isSuccess) {
            if (KeepAwakeService.start(this, preset).isFailure) {
                controller.stop(StopReason.ERROR)
            }
        }
    }

    private fun render(state: KeepAwakeState) {
        binding.statusText.text = if (state.isActive) {
            getString(R.string.status_active)
        } else {
            getString(R.string.status_idle)
        }
        binding.heroSubtitle.text = if (state.isActive) {
            getString(R.string.hero_subtitle_active)
        } else {
            getString(R.string.hero_subtitle)
        }
        binding.timerText.text = timerLabel(state)
        binding.timerLabel.visibility = if (state.isActive && state.preset != Preset.INDEFINITE) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // The highlighted preset must match the preset the running mode actually uses.
        if (state.isActive) {
            applySelectedPreset(state.preset, persist = false)
        }

        if (state.isActive) {
            binding.heroOrb.setBackgroundResource(R.drawable.bg_hero_orb_active)
            binding.heroStateLabel.setText(R.string.hero_state_active)
            binding.heroStateLabel.setTextColor(ContextCompat.getColor(this, R.color.accent))
            binding.timerText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            // Active mode = solid industrial red stop button.
            binding.toggleButton.setBackgroundResource(R.drawable.bg_pill_off)
            binding.toggleButton.setTextColor(ContextCompat.getColor(this, R.color.on_stop))
        } else {
            binding.heroOrb.setBackgroundResource(R.drawable.bg_hero_orb_idle)
            binding.heroStateLabel.setText(R.string.hero_state_idle)
            binding.heroStateLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.timerText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            // Idle mode = high-contrast monochrome call to action.
            binding.toggleButton.setBackgroundResource(R.drawable.bg_pill_on)
            binding.toggleButton.setTextColor(ContextCompat.getColor(this, R.color.on_accent))
        }
        binding.toggleButton.text = if (state.isActive) {
            getString(R.string.toggle_off)
        } else {
            getString(R.string.toggle_on)
        }
    }

    private fun timerLabel(state: KeepAwakeState): String {
        if (!state.isActive) return getString(R.string.timer_idle)
        if (state.preset == Preset.INDEFINITE) return getString(R.string.timer_indefinite)
        val remaining = (AppContainer.controller().remainingMs() ?: 0L).coerceAtLeast(0L)
        val totalSeconds = (remaining + 999L) / 1_000L
        return String.format(
            Locale.US,
            "%02d:%02d",
            totalSeconds / 60L,
            totalSeconds % 60L
        )
    }

    private fun updatePermissionUi() {
        val writeGranted = AppContainer.canWriteSettings()
        binding.writeSettingsCheck.visibility = if (writeGranted) View.VISIBLE else View.GONE
        binding.buttonWriteSettings.visibility = if (writeGranted) View.GONE else View.VISIBLE

        val notificationsGranted = Permissions.hasNotificationPermission(this)
        binding.notificationsCheck.visibility = if (notificationsGranted) View.VISIBLE else View.GONE
        binding.buttonNotifications.visibility = if (notificationsGranted) View.GONE else View.VISIBLE

        binding.permissionsCard.visibility = if (writeGranted && notificationsGranted) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            startActivity(Permissions.appDetailsIntent(this))
        }
    }

    private fun applySelectedPreset(preset: Preset, persist: Boolean) {
        syncingPresets = true
        binding.radioIndefinite.isChecked = preset == Preset.INDEFINITE
        binding.radio15.isChecked = preset == Preset.MIN_15
        binding.radio30.isChecked = preset == Preset.MIN_30
        binding.radio60.isChecked = preset == Preset.HOUR_1
        syncingPresets = false
        if (persist) AppContainer.setSelectedPreset(preset)
    }

    private fun presetFor(checkedId: Int): Preset = when (checkedId) {
        R.id.radio15 -> Preset.MIN_15
        R.id.radio30 -> Preset.MIN_30
        R.id.radio60 -> Preset.HOUR_1
        else -> Preset.INDEFINITE
    }

    private fun isLauncherIntent(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_MAIN && intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1001
    }
}

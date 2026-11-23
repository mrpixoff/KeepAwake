package io.github.mrpixoff.keepawake.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import io.github.mrpixoff.keepawake.AppContainer
import io.github.mrpixoff.keepawake.R
import io.github.mrpixoff.keepawake.databinding.ActivityOnboardingBinding
import io.github.mrpixoff.keepawake.util.Permissions

class OnboardingActivity : ComponentActivity() {
    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.onboardingWriteSettingsButton.setOnClickListener {
            startActivity(Permissions.writeSettingsIntent(this))
        }
        binding.onboardingNotificationsButton.setOnClickListener { requestNotificationsPermission() }
        binding.continueButton.setOnClickListener {
            AppContainer.setOnboardingCompleted(true)
            if (intent.getBooleanExtra(EXTRA_FROM_HELP, false)) {
                finish()
            } else {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
    }

    private fun updatePermissionUi() {
        val writeGranted = AppContainer.canWriteSettings()
        binding.onboardingWriteCheck.visibility = if (writeGranted) View.VISIBLE else View.GONE
        binding.onboardingWriteSettingsButton.visibility = if (writeGranted) View.GONE else View.VISIBLE

        val notificationsGranted = Permissions.hasNotificationPermission(this)
        binding.onboardingNotifyCheck.visibility = if (notificationsGranted) View.VISIBLE else View.GONE
        binding.onboardingNotificationsButton.visibility = if (notificationsGranted) View.GONE else View.VISIBLE
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

    companion object {
        const val EXTRA_FROM_HELP = "from_help"
        private const val REQUEST_NOTIFICATIONS = 2001
    }
}

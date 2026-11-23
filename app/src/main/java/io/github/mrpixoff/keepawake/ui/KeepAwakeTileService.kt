package io.github.mrpixoff.keepawake.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.mrpixoff.keepawake.AppContainer
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.StopReason
import io.github.mrpixoff.keepawake.domain.TileStateMapper
import io.github.mrpixoff.keepawake.domain.TileUiState
import io.github.mrpixoff.keepawake.service.KeepAwakeService
import io.github.mrpixoff.keepawake.util.Permissions

open class KeepAwakeTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateTile()
            handler.postDelayed(this, Constants.TILE_REFRESH_INTERVAL_MS)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, Constants.TILE_REFRESH_INTERVAL_MS)
    }

    override fun onStopListening() {
        handler.removeCallbacks(refreshRunnable)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        try {
            val controller = AppContainer.controller()
            if (controller.state.value.isActive) {
                controller.stop(StopReason.USER)
                KeepAwakeService.stop(this)
                return
            }
            if (!AppContainer.canWriteSettings()) {
                openWriteSettings()
                return
            }
            val preset = AppContainer.selectedPreset()
            if (controller.start(preset).isFailure) {
                return
            }
            // Android 12+ may refuse a foreground-service start from the QS
            // shade (e.g. while the screen is locked). Never let that surface
            // as an exception in SystemUI: roll the mode back instead.
            if (startModeService(preset).isFailure) {
                controller.stop(StopReason.ERROR)
            }
        } catch (error: RuntimeException) {
            // Tile clicks run inside SystemUI: swallowing here prevents an app
            // crash from taking down the system's Quick Settings process.
            Log.w(TAG, "Tile click failed", error)
        } finally {
            // The tile must always reflect the real controller state afterwards.
            updateTile()
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val controller = AppContainer.controller()
        val canWrite = AppContainer.canWriteSettings()
        if (!canWrite && controller.state.value.isActive) {
            controller.stop(StopReason.SETTINGS_REVOKED)
        }
        val view = TileStateMapper.map(
            state = controller.state.value,
            nowMs = AppContainer.nowMs(),
            canWrite = canWrite
        )
        tile.label = getString(view.labelRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (view.subtitleArg == null) {
                getString(view.subtitleRes)
            } else {
                getString(view.subtitleRes, view.subtitleArg)
            }
        }
        tile.icon = Icon.createWithResource(this, view.iconRes)
        tile.state = when (view.uiState) {
            TileUiState.ACTIVE -> Tile.STATE_ACTIVE
            TileUiState.INACTIVE -> Tile.STATE_INACTIVE
            TileUiState.ERROR -> Tile.STATE_UNAVAILABLE
        }
        tile.updateTile()
    }

    /**
     * Starts [KeepAwakeService]. Extracted so tests can simulate the
     * `ForegroundServiceStartNotAllowedException` path without a real system.
     */
    open fun startModeService(preset: Preset): Result<Unit> = KeepAwakeService.start(this, preset)

    private fun openWriteSettings() {
        val settingsIntent = Permissions.writeSettingsIntent(this)
        // On API 34+ the Intent overload of startActivityAndCollapse is
        // deprecated and can crash SystemUI, so a PendingIntent is required.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val target = if (canResolve(settingsIntent)) {
                settingsIntent
            } else {
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            collapseToPendingIntent(permissionPendingIntent(target))
        } else {
            collapseToIntent(settingsIntent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun permissionPendingIntent(target: Intent): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_PERMISSIONS,
            target,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Overridable seam: tests control whether the settings activity resolves. */
    open fun canResolve(intent: Intent): Boolean =
        packageManager.resolveActivity(intent, 0) != null

    /**
     * The framework methods are `final`, so these wrappers are the only way to
     * observe/replace the collapse behaviour in Robolectric tests.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    open fun collapseToPendingIntent(pendingIntent: PendingIntent) {
        startActivityAndCollapse(pendingIntent)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    open fun collapseToIntent(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private companion object {
        const val TAG = "KeepAwakeTile"
        const val REQUEST_PERMISSIONS = 4101
    }
}

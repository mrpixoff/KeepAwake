package io.github.mrpixoff.keepawake

import android.annotation.SuppressLint
import android.content.Context
import io.github.mrpixoff.keepawake.core.Clock
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.SystemClockImpl
import io.github.mrpixoff.keepawake.core.TimerScheduler
import io.github.mrpixoff.keepawake.core.CountDownTimerScheduler
import io.github.mrpixoff.keepawake.data.AndroidSystemSettingsGateway
import io.github.mrpixoff.keepawake.data.AppStateStore
import io.github.mrpixoff.keepawake.data.PrefsAppStateStore
import io.github.mrpixoff.keepawake.domain.AndroidNotificationGuard
import io.github.mrpixoff.keepawake.domain.AndroidWriteSettingsGuard
import io.github.mrpixoff.keepawake.domain.WriteSettingsGuard
import io.github.mrpixoff.keepawake.domain.KeepAwakeController
import io.github.mrpixoff.keepawake.domain.ScreenTimeoutManager

/**
 * Process-wide service locator: Activity, Service, TileService and receivers
 * all observe the same KeepAwakeController instance.
 */
object AppContainer {
    @Volatile
    private var appContext: Context? = null

    // KeepAwakeController only keeps applicationContext, so this is safe.
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var testController: KeepAwakeController? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context = requireNotNull(appContext) { "AppContainer is not initialized" }

    fun setControllerForTests(controller: KeepAwakeController?) {
        testController = controller
    }

    @Volatile
    private var testWriteSettingsAllowed: Boolean? = null

    @Volatile
    private var testClock: Clock? = null

    fun setWriteSettingsAllowedForTests(value: Boolean?) {
        testWriteSettingsAllowed = value
    }

    fun setClockForTests(clock: Clock?) {
        testClock = clock
    }

    fun nowMs(): Long = (testClock ?: SystemClockImpl()).nowMs()

    fun canWriteSettings(): Boolean = testWriteSettingsAllowed
        ?: (AndroidWriteSettingsGuard() as WriteSettingsGuard).canWrite(context())

    private fun uiPrefs() = context().getSharedPreferences(Constants.UI_PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun selectedPreset(): Preset {
        val name = uiPrefs().getString(Constants.PREF_SELECTED_PRESET, Preset.INDEFINITE.name)
        return runCatching { Preset.valueOf(name ?: Preset.INDEFINITE.name) }
            .getOrDefault(Preset.INDEFINITE)
    }

    fun setSelectedPreset(preset: Preset) {
        uiPrefs().edit().putString(Constants.PREF_SELECTED_PRESET, preset.name).apply()
    }

    fun isOnboardingCompleted(): Boolean =
        uiPrefs().getBoolean(Constants.PREF_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        uiPrefs().edit().putBoolean(Constants.PREF_ONBOARDING_COMPLETED, completed).apply()
    }

    fun controller(): KeepAwakeController = testController ?: synchronized(this) {
        testController ?: createController().also { testController = it }
    }

    private fun createController(): KeepAwakeController {
        val context = context()
        val store: AppStateStore = PrefsAppStateStore(context)
        val gateway = AndroidSystemSettingsGateway(context)
        val timer: TimerScheduler = CountDownTimerScheduler()
        val clock: Clock = SystemClockImpl()
        return KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = timer,
            clock = clock,
            writeSettingsGuard = AndroidWriteSettingsGuard(),
            notificationGuard = AndroidNotificationGuard()
        )
    }
}

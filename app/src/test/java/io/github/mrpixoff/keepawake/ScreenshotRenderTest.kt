package io.github.mrpixoff.keepawake

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.StopReason
import io.github.mrpixoff.keepawake.domain.KeepAwakeController
import io.github.mrpixoff.keepawake.domain.ScreenTimeoutManager
import io.github.mrpixoff.keepawake.fakes.FakeAppStateStore
import io.github.mrpixoff.keepawake.fakes.FakeClock
import io.github.mrpixoff.keepawake.fakes.FakeNotificationGuard
import io.github.mrpixoff.keepawake.fakes.FakeSystemSettingsGateway
import io.github.mrpixoff.keepawake.fakes.FakeTimerScheduler
import io.github.mrpixoff.keepawake.fakes.FakeWriteSettingsGuard
import io.github.mrpixoff.keepawake.ui.MainActivity
import io.github.mrpixoff.keepawake.ui.OnboardingActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders real pixels of [MainActivity] and [OnboardingActivity] and writes them
 * to the repository-level `screenshots/` folder.
 *
 * Native graphics mode is required: Robolectric's legacy graphics mode records
 * canvas calls without rasterising anything, so PNGs would come out blank.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class ScreenshotRenderTest {

    private val context = RuntimeEnvironment.getApplication()
    private val store = FakeAppStateStore()
    private lateinit var controller: KeepAwakeController

    @Before
    fun setUp() {
        // Robolectric SDK 33 touches BluetoothDevice while inflating windows.
        Shadows.shadowOf(context as Application).setSystemService(
            Context.AUDIO_SERVICE,
            io.mockk.mockk<android.media.AudioManager>(relaxed = true)
        )
        controller = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(FakeSystemSettingsGateway(value = 6_000), store),
            timer = FakeTimerScheduler(),
            clock = FakeClock(nowMs = 1_000L),
            writeSettingsGuard = FakeWriteSettingsGuard(canWrite = true),
            notificationGuard = FakeNotificationGuard(granted = true)
        )
        AppContainer.setControllerForTests(controller)
        AppContainer.setWriteSettingsAllowedForTests(true)
        AppContainer.setOnboardingCompleted(true)
    }

    @After
    fun tearDown() {
        controller.stop(StopReason.USER)
        AppContainer.setControllerForTests(null)
        AppContainer.setWriteSettingsAllowedForTests(null)
        AppContainer.setClockForTests(null)
        AppContainer.setOnboardingCompleted(false)
    }

    @Test
    fun main_light_standby() {
        setTheme(night = false)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        render(activity, "01_main_light_standby.png", expectedText = "Режим выключен")
    }

    @Test
    fun main_light_rec() {
        setTheme(night = false)
        controller.start(Preset.MIN_15)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        render(activity, "02_main_light_rec.png", expectedText = "15:00")
    }

    @Test
    fun main_dark_standby() {
        setTheme(night = true)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        render(activity, "03_main_dark_standby.png", expectedText = "Режим выключен")
    }

    @Test
    fun main_dark_rec() {
        setTheme(night = true)
        controller.start(Preset.MIN_15)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        render(activity, "04_main_dark_rec.png", expectedText = "15:00")
    }

    @Test
    fun onboarding_light() {
        setTheme(night = false)
        AppContainer.setOnboardingCompleted(false)
        AppContainer.setWriteSettingsAllowedForTests(false)
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()
        render(activity, "05_onboarding_light.png", expectedText = "Добро пожаловать")
    }

    @Test
    fun onboarding_dark() {
        setTheme(night = true)
        AppContainer.setOnboardingCompleted(false)
        AppContainer.setWriteSettingsAllowedForTests(false)
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()
        render(activity, "06_onboarding_dark.png", expectedText = "Добро пожаловать")
    }

    private fun setTheme(night: Boolean) {
        RuntimeEnvironment.setQualifiers(
            "+" + if (night) "night" else "notnight"
        )
    }

    /**
     * Measures and draws the whole window, then asserts that the expected state
     * text is present in the view tree and that a non-trivial PNG was written.
     */
    private fun render(activity: android.app.Activity, fileName: String, expectedText: String) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val decor = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        decor.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        decor.layout(0, 0, width, height)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "Expected '$expectedText' somewhere on screen",
            textTree(decor).contains(expectedText)
        )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))

        val outputDir = screenshotsDir()
        val file = File(outputDir, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("PNG ${file.absolutePath} looks empty", file.length() > 5_000)
        println("SCREENSHOT ${file.absolutePath} (${file.length()} bytes, ${width}x${height})")
    }

    private fun textTree(view: View): String = buildString {
        if (view is android.widget.TextView) append(view.text).append('\n')
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) append(textTree(view.getChildAt(i)))
        }
    }

    private fun screenshotsDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null && !File(dir, "gradlew").exists()) {
            dir = dir.parentFile!!
        }
        return File(dir, "screenshots").apply { mkdirs() }
    }
}

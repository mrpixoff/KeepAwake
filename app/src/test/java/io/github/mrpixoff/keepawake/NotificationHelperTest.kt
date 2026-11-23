package io.github.mrpixoff.keepawake

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.service.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationHelperTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun createChannel_registers_low_importance_channel() {
        NotificationHelper.createChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun buildNotification_has_ongoing_flag_and_stop_action() {
        val state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        val notification = NotificationHelper.buildNotification(context, state)

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(1, notification.actions.size)
        assertEquals(context.getString(R.string.notification_stop), notification.actions[0].title.toString())
    }
}

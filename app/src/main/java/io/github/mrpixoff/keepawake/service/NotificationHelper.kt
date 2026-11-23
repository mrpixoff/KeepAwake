package io.github.mrpixoff.keepawake.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.mrpixoff.keepawake.R
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset

object NotificationHelper {
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun buildNotification(context: Context, state: KeepAwakeState): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, KeepAwakeService::class.java).setAction(Constants.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val textRes = if (state.preset == Preset.INDEFINITE) {
            R.string.notification_text_indefinite
        } else {
            R.string.notification_text_timed
        }
        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(textRes))
            .setOngoing(true)
            .addAction(
                R.drawable.ic_tile_inactive,
                context.getString(R.string.notification_stop),
                stopIntent
            )
            .build()
    }
}

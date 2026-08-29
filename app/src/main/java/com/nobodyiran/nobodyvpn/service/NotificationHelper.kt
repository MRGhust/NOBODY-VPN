package com.nobodyiran.nobodyvpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nobodyiran.nobodyvpn.MainActivity
import com.nobodyiran.nobodyvpn.R
import com.nobodyiran.nobodyvpn.util.C
import com.nobodyiran.nobodyvpn.util.Fmt

object NotificationHelper {
    const val CHANNEL_ID = "vpn_status"
    const val FOREGROUND_ID = 1

    fun createChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun stopAction(context: Context): PendingIntent =
        PendingIntent.getService(
            context, 1,
            Intent(context, NobodyVpnService::class.java).setAction(NobodyVpnService.ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun restartAction(context: Context): PendingIntent =
        PendingIntent.getService(
            context, 2,
            Intent(context, NobodyVpnService::class.java).setAction(NobodyVpnService.ACTION_RECONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun build(context: Context, connected: Boolean, remark: String, speedLine: String?): Notification {
        val title = if (connected) {
            "${context.getString(R.string.notif_connected_title)} • $remark"
        } else {
            context.getString(R.string.status_connecting)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nobody)
            .setContentTitle(title)
            .setContentText(speedLine ?: context.getString(R.string.app_name))
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (connected) {
            builder.addAction(0, context.getString(R.string.notif_action_disconnect), stopAction(context))
        } else {
            builder.addAction(0, context.getString(R.string.notif_action_reconnect), restartAction(context))
        }
        return builder.build()
    }
}

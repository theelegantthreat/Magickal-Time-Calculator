package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "magickal_time_shifts"
    private const val CHANNEL_NAME = "Planetary Shifts & Tattwas"
    private const val CHANNEL_DESC = "Notifications for transitions of Planetary Hours and Tattwas"
    private const val NOTIFICATION_ID = 44100

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun postShiftNotification(
        context: Context,
        planetName: String,
        planetSymbol: String,
        tattvaName: String,
        tattvaSymbol: String,
        description: String
    ) {
        // Build pending intent to launch MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "Cosmic Shift occurred! ✦"
        val message = "Now in the Hour of $planetSymbol $planetName with $tattvaSymbol $tattvaName active. $description"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_notification_overlay) // standard native icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false) // Toggleable, persistent/ongoing if the user wants it to stick, but lets keep it standard cancellable for standard usage, and let them dismiss

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            // Check for POST_NOTIFICATIONS permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

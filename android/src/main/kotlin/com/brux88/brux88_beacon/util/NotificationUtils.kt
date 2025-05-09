package com.brux88.brux88_beacon.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Utility per la gestione delle notifiche dell'app
 */
object NotificationUtils {

    private const val CHANNEL_ID = "beacon_monitoring_channel"
    private const val NOTIFICATION_ID = 1001


    /**
     * Crea il canale di notifica (richiesto per Android 8.0+)
     */
    fun createNotificationChannel(context: Context) {
        // Codice invariato
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Monitoraggio Beacon"
            val descriptionText = "Canale per il monitoraggio dei beacon BLE"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea una notifica per il servizio foreground
     * Questa notifica rimane sempre attiva indipendentemente dalle impostazioni
     */
    fun createServiceNotification(context: Context, title: String, content: String): Notification {
        // Invariato - sempre mostrato perché necessario per il foreground service
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Aggiorna la notifica esistente del servizio
     * Questa funzione aggiorna sempre la notifica perché è necessaria per il foreground service
     */
    fun updateNotification(context: Context, title: String, content: String) {
        val notification = createServiceNotification(context, title, content)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

 
}
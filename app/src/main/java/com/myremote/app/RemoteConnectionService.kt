package com.myremote.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.myremote.app.diagnostics.RemoteDiagnostics

/** Keeps the remote's Bluetooth/LAN session available while the phone is used normally. */
class RemoteConnectionService : Service() {
    private val remote get() = (application as RemoteApplication).remoteSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            remote.stopConnections()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                getString(R.string.remote_connection_channel), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val disconnect = PendingIntent.getService(this, 1,
                Intent(this, RemoteConnectionService::class.java).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_remote)
                .setContentTitle(getString(R.string.remote_connection_title))
                .setContentText(getString(R.string.remote_connection_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(Notification.Action.Builder(null, getString(R.string.disconnect_remote), disconnect).build())
                .build()
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIFICATION_ID, notification)
            // Promote before Bluetooth SDP registration. Repeated starts never recreate live sessions.
            remote.startConnections()
            RemoteDiagnostics.record("remote", "connection_service", "active")
        } catch (error: Exception) {
            remote.stopConnections()
            remote.connectionServiceFailed(error)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            RemoteDiagnostics.record("remote", "connection_service", "failed")
        }
        // Never restart pairing/background connections after process death without opening the remote.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        remote.stopConnections()
        RemoteDiagnostics.record("remote", "connection_service", "stopped")
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.myremote.app.DISCONNECT_REMOTE"
        private const val CHANNEL = "remote_connections"
        private const val NOTIFICATION_ID = 1001
        fun start(context: Context) {
            try { context.startForegroundService(Intent(context, RemoteConnectionService::class.java)) }
            catch (error: Exception) {
                (context.applicationContext as RemoteApplication).remoteSession.connectionServiceFailed(error)
            }
        }
        fun disconnect(context: Context) {
            // stopService also works if notifications are denied. onDestroy owns connection cleanup.
            context.stopService(Intent(context, RemoteConnectionService::class.java))
        }
    }
}

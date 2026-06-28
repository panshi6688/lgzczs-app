package com.lgzczs.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "后台服务",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("流光之城运行中")
            .setContentText("正在后台监控订单")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}

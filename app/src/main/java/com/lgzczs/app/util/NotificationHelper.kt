package com.lgzczs.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lgzczs.app.ui.OrderAlertActivity

object NotificationHelper {

    private const val CHANNEL_ID = "order_alert"
    private const val CHANNEL_NAME = "订单提醒"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "有新订单时发送通知提醒"
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun sendOrderNotification(context: Context, platformName: String, orderIds: List<String> = emptyList(), showPopup: Boolean = true) {
        createNotificationChannel(context)

        val intent = Intent(context, OrderAlertActivity::class.java).apply {
            putExtra("platform", platformName)
            putStringArrayListExtra("order_ids", ArrayList(orderIds))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$platformName - 新订单提醒")
            .setContentText("$platformName 有新的可抢订单")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .apply {
                if (showPopup) {
                    setFullScreenIntent(fullScreenIntent, true)
                }
            }
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun sendTestNotification(context: Context) {
        sendOrderNotification(context, "测试", showPopup = true)
        Toast.makeText(context, "已发送测试通知，请查看屏幕上方是否有通知弹出。如没有，请到系统设置中开启【订单提醒】通知类别的悬浮通知权限", Toast.LENGTH_LONG).show()
    }
}

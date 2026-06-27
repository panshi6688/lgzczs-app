package com.lgzczs.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lgzczs.app.model.PlatformStatus
import com.lgzczs.app.model.PollingEvent
import com.lgzczs.app.network.HuiApiClient
import com.lgzczs.app.network.YoukaApiClient
import com.lgzczs.app.util.NotificationHelper
import com.lgzczs.app.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PollingService : Service() {

    companion object {
        const val ACTION_START_HUI = "com.lgzczs.app.action.START_HUI"
        const val ACTION_STOP_HUI = "com.lgzczs.app.action.STOP_HUI"
        const val ACTION_START_YOUKA = "com.lgzczs.app.action.START_YOUKA"
        const val ACTION_STOP_YOUKA = "com.lgzczs.app.action.STOP_YOUKA"
        const val ACTION_STOP_ALL = "com.lgzczs.app.action.STOP_ALL"
        const val ACTION_SHOW_ALERT = "com.lgzczs.app.action.SHOW_ALERT"
        const val ACTION_STATUS_CHANGE = "com.lgzczs.app.action.STATUS_CHANGE"

        const val EXTRA_PLATFORM = "platform"
        const val EXTRA_STATUS = "status"

        private const val POLLING_INTERVAL = 10_000L
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "polling_service"
    }

    private lateinit var tokenManager: TokenManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var huiPollingJob: Job? = null
    private var youkaPollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(applicationContext)
        startForegroundNotification()
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "轮询服务",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("轮询服务运行中")
            .setContentText("正在监控订单状态")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_HUI -> startPolling("hui")
            ACTION_STOP_HUI -> stopPolling("hui")
            ACTION_START_YOUKA -> startPolling("youka")
            ACTION_STOP_YOUKA -> stopPolling("youka")
            ACTION_STOP_ALL -> {
                stopPolling("hui")
                stopPolling("youka")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startPolling(platform: String) {
        when (platform) {
            "hui" -> {
                huiPollingJob?.cancel()
                huiPollingJob = scope.launch {
                    while (isActive) {
                        val token = tokenManager.huiToken
                        if (token == null) break

                        val client = HuiApiClient()
                        when (client.checkOrders(token)) {
                            PollingEvent.HAS_ORDERS -> {
                                if (tokenManager.notificationEnabled) {
                                    NotificationHelper.sendOrderNotification(
                                        this@PollingService,
                                        "汇权益"
                                    )
                                }
                                if (tokenManager.alertDialogEnabled) {
                                    sendBroadcast(
                                        Intent(ACTION_SHOW_ALERT)
                                            .putExtra(EXTRA_PLATFORM, "汇权益")
                                    )
                                }
                            }
                            PollingEvent.TOKEN_INVALID -> {
                                tokenManager.clearHuiToken()
                                sendBroadcast(
                                    Intent(ACTION_STATUS_CHANGE)
                                        .putExtra(EXTRA_PLATFORM, "hui")
                                        .putExtra(EXTRA_STATUS, PlatformStatus.TOKEN_EXPIRED.name)
                                )
                                break
                            }
                            else -> { }
                        }
                        delay(POLLING_INTERVAL)
                    }
                }
            }
            "youka" -> {
                youkaPollingJob?.cancel()
                youkaPollingJob = scope.launch {
                    while (isActive) {
                        val token = tokenManager.youkaToken
                        if (token == null) break

                        val client = YoukaApiClient()
                        when (client.checkOrders(token)) {
                            PollingEvent.HAS_ORDERS -> {
                                if (tokenManager.notificationEnabled) {
                                    NotificationHelper.sendOrderNotification(
                                        this@PollingService,
                                        "优卡云"
                                    )
                                }
                                if (tokenManager.alertDialogEnabled) {
                                    sendBroadcast(
                                        Intent(ACTION_SHOW_ALERT)
                                            .putExtra(EXTRA_PLATFORM, "优卡云")
                                    )
                                }
                            }
                            PollingEvent.TOKEN_INVALID -> {
                                tokenManager.clearYoukaToken()
                                sendBroadcast(
                                    Intent(ACTION_STATUS_CHANGE)
                                        .putExtra(EXTRA_PLATFORM, "youka")
                                        .putExtra(EXTRA_STATUS, PlatformStatus.TOKEN_EXPIRED.name)
                                )
                                break
                            }
                            else -> { }
                        }
                        delay(POLLING_INTERVAL)
                    }
                }
            }
        }
    }

    private fun stopPolling(platform: String) {
        when (platform) {
            "hui" -> {
                huiPollingJob?.cancel()
                huiPollingJob = null
            }
            "youka" -> {
                youkaPollingJob?.cancel()
                youkaPollingJob = null
            }
        }
    }
}

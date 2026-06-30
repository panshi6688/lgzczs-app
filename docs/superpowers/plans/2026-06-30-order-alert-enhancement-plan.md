# 订单提醒增强功能 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:superpowers-subagent-driven-development (recommended) or superpowers:superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 升级流光之城 App 的订单提醒系统，包括弹窗通知（订单提醒页面）、账号密码记忆、悬浮图标红点、声音提醒、通知优化

**Architecture:** 新建 OrderAlertActivity 透明主题 Activity 实现跨 App 弹窗；TokenManager 扩展存储账号密码和声音设置；JavaScript 注入实现 WebView 登录表单自动填充；FloatWindowService 叠加 BadgeView 实现红点

**Tech Stack:** Kotlin, Jetpack Compose, Android WebView, MediaPlayer, NotificationManager

---

## 文件变更总览

| 文件 | 操作 | 职责 |
|------|:----:|------|
| `util/TokenManager.kt` | 修改 | 新增账号密码、声音、红点状态的存取 |
| `util/NotificationHelper.kt` | 修改 | 修复通知ID为随机，新增测试通知方法 |
| `util/SoundManager.kt` | 新建 | 铃声播放/停止/循环工具类 |
| `ui/OrderAlertActivity.kt` | 新建 | 订单提醒页面 Activity（透明主题，Compose 布局） |
| `ui/DataPage.kt` | 修改 | 新增声音开关/铃声选择/测试按钮 |
| `service/FloatWindowService.kt` | 修改 | 添加红点 + "新订单"文字 |
| `service/PollingService.kt` | 修改 | 新订单时启动 OrderAlertActivity + 播放声音 |
| `MainActivity.kt` | 修改 | 启动时创建通知渠道 |
| `AndroidManifest.xml` | 修改 | 注册 OrderAlertActivity |
| `res/values/themes.xml` | 修改 | 新增透明主题 |
| `ui/HuiPage.kt` | 修改 | 账号密码保存 + 自动填充 |
| `ui/YoukaPage.kt` | 修改 | 账号密码保存 + 自动填充 |

---

### Task 1: TokenManager 新增字段

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/util/TokenManager.kt`

- [ ] **Step 1: 在 TokenManager 添加新字段**

```kotlin
var huiUsername: String?
    get() = prefs.getString("hui_username", null)
    set(value) = prefs.edit().putString("hui_username", value).apply()

var huiPassword: String?
    get() = prefs.getString("hui_password", null)
    set(value) = prefs.edit().putString("hui_password", value).apply()

var youkaUsername: String?
    get() = prefs.getString("youka_username", null)
    set(value) = prefs.edit().putString("youka_username", value).apply()

var youkaPassword: String?
    get() = prefs.getString("youka_password", null)
    set(value) = prefs.edit().putString("youka_password", value).apply()

var soundEnabled: Boolean
    get() = prefs.getBoolean("sound_enabled", true)
    set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()

var ringtoneUri: String?
    get() = prefs.getString("ringtone_uri", null)
    set(value) = prefs.edit().putString("ringtone_uri", value).apply()

var hasUnviewedOrders: Boolean
    get() = prefs.getBoolean("has_unviewed_orders", false)
    set(value) = prefs.edit().putBoolean("has_unviewed_orders", value).apply()
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/util/TokenManager.kt
git commit -m "feat: add credential/sound/badge fields to TokenManager"
```

---

### Task 2: NotificationHelper 修复 + 测试通知

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/util/NotificationHelper.kt`

- [ ] **Step 1: 修改 NotificationHelper，修复通知 ID 为随机，添加测试通知方法**

```kotlin
package com.lgzczs.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lgzczs.app.MainActivity

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

    fun sendOrderNotification(context: Context, platformName: String) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
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
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun sendTestNotification(context: Context) {
        sendOrderNotification(context, "测试")
        Toast.makeText(context, "已发送测试通知，请查看屏幕上方是否有通知弹出。如没有，请到系统设置中开启【订单提醒】通知类别的悬浮通知权限", Toast.LENGTH_LONG).show()
    }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/util/NotificationHelper.kt
git commit -m "feat: fix notification ID to random, add test notification"
```

---

### Task 3: SoundManager 工具类

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/util/SoundManager.kt`

- [ ] **Step 1: 创建 SoundManager 工具类**

```kotlin
package com.lgzczs.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

object SoundManager {
    private var mediaPlayer: MediaPlayer? = null

    fun playNotificationSound(context: Context, ringtoneUri: String? = null) {
        try {
            val uri = if (ringtoneUri != null) Uri.parse(ringtoneUri)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri == null) return

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener { reset() }
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    fun playAlertLoop(context: Context, ringtoneUri: String? = null) {
        try {
            val uri = if (ringtoneUri != null) Uri.parse(ringtoneUri)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (uri == null) return

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun reset() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/util/SoundManager.kt
git commit -m "feat: add SoundManager for notification and alert loop sounds"
```

---

### Task 4: 透明主题样式

**Files:**
- Modify: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: 在 themes.xml 中添加透明主题**

Read the existing file first. Then add:

```xml
<style name="Theme.Translucent" parent="Theme.AppCompat.DayNight.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
    <item name="android:windowFullscreen">true</item>
    <item name="android:backgroundDimEnabled">false</item>
</style>
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/res/values/themes.xml
git commit -m "feat: add translucent theme for OrderAlertActivity"
```

---

### Task 5: AndroidManifest 注册 OrderAlertActivity

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 在 application 标签内添加 OrderAlertActivity 声明**

在 `</activity>` 后、`<provider>` 前添加：

```xml
        <activity
            android:name=".ui.OrderAlertActivity"
            android:exported="false"
            android:theme="@style/Theme.Translucent"
            android:excludeFromRecents="true"
            android:taskAffinity=""
            android:showOnLockScreen="true"
            android:showWhenLocked="true"
            android:turnScreenOn="true" />
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register OrderAlertActivity with translucent theme"
```

---

### Task 6: OrderAlertActivity 订单提醒页面

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/ui/OrderAlertActivity.kt`

- [ ] **Step 1: 创建 OrderAlertActivity**

```kotlin
package com.lgzczs.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgzczs.app.MainActivity
import com.lgzczs.app.util.SoundManager
import com.lgzczs.app.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OrderAlertActivity : ComponentActivity() {

    private var autoDismissJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val platformName = intent.getStringExtra("platform") ?: "未知平台"
        val orderIds = intent.getStringArrayListExtra("order_ids") ?: arrayListOf()
        val tokenManager = TokenManager(applicationContext)

        if (tokenManager.soundEnabled) {
            SoundManager.playAlertLoop(this, tokenManager.ringtoneUri)
        }

        setContent {
            MaterialTheme {
                OrderAlertScreen(
                    platformName = platformName,
                    orderIds = orderIds,
                    onView = {
                        SoundManager.stop()
                        tokenManager.hasUnviewedOrders = false
                        Intent(this@OrderAlertActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(this)
                        }
                        finish()
                    },
                    onDismiss = {
                        SoundManager.stop()
                        tokenManager.hasUnviewedOrders = false
                        finish()
                    }
                )
            }
        }

        autoDismissJob = CoroutineScope(Dispatchers.Main).launch {
            delay(30_000L)
            if (isActive) {
                SoundManager.stop()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoDismissJob?.cancel()
        SoundManager.stop()
    }
}

@Composable
private fun OrderAlertScreen(
    platformName: String,
    orderIds: List<String>,
    onView: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔔",
                    fontSize = 40.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "新订单提醒",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "── $platformName ──",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.height(8.dp))

                orderIds.forEach { orderId ->
                    Text(
                        text = orderId,
                        fontSize = 14.sp,
                        color = Color(0xFF333333),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("关闭", fontSize = 15.sp)
                    }
                    Button(
                        onClick = onView,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("查看", fontSize = 15.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/ui/OrderAlertActivity.kt
git commit -m "feat: add OrderAlertActivity for full-screen order alert over other apps"
```

---

### Task 7: MainActivity 启动时创建通知渠道

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/MainActivity.kt`

- [ ] **Step 1: 在 MainActivity.onCreate 中添加通知渠道创建**

在 `setContent` 调用之前，添加：

```kotlin
import com.lgzczs.app.util.NotificationHelper

// 在 onCreate 中 setContent 之前加入:
NotificationHelper.createNotificationChannel(this)
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/MainActivity.kt
git commit -m "feat: create notification channel at app startup"
```

---

### Task 8: FloatWindowService 添加红点 + "新订单"文字

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/service/FloatWindowService.kt`

- [ ] **Step 1: 在 FloatWindowService 中添加红点和"新订单"文字**

主要改动：
1. 导入 TokenManager
2. 在 createFloatView 中根据 `hasUnviewedOrders` 控制红点和文字的显示
3. 在 onFloatViewClick 中清除标记

```kotlin
// 在类顶部添加属性
import com.lgzczs.app.util.TokenManager
import android.widget.TextView
import android.graphics.Color as AndroidColor

// 在 class FloatWindowService 内添加：
private lateinit var tokenManager: TokenManager
private var badgeView: View? = null
private var orderText: TextView? = null

// 在 onCreate 中初始化：
tokenManager = TokenManager(applicationContext)

// 修改 createFloatView 方法，在 iconView 上方叠加红点和文字
// 在 addView(iconView, viewSize, viewSize) 之后添加：

val hasOrders = tokenManager.hasUnviewedOrders

// 红点
val badge = View(this).apply {
    val gd = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setSize((10 * density).toInt(), (10 * density).toInt())
        setColor(AndroidColor.RED)
    }
    background = gd
    visibility = if (hasOrders) View.VISIBLE else View.GONE
}
val badgeParams = FrameLayout.LayoutParams(
    (10 * density).toInt(), (10 * density).toInt()
).apply {
    gravity = Gravity.TOP or Gravity.END
    topMargin = (2 * density).toInt()
    rightMargin = (2 * density).toInt()
}
floatView.addView(badge, badgeParams)
badgeView = badge

// "新订单"文字
val textView = TextView(this).apply {
    text = "新订单"
    textSize = 10f
    setTextColor(AndroidColor.RED)
    gravity = Gravity.CENTER
    visibility = if (hasOrders) View.VISIBLE else View.GONE
}
val textParams = FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.WRAP_CONTENT,
    FrameLayout.LayoutParams.WRAP_CONTENT
).apply {
    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    bottomMargin = (2 * density).toInt()
}
floatView.addView(textView, textParams)
orderText = textView

// 在 onFloatViewClick 中：
private fun onFloatViewClick() {
    tokenManager.hasUnviewedOrders = false
    badgeView?.visibility = View.GONE
    orderText?.visibility = View.GONE
    Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(this)
    }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/service/FloatWindowService.kt
git commit -m "feat: add red badge and '新订单' text to floating icon"
```

---

### Task 9: PollingService 启动 OrderAlertActivity + 播放声音

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/service/PollingService.kt`

- [ ] **Step 1: 修改 PollingService 检测到新订单时启动 OrderAlertActivity 并播放声音**

改动点：
1. 在 `HAS_ORDERS` 分支中：
   - 设置 `tokenManager.hasUnviewedOrders = true`
   - 如果 `alertDialogEnabled == true`，启动 `OrderAlertActivity`
   - 如果 `soundEnabled == true`，播放一次通知声音

```kotlin
import com.lgzczs.app.ui.OrderAlertActivity
import android.content.Intent
import com.lgzczs.app.util.SoundManager
import android.app.PendingIntent

// 在 HAS_ORDERS 分支中，发送通知之后：
tokenManager.hasUnviewedOrders = true

if (tokenManager.alertDialogEnabled) {
    val alertIntent = Intent(this@PollingService, OrderAlertActivity::class.java).apply {
        putExtra("platform", "汇权益")
        putStringArrayListExtra("order_ids", ArrayList(newIds))
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    startActivity(alertIntent)
}

if (tokenManager.soundEnabled) {
    SoundManager.playNotificationSound(this, tokenManager.ringtoneUri)
}
```

同样修改优卡云的 HAS_ORDERS 分支（platform 改为 "优卡云"）。

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/service/PollingService.kt
git commit -m "feat: launch OrderAlertActivity and play sound on new orders"
```

---

### Task 10: DataPage 功能开关更新

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/ui/DataPage.kt`

- [ ] **Step 1: 更新 DataPage 的 UI**

改动点：
1. 删除"清除已保存账号"引用
2. 添加声音提醒开关、铃声选择、试听按钮
3. 添加测试弹窗按钮（在弹窗通知开关下）
4. 添加测试通知按钮（在状态栏通知开关下）
5. 添加 `hasUnviewedOrders` 的引用

新增状态变量：
```kotlin
var soundEnabled by remember { mutableStateOf(tokenManager.soundEnabled) }
var ringtoneUri by remember { mutableStateOf(tokenManager.ringtoneUri) }
```

新增声音开关和铃声选择：

```kotlin
// 在悬浮窗开关之后添加：
Spacer(modifier = Modifier.height(4.dp))
Divider()
Spacer(modifier = Modifier.height(4.dp))
ToggleRow(
    label = "声音提醒",
    description = "新订单提醒时声音同步提醒",
    checked = soundEnabled,
    onCheckedChange = {
        soundEnabled = it
        tokenManager.soundEnabled = it
        if (!it) SoundManager.stop()
    }
)
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
) {
    OutlinedButton(
        onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let { Uri.parse(it) })
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            }
            (context as Activity).startActivityForResult(intent, RINGTONE_REQUEST_CODE)
        }
    ) { Text("选择铃声", fontSize = 12.sp) }
    Spacer(modifier = Modifier.width(8.dp))
    OutlinedButton(
        onClick = {
            SoundManager.playNotificationSound(context, ringtoneUri)
        }
    ) { Text("试听", fontSize = 12.sp) }
}
```

添加 RINGTONE_REQUEST_CODE 常量。注意 DataPage 是 Composable 函数，不能直接用 startActivityForResult。需要改用 rememberLauncherForActivityResult。

同时，在权限检查 DisposableEffect 中添加 onResume 时重新读取 hasUnviewedOrders。

需要添加 ActivityResult 处理铃声选择回调：

```kotlin
private const val RINGTONE_REQUEST_CODE = 1001
```

但由于 DataPage 是 Composable，使用 `rememberLauncherForActivityResult` 方式处理。

在 DataPage 开头添加：
```kotlin
val ringtoneLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            ringtoneUri = uri.toString()
            tokenManager.ringtoneUri = ringtoneUri
        }
    }
}
```

修改铃声选择按钮的 onClick 为 `ringtoneLauncher.launch(...)`。

在 "弹窗通知" 开关下方添加测试弹窗按钮：
```kotlin
// 在弹窗通知 ToggleRow 之后：
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    horizontalArrangement = Arrangement.Start
) {
    OutlinedButton(
        onClick = {
            val intent = Intent(context, OrderAlertActivity::class.java).apply {
                putExtra("platform", "汇权益 (测试)")
                putStringArrayListExtra("order_ids", arrayListOf("TEST202606300001"))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    ) { Text("测试弹窗", fontSize = 12.sp) }
}
```

在 "状态栏通知" 开关下方添加测试通知按钮：
```kotlin
// 在状态栏通知 ToggleRow 之后：
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    horizontalArrangement = Arrangement.Start
) {
    OutlinedButton(
        onClick = {
            NotificationHelper.sendTestNotification(context)
        }
    ) { Text("发送测试通知", fontSize = 12.sp) }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/ui/DataPage.kt
git commit -m "feat: add sound toggle, ringtone picker, test buttons to DataPage"
```

---

### Task 11: HuiPage 账号密码保存 + 自动填充

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/ui/HuiPage.kt`

- [ ] **Step 1: 修改 HuiPage 添加自动填充 JS 和保存逻辑**

改动点：
1. 在 `onPageFinished` 中，检测是否是登录页面 URL，注入填充 JS
2. 在登录成功后保存账号密码

需要检测汇权益登录页面 URL（包含 "login" 的路径）。注入 JS 在 `onPageFinished` 中：

```kotlin
// 在 onPageFinished 内部，token 提取代码之前加入：
val savedUser = tokenManager.huiUsername
val savedPass = tokenManager.huiPassword
if (savedUser != null && savedPass != null && url?.contains("login") == true) {
    view?.evaluateJavascript("""
        (function(){
            var acc = document.getElementById('account');
            var pwd = document.getElementById('password');
            if(acc && pwd) {
                acc.value = '$savedUser';
                pwd.value = '$savedPass';
            }
        })()
    """.trimIndent(), null)
}
```

拦截登录：在 token 被提取（登录成功）时保存账号密码。在 sessionManager.onHuiToken 之前检测。由于 token 提取是通过 JS 注入回调的，可以在 onHuiToken 回调时保存账号密码。但 WebView 中获取 input 值需要再次 evaluateJavascript。

在 token 检测成功后（onPageFinished 的 evaluateJavascript 回调中），可以注入获取当前 input 值的 JS：

```kotlin
// 在 onPageFinished 的 evaluateJavascript 回调中，token 非空时：
if (!token.isNullOrEmpty() && token != "null") {
    sessionManager.onHuiToken(token)
    // 保存账号密码（如果还没有保存）
    if (tokenManager.huiUsername == null) {
        view?.evaluateJavascript("""
            (function(){
                return JSON.stringify({
                    user: document.getElementById('account')?.value || '',
                    pass: document.getElementById('password')?.value || ''
                });
            })()
        """.trimIndent()) { json ->
            try {
                val obj = org.json.JSONObject(json?.trim('"') ?: "{}")
                val user = obj.optString("user", "")
                val pass = obj.optString("pass", "")
                if (user.isNotEmpty()) {
                    tokenManager.huiUsername = user
                    tokenManager.huiPassword = pass
                }
            } catch (_: Exception) { }
        }
    }
}
```

另外需在 HuiPage 参数中添加 `tokenManager`（已存在，但需要确认是否有访问权限）。检查 HuiPage 的签名：`(tokenManager: TokenManager, sessionManager: SessionManager, huiToken: String?)` - 已有 tokenManager 参数。

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/ui/HuiPage.kt
git commit -m "feat: auto-fill and save credentials for 汇权益 login"
```

---

### Task 12: YoukaPage 账号密码保存 + 自动填充

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/ui/YoukaPage.kt`

- [ ] **Step 1: 修改 YoukaPage 添加自动填充 JS 和保存逻辑**

与 HuiPage 类似，但优卡云表单使用 placeholder 选择器：

```kotlin
// 在 onPageFinished 中，检测是否是登录页面：
val savedUser = tokenManager.youkaUsername
val savedPass = tokenManager.youkaPassword
if (savedUser != null && savedPass != null && url?.contains("login") == true) {
    view?.evaluateJavascript("""
        (function(){
            var inputs = document.querySelectorAll('input.ivu-input');
            if(inputs.length >= 2) {
                inputs[0].value = '$savedUser';
                inputs[1].value = '$savedPass';
            }
        })()
    """.trimIndent(), null)
}

// 在 token 检测成功后保存：
if (!token.isNullOrEmpty() && token != "null") {
    sessionManager.onYoukaToken(token)
    if (tokenManager.youkaUsername == null) {
        view?.evaluateJavascript("""
            (function(){
                var inputs = document.querySelectorAll('input.ivu-input');
                if(inputs.length >= 2) {
                    return JSON.stringify({
                        user: inputs[0].value,
                        pass: inputs[1].value
                    });
                }
                return '{}';
            })()
        """.trimIndent()) { json ->
            try {
                val obj = org.json.JSONObject(json?.trim('"') ?: "{}")
                val user = obj.optString("user", "")
                val pass = obj.optString("pass", "")
                if (user.isNotEmpty()) {
                    tokenManager.youkaUsername = user
                    tokenManager.youkaPassword = pass
                }
            } catch (_: Exception) { }
        }
    }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd D:\Users\Administrator\Desktop\ps\闲聊
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "error|FAILED|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no"
git add app/src/main/java/com/lgzczs/app/ui/YoukaPage.kt
git commit -m "feat: auto-fill and save credentials for 优卡云 login"
```

---

## 确认清单

- [ ] 所有文件修改完成
- [ ] 本地 `.\gradlew.bat assembleDebug` 编译通过
- [ ] 已推送到 `ssh-origin main`
- [ ] GitHub Actions 编译成功
- [ ] APK 已下载到 `apk/` 目录且文件名含时间戳

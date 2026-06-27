# 流光之城出卡助手 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个 Android App，包含三个底部导航页（汇权益、优卡云、数据），用户手动登录后自动轮询订单，有订单时通知+弹窗，支持悬浮窗快捷返回。

**Architecture:** 单 Activity 多页面（Jetpack Compose + Navigation），WebView 内手动登录，JS/Cookie 提取 token，服务层轮询，TYPE_APPLICATION_OVERLAY 实现全局弹窗和悬浮窗。

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Android WebView, ForegroundService, OkHttp, NotificationManager, SharedPreferences

---

### Task 1: 创建 Android 项目骨架

**Files:**
- Create: `app/build.gradle.kts`
- Create: `build.gradle.kts` (project-level)
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/java/com/lgzczs/app/MainActivity.kt`
- Create: `app/src/main/java/com/lgzczs/app/ui/theme/Theme.kt`

- [ ] **Step 1: Create project-level build.gradle.kts**
- [ ] **Step 2: Create settings.gradle.kts**
- [ ] **Step 3: Create app/build.gradle.kts** (Compose, Navigation, OkHttp, Gson, WorkManager)
- [ ] **Step 4: Create gradle.properties**
- [ ] **Step 5: Create gradle-wrapper.properties**
- [ ] **Step 6: Create AndroidManifest.xml** (permissions: INTERNET, POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC; services: PollingService, FloatWindowService)
- [ ] **Step 7: Create strings.xml** (app_name = "流光之城出卡助手")
- [ ] **Step 8: Create themes.xml** (Theme.LgzczsApp parent = Material.Light.NoActionBar)
- [ ] **Step 9: Create colors.xml** (ic_launcher_background, ic_launcher_foreground)
- [ ] **Step 10: Create network_security_config.xml** (cleartextTrafficPermitted=true)
- [ ] **Step 11: Create Theme.kt** (Material3 light color scheme, primary = #1976D2)
- [ ] **Step 12: Create MainActivity.kt** with BottomNavigation (3 items: 汇权益/优卡云/数据), NavHost with 3 composable placeholders

---

### Task 2: 创建工具类和模型

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/model/OrderStatus.kt`
- Create: `app/src/main/java/com/lgzczs/app/util/TokenManager.kt`
- Create: `app/src/main/java/com/lgzczs/app/util/NotificationHelper.kt`
- Create: `app/src/main/java/com/lgzczs/app/util/PermissionHelper.kt`

- [ ] **Step 1: Create OrderStatus.kt**
  - `PlatformStatus` enum: NOT_LOGGED_IN, LOGGING_IN, LOGGED_IN, TOKEN_EXPIRED
  - `PlatformState` data class: name, status, hasOrders
  - `PollingEvent` enum: NO_ORDERS, HAS_ORDERS, TOKEN_INVALID, ERROR

- [ ] **Step 2: Create TokenManager.kt**
  - SharedPreferences wrapper
  - Properties: huiToken, youkaToken, alertDialogEnabled, notificationEnabled, floatWindowEnabled
  - Methods: clearHuiToken(), clearYoukaToken()

- [ ] **Step 3: Create NotificationHelper.kt**
  - Create channel "order_alert" with IMPORTANCE_HIGH
  - sendOrderNotification(platformName) - click opens MainActivity

- [ ] **Step 4: Create PermissionHelper.kt**
  - isNotificationEnabled(context)
  - openNotificationSettings(context)
  - isOverlayPermissionGranted(context)
  - openOverlaySettings(context)

---

### Task 3: 实现汇权益 WebView 页面 + API 客户端

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/ui/HuiPage.kt`
- Create: `app/src/main/java/com/lgzczs/app/network/HuiApiClient.kt`
- Modify: `app/src/main/java/com/lgzczs/app/MainActivity.kt`

- [ ] **Step 1: Create HuiApiClient.kt**
  - OkHttp GET `https://public.kky.v3.supplier.kakayun.vip/sup/v2/order/list`
  - Query params: page=1, limit=20, key="", keytype=2, status=pending, starttime={00:00 timestamp}, endtime={23:59 timestamp}, sort_mode=0
  - Headers: Authorization: Bearer {token}, Referer: https://sup.78k.cn/
  - Parse JSON `count` field, return PollingEvent

- [ ] **Step 2: Create HuiPage.kt**
  - AndroidView wrapping WebView
  - JavaScript enabled, DOM storage enabled
  - Add JavascriptInterface "Android" with onTokenReceived(token) callback
  - WebViewClient.onPageFinished: inject JS to read localStorage.access_token
  - Store token via TokenManager, call onStatusChange

- [ ] **Step 3: Update MainActivity.kt** - wire HuiPage composable, pass tokenManager

---

### Task 4: 实现优卡云 WebView 页面 + API 客户端

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/ui/YoukaPage.kt`
- Create: `app/src/main/java/com/lgzczs/app/network/YoukaApiClient.kt`
- Modify: `app/src/main/java/com/lgzczs/app/MainActivity.kt`

- [ ] **Step 1: Create YoukaApiClient.kt**
  - OkHttp client with crypto utilities
  - AES-256-CBC decrypt: key = "7aca3c37e3745f8768b0e559797d521f", IV = MD5(key).substring(0,16)
  - SHA256 sign: params + nonce + salt + timestamp + version=2
  - Salt = MD5(timestamp[-5:] + nonce), nonce = 5 random chars
  - getServerTimestamp(): GET `http://supplier.ukayun.cn/spa/auth/timestamp`
  - checkOrders(): GET `http://supplier.ukayun.cn/spa/order?status=1&page=1&limit=15&time_range={today} 00:00:00~{today} 23:59:59`
  - Headers: Authorization, nonce, timestamp, sign, version=2
  - Decrypt response, parse count, return PollingEvent

- [ ] **Step 2: Create YoukaPage.kt**
  - AndroidView wrapping WebView, same settings as HuiPage
  - No JavascriptInterface needed
  - CookieManager.getInstance().getCookie() to extract admin_token
  - Poll cookie every 2 seconds via update block

- [ ] **Step 3: Update MainActivity.kt** - wire YoukaPage composable

---

### Task 5: 实现数据页面

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/ui/DataPage.kt`
- Modify: `app/src/main/java/com/lgzczs/app/MainActivity.kt`

- [ ] **Step 1: Create DataPage.kt**
  - Platform status section: 汇权益 and 优卡云 with current PlatformStatus text
  - Notification permission: status + "去设置" button
  - Overlay permission: status + "去设置" button
  - Toggle switches: 弹窗通知, 状态栏通知, 悬浮窗 (persisted via TokenManager)

- [ ] **Step 2: Update MainActivity.kt** - wire DataPage composable with state

---

### Task 6: 实现轮询服务（前台服务）

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/service/PollingService.kt`
- Modify: `app/src/main/java/com/lgzczs/app/MainActivity.kt`

- [ ] **Step 1: Create PollingService.kt**
  - Foreground Service with persistent notification
  - CoroutineScope with 10-second delay loop per platform
  - On HAS_ORDERS: send notification + broadcast ACTION_SHOW_ALERT
  - On TOKEN_INVALID: clear token, broadcast ACTION_STATUS_CHANGE
  - Stop polling when token cleared

- [ ] **Step 2: Update MainActivity.kt** - start/stop PollingService based on login status changes

---

### Task 7: 实现全局弹窗（跨 App AlertDialog）

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/ui/GlobalAlertActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create GlobalAlertActivity.kt**
  - TYPE_APPLICATION_OVERLAY dialog using AlertDialog.Builder
  - Shows "订单提醒: {platform} 有新的待处理订单"
  - Buttons: "查看" (launch MainActivity), "关闭" (dismiss)
  - Check tokenManager.alertDialogEnabled before showing

- [ ] **Step 2: Update AndroidManifest.xml** - register GlobalAlertActivity with theme
- [ ] **Step 3: Update PollingService.kt** - broadcast ACTION_SHOW_ALERT handled by this Activity

---

### Task 8: 实现悬浮窗

**Files:**
- Create: `app/src/main/java/com/lgzczs/app/service/FloatWindowService.kt`
- Modify: `app/src/main/java/com/lgzczs/app/MainActivity.kt`

- [ ] **Step 1: Create FloatWindowService.kt**
  - TYPE_APPLICATION_OVERLAY small view, positioned on screen right edge, vertical center
  - DragGestureListener for move + snap to nearest edge on release
  - On click: launch MainActivity
  - Check tokenManager.floatWindowEnabled

- [ ] **Step 2: Update MainActivity.kt**
  - onStart: stop FloatWindowService (hide when app is foreground)
  - onStop: start FloatWindowService if enabled

---

### Task 9: 图标资源处理

**Files:**
- Modify: Convert `优卡云.ico` to PNG, place in res/drawable
- Modify: Use `汇权益.png` as drawable resource

- [ ] **Step 1: Convert 优卡云.ico → PNG** using external tool or Android Studio
- [ ] **Step 2: Copy both icons to** `app/src/main/res/drawable/` as `ic_hui.png` and `ic_youka.png`
- [ ] **Step 3: Update bottom nav icons** in MainActivity.kt to use these custom drawables (or keep Material icons if preferred)

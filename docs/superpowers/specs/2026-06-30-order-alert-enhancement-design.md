# 订单提醒增强功能设计文档

## 概述

对流光之城出卡助手 App 的订单提醒系统进行全面升级，覆盖弹窗通知（订单提醒页面）、账号密码记忆、悬浮图标红点、声音提醒、通知机制优化等功能。

## 涉及文件与变更范围

| 文件 | 变更类型 | 说明 |
|------|:--------:|------|
| `MainActivity.kt` | 修改 | 启动时创建通知渠道，账号密码预填充传递 |
| `DataPage.kt` | 修改 | 新增声音开关、铃声选择器、试听、测试通知按钮 |
| `HuiPage.kt` | 修改 | 账号密码保存 + 自动填充 JS 注入 |
| `YoukaPage.kt` | 修改 | 账号密码保存 + 自动填充 JS 注入 |
| `PollingService.kt` | 修改 | 检测新订单时启动 OrderAlertActivity，发送通知时添加声音 |
| `FloatWindowService.kt` | 修改 | 添加红点 + "新订单"文字 badge |
| `NotificationHelper.kt` | 修改 | 随机通知ID，添加测试通知方法 |
| `TokenManager.kt` | 修改 | 新增账号密码、声音设置、红点状态的存取 |
| `OrderStatus.kt` | 修改 | 新增 OrderAlertEvent 数据类 |
| `OrderAlertActivity.kt` | 新建 | 订单提醒页面 Activity |
| `AndroidManifest.xml` | 修改 | 注册 OrderAlertActivity（透明主题） |
| `res/values/themes.xml` | 修改 | 新增透明主题样式 |

---

## 1. 弹窗通知 = 订单提醒页面

### 1.1 实现方式

- 新建 `OrderAlertActivity`，透明主题，`android:theme="@style/Theme.Translucent"`
- Compose 实现全屏半透明遮罩 + 居中卡片
- `PollingService` 检测到新订单时，若 `alertDialogEnabled == true`，通过 `FLAG_ACTIVITY_NEW_TASK` 启动
- 启动机制：`context.startActivity(Intent(context, OrderAlertActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK))`
  - PollingService 是前台服务（有可见通知），加上已授予 `SYSTEM_ALERT_WINDOW` 权限，能可靠地在后台启动 Activity
  - 不需要走通知 PendingIntent 绕路
- 关闭按钮：`finish()` + 清除 `has_unviewed_orders` 标记
- 查看按钮：`Intent.FLAG_ACTIVITY_REORDER_TO_FRONT` 启动 `MainActivity` + `finish()`
- 30 秒无操作自动关闭（用户可能在忙，避免持续占用屏幕）

### 1.2 布局

```
┌──────────────────────────────────────┐
│        半透明黑色背景（alpha 0.6）     │
│  ┌──────────────────────────────┐    │
│  │         🔔 新订单提醒          │    │
│  │                              │    │
│  │     ── 汇权益 ──              │    │
│  │     订单号: O202606300001     │    │
│  │     订单号: O202606300002     │    │
│  │                              │    │
│  │     ── 优卡云 ──              │    │
│  │     订单号: Y202606300001     │    │
│  │                              │    │
│  │      [查看]   [关闭]          │    │
│  └──────────────────────────────┘    │
└──────────────────────────────────────┘
```

### 1.3 声音播放

- 页面启动时开始播放选中铃声
- 使用 `MediaPlayer`，设置 `setLooping(true)` 循环播放
- 点击查看/关闭 → 停止播放并释放
- 使用 `AudioAttributes` 设为 `USAGE_ALARM` 避开静音模式

---

## 2. 账号密码记忆

### 2.1 存储

`TokenManager` 新增字段：

```kotlin
var huiUsername: String?    // prefs key: "hui_username"
var huiPassword: String?    // prefs key: "hui_password"
var youkaUsername: String?  // prefs key: "youka_username"
var youkaPassword: String?  // prefs key: "youka_password"
```

### 2.2 汇权益自动填充

`HuiPage.kt` 的 `onPageFinished` 中检测登录页面 URL，注入 JS：

```javascript
(function(){
  var acc = document.getElementById('account');
  var pwd = document.getElementById('password');
  if(acc && pwd) {
    acc.value = '{saved_username}';
    pwd.value = '{saved_password}';
  }
})()
```

拦截登录请求（通过检测 URL 变化或 localStorage token 出现），登录成功后保存账号密码。

### 2.3 优卡云自动填充

`YoukaPage.kt` 的 `onPageFinished` 中检测登录页面 URL，注入 JS：

```javascript
(function(){
  var inputs = document.querySelectorAll('input.ivu-input');
  if(inputs.length >= 2) {
    inputs[0].value = '{saved_username}';
    inputs[1].value = '{saved_password}';
  }
})()
```



---

## 3. 悬浮图标红点 + "新订单"文字

### 3.1 实现

`FloatWindowService.kt` 中 `FrameLayout` 内添加：

```
┌───────────────────┐
│   App 图标         │
│  ┌───┐            │
│  │ ● │ ← 红点     │
│  └───┘            │
│  新订单  ← 红色文字│
└───────────────────┘
```

- 红点：`GradientDrawable` 红色圆形，8dp，位于图标右上角
- "新订单"文字：`TextView`，红色，12sp，紧跟在红点右侧显示（"● 新订单"）
- 监听 `has_unviewed_orders` 标记控制可见性

### 3.2 红点生命周期

- 设置：`PollingService` 检测到新订单时 → `tokenManager.hasUnviewedOrders = true`
- 清除：点击悬浮图标（`onFloatViewClick`）或 App 回到前台（`ON_START`）→ `tokenManager.hasUnviewedOrders = false`
- `FloatWindowService` 启动时读取该标记

---

## 4. 声音提醒

### 4.1 存储

```kotlin
var soundEnabled: Boolean     // prefs: "sound_enabled", default true
var ringtoneUri: String?      // prefs: "ringtone_uri", default null(系统默认)
```

### 4.2 DataPage 开关

功能开关区新增：

```
声音提醒  [开关]
  └─ 备注: 新订单提醒时声音同步提醒
  └─ 选择铃声 → 启动 RingtoneManager.ACTION_RINGTONE_PICKER
  └─ [试听] → 播放选中铃声 3 秒后停止
```

### 4.3 声音触发场景

| 场景 | 行为 |
|:----|:-----|
| 订单提醒页面弹出 | 循环播放，直到用户操作 |
| 状态栏通知发送 | 播放一次（约 3 秒）|
| 弹窗通知关闭 / 声音提醒开关关 | 停止所有播放 |

使用 `RingtoneManager.getRingtone()` 或 `MediaPlayer` 播放。

---

## 5. 通知系统优化

### 5.1 通知渠道提前创建

`MainActivity.onCreate()` 中调用 `NotificationHelper.createNotificationChannel()`，确保 App 启动时渠道就存在。

### 5.2 通知ID修复

`NotificationHelper.sendOrderNotification` 使用 `System.currentTimeMillis().toInt()` 或递增计数器作为 `notificationId`，避免多条通知互相覆盖。

### 5.3 测试按钮

DataPage 的功能开关卡片中添加两个测试按钮：

**弹窗通知测试按钮**
- 位于"弹窗通知"开关下方
- 文本：`[测试弹窗]`
- 点击直接启动 `OrderAlertActivity`，传入测试数据（模拟一个来自"汇权益"的订单号 "TEST202606300001"）
- 用于验证弹窗通知在所有 App 上方正常显示

**状态栏通知测试按钮**
- 位于"状态栏通知"开关下方
- 文本：`[发送测试通知]`
- 点击调用 `NotificationHelper.sendOrderNotification(context, "测试")`
- Toast 提示："已发送测试通知，请查看屏幕上方是否有通知弹出。如没有，请到系统设置中开启【订单提醒】通知类别的悬浮通知权限"

---

## 6. 数据页面功能开关最终布局

```
┌─ 功能开关 ──────────────────────────┐
│ 弹窗通知  [开关]                     │
│   有订单时在所有 App 上方弹窗提示     │
│  [测试弹窗]                          │
│                                      │
│ 状态栏通知  [开关]                    │
│   有订单时发送系统通知栏通知          │
│  [发送测试通知]                      │
│                                      │
│ 声音提醒  [开关]                     │
│   新订单提醒时声音同步提醒            │
│  [选择铃声]  [试听]                  │
│                                      │
│ 悬浮窗  [开关]                       │
│   后台运行时显示可拖动的悬浮图标      │
└──────────────────────────────────────┘
```

---

## 7. 权限说明

| 权限 | 用途 | 说明 |
|:-----|:-----|:-----|
| `SYSTEM_ALERT_WINDOW` | `OrderAlertActivity` 叠加层 | 已有 |
| `POST_NOTIFICATIONS` | 通知栏通知 | 已有 |
| 通知类别悬浮通知 | 系统通知头顶弹出 | 只能引导用户手动开启 |

---

## 8. 数据流（新订单到来时）

```
PollingService 轮询
  → 检测到新订单 ID
  → 保存已通知 ID（去重）
  → tokenManager.hasUnviewedOrders = true

  → [notificationEnabled] → 系统通知（随机ID）
      → [soundEnabled] → 播放一次声音

  → [alertDialogEnabled] → 启动 OrderAlertActivity（FLAG_NEW_TASK）
      → 页面显示平台+订单号
      → [soundEnabled] → 循环播放声音
      → 用户点[查看] → 打开 MainActivity + 停止声音
      → 用户点[关闭] → 关闭页面 + 停止声音 + 清除红点标记
      → 30秒无操作 → 自动关闭 + 停止声音
```

---

## 9. 界面截图参考

（文字描述，无需截图）

订单提醒页面采用 Material3 卡片风格，圆角 16dp，背景白色，标题用粗体，订单内容等宽字体，按钮用主题色填充。

悬浮图标红点位于图标右上角，8dp 红色圆形；下方"新订单"红色文字，12sp。

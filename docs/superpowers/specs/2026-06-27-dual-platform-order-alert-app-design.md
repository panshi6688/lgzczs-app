# 双平台订单监控 App 设计文档

## App 信息

- App 名称: 流光之城出卡助手
- 包名: com.lgzczs.app

## 概述

一个 Android App，提供三个底部导航页面（汇权益、优卡云、数据），用户手动登录两个供货平台后，App 自动轮询订单列表，有订单时通过通知栏和全局弹窗提醒用户。支持悬浮窗快捷返回。

## 技术栈

- 语言: Kotlin
- UI: Jetpack Compose
- 导航: Navigation Compose（底部导航栏）
- WebView: 原生 Android WebView + 配置 Chrome 引擎加速
- 后台轮询: WorkManager（每10秒）
- 通知: NotificationManager + NotificationChannel
- 全局弹窗: ForegroundService + TYPE_APPLICATION_OVERLAY
- 悬浮窗: WindowManager + TYPE_APPLICATION_OVERLAY

## 页面设计

### 底部导航

三个菜单项，文字在图标下方：

| 菜单 | 图标 | 目标页面 |
|------|------|----------|
| 汇权益 | 汇权益.png（Android 中转为 Drawable） | WebView 加载 https://sup.78k.cn/ |
| 优卡云 | 优卡云.ico（需转为 PNG） | WebView 加载 http://supplier.ukayun.cn/ |
| 数据 | 自定义图标（设置/数据样式） | 数据管理页面 |

### 汇权益页面 (WebView)

- 加载 `https://sup.78k.cn/`
- 用户手动输入账号密码 + 完成滑块验证码登录
- WebViewClient 在页面加载完成后注入 JavaScript，定时检查 `localStorage.access_token`
- 提取到 token 后，保存到本地 SharedPreferences
- 状态更新为 "已登录"，开始 10 秒轮询
- WebView 完全标准配置，支持文件上传（onShowFileChooser 系统默认行为）

获取 token 的注入 JS:

```javascript
(function() {
  var token = localStorage.getItem('access_token');
  if (token) {
    Android.onTokenReceived(token);
  }
})();
```

### 优卡云页面 (WebView)

- 加载 `http://supplier.ukayun.cn/`
- 用户手动输入账号密码 + 完成顶象滑动验证码登录
- 通过 CookieManager 监听 `admin_token` Cookie 的变化
- 提取到 token 后，保存到本地 SharedPreferences
- 状态更新为 "已登录"，开始 10 秒轮询
- WebView 完全标准配置，支持文件上传

Cookie 监听方式:

```kotlin
CookieManager.getInstance().getCookie(url)?.let { cookieStr ->
    val adminToken = cookieStr.split(";").firstOrNull { 
        it.trim().startsWith("admin_token=") 
    }?.substringAfter("=")
}
```

### 数据页面

| 功能区域 | 内容 |
|----------|------|
| 平台状态 | 汇权益: ✅ 已登录 / ❌ 未登录 / ⏳ 登录中 |
| | 优卡云: ✅ 已登录 / ❌ 未登录 / ⏳ 登录中 |
| 通知权限 | 显示当前授权状态，按钮"去设置"跳转系统通知设置页 |
| 悬浮窗权限 | 显示当前授权状态，按钮"去设置"跳转悬浮窗设置页 |
| 弹窗通知开关 | Toggle 开关，控制有订单时是否弹出全局 Dialog |
| 状态栏通知开关 | Toggle 开关，控制有订单时是否发系统通知栏通知 |

## 轮询机制

1. 用户成功登录并提取 token 后，立即启动轮询
2. 使用 WorkManager 的 PeriodicWorkRequest（最小间隔 15 分钟，通过自定义实现 10 秒间隔: 使用 Handler + 循环的 CoroutineWorker）
3. 两个平台各自独立轮询，互不干扰
4. 轮询接口:
   - 汇权益: `GET https://public.kky.v3.supplier.kakayun.vip/sup/v2/order/list`
     - Query 参数: `page=1&limit=20&key=&keytype=2&status=pending&starttime={当天00:00时间戳}&endtime={当天23:59时间戳}&sort_mode=0`
     - starttime/endtime 为当天 00:00:00 和 23:59:59 的 10 位时间戳
     - Header: `Authorization: Bearer {token}`
     - Header: `Referer: https://sup.78k.cn/`
   - 优卡云: `GET http://supplier.ukayun.cn/spa/order?status=1&page=1&limit=15&time_range={当天日期} 00:00:00~{当天日期} 23:59:59`
     - time_range 格式如 `2026-06-27 00:00:00~2026-06-27 23:59:59`
     - Header: `Authorization: Bearer {admin_token}`
     - 需计算 nonce、salt、sign 签名
     - 响应需 AES-256-CBC 解密（密钥见 ukayun_api_analysis.md）
5. 返回 `count > 0` 时视为有订单
6. 有订单时触发通知 + 弹窗（受数据页开关控制）
7. 请求失败（401/token 无效）→ 暂停轮询，状态改为"登录已过期"

## 通知与弹窗

### 系统状态栏通知

- 创建 NotificationChannel("order_alert", "订单提醒")
- 有订单时发送通知，标题: "订单提醒"，内容: "汇权益/优卡云 有新的待处理订单"
- 点击通知打开 App

### 全局弹窗 (跨 App)

- 需要 `SYSTEM_ALERT_WINDOW` 权限
- 使用前台服务 ForegroundService
- 有订单时创建 TYPE_APPLICATION_OVERLAY 的 AlertDialog
- 用户可点击"查看"跳回 App 对应页面，或点击"关闭"关闭弹窗
- 数据页开关可完全禁用弹窗

## 悬浮窗

- 需要 `SYSTEM_ALERT_WINDOW` 权限
- App 进入后台时（onStop）自动显示悬浮窗
- 悬浮窗位于屏幕右侧边缘，垂直居中
- 可拖动，拖动手势松手后自动靠边吸附到最近的左右边缘
- 点击悬浮窗 → 回到 App（拉起 App 主 Activity）
- App 回到前台时（onStart）自动隐藏悬浮窗
- 数据页开关可完全禁用悬浮窗

## 数据存储

- SharedPreferences 存储:
  - `hui_token`: 汇权益 access_token
  - `you_token`: 优卡云 admin_token
  - `alert_dialog_enabled`: 弹窗开关
  - `notification_enabled`: 通知栏开关
  - `float_window_enabled`: 悬浮窗开关

## 权限清单

| 权限 | 用途 | 是否必需 |
|------|------|----------|
| INTERNET | 网络请求 | 是 |
| POST_NOTIFICATIONS | 发送通知 | Android 13+ 运行时申请 |
| SYSTEM_ALERT_WINDOW | 全局弹窗 + 悬浮窗 | 运行时申请 |
| FOREGROUND_SERVICE | 后台轮询 | 是 |
| FOREGROUND_SERVICE_DATA_SYNC | 后台数据同步 | Android 14+ |
| ACCESS_NETWORK_STATE | 检测网络 | 推荐 |

## 项目结构

```
app/src/main/java/com/example/orderapp/
├── MainActivity.kt              # 主 Activity + 底部导航
├── ui/
│   ├── Huipage.kt               # 汇权益 WebView 页面
│   ├── YoukayunPage.kt          # 优卡云 WebView 页面
│   ├── DataPage.kt              # 数据页面
│   └── theme/
│       └── Theme.kt             # Material3 主题
├── service/
│   ├── PollingService.kt        # 前台服务 + 轮询逻辑
│   └── FloatWindowService.kt    # 悬浮窗服务
├── network/
│   ├── HuiApiClient.kt          # 汇权益 API 客户端
│   └── YoukaApiClient.kt        # 优卡云 API 客户端（签名+解密）
├── model/
│   └── OrderStatus.kt           # 订单状态和平台状态模型
└── util/
    ├── TokenManager.kt          # Token 存储与读取
    ├── NotificationHelper.kt    # 通知管理
    └── PermissionHelper.kt      # 权限检测与跳转
```

## 异常处理

- 网络错误 → 自动重试，最多3次，间隔5秒
- Token 过期 → 暂停轮询，状态改为"登录已过期"，用户需重新登录
- 优卡云签名错误 → 打印日志，不通知用户（开发者排查）
- 解密失败 → 记录异常，不通知用户

# GeckoView 迁移设计文档

## 概述

将 App 内两个平台页面（汇权益、优卡云）从 Android 系统 `WebView` 迁移到 Mozilla `GeckoView` 渲染引擎，解决渲染不完整（文字缺失、背景不全、商品列表空白）、弹窗异常等兼容性问题。

## 动机

当前系统 WebView 存在以下问题：
- uni-app 页面渲染不完整（文字不显示、商品列表空白、背景未完全渲染）
- 业务弹窗（公告通知）显示异常
- 多次添加 CSS hack 和兼容性修复仍无法根治
- 各设备 WebView 版本不一致导致行为不可控

GeckoView 使用 Firefox 渲染引擎，渲染路径与 Blink（WebView 底层）完全不同，可直接解决上述问题。

## 技术栈变更

| 项目 | 当前 | 迁移后 |
|---|---|---|
| 渲染引擎 | Android System WebView (Blink) | Mozilla GeckoView (Gecko) |
| SDK 坐标 | 内置系统 API | `org.mozilla.geckoview:geckoview:130.0` |
| Maven 仓库 | 无需额外配置 | 新增 `https://maven.mozilla.org/maven2/` |

## 架构变化

### 模型变化

```
迁移前: WebView (View + 数据 + 渲染 合一)
迁移后: GeckoView (显示容器) + GeckoSession (页面状态) + GeckoRuntime (引擎级单例)
```

### 类变化

| 新增类 | 职责 |
|---|---|
| `GeckoRuntimeManager` | Application 级 GeckoRuntime 单例，控制引擎生命周期 |
| `SessionManager` | 管理两个持久 GeckoSession（youkaSession / huiSession），绑定所有 Delegate |
| `GeckoPageFactory` (可选) | 工厂方法创建配置好的 GeckoPage Composable |

| 重写类 | 变更内容 |
|---|---|
| `YoukaPage.kt` | WebView → GeckoView + GeckoSession，删除所有 CSS hack/兼容性配置 |
| `HuiPage.kt` | WebView → GeckoView + GeckoSession，删除所有 CSS hack/兼容性配置 |

| 删除配置 | 原因 |
|---|---|
| `#h-fix` CSS 注入 | Gecko 渲染不同，不需要 |
| `MIXED_CONTENT_ALWAYS_ALLOW` | Gecko 默认行为合理 |
| `onReceivedSslError { proceed() }` | Gecko 有安全的证书处理 |
| `useWideViewPort / loadWithOverviewMode` | GeckoView 默认适配 |
| `setWebContentsDebuggingEnabled` | 改为 GeckoView `--debug` 参数 |

| 保留逻辑 | 迁移方式 |
|---|---|
| Cookie token 提取 | 仍使用 `CookieManager.getCookie()`（系统 API） |
| localStorage token 提取 | 改为 `GeckoSession.evaluateJavascript()` 异步回调 |
| JS console 日志 | 迁移至 `GeckoSession.consoleCallback` |
| DebugPanel | 数据源从 WebViewDiagnostics 改为 GeckoView 日志 |

## API 映射

| WebView API | GeckoView API | 说明 |
|---|---|---|
| `WebView` | `GeckoView` + `GeckoSession` | 视图与状态分离 |
| `WebViewClient` | `ContentDelegate` + `NavigationDelegate` | 加载状态 / 导航分别处理 |
| `WebChromeClient` | `PromptDelegate` + `PermissionDelegate` | 弹窗 / 权限分离 |
| `evaluateJavascript()` | `GeckoSession.evaluateJavascript()` | 签名：`(js: String, callback: (String?) -> Unit)` |
| `onCreateWindow` | `NavigationDelegate.onNewSession()` | 真正支持多 session |
| `onJsAlert/Confirm/Prompt` | `PromptDelegate.Alert/Button/Text` | 类似的委托模式 |
| `onReceivedHttpError` | `NavigationDelegate.onLoadError()` | 错误处理类似 |
| `onConsoleMessage` | `GeckoSession.consoleCallback` | 行为类似 |
| `WebViewClient.onPageFinished` | `NavigationDelegate.onPageStop()` | 触发时机相同 |
| `CookieManager` | `CookieManagerController` | 系统 CookieManager 仍可用 |

## 数据流

```
SessionManager.loadUrl(url)
    → GeckoSession.open(geckoRuntime)
    → GeckoView.setSession(session)
    → NavigationDelegate.onPageStop()
        → YoukaPage: CookieManager.getCookie()
        → HuiPage: evaluateJavascript("localStorage.getItem('access_token')")
    → Token 写入 ViewModel / SharedPreferences
    → API 轮询（不变）
```

## 弹窗处理

- **DOM 弹窗（公告通知覆盖层）**：Gecko 引擎默认正常渲染，不需要任何处理
- **JS alert / confirm / prompt**：迁移至 `PromptDelegate`，UI 仍使用 Android AlertDialog（与当前一致）

## DebugPanel 改造

- `WebViewDiagnostics` (WebView 日志捕获) → 替换为 GeckoView 的 `consoleCallback` + 网络请求回调
- DOM 检查功能（DebugPanel 中用 JavaScript 查询页面结构）→ 改为通过 `session.evaluateJavascript()` 实现，API 兼容

## 实施步骤

1. 添加 GeckoView 依赖（Maven 仓库 + build.gradle.kts）
2. 创建 `GeckoRuntimeManager` 单例
3. 创建 `SessionManager`，管理两个 session + 绑定 Delegate
4. 重写 `YoukaPage.kt`：GeckoView + Cookie token 提取
5. 重写 `HuiPage.kt`：GeckoView + evaluateJavascript token 提取
6. 迁移 DebugPanel 日志数据源
7. 删除所有 WebView 兼容性代码和 CSS hack
8. 测试两个平台的登录、页面渲染、弹窗、token 提取
9. 发布前验证 APK 体积增量

## 排除范围

- 不修改后台轮询逻辑（PollingService）
- 不修改 API 客户端（HuiApiClient / YoukaApiClient）
- 不修改订单提醒和通知逻辑
- 不修改数据页面和配置开关
- 不修改悬浮窗逻辑

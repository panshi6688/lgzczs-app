# 流光之城 CI/CD 工作流程

## 项目概览

- 项目路径：`D:\Users\Administrator\Desktop\ps\闲聊`
- GitHub 仓库：`https://github.com/panshi6688/lgzczs-app.git`
- 语言/框架：Kotlin + Jetpack Compose + Navigation Compose
- MinSdk: 26, TargetSdk: 34

---

## 1. GitHub Actions 自动编译 APK

### 触发条件
推送到 `main` 分支或创建 PR 到 `main` 时，自动触发编译。

### Workflow 文件
```yaml
# .github/workflows/build.yml
name: Build APK

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
      - name: Grant execute permission
        run: chmod +x gradlew
      - name: Build Debug APK
        run: ./gradlew assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: lgzczs-app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

### 产物
- GitHub Actions Artifacts 名：`lgzczs-app-debug`
- APK 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 在 GitHub Actions 运行页面 → Summary → Artifacts 下载

---

## 2. GitHub 仓库配置

### 远程仓库
```powershell
# HTTPS（需要认证）
git remote add origin https://github.com/panshi6688/lgzczs-app.git

# SSH（已有 deploy key）
git remote add ssh-origin git@github.com:panshi6688/lgzczs-app.git
```

### Token
- 类型：Personal Access Token (classic)
- 权限：`repo` + `workflow`
- Token 值：`【已移除，请自行设置】`

---

## 3. 推送代码（关键：HTTPS 被防火墙拦截，改用 SSH）

**网络上 HTTPS 出站被防火墙拦截，Clash 代理也无法正常工作**，因此必须使用 SSH 方式推送。

### SSH 配置

```
SSH 私钥路径:   C:\Users\Administrator\.ssh\github_actions
SSH 公钥:       已在 GitHub 添加为 Deploy Key（有写入权限）
```

**注意：** 本地 git 仓库未设置 `user.name`（无配置），推送到 GitHub 用 Actions 运行，commit 信息不需要本地 user 配置。

### 推送命令

在推送前，先检查 ssh-agent 是否运行：

```powershell
# 确保 ssh-agent 正在运行
Get-Service ssh-agent | Start-Service -PassThru

# 添加 SSH 密钥到 agent
ssh-add C:\Users\Administrator\.ssh\github_actions

# 设置 SSH 命令（每次推送前执行）
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no -o ConnectTimeout=10"

# 推送到 main 分支（触发 CI 编译）
git push ssh-origin main
```

### 完整推送示例

```powershell
# 在项目目录执行
$env:GIT_SSH_COMMAND="ssh -i C:/Users/Administrator/.ssh/github_actions -o StrictHostKeyChecking=no -o ConnectTimeout=10"
git add .
git commit -m "feat: 描述本次改动"
git push ssh-origin main
```

---

## 4. 从 GitHub Actions 下载 APK

### 步骤
1. 浏览器打开仓库：`https://github.com/panshi6688/lgzczs-app`
2. 点击 Actions 标签页
3. 选择最新的成功运行
4. 在 Summary 底部找到 Artifacts
5. 下载 `lgzczs-app-debug`
6. 解压得到 `app-debug.apk`

---

## 5. CI 运行状态

| 运行 ID | 状态 |
|---------|------|
| #28305407870 | 成功 |
| #28307066738 | 成功 |
| #28307271460 | 成功 |

---

## 6. 本地构建 APK（备选）

```powershell
# Windows
gradlew assembleDebug

# Linux/macOS
./gradlew assembleDebug
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 7. 重要提醒

1. **SSH 是唯一可用的推送方式**（HTTPS 网络不通）
2. 推送前务必设置 `$env:GIT_SSH_COMMAND` 环境变量
3. 确保 `main` 分支是推送目标（CI 只监听 `main`）
4. Token (`ghp_...`) 只在需要 GitHub API 操作时使用（如 Workflow dispatch），推送代码不需要 token
5. `.github/workflows/build.yml` 中只有 `assembleDebug`，如果要 release build 需添加签名配置

---

## 8. 当前项目文件布局

```
闲聊/
├── .github/workflows/build.yml    # CI 配置文件
├── apk/                            # 历史 APK 备份
│   ├── app-debug.apk
│   ├── app-debug4.apk
│   ├── lgzczs-app-debug.apk
│   ├── lgzczs-app-debug-v2.apk
│   └── lgzczs-app-debug-v3.apk
├── app/                            # Android 应用源码
├── build.gradle.kts                # 根级 Gradle 配置
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradlew.bat           # Gradle wrapper
```

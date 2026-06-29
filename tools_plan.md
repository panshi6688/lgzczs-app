# 工具页实施计划

## 一、数据模型

```json
// Cloudflare KV 中存储
{
  "groups": [
    {
      "id": "all_features",
      "name": "全部功能",
      "order": 1,
      "buttons": [
        { "id": "10w", "label": "10W", "url": "https://...", "badge": "10次", "order": 1 },
        ...
      ]
    },
    {
      "id": "red_packet",
      "name": "红包区域",
      "order": 2,
      "buttons": [
        { "id": "farm_seckill", "label": "农场秒杀", "url": "https://...", "badge": null, "order": 1 },
        ...
      ]
    },
    {
      "id": "diantao",
      "name": "点淘区域",
      "order": 3,
      "buttons": [
        { "id": "diantao_shop", "label": "店铺主页", "url": "https://...", "badge": "点淘", "order": 1 },
        ...
      ]
    }
  ]
}
```

## 二、Cloudflare Workers API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/buttons` | App 获取所有按钮 | 无 |
| POST | `/api/admin/login` | 管理员登录，返回 JWT | 无 |
| GET | `/api/admin/buttons` | 管理后台获取 | JWT |
| POST | `/api/admin/groups` | 新增分组 | JWT |
| PUT | `/api/admin/groups/:id` | 编辑分组 | JWT |
| DELETE | `/api/admin/groups/:id` | 删除分组 | JWT |
| POST | `/api/admin/groups/:id/buttons` | 新增按钮 | JWT |
| PUT | `/api/admin/groups/:id/buttons/:bid` | 编辑按钮 | JWT |
| DELETE | `/api/admin/groups/:id/buttons/:bid` | 删除按钮 | JWT |
| PUT | `/api/admin/reorder` | 调整分组/按钮顺序 | JWT |

## 三、Android 端改动

### 文件清单

- `MainActivity.kt` — 底部导航加 Tools tab，调整 nav graph
- `ui/ToolsPage.kt` — 新页面，Compose 按钮网格
- `data/ToolsRepository.kt` — 从 Workers API 拉数据 + 本地缓存
- `model/ToolItem.kt` / `ToolGroup.kt` — 数据模型

### ToolsPage 布局

```
┌──────────────────────────┐
│  工具                    │  ← 标题
│                          │
│  ── 全部功能 ──          │  ← 分组标题
│  [10W] [6W] [5W] [4W①]  │  ← 4列网格
│  [4W②] [3W①] [3W②] [3W③] │
│  ...                     │
│                          │
│  ── 红包区域 ──          │
│  [农场秒杀] [秒杀频道]    │
│  ...                     │
│                          │
│  ── 点淘区域 ──          │
│  [店铺主页] [购物车] [代付款]│
└──────────────────────────┘
```

- 首次打开 fetch → 缓存到 SP/文件
- 后续打开先渲染缓存，后台静默刷新
- 下拉刷新手动更新
- 按钮点击 → `Intent(Intent.ACTION_VIEW, Uri.parse(url))` → 系统选择

## 四、管理后台页面

**技术选型：** 纯 HTML/CSS/JS 单页（无需框架，部署到 Cloudflare Pages）

**页面结构：**

```
登录页 → 密码输入 → 管理主页

管理主页：
├── 分组列表（可拖拽排序）
│   ├── 全部功能 → 展开显示该组按钮
│   │   ├── 10W [编辑] [删除]
│   │   ├── 6W  [编辑] [删除]
│   │   └── [+ 新增按钮]
│   ├── 红包区域 → 展开显示该组按钮
│   └── 点淘区域 → 展开显示该组按钮
├── [+ 新增分组]
└── [保存全部] 按钮 → 批量写入 KV
```

**功能：**
- 增删改分组
- 增删改按钮（label、url、badge、order）
- 拖拽排序
- 实时预览

**部署：** `npx wrangler pages deploy` → 自动部署到 Cloudflare Pages

## 五、实施步骤

1. 注册 Cloudflare Workers + KV namespace
2. 创建 Workers 项目，部署 API
3. 创建管理页面 HTML，部署到 Pages
4. Android 端新增 ToolsPage.kt + ToolsRepository.kt
5. 从 UrlConstants.java 提取全部按钮数据，初始化 KV
6. 测试：app 打开工具页 → 拉取配置 → 渲染按钮 → 点击跳转

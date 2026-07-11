# Tab/Category System — Design Spec

## Overview

Add a horizontally scrollable tab bar below the quick-access bar in the Tools page. Each tab filters which groups are displayed. Tabs are manageable from the admin panel.

## Component Choice

**ScrollableTabRow** (Material3) — standard Material Design tabs, automatically scrollable when tabs overflow.

## Data Model

```kotlin
// New model
data class TabConfig(
    val name: String,   // display label
    val order: Int
)

// ToolConfig additions
data class ToolConfig(
    val tabs: List<TabConfig>? = null,  // NEW: if null → no tab bar
    val groups: List<ToolGroup>
)

// ToolGroup additions
data class ToolGroup(
    val name: String,
    val tab: String? = null,  // NEW: which tab this group belongs to; null/"全部" → shows under "全部"
    val order: Int,
    val hints: List<String>,
    val buttons: List<ToolItem>
)
```

### JSON Example

```json
{
  "tabs": [
    { "name": "全部", "order": 0 },
    { "name": "红包", "order": 1 },
    { "name": "点淘", "order": 2 }
  ],
  "groups": [
    {
      "name": "全部功能",
      "tab": "全部",
      "order": 0,
      "hints": [],
      "buttons": []
    },
    {
      "name": "红包区域",
      "tab": "红包",
      "order": 0,
      "hints": ["提示文字"],
      "buttons": []
    },
    {
      "name": "点淘区域",
      "tab": "点淘",
      "order": 0,
      "hints": [],
      "buttons": []
    }
  ]
}
```

### Backward Compatibility

- If `tabs` is null/absent → no tab bar rendered, all groups shown as before
- If `tabs` is present → tab bar shown above groups
- Groups without `tab` field or with tab="全部" → shown under the "全部" tab
- "全部" tab is always the first tab if defined; it shows all groups

## Android UI Layout

```
┌──────────────────────────────┐
│          工具 (centered)      │  ← Box + Alignment.Center
│ [QA1] [QA2] [QA3] [QA4]      │  ← Quick Access (4 slots)
│ [全部] [红包] [点淘] [其他...]  │  ← ScrollableTabRow
├──────────────────────────────┤
│ (selected tab's groups)      │
│ 分组1 name                   │
│ [btn] [btn] [btn] [btn]      │
│                              │
│ 分组2 name                   │
│ [btn] [btn] [btn] [btn]      │
└──────────────────────────────┘
```

### Key Behaviors

- Tab state: `var selectedTabIndex by remember { mutableIntStateOf(0) }`
- On tab change → filter groups to only those matching selected tab name (except "全部" which shows all)
- Quick access bar persists across all tabs (global)
- ScrollableTabRow with `edgePadding = 0.dp` for compact layout
- If there's only 1 tab (e.g. only "全部"), the tab bar is not shown

## Admin Page Changes

### New Section: 标签管理

Placement: after existing grouping area, before the save button.

UI:
```
┌──────────────────────────────┐
│ 标签管理                      │
│ [输入标签名] [+添加]          │
│                              │
│ 全部  [↑] [↓] [删除]        │
│ 红包  [↑] [↓] [删除]        │
│ 点淘  [↑] [↓] [删除]        │
└──────────────────────────────┘
```

- "全部" tab is always present (cannot be deleted)
- Order buttons move tabs up/down
- Delete button removes tab (if no groups reference it) — warn if groups still reference it

### Modified: 分组编辑

Each group's existing edit form gets a new dropdown:

```
分组名称: [___________]
归属标签: [▼ 全部 | 红包 | 点淘]
排序:     [___]
按钮列表:
  ...
```

- The dropdown options are populated from the tabs list
- Default is "全部"

### Data Output

When saving, the admin page must produce JSON in the new format with both `tabs[]` and groups with `tab` fields.

## Implementation Steps (Android)

1. Add `TabConfig` data class to model package
2. Add `tabs: List<TabConfig>?` to `ToolConfig`
3. Add `tab: String?` to `ToolGroup`
4. Update `ToolsPage.kt`:
   - Add `ScrollableTabRow` between quick access bar and groups
   - Add tab selection state
   - Filter groups by selected tab
   - Add backward-compat: skip tab bar if tabs is null/empty

## Implementation Steps (Admin)

1. Add tab management section HTML (input + list + order/delete buttons)
2. Modify group edit forms to include tab dropdown
3. Update save function to output `tabs[]` and `tab` on each group
4. Backward-compat: if old JSON has no tabs, treat groups as belonging to "全部"

## Files to Modify

| File | Changes |
|------|---------|
| `app/.../model/ToolConfig.kt` | Add `TabConfig` data class, `tabs` field |
| `app/.../model/ToolGroup.kt` | Add `tab: String?` field |
| `app/.../ui/ToolsPage.kt` | Add ScrollableTabRow + tab filtering logic |
| `workers-api/admin/index.html` | Add tab management UI, modify group editor |

## Out of Scope

- Tab reordering via drag-and-drop (use up/down buttons instead)
- Tab icons or badges
- HorizontalPager swipe between tabs (ScrollableTabRow only, no swipe)

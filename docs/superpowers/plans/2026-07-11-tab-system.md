# Tab System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a horizontally scrollable tab bar (ScrollableTabRow) below the quick access bar to filter groups, with full admin management support.

**Architecture:** Tabs are a new `tabs` array in the JSON config. Each group gets a `tab` string field to associate with a tab. The Android app renders ScrollableTabRow with filtering logic. The admin page adds a tab management section and a tab dropdown in each group editor.

**Tech Stack:** Android (Jetpack Compose, Material3), Admin (vanilla HTML/CSS/JS), Worker API (unchanged)

---

### Task 1: Android Data Model

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/model/ToolModels.kt`

- [ ] **Step 1: Add `TabConfig` data class and update existing models**

```kotlin
package com.lgzczs.app.model

data class TabConfig(
    val name: String,
    val order: Int
)

data class ToolConfig(
    val groups: List<ToolGroup>,
    val tabs: List<TabConfig>? = null
)

data class ToolGroup(
    val id: String,
    val name: String,
    val tab: String? = null,
    val order: Int,
    val hints: List<String> = emptyList(),
    val buttons: List<ToolItem>
)

data class ToolItem(
    val id: String,
    val label: String,
    val url: String,
    val badge: String?,
    val order: Int
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/lgzczs/app/model/ToolModels.kt
git commit -m "feat: add TabConfig, tabs field to ToolConfig, tab field to ToolGroup"
```

---

### Task 2: ToolsPage Tab Bar UI

**Files:**
- Modify: `app/src/main/java/com/lgzczs/app/ui/ToolsPage.kt`

- [ ] **Step 1: Add missing imports at top**

Add after existing Material3 imports:
```kotlin
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableIntStateOf
```

- [ ] **Step 2: Add tab state variable near other state declarations** (after line `var quickAccess by remember ...`)

```kotlin
var selectedTabIndex by remember { mutableIntStateOf(0) }
```

- [ ] **Step 3: Compute derived tab values** (add after `val showLoading = ...`)

```kotlin
val tabs = if (config?.tabs != null && config!!.tabs.isNotEmpty()) {
    config!!.tabs.filter { it.name != "全部" }.sortedBy { it.order }.map { it.name }.let { listOf("全部") + it }
} else {
    emptyList()
}
```

- [ ] **Step 4: Insert ScrollableTabRow between quick access bar and groups**

Replace:
```kotlin
            Spacer(modifier = Modifier.height(8.dp))

            val sortedGroups = config!!.groups.sortedBy { it.order }
```

With:
```kotlin
            if (tabs.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, name ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(name, maxLines = 1) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val selectedTabName = tabs.getOrNull(selectedTabIndex) ?: "全部"
            val sortedGroups = config!!.groups.sortedBy { it.order }
                .filter { group ->
                    selectedTabName == "全部" || group.tab == null || group.tab == selectedTabName
                }
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lgzczs/app/ui/ToolsPage.kt
git commit -m "feat: add ScrollableTabRow with group filtering"
```

---

### Task 3: Admin Page — Tab Management Section

**Files:**
- Modify: `workers-api/admin/index.html`

- [ ] **Step 1: Add HTML for tab management section**

Insert after the `bottom-actions` div (`</div>` closing the card on or about line 106) and before the closing `</div>` of adminView (line 107):

```html
    <div class="card" style="margin-top:12px">
      <h2 style="font-size:16px;margin-bottom:12px">标签管理</h2>
      <div class="hints-row">
        <input id="newTabInput" placeholder="新标签名称" onkeydown="if(event.key==='Enter')addTab()">
        <button class="btn btn-primary btn-sm" onclick="addTab()">添加</button>
      </div>
      <div id="tabList"></div>
    </div>
```

- [ ] **Step 2: Add JavaScript for tab CRUD**

Add these functions before the `saveAll` function:

```javascript
function renderTabManager() {
  const container = document.getElementById('tabList');
  if (!config.tabs) config.tabs = [];
  const sorted = [...config.tabs].sort((a, b) => a.order - b.order);
  let html = '';
  sorted.forEach((t, i) => {
    const isAll = t.name === '全部';
    html += `
      <div style="display:flex;align-items:center;gap:8px;padding:6px 0;border-bottom:1px solid #f0f0f0">
        <span style="flex:1;font-size:14px">${escHtml(t.name)}</span>
        ${!isAll ? `
          <button class="btn btn-outline btn-sm" onclick="moveTab(${i}, -1)" ${i === 0 || (i === 1 && sorted[0].name === '全部') ? 'disabled' : ''}>↑</button>
          <button class="btn btn-outline btn-sm" onclick="moveTab(${i}, 1)" ${i === sorted.length - 1 ? 'disabled' : ''}>↓</button>
          <button class="btn btn-danger btn-sm" onclick="deleteTab('${escHtml(t.name)}')">删除</button>
        ` : '<span style="color:#999;font-size:12px">固定标签</span>'}
      </div>
    `;
  });
  if (sorted.length === 0) html = '<div style="color:#999;font-size:13px;padding:8px 0">暂无标签，添加一个开始</div>';
  container.innerHTML = html;
}

function addTab() {
  const input = document.getElementById('newTabInput');
  const name = input.value.trim();
  if (!name) return toast('请输入标签名称');
  if (!config.tabs) config.tabs = [];
  if (config.tabs.some(t => t.name === name)) return toast('标签已存在');
  // Ensure "全部" is always first
  if (!config.tabs.some(t => t.name === '全部')) {
    config.tabs.push({ name: '全部', order: 0 });
  }
  config.tabs.push({ name, order: config.tabs.length });
  input.value = '';
  renderTabManager();
  toast('标签已添加');
}

function deleteTab(name) {
  if (!confirm(`确定删除标签「${name}」？\n归属于此标签的分组仍会保留，但不会显示在任何标签下。`)) return;
  config.tabs = config.tabs.filter(t => t.name !== name);
  // Update groups that referenced this tab: set tab to null
  config.groups.forEach(g => { if (g.tab === name) g.tab = null; });
  renderTabManager();
  toast('已删除');
}

function moveTab(index, direction) {
  if (!config.tabs) return;
  const sorted = [...config.tabs].sort((a, b) => a.order - b.order);
  const targetIdx = index + direction;
  if (targetIdx < 0 || targetIdx >= sorted.length) return;
  // Don't move "全部" out of first position
  if (sorted[index].name === '全部' || sorted[targetIdx].name === '全部') return;
  [sorted[index], sorted[targetIdx]] = [sorted[targetIdx], sorted[index]];
  sorted.forEach((t, i) => t.order = i);
  // Re-sort config.tabs to match
  config.tabs = sorted;
  renderTabManager();
}
```

- [ ] **Step 3: Update `loadConfig` to initialize tabs if absent**

```javascript
async function loadConfig() {
  const data = await api('/api/admin/buttons');
  if (data && data.groups) {
    config = data;
    if (!config.tabs) config.tabs = [];
    if (!config.tabs.some(t => t.name === '全部')) {
      config.tabs.unshift({ name: '全部', order: 0 });
    }
    if (!currentGroupId && config.groups.length) currentGroupId = config.groups[0].id;
  }
}
```

- [ ] **Step 4: Update `render` to also render tab manager**

Add `renderTabManager();` inside the `render()` function after `renderGrid();`.

- [ ] **Step 5: Commit**

```bash
git add workers-api/admin/index.html
git commit -m "feat: add tab management section to admin page"
```

---

### Task 4: Admin Page — Group Tab Dropdown + Save Tab Data

**Files:**
- Modify: `workers-api/admin/index.html`

- [ ] **Step 1: Add tab dropdown to each group editing**

The current group editing happens via `addGroup()` which uses `prompt()`. Replace the group edit flow to include a tab select.

Modify the `renderTabs()` function's group tab area to add a tab indicator, and modify `addGroup()` to prompt for tab:

```javascript
function addGroup() {
  const name = prompt('请输入分组名称：');
  if (!name) return;
  let tab = '全部';
  const tabOptions = config.tabs && config.tabs.length > 0
    ? config.tabs.map(t => t.name).join(', ')
    : '全部';
  const tabInput = prompt(`请输入归属标签（可选：${tabOptions}，留空默认为"全部"）：`);
  if (tabInput !== null && tabInput.trim()) tab = tabInput.trim();
  const newGroup = {
    id: 'group_' + Date.now(),
    name,
    tab,
    order: config.groups.length + 1,
    hints: [],
    buttons: []
  };
  config.groups.push(newGroup);
  currentGroupId = newGroup.id;
  render();
  toast('分组已添加');
}
```

Also add a way to edit a group's tab after creation. Since groups are shown as tabs at the top, add a right-click context or double-click to edit group properties. Simpler approach: add a small edit button next to each group tab.

Modify `renderTabs()` to include a group edit button that opens a prompt for changing tab:

```javascript
function renderTabs() {
  const container = document.getElementById('tabsContainer');
  container.innerHTML = '';
  config.groups.sort((a, b) => a.order - b.order).forEach(g => {
    const btn = document.createElement('button');
    btn.className = `tab ${g.id === currentGroupId ? 'active' : ''}`;
    btn.textContent = g.name;
    btn.onclick = () => { currentGroupId = g.id; render(); };
    container.appendChild(btn);

    // Group edit button (small gear icon next to tab)
    const editBtn = document.createElement('button');
    editBtn.className = 'tab';
    editBtn.textContent = '⚙';
    editBtn.style.fontSize = '12px';
    editBtn.style.padding = '8px 6px';
    editBtn.title = '编辑分组属性';
    editBtn.onclick = (e) => { e.stopPropagation(); editGroup(g.id); };
    container.appendChild(editBtn);
  });
  const addBtn = document.createElement('button');
  addBtn.className = 'tab tab-add';
  addBtn.textContent = '+';
  addBtn.title = '新增分组';
  addBtn.onclick = addGroup;
  container.appendChild(addBtn);
}

function editGroup(groupId) {
  const group = config.groups.find(g => g.id === groupId);
  if (!group) return;
  const name = prompt('分组名称：', group.name);
  if (!name) return;
  const tabOptions = config.tabs && config.tabs.length > 0
    ? config.tabs.map(t => t.name).join(', ')
    : '全部';
  const tab = prompt(`归属标签（可选：${tabOptions}，留空为"全部"）：`, group.tab || '全部');
  group.name = name;
  group.tab = (tab !== null && tab.trim()) ? tab.trim() : null;
  render();
  toast('分组已更新');
}
```

- [ ] **Step 2: Update `saveAll` to include tabs**

```javascript
async function saveAll() {
  saveHints();
  const body = { groups: config.groups };
  if (config.tabs) body.tabs = config.tabs;
  const data = await api('/api/admin/reorder', {
    method: 'PUT',
    body: JSON.stringify(body)
  });
  if (data.success) toast('已保存');
}
```

- [ ] **Step 3: Update Worker API to accept tabs in the reorder endpoint**

Check the worker API's reorder handler. If it validates the request body structure, it may reject the `tabs` field. The worker just stores JSON in KV, so it should be fine as-is if it stores the entire body. But we need to verify.

Read: `workers-api/functions/api/[[route]].ts` to check if the reorder handler validates the body.

If it does validate, add `tabs` to the accepted fields.

- [ ] **Step 4: Commit**

```bash
git add workers-api/admin/index.html
git commit -m "feat: add tab dropdown to group editor, include tabs in save payload"
```

---

### Task 5: Verify and Deploy

- [ ] **Step 1: Verify Android compilation**

Run: `./gradlew :app:assembleDebug` or check with lint.

- [ ] **Step 2: Verify admin page**

Open `http://localhost:xxxx` (dev server) and test:
1. Tabs render below quick access bar in app
2. Clicking tabs filters groups
3. Admin: add/delete/reorder tabs
4. Admin: change group's tab
5. Save and reload: tabs persist

- [ ] **Step 3: Push and deploy**

```bash
git add -A
git commit -m "feat: tab system for tools page with admin management"
git push ssh-origin master:main
```

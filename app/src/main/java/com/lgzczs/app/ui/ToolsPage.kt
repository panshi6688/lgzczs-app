package com.lgzczs.app.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgzczs.app.data.ToolsRepository
import com.lgzczs.app.model.ToolItem
import com.lgzczs.app.util.TokenManager
import com.lgzczs.app.util.UrlOpener
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ToolsPage(
    repository: ToolsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(repository.getCachedConfig()) }
    var isLoading by remember { mutableStateOf(config == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var quickAccess by remember { mutableStateOf(repository.getQuickAccessButtons()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedKeyword by remember { mutableStateOf(repository.getSelectedKeyword()) }
    var customKeywords by remember { mutableStateOf(repository.getCustomKeywords()) }
    var keywordDropdownExpanded by remember { mutableStateOf(false) }
    var showCustomKeywordDialog by remember { mutableStateOf(false) }
    var customKeywordInput by remember { mutableStateOf("") }
    val tokenManager = remember { TokenManager(context) }
    var floatToolsEnabled by remember { mutableStateOf(tokenManager.floatToolsEnabled) }

    fun addToQuickAccess(item: ToolItem) {
        val items = quickAccess.toMutableList()
        val emptyIndex = items.indexOfFirst { it == null }
        if (emptyIndex >= 0) {
            items[emptyIndex] = item
            quickAccess = items
            repository.setQuickAccess(emptyIndex, item)
            Toast.makeText(context, "已添加到快速访问", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "快速访问已满，请先长按移除一个", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeQuickAccess(index: Int) {
        val items = quickAccess.toMutableList()
        val item = items[index]
        items[index] = null
        quickAccess = items
        repository.setQuickAccess(index, null)
        if (item != null) {
            Toast.makeText(context, "已移除「${item.label}」", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun load() {
        isLoading = true
        errorMessage = null
        try {
            val result = repository.fetchButtons()
            result.onSuccess {
                config = it
                errorMessage = null
            }.onFailure { e ->
                if (config == null) {
                    val fallback = repository.loadDefaultConfig()
                    if (fallback != null) {
                        config = fallback
                    } else {
                        errorMessage = if (e.message?.contains("Unable to resolve host") == true) {
                            "无法连接服务器，请检查网络"
                        } else {
                            "加载失败：${e.message}"
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            if (config == null) errorMessage = "加载失败"
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    val hasError = errorMessage != null && config == null
    val showLoading = isLoading && config == null

val tabs = if (config?.tabs != null && config!!.tabs!!.isNotEmpty()) {
    config!!.tabs!!.filter { it.name != "全部" }.sortedBy { it.order }.map { it.name }.let { listOf("全部") + it }
} else {
    emptyList()
}

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "工具",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (isLoading && !hasError) {
                LoadingIndicator(modifier = Modifier.align(Alignment.CenterEnd).size(20.dp))
            } else {
                IconButton(
                    onClick = { scope.launch { load() } },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (hasError) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { scope.launch { load() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("重试")
            }
        }

        if (showLoading) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(32.dp))
            }
        }

        if (config != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickAccess.forEachIndexed { index, item ->
                    if (item != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE8E8E8))
                                .combinedClickable(
                                    onClick = { UrlOpener.open(context, item.url, selectedKeyword) },
                                    onLongClick = { removeQuickAccess(index) }
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.badge != null) {
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = item.badge,
                                        fontSize = 8.sp,
                                        color = Color(0xFFFF6200),
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF0F0F0))
                                .clickable { Toast.makeText(context, "长按下面的功能按钮可添加至快速访问栏", Toast.LENGTH_SHORT).show() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
            val keywordOptions = remember(config, customKeywords) {
                val options = mutableListOf<Pair<String, String>>()
                options.add("" to "(不使用)")
                config?.keywords?.forEach { options.add(it to it) }
                customKeywords.forEach { kw -> if (!options.any { p -> p.first == kw }) options.add(kw to kw) }
                options.add("__custom__" to "自定义...")
                options.toList()
            }
            val currentKeywordLabel = keywordOptions.find { it.first == selectedKeyword }?.second
                ?: selectedKeyword.ifBlank { "(不使用)" }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "关键词:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.widthIn(max = 110.dp)) {
                    Text(
                        text = currentKeywordLabel,
                        modifier = Modifier
                            .clickable { keywordDropdownExpanded = true }
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    DropdownMenu(
                        expanded = keywordDropdownExpanded,
                        onDismissRequest = { keywordDropdownExpanded = false }
                    ) {
                        keywordOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    keywordDropdownExpanded = false
                                    if (value == "__custom__") {
                                        customKeywordInput = ""
                                        showCustomKeywordDialog = true
                                    } else {
                                        selectedKeyword = value
                                        repository.setSelectedKeyword(value)
                                    }
                                }
                            )
                        }
                    }
                }
                if (selectedKeyword.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "✕",
                        modifier = Modifier.clickable {
                            selectedKeyword = ""
                            repository.setSelectedKeyword("")
                        },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "悬浮显示",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = floatToolsEnabled,
                    onCheckedChange = {
                        floatToolsEnabled = it
                        tokenManager.floatToolsEnabled = it
                    }
                )
            }
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
                    selectedTabName == "全部" || group.tab == selectedTabName
                }
            sortedGroups.forEach { group ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\u3000\u3000 ${group.name} \u3000\u3000",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                group.hints.forEach { hint ->
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        color = Color(0xFFFF6200),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val buttons = group.buttons.sortedBy { it.order }
                val rows = buttons.chunked(4)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .combinedClickable(
                                        onClick = { UrlOpener.open(context, item.url, selectedKeyword) },
                                        onLongClick = { addToQuickAccess(item) }
                                    )
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.badge != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.badge,
                                            fontSize = 9.sp,
                                            color = Color(0xFFFF6200),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        if (row.size < 4) {
                            repeat(4 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
        if (showCustomKeywordDialog) {
            AlertDialog(
                onDismissRequest = { showCustomKeywordDialog = false },
                title = { Text("自定义关键词") },
                text = {
                    OutlinedTextField(
                        value = customKeywordInput,
                        onValueChange = { customKeywordInput = it },
                        label = { Text("输入搜索关键词") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (customKeywordInput.isNotBlank()) {
                            repository.addCustomKeyword(customKeywordInput)
                            customKeywords = repository.getCustomKeywords()
                            selectedKeyword = customKeywordInput
                            repository.setSelectedKeyword(customKeywordInput)
                        }
                        showCustomKeywordDialog = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomKeywordDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sweep = 270f
        drawArc(
            color = Color.Gray,
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset.Zero,
            size = size
        )
    }
}

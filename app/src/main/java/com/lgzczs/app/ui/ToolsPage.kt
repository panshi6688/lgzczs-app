package com.lgzczs.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgzczs.app.data.ToolsRepository
import com.lgzczs.app.model.ToolConfig
import com.lgzczs.app.model.ToolGroup
import com.lgzczs.app.model.ToolItem
import com.lgzczs.app.util.UrlOpener
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolsPage(
    repository: ToolsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<ToolConfig?>(repository.getCachedConfig()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var quickAccessItems by remember { mutableStateOf(repository.getQuickAccessButtons()) }

    var showQuickAccessDialog by remember { mutableStateOf(false) }
    var pendingQuickAccessItem by remember { mutableStateOf<ToolItem?>(null) }
    var showRemoveQaDialog by remember { mutableStateOf(false) }
    var pendingRemoveIndex by remember { mutableStateOf(-1) }
    var showQaHelpDialog by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = repository.fetchButtons()
                result.onSuccess {
                    config = it
                    errorMessage = null
                }.onFailure { e ->
                    if (config == null) {
                        errorMessage = if (e.message?.contains("Unable to resolve host") == true) {
                            "无法连接服务器，请检查网络"
                        } else {
                            "加载失败：${e.message}"
                        }
                    }
                }
            } catch (e: Throwable) {
                if (config == null) errorMessage = "加载失败"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    if (showQuickAccessDialog && pendingQuickAccessItem != null) {
        AlertDialog(
            onDismissRequest = { showQuickAccessDialog = false; pendingQuickAccessItem = null },
            title = { Text("添加到快速访问") },
            text = { Text("是否将\"${pendingQuickAccessItem!!.label}\"添加到快速访问？") },
            confirmButton = {
                TextButton(onClick = {
                    val items = quickAccessItems.toMutableList()
                    val emptyIndex = items.indexOfFirst { it == null }
                    if (emptyIndex == -1) {
                        Toast.makeText(context, "快速访问位置已满，长按已有按钮可移除", Toast.LENGTH_SHORT).show()
                    } else {
                        items[emptyIndex] = pendingQuickAccessItem
                        repository.setQuickAccess(emptyIndex, pendingQuickAccessItem)
                        quickAccessItems = items
                    }
                    showQuickAccessDialog = false
                    pendingQuickAccessItem = null
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAccessDialog = false; pendingQuickAccessItem = null }) { Text("取消") }
            }
        )
    }

    if (showRemoveQaDialog && pendingRemoveIndex >= 0) {
        val item = quickAccessItems.getOrNull(pendingRemoveIndex)
        AlertDialog(
            onDismissRequest = { showRemoveQaDialog = false; pendingRemoveIndex = -1 },
            title = { Text("移除快速访问") },
            text = { Text("是否将\"${item?.label}\"从快速访问移除？") },
            confirmButton = {
                TextButton(onClick = {
                    val items = quickAccessItems.toMutableList()
                    if (pendingRemoveIndex in items.indices) {
                        items[pendingRemoveIndex] = null
                        repository.setQuickAccess(pendingRemoveIndex, null)
                        quickAccessItems = items
                    }
                    showRemoveQaDialog = false
                    pendingRemoveIndex = -1
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveQaDialog = false; pendingRemoveIndex = -1 }) { Text("取消") }
            }
        )
    }

    if (showQaHelpDialog) {
        AlertDialog(
            onDismissRequest = { showQaHelpDialog = false },
            title = { Text("快速访问使用说明") },
            text = { Text("长按全部功能中按钮可添加到快速访问，长按快速访问上的功能也可以移出快速访问位置") },
            confirmButton = {
                TextButton(onClick = { showQaHelpDialog = false }) { Text("知道了") }
            }
        )
    }

    when {
        isLoading && config == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        config != null -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "工具",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { load() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "刷新",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage!!,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    QuickAccessBar(
                        items = quickAccessItems,
                        maxSlots = repository.getMaxQuickAccess(),
                        onItemClick = { item -> UrlOpener.open(context, item.url) },
                        onItemLongClick = { index ->
                            pendingRemoveIndex = index
                            showRemoveQaDialog = true
                        },
                        onEmptyClick = { showQaHelpDialog = true }
                    )
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                val sortedGroups = config?.groups?.sortedBy { it.order } ?: emptyList()
                sortedGroups.forEach { group ->
                    item {
                        Text(
                            text = "── ${group.name} ──",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    (group.hints ?: emptyList()).forEach { hint ->
                        item {
                            Text(
                                text = hint,
                                fontSize = 12.sp,
                                color = Color(0xFFFF6200),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    val buttons = group.buttons ?: emptyList()
                    item {
                        ToolGrid(
                            items = buttons.sortedBy { it.order },
                            columns = 4,
                            onButtonClick = { item -> UrlOpener.open(context, item.url) },
                            onButtonLongClick = { item ->
                                pendingQuickAccessItem = item
                                showQuickAccessDialog = true
                            }
                        )
                    }
                }
            }
        }
        errorMessage != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = errorMessage!!, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重试")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun QuickAccessBar(
    items: List<ToolItem?>,
    maxSlots: Int,
    onItemClick: (ToolItem) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onEmptyClick: () -> Unit
) {
    Column {
        Text(
            text = "快速访问",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until maxSlots) {
                val item = items.getOrNull(i)
                if (item != null) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(i) }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Card(
                        onClick = onEmptyClick,
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolGrid(
    items: List<ToolItem>,
    columns: Int,
    onButtonClick: (ToolItem) -> Unit,
    onButtonLongClick: (ToolItem) -> Unit
) {
    val rows = items.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { item ->
                    ToolButton(
                        item = item,
                        onClick = { onButtonClick(item) },
                        onLongClick = { onButtonLongClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size < columns) {
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(
    item: ToolItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
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
}

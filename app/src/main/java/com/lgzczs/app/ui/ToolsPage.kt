package com.lgzczs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun ToolsPage(
    repository: ToolsRepository
) {
    val context = LocalContext.current

    val config = remember {
        ToolConfig(
            groups = listOf(
                ToolGroup(
                    id = "g1",
                    name = "测试分组",
                    order = 1,
                    hints = listOf("提示信息"),
                    buttons = listOf(
                        ToolItem(id = "b1", label = "按钮1", url = "https://example.com", badge = "热门", order = 1),
                        ToolItem(id = "b2", label = "按钮2", url = "https://example.com", badge = null, order = 2),
                        ToolItem(id = "b3", label = "按钮3", url = "https://example.com", badge = "New", order = 3),
                        ToolItem(id = "b4", label = "按钮4", url = "https://example.com", badge = null, order = 4),
                        ToolItem(id = "b5", label = "按钮5", url = "https://example.com", badge = null, order = 5)
                    )
                ),
                ToolGroup(
                    id = "g2",
                    name = "第二组",
                    order = 2,
                    hints = emptyList(),
                    buttons = listOf(
                        ToolItem(id = "b6", label = "按钮6", url = "https://example.com", badge = null, order = 1),
                        ToolItem(id = "b7", label = "按钮7很长很长很长很长很长很长", url = "https://example.com", badge = "推荐", order = 2)
                    )
                )
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        val sortedGroups = config.groups.sortedBy { it.order }
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
                                .clickable { UrlOpener.open(context, item.url) }
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
}

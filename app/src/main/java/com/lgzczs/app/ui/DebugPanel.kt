package com.lgzczs.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgzczs.app.util.LogEntry
import com.lgzczs.app.util.LogType
import com.lgzczs.app.util.WebViewDiagnostics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugPanel(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(WebViewDiagnostics.getLogs()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🪲 诊断 (${logs.size})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                TextButtonSmall("清空") {
                    WebViewDiagnostics.clear()
                    logs = emptyList()
                }
                TextButtonSmall("刷新") {
                    logs = WebViewDiagnostics.getLogs()
                }
                TextButtonSmall("复制") {
                    WebViewDiagnostics.copyToClipboard(context)
                }
                TextButtonSmall("关闭") {
                    onClose()
                }
            }

            // Log list
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs.reversed(), key = { "${it.timestamp}_${it.message.hashCode()}" }) { entry ->
                    LogEntryRow(entry)
                }
            }

            // Bottom hint
            Text(
                "连接电脑 → Chrome → chrome://inspect 可远程调试",
                color = Color(0x99FFFFFF),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val time = sdf.format(Date(entry.timestamp))
    val color = when (entry.type) {
        LogType.JS_ERROR, LogType.ERROR, LogType.HTTP_ERROR -> Color(0xFFFF4444)
        LogType.JS_WARN -> Color(0xFFFFBB33)
        LogType.JS_LOG, LogType.JS_DEBUG -> Color(0xFF99CC00)
        LogType.NETWORK_REQ -> Color(0xFF33B5E5)
        LogType.NETWORK_RESP -> Color(0xFFAA66CC)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "[${entry.type.name}] $time",
                color = color,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = entry.source,
            color = Color(0xAAFFFFFF),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = entry.message,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TextButtonSmall(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color(0xFF33B5E5), fontSize = 12.sp)
    }
}

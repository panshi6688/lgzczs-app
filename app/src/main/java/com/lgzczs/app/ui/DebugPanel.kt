package com.lgzczs.app.ui

import android.webkit.WebView
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
    webView: WebView? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(WebViewDiagnostics.getLogs()) }
    var running by remember { mutableStateOf(false) }

    fun refresh() {
        logs = WebViewDiagnostics.getLogs()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
                TextButtonSmall(if (running) "..." else "DOM") {
                    if (!running && webView != null) {
                        running = true
                        runDomDiagnostics(webView) { running = false; refresh() }
                    }
                }
                TextButtonSmall("清空") {
                    WebViewDiagnostics.clear()
                    logs = emptyList()
                }
                TextButtonSmall("刷新") {
                    refresh()
                }
                TextButtonSmall("复制") {
                    WebViewDiagnostics.copyToClipboard(context)
                }
                TextButtonSmall("关闭") {
                    onClose()
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs.reversed(), key = { "${it.timestamp}_${it.message.hashCode()}_${it.source.hashCode()}" }) { entry ->
                    LogEntryRow(entry)
                }
            }

            Text(
                "连接电脑 → Chrome → chrome://inspect 可远程调试",
                color = Color(0x99FFFFFF),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

private fun runDomDiagnostics(webView: WebView, onComplete: () -> Unit) {
    val scripts = listOf(
        "PageMeta" to """(function(){
  return JSON.stringify({
    readyState: document.readyState,
    viewport: innerWidth+'x'+innerHeight,
    scrollSize: document.documentElement.scrollWidth+'x'+document.documentElement.scrollHeight,
    url: location.href,
    title: document.title
  });
})()""",
        "BodyStructure" to """(function(){
  var b = document.body;
  if(!b) return 'NO_BODY';
  var r = { childCount: b.children.length, htmlLen: b.innerHTML.length, textLen: b.innerText.length, first5: [] };
  for(var i=0;i<Math.min(5,b.children.length);i++){
    var el=b.children[i];
    r.first5.push(el.tagName+(el.id?'#'+el.id:'')+'.'+(el.className||'').slice(0,35)+' visible='+(el.offsetParent!==null)+' text="'+(el.innerText||'').replace(/\s+/g,' ').slice(0,50)+'"');
  }
  return JSON.stringify(r);
})()""",
        "UniAppRoot" to """(function(){
  var app=document.querySelector('uni-app');
  if(!app) return 'NO_uni-app';
  var s=window.getComputedStyle(app);
  var r={
    childCount: app.children.length,
    textLen: app.innerText.length,
    display: s.display, visibility: s.visibility, opacity: s.opacity,
    w: s.width, h: s.height, overflow: s.overflow, position: s.position, zIndex: s.zIndex,
    children: []
  };
  for(var i=0;i<Math.min(3,app.children.length);i++){
    var c=app.children[i];
    var cs=window.getComputedStyle(c);
    r.children.push(c.tagName+(c.id?'#'+c.id:'')+'.'+(c.className||'').slice(0,25)+' d='+cs.display+' v='+cs.visibility+' o='+cs.opacity+' w='+cs.width+' h='+cs.height);
  }
  return JSON.stringify(r);
})()""",
        "Components" to """(function(){
  var results=[];
  var maxwidth=document.querySelector('.uni-app--maxwidth');
  if(!maxwidth) return 'NO_.uni-app--maxwidth';
  function describe(el,d){
    if(d>4||!el||!el.tagName)return;
    if(el.tagName==='SCRIPT'||el.tagName==='STYLE')return;
    var s=window.getComputedStyle(el);
    var text=(el.innerText||'').replace(/\s+/g,' ').slice(0,30);
    var info=el.tagName+(el.id?'#'+el.id:'')+'.'+(el.className||'').slice(0,20)+' d='+s.display+' v='+s.visibility+' o='+s.opacity+' w='+s.width+' h='+s.height+' text="'+text+'"';
    results.push(info);
    if(d<3) for(var i=0;i<el.children.length;i++) describe(el.children[i],d+1);
  }
  describe(maxwidth,0);
  return JSON.stringify(results.slice(0,30));
})()""",
        "ZeroHeight" to """(function(){
  var r=[];
  function walk(el,d){
    if(d>3||!el||!el.tagName)return;
    if(el.tagName!=='SCRIPT'&&el.tagName!=='STYLE'&&el.tagName!=='LINK'){
      var s=window.getComputedStyle(el);
      if(s.display!=='none'&&parseInt(s.height)===0&&parseInt(s.width)>0){
        r.push('ZERO-H: '+el.tagName+(el.id?'#'+el.id:'')+'.'+(el.className||'').slice(0,20)+' w='+s.width);
      }
      if(el.offsetParent===null&&el.tagName!=='HTML'&&el.tagName!=='BODY'){
        r.push('HIDDEN: '+el.tagName+(el.id?'#'+el.id:'')+'.'+(el.className||'').slice(0,20));
      }
    }
    for(var i=0;i<el.children.length;i++) walk(el.children[i],d+1);
  }
  walk(document.body,0);
  return JSON.stringify(r.length>0?r.slice(0,25):'none');
})()""",
        "ImageStatus" to """(function(){
  var imgs=document.getElementsByTagName('img');
  var total=imgs.length, loaded=0, failed=0, rendered=0;
  for(var i=0;i<imgs.length;i++){
    if(imgs[i].complete) loaded++;
    if(imgs[i].naturalWidth===0&&imgs[i].complete) failed++;
    if(imgs[i].naturalWidth>0) rendered++;
  }
  return JSON.stringify({total:total,loaded:loaded,failed:failed,rendered:rendered});
})()"""
    )

    var index = 0
    fun runNext() {
        if (index >= scripts.size) {
            onComplete()
            return
        }
        val (name, script) = scripts[index]
        index++
        webView.evaluateJavascript(script) { result ->
            WebViewDiagnostics.add(LogType.DOM_INSPECT, "DOM<$name>", result ?: "null")
            runNext()
        }
    }
    runNext()
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
        LogType.DOM_INSPECT -> Color(0xFF00BCD4)
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
            maxLines = 6,
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

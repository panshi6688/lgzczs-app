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

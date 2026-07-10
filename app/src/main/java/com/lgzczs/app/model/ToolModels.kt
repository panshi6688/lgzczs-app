package com.lgzczs.app.model

data class ToolConfig(
    val groups: List<ToolGroup>
)

data class ToolGroup(
    val id: String,
    val name: String,
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

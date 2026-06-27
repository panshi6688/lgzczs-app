package com.lgzczs.app.model

enum class PlatformStatus {
    NOT_LOGGED_IN,
    LOGGING_IN,
    LOGGED_IN,
    TOKEN_EXPIRED
}

data class PlatformState(
    val name: String,
    val status: PlatformStatus,
    val hasOrders: Boolean
)

enum class PollingEvent {
    NO_ORDERS,
    HAS_ORDERS,
    TOKEN_INVALID,
    ERROR
}

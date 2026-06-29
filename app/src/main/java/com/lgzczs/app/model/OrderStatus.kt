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

data class PollingEvent(
    val type: EventType,
    val orderIds: List<String> = emptyList()
) {
    enum class EventType { NO_ORDERS, HAS_ORDERS, TOKEN_INVALID, ERROR }

    companion object {
        val NO_ORDERS = PollingEvent(EventType.NO_ORDERS)
        val HAS_ORDERS = PollingEvent(EventType.HAS_ORDERS)
        val TOKEN_INVALID = PollingEvent(EventType.TOKEN_INVALID)
        val ERROR = PollingEvent(EventType.ERROR)
    }
}

package com.lgzczs.app.util

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("token_prefs", Context.MODE_PRIVATE)

    var huiToken: String?
        get() = prefs.getString("hui_token", null)
        set(value) = prefs.edit().putString("hui_token", value).apply()

    var youkaToken: String?
        get() = prefs.getString("youka_token", null)
        set(value) = prefs.edit().putString("youka_token", value).apply()

    var alertDialogEnabled: Boolean
        get() = prefs.getBoolean("alert_dialog_enabled", true)
        set(value) = prefs.edit().putBoolean("alert_dialog_enabled", value).apply()

    var notificationEnabled: Boolean
        get() = prefs.getBoolean("notification_enabled", true)
        set(value) = prefs.edit().putBoolean("notification_enabled", value).apply()

    var floatWindowEnabled: Boolean
        get() = prefs.getBoolean("float_window_enabled", true)
        set(value) = prefs.edit().putBoolean("float_window_enabled", value).apply()

    var floatToolsEnabled: Boolean
        get() = prefs.getBoolean("float_tools_enabled", false)
        set(value) = prefs.edit().putBoolean("float_tools_enabled", value).apply()

    var huiUsername: String?
        get() = prefs.getString("hui_username", null)
        set(value) = prefs.edit().putString("hui_username", value).apply()

    var huiPassword: String?
        get() = prefs.getString("hui_password", null)
        set(value) = prefs.edit().putString("hui_password", value).apply()

    var youkaUsername: String?
        get() = prefs.getString("youka_username", null)
        set(value) = prefs.edit().putString("youka_username", value).apply()

    var youkaPassword: String?
        get() = prefs.getString("youka_password", null)
        set(value) = prefs.edit().putString("youka_password", value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()

    var ringtoneUri: String?
        get() = prefs.getString("ringtone_uri", null)
        set(value) = prefs.edit().putString("ringtone_uri", value).apply()

    var hasUnviewedOrders: Boolean
        get() = prefs.getBoolean("has_unviewed_orders", false)
        set(value) = prefs.edit().putBoolean("has_unviewed_orders", value).apply()

    fun getNotifiedOrderIds(): Set<String> =
        prefs.getStringSet("notified_order_ids", emptySet()) ?: emptySet()

    fun addNotifiedOrderIds(ids: Set<String>) {
        val current = getNotifiedOrderIds().toMutableSet()
        current.addAll(ids)
        prefs.edit().putStringSet("notified_order_ids", current).apply()
    }

    fun clearHuiToken() {
        prefs.edit().remove("hui_token").apply()
    }

    fun clearYoukaToken() {
        prefs.edit().remove("youka_token").apply()
    }
}

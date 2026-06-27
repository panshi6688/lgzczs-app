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

    fun clearHuiToken() {
        prefs.edit().remove("hui_token").apply()
    }

    fun clearYoukaToken() {
        prefs.edit().remove("youka_token").apply()
    }
}

package com.lgzczs.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lgzczs.app.R
import com.lgzczs.app.model.ToolConfig
import com.lgzczs.app.model.ToolItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ToolsRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tools_cache", Context.MODE_PRIVATE)

    private val gson = Gson()

    companion object {
        var apiBaseUrl: String = "https://lgzc-toolsmenu.lghaomaauto.workers.dev"
        private const val PREFS_QUICK = "quick_access"
        private const val KEY_QUICK_PREFIX = "qa_"
        private const val MAX_QUICK_ACCESS = 4
    }

    suspend fun fetchButtons(): Result<ToolConfig> = withContext(Dispatchers.IO) {
        try {
            val url = "${apiBaseUrl}/api/buttons"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: $body")
            }

            val configType = object : TypeToken<ToolConfig>() {}.type
            val config: ToolConfig = gson.fromJson(body, configType)

            prefs.edit().putString("cached_config", body).apply()

            Result.success(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCachedConfig(): ToolConfig? {
        val json = prefs.getString("cached_config", null) ?: return null
        return try {
            val configType = object : TypeToken<ToolConfig>() {}.type
            gson.fromJson(json, configType)
        } catch (_: Exception) {
            null
        }
    }

    fun getQuickAccessButtons(): List<ToolItem?> {
        val result = mutableListOf<ToolItem?>()
        for (i in 0 until MAX_QUICK_ACCESS) {
            val json = prefs.getString("${KEY_QUICK_PREFIX}$i", null)
            if (json != null) {
                try {
                    val item = gson.fromJson(json, ToolItem::class.java)
                    result.add(item)
                } catch (_: Exception) {
                    result.add(null)
                }
            } else {
                result.add(null)
            }
        }
        return result
    }

    fun setQuickAccess(index: Int, item: ToolItem?) {
        if (index < 0 || index >= MAX_QUICK_ACCESS) return
        val editor = prefs.edit()
        if (item != null) {
            editor.putString("${KEY_QUICK_PREFIX}$index", gson.toJson(item))
        } else {
            editor.remove("${KEY_QUICK_PREFIX}$index")
        }
        editor.apply()
    }

    fun getMaxQuickAccess() = MAX_QUICK_ACCESS

    fun loadDefaultConfig(): ToolConfig? {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.tools_default_config)
            val json = inputStream.bufferedReader().use { it.readText() }
            val configType = object : TypeToken<ToolConfig>() {}.type
            gson.fromJson(json, configType)
        } catch (_: Exception) {
            null
        }
    }
}

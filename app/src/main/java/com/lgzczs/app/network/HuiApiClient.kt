package com.lgzczs.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lgzczs.app.model.PollingEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.concurrent.TimeUnit

class HuiApiClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun checkOrders(token: String): PollingEvent = withContext(Dispatchers.IO) {
        try {
            val now = Calendar.getInstance()
            now.set(Calendar.HOUR_OF_DAY, 0)
            now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            val startOfDay = now.timeInMillis / 1000
            val endOfDay = startOfDay + 86400 - 1

            val url = HttpUrl.Builder()
                .scheme("https")
                .host("public.kky.v3.supplier.kakayun.vip")
                .addPathSegments("sup/v2/order/list")
                .addQueryParameter("page", "1")
                .addQueryParameter("limit", "20")
                .addQueryParameter("key", "")
                .addQueryParameter("keytype", "2")
                .addQueryParameter("status", "pending")
                .addQueryParameter("starttime", startOfDay.toString())
                .addQueryParameter("endtime", endOfDay.toString())
                .addQueryParameter("sort_mode", "0")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Referer", "https://sup.78k.cn/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (response.code == 401) {
                return@withContext PollingEvent.TOKEN_INVALID
            }

            val body = response.body?.string()
            if (body == null) return@withContext PollingEvent.ERROR

            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val json: Map<String, Any> = gson.fromJson(body, mapType)
            val count = (json["count"] as? Double)?.toInt() ?: 0

            if (count > 0) PollingEvent.HAS_ORDERS else PollingEvent.NO_ORDERS
        } catch (e: Exception) {
            PollingEvent.ERROR
        }
    }
}

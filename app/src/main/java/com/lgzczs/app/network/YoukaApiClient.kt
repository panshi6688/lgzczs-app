package com.lgzczs.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lgzczs.app.model.PollingEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class YoukaApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val KEY = "7aca3c37e3745f8768b0e559797d521f"
        private const val API_BASE = "http://supplier.ukayun.cn"
        private const val CHARSET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun generateNonce(): String {
        return (1..5).map { CHARSET[(Math.random() * CHARSET.length).toInt()] }.joinToString("")
    }

    private fun decryptAes(ciphertext: String): String {
        val raw = Base64.getDecoder().decode(ciphertext)
        val keySpec = SecretKeySpec(KEY.toByteArray(Charsets.UTF_8), "AES")
        val ivBytes = MessageDigest.getInstance("MD5").digest(KEY.toByteArray(Charsets.UTF_8))
        val ivHex = ivBytes.joinToString("") { "%02x".format(it) }.substring(0, 16)
        val ivSpec = IvParameterSpec(ivHex.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return String(cipher.doFinal(raw), Charsets.UTF_8)
    }

    suspend fun getServerTimestamp(): Long = withContext(Dispatchers.IO) {
        val url = "${API_BASE}/spa/auth/timestamp"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty timestamp response")
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val json: Map<String, Any> = gson.fromJson(body, mapType)
        val data = json["data"] as? Map<*, *> ?: throw Exception("Invalid timestamp response")
        (data["time"] as? Number)?.toLong() ?: throw Exception("Missing time field")
    }

    suspend fun checkOrders(adminToken: String): PollingEvent = withContext(Dispatchers.IO) {
        try {
            val timestamp = getServerTimestamp()
            val nonce = generateNonce()
            val salt = md5(timestamp.toString().takeLast(5) + nonce)

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val timeRange = "${today} 00:00:00~${today} 23:59:59"

            val params = mapOf(
                "limit" to "15",
                "page" to "1",
                "status" to "1",
                "time_range" to timeRange
            )
            val sortedParams = params.toSortedMap()
            val paramStr = sortedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            val signStr =
                "${paramStr}&nonce=${nonce}&salt=${salt}&timestamp=${timestamp}&version=2"
            val sign = sha256(signStr)

            val url = HttpUrl.Builder()
                .scheme("http")
                .host("supplier.ukayun.cn")
                .addPathSegments("spa/order")
                .addQueryParameter("limit", "15")
                .addQueryParameter("page", "1")
                .addQueryParameter("status", "1")
                .addQueryParameter("time_range", timeRange)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $adminToken")
                .header("nonce", nonce)
                .header("timestamp", timestamp.toString())
                .header("sign", sign)
                .header("version", "2")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (response.code == 401 || response.code == 500) {
                return@withContext PollingEvent.TOKEN_INVALID
            }

            val body = response.body?.string() ?: return@withContext PollingEvent.ERROR

            val decrypted = try {
                val mapType = object : TypeToken<Map<String, Any>>() {}.type
                val json: Map<String, Any> = gson.fromJson(body, mapType)
                if (json.containsKey("data") && json["data"] is String) {
                    decryptAes(json["data"] as String)
                } else {
                    decryptAes(body)
                }
            } catch (e: Exception) {
                decryptAes(body)
            }

            val decryptedMapType = object : TypeToken<Map<String, Any>>() {}.type
            val decryptedJson: Map<String, Any> =
                gson.fromJson(decrypted, decryptedMapType)
            val count = (decryptedJson["count"] as? Number)?.toInt() ?: 0

            if (count > 0) PollingEvent.HAS_ORDERS else PollingEvent.NO_ORDERS
        } catch (e: Exception) {
            PollingEvent.ERROR
        }
    }
}

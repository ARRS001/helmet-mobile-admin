package com.helmet.mobileadmin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiService {
    var serverBase: String = ""
        private set
    private var _client: OkHttpClient? = null
    private var _clientHost: String? = null
    private val client: OkHttpClient
        get() {
            val host = serverBase.removePrefix("https://").removePrefix("http://").removeSuffix("/")
            if (_client == null || _clientHost != host) {
                _client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
                    .hostnameVerifier { hn, _ -> hn == host }
                    .build()
                _clientHost = host
            }
            return _client!!
        }
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun configure(serverUrl: String) {
        serverBase = serverUrl.trim().removeSuffix("/")
        if (!serverBase.startsWith("http")) serverBase = "https://$serverBase"
        _client = null // force rebuild
    }

    fun loadServerUrl(context: android.content.Context): String {
        serverBase = context.getSharedPreferences("admin_prefs", android.content.Context.MODE_PRIVATE)
            .getString("server_url", "") ?: ""
        return serverBase
    }

    fun saveServerUrl(context: android.content.Context) {
        context.getSharedPreferences("admin_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("server_url", serverBase).apply()
    }

    var authToken: String = ""
    var adminId: String = ""
    var username: String = ""
    var level: Int = -1
    var permissions: List<String> = emptyList()

    data class ApiResult(val code: Int, val msg: String?, val data: Map<String, Any>?)

    suspend fun request(method: String, path: String, body: Any? = null, useAuth: Boolean = true): ApiResult {
        return withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url("$serverBase$path")
                if (useAuth && authToken.isNotEmpty()) {
                    builder.addHeader("Authorization", "Bearer $authToken")
                }
                if (body != null) {
                    val json = gson.toJson(body)
                    builder.method(method, json.toRequestBody(JSON))
                } else {
                    builder.method(method, null)
                }
                val res = client.newCall(builder.build()).execute()
                val resBody = res.body?.string() ?: "{}"
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(resBody, type)
                @Suppress("UNCHECKED_CAST")
                ApiResult(
                    code = (map["code"] as? Double)?.toInt() ?: -1,
                    msg = map["msg"] as? String,
                    data = map["data"] as? Map<String, Any>
                )
            } catch (e: IOException) {
                ApiResult(-1, "网络错误: ${e.message}", null)
            } catch (e: Exception) {
                ApiResult(-1, "解析错误: ${e.message}", null)
            }
        }
    }

    suspend fun login(username: String, password: String): ApiResult {
        val r = request("POST", "/api/auth/login", mapOf("username" to username, "password" to password), useAuth = false)
        if (r.code == 0 && r.data != null) {
            authToken = r.data["token"] as? String ?: ""
            adminId = r.data["adminId"] as? String ?: ""
            this.username = username
            level = (r.data["level"] as? Double)?.toInt() ?: -1
            @Suppress("UNCHECKED_CAST")
            permissions = (r.data["permissions"] as? List<String>) ?: emptyList()
        }
        return r
    }

    // ── Dashboard ──
    suspend fun dashboard(): ApiResult = request("GET", "/api/dashboard")
    suspend fun deviceStats(): ApiResult = request("GET", "/api/devices/stats/overview")
    suspend fun alarmStats(): ApiResult = request("GET", "/api/alarms/stats")
    suspend fun activeStreams(): ApiResult = request("GET", "/api/active-streams")

    // ── Devices ──
    suspend fun deviceList(params: Map<String, String> = emptyMap()): ApiResult {
        val qs = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return request("GET", "/api/devices?$qs")
    }
    suspend fun deviceAdd(data: Map<String, String>): ApiResult = request("POST", "/api/devices", data)
    suspend fun deviceDetail(deviceId: String): ApiResult = request("GET", "/api/devices/$deviceId")
    suspend fun deviceDelete(deviceId: String): ApiResult = request("DELETE", "/api/devices/$deviceId")
    suspend fun deviceAssign(deviceId: String, userId: String): ApiResult =
        request("POST", "/api/devices/$deviceId/assign", mapOf("userId" to userId))
    suspend fun deviceUnassign(deviceId: String): ApiResult =
        request("POST", "/api/devices/$deviceId/unassign")
    suspend fun assignableUsers(): ApiResult = request("GET", "/api/devices/assignable/users")

    // ── Accounts ──
    suspend fun subAdmins(): ApiResult = request("GET", "/api/auth/subadmins")
    suspend fun createSubAdmin(data: Map<String, String>): ApiResult =
        request("POST", "/api/auth/subadmins", data)
    suspend fun deleteSubAdmin(id: String): ApiResult = request("DELETE", "/api/auth/subadmins/$id")
    suspend fun availablePermissions(): ApiResult = request("GET", "/api/auth/permissions")

    // ── Alarms ──
    suspend fun alarmList(params: Map<String, String> = emptyMap()): ApiResult {
        val qs = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return request("GET", "/api/alarms?$qs")
    }
    suspend fun alarmHandle(id: String): ApiResult = request("PUT", "/api/alarms/$id/handle")

    // ── Playback ──
    suspend fun recordings(deviceId: String, period: String): ApiResult =
        request("GET", "/api/playback/list/$deviceId?period=$period")
    suspend fun playbackUrl(deviceId: String, date: String, file: String): ApiResult =
        request("GET", "/api/playback/url?deviceId=$deviceId&date=$date&file=$file")
    suspend fun snapshots(deviceId: String): ApiResult =
        request("GET", "/api/screenshots/$deviceId")
    suspend fun callRecords(deviceId: String): ApiResult =
        request("GET", "/api/call-records/$deviceId")
}

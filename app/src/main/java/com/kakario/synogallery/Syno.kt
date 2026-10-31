package com.kakario.synogallery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

/** 시놀로지 포토(Synology Photos) Web API */
object Syno {
    @Volatile
    lateinit var client: OkHttpClient
        private set

    @Volatile
    var sid: String? = null
        private set

    /** 이번 실행 동안만 들고 있는 비밀번호 (세션 만료 시 재로그인용) */
    private var sessionPassword: String = ""

    class SynoException(val code: Int, message: String) : Exception(message)

    fun init() = rebuildClient()

    fun rebuildClient() {
        val b = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
        if (Settings.trustAll) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), SecureRandom())
            b.sslSocketFactory(ctx.socketFactory, tm).hostnameVerifier { _, _ -> true }
        }
        client = b.build()
    }

    fun normalizeHost(raw: String): String {
        var h = raw.trim().trimEnd('/')
        if (h.isEmpty()) return h
        if (!h.startsWith("http://") && !h.startsWith("https://")) {
            h = if (h.endsWith(":5000")) "http://$h" else "https://$h"
        }
        return h
    }

    private val base get() = normalizeHost(Settings.host)
    private val prefix get() = if (Settings.sharedSpace) "SYNO.FotoTeam" else "SYNO.Foto"

    // ───────────────── 호출 공통 ─────────────────

    private fun entryUrl(host: String, params: Map<String, String>, withSid: Boolean): HttpUrl {
        val b = "$host/webapi/entry.cgi".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        if (withSid) sid?.let { b.addQueryParameter("_sid", it) }
        return b.build()
    }

    private fun post(host: String, params: Map<String, String>, withSid: Boolean = true): JSONObject {
        val form = FormBody.Builder()
        params.forEach { (k, v) -> form.add(k, v) }
        if (withSid) sid?.let { form.add("_sid", it) }
        val req = Request.Builder().url("$host/webapi/entry.cgi").post(form.build()).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw SynoException(-res.code, "서버 응답 오류 (HTTP ${res.code})")
            val body = res.body?.string().orEmpty()
            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw SynoException(-1, "시놀로지 포토 응답이 아니에요. 주소·포트를 확인해 주세요.")
            }
            if (!json.optBoolean("success")) {
                val code = json.optJSONObject("error")?.optInt("code") ?: -1
                throw SynoException(code, errorMessage(code))
            }
            return json.optJSONObject("data") ?: JSONObject()
        }
    }

    private fun errorMessage(code: Int) = when (code) {
        400 -> "아이디 또는 비밀번호가 틀렸어요."
        401 -> "비활성화된 계정이에요."
        402 -> "이 계정은 권한이 없어요."
        403 -> "2단계 인증 코드(OTP)를 입력해 주세요."
        404 -> "OTP 코드가 맞지 않아요."
        407 -> "로그인 시도가 많아 IP가 차단됐어요."
        105 -> "권한이 없어요. 시놀로지 포토 앱이 설치·허용됐는지 확인해 주세요."
        102, 103, 104 -> "시놀로지 포토(Synology Photos)를 찾지 못했어요."
        106, 107, 119 -> "로그인이 만료됐어요. 다시 로그인해 주세요."
        else -> "오류가 발생했어요 (코드 $code)"
    }

    private fun isSessionError(e: Exception) = e is SynoException && e.code in listOf(106, 107, 119)

    /** 세션이 끊기면 한 번 재로그인 후 다시 시도 */
    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            val pw = sessionPassword.ifEmpty { Settings.password }
            if (isSessionError(e) && pw.isNotEmpty()) {
                loginBlocking(Settings.host, Settings.account, pw, null)
                block()
            } else throw friendly(e)
        }
    }

    private fun friendly(e: Exception): Exception = when (e) {
        is SynoException -> e
        is SSLException -> Exception("보안 인증서 오류예요. 로그인 화면에서 '인증서 무시'를 켜 보세요.")
        is java.net.UnknownHostException -> Exception("NAS 주소를 찾을 수 없어요.")
        is java.net.ConnectException, is java.net.SocketTimeoutException -> Exception("NAS에 연결할 수 없어요. 주소·포트·네트워크를 확인해 주세요.")
        else -> e
    }

    // ───────────────── 로그인 ─────────────────

    private fun loginBlocking(host: String, account: String, password: String, otp: String?) {
        val params = mutableMapOf(
            "api" to "SYNO.API.Auth",
            "version" to "6",
            "method" to "login",
            "account" to account,
            "passwd" to password,
            "session" to "SynologyPhotos",
            "format" to "sid",
        )
        if (!otp.isNullOrBlank()) params["otp_code"] = otp.trim()
        val data = post(normalizeHost(host), params, withSid = false)
        sid = data.optString("sid").ifEmpty { throw SynoException(-1, "로그인 응답에 세션이 없어요.") }
        sessionPassword = password
    }

    suspend fun login(host: String, account: String, password: String, otp: String?) =
        withContext(Dispatchers.IO) {
            try {
                loginBlocking(host, account, password, otp)
            } catch (e: Exception) {
                throw friendly(e)
            }
        }

    fun logout() {
        sid = null
        sessionPassword = ""
    }

    // ───────────────── 목록 ─────────────────

    private val itemExtras = JSONArray(listOf("thumbnail", "resolution", "video_meta")).toString()

    suspend fun listItems(source: Source, offset: Int, limit: Int): List<Item> = call {
        val p = mutableMapOf(
            "api" to "$prefix.Browse.Item",
            "version" to "1",
            "method" to "list",
            "offset" to "$offset",
            "limit" to "$limit",
            "additional" to itemExtras,
            "sort_by" to Settings.sortBy,
            "sort_direction" to if (Settings.sortDesc) "desc" else "asc",
        )
        when (source) {
            is AlbumSource -> p["album_id"] = "${source.id}"
            is FolderSource -> p["folder_id"] = "${source.id}"
            AllSource -> {}
        }
        val filter = Settings.mediaFilter
        val data = if (filter != "all") {
            try {
                post(base, p + ("type" to filter))
            } catch (e: SynoException) {
                if (e.code in listOf(106, 107, 119)) throw e
                post(base, p) // 서버가 type 필터를 모르면 필터 없이
            }
        } else post(base, p)
        val arr = data.optJSONArray("list") ?: JSONArray()
        (0 until arr.length()).map { parseItem(arr.getJSONObject(it)) }
    }

    private fun parseItem(o: JSONObject): Item {
        val add = o.optJSONObject("additional")
        val th = add?.optJSONObject("thumbnail")
        val res = add?.optJSONObject("resolution")
        val vm = add?.optJSONObject("video_meta")
        val id = o.optLong("id")
        return Item(
            id = id,
            filename = o.optString("filename"),
            time = o.optLong("time"),
            type = o.optString("type", "photo"),
            size = o.optLong("filesize"),
            width = res?.optInt("width") ?: 0,
            height = res?.optInt("height") ?: 0,
            cacheKey = th?.optString("cache_key").orEmpty(),
            thumbUnitId = th?.optLong("unit_id", id) ?: id,
            hasXl = th?.optString("xl") == "ready",
            duration = vm?.optLong("duration") ?: 0L,
        )
    }

    suspend fun listAlbums(): List<Album> = call {
        val out = mutableListOf<Album>()
        var offset = 0
        while (true) {
            val data = post(
                base, mapOf(
                    "api" to "SYNO.Foto.Browse.Album",
                    "version" to "1",
                    "method" to "list",
                    "offset" to "$offset",
                    "limit" to "500",
                    "additional" to JSONArray(listOf("thumbnail")).toString(),
                )
            )
            val arr = data.optJSONArray("list") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val th = o.optJSONObject("additional")?.optJSONObject("thumbnail")
                out += Album(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    count = o.optInt("item_count"),
                    cacheKey = th?.optString("cache_key").orEmpty(),
                    thumbUnitId = th?.optLong("unit_id") ?: 0L,
                )
            }
            if (arr.length() < 500) break
            offset += 500
        }
        out
    }

    suspend fun rootFolderId(): Long = call {
        try {
            val data = post(base, mapOf("api" to "$prefix.Browse.Folder", "version" to "1", "method" to "get"))
            data.optJSONObject("folder")?.optLong("id") ?: 0L
        } catch (e: SynoException) {
            if (e.code in listOf(106, 107, 119)) throw e
            0L
        }
    }

    suspend fun listFolders(parentId: Long): List<Folder> = call {
        val data = post(
            base, mapOf(
                "api" to "$prefix.Browse.Folder",
                "version" to "1",
                "method" to "list",
                "id" to "$parentId",
                "offset" to "0",
                "limit" to "1000",
                "sort_by" to "filename",
                "sort_direction" to "asc",
            )
        )
        val arr = data.optJSONArray("list") ?: JSONArray()
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Folder(o.optLong("id"), o.optString("name"))
        }
    }

    // ───────────────── 이미지·영상 주소 ─────────────────

    fun thumbUrl(unitId: Long, cacheKey: String, size: String, team: Boolean = Settings.sharedSpace): String =
        entryUrl(
            base, mapOf(
                "api" to (if (team) "SYNO.FotoTeam" else "SYNO.Foto") + ".Thumbnail",
                "version" to "1",
                "method" to "get",
                "mode" to "download",
                "id" to "$unitId",
                "type" to "unit",
                "size" to size,
                "cache_key" to cacheKey,
            ), withSid = true
        ).toString()

    fun thumbKey(unitId: Long, cacheKey: String, size: String) = "t_${unitId}_${cacheKey}_$size"

    fun originalUrl(item: Item): String =
        entryUrl(
            base, mapOf(
                "api" to "$prefix.Download",
                "version" to "1",
                "method" to "download",
                "unit_id" to "[${item.id}]",
                "cache_key" to "\"${item.cacheKey}\"",
            ), withSid = true
        ).toString()
}

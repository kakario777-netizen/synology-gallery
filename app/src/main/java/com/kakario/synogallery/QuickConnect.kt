package com.kakario.synogallery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * QuickConnect ID → 실제 접속 주소 찾기.
 * 순서: 내부망(같은 와이파이) → 외부(포트포워딩/DDNS) → 시놀로지 릴레이
 */
object QuickConnect {

    data class Resolved(val base: String, val route: String, val insecure: Boolean)

    private data class Cand(val url: String, val route: String, val insecure: Boolean)

    private val JSON = "application/json".toMediaType()
    private val ID_RE = Regex("^[A-Za-z0-9][A-Za-z0-9-]*$")
    private val PATH_RE = Regex("^(?:www\\.)?quickconnect\\.(?:to|cn)/([A-Za-z0-9-]+)", RegexOption.IGNORE_CASE)
    private val SUB_RE = Regex("^([A-Za-z0-9-]+)\\.quickconnect\\.(?:to|cn)$", RegexOption.IGNORE_CASE)

    /** 입력값이 QuickConnect ID면 ID를, 아니면 null */
    fun extractId(raw: String): String? {
        val s = raw.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (s.isEmpty()) return null
        PATH_RE.find(s)?.let { return it.groupValues[1] }
        SUB_RE.find(s)?.let { return it.groupValues[1] }
        return if (ID_RE.matches(s)) s else null
    }

    private val api = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 후보 주소 확인용 (IP 주소는 인증서 이름이 안 맞으므로 검증 대신 ezid로 확인) */
    private val ping: OkHttpClient by lazy {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(tm), SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(ctx.socketFactory, tm)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private fun req(cmd: String, id: String, svc: String) = JSONObject()
        .put("version", 1)
        .put("command", cmd)
        .put("stop_when_error", false)
        .put("stop_when_success", false)
        .put("id", svc)
        .put("serverID", id)
        .put("is_gofile", false)
        .put("path", "")

    private fun serv(host: String, body: JSONArray): JSONArray? = try {
        val r = Request.Builder()
            .url("https://$host/Serv.php")
            .post(body.toString().toRequestBody(JSON))
            .build()
        api.newCall(r).execute().use { res ->
            val text = res.body?.string().orEmpty().trim()
            if (text.startsWith("[")) JSONArray(text) else JSONArray().put(JSONObject(text))
        }
    } catch (e: Exception) {
        null
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun check(url: String, serverId: String): Boolean {
        return try {
            val r = Request.Builder().url("$url/webman/pingpong.cgi?action=cors&quickconnect=true").build()
            ping.newCall(r).execute().use { res ->
                if (!res.isSuccessful) {
                    false
                } else {
                    val j = JSONObject(res.body?.string().orEmpty())
                    j.optBoolean("success") && (serverId.isEmpty() || j.optString("ezid") == md5(serverId))
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isPrivate(ip: String): Boolean =
        ip.startsWith("10.") || ip.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(ip)

    private fun valid(s: String?) = !s.isNullOrBlank() && s != "NULL"

    suspend fun resolve(id: String): Resolved = withContext(Dispatchers.IO) {
        // 1) 서버 정보 받기 (다른 지역 서버로 안내되면 따라감)
        val body = JSONArray()
            .put(req("get_server_info", id, "dsm_portal_https"))
            .put(req("get_server_info", id, "dsm_portal"))
        val queue = ArrayDeque(listOf("global.quickconnect.to"))
        val tried = mutableSetOf<String>()
        var info: JSONArray? = null
        while (queue.isNotEmpty()) {
            val h = queue.removeFirst()
            if (!tried.add(h)) continue
            val r = serv(h, body) ?: continue
            val first = r.optJSONObject(0) ?: continue
            if (first.optInt("errno", -1) == 0 && first.has("server")) {
                info = r
                break
            }
            first.optJSONArray("sites")?.let { s -> for (i in 0 until s.length()) queue.addFirst(s.getString(i)) }
        }
        val r = info ?: throw Exception(
            "QuickConnect ID를 찾지 못했어요.\nID가 맞는지, DSM에서 QuickConnect가 켜져 있는지 확인해 주세요."
        )

        val hs = r.getJSONObject(0)
        val hp = r.optJSONObject(1)
        val server = hs.getJSONObject("server")
        val serverId = server.optString("serverID")
        val svc = hs.optJSONObject("service")
        val port = svc?.optInt("port", 5001)?.takeIf { it > 0 } ?: 5001
        val ext = svc?.optInt("ext_port", 0) ?: 0
        val hsvc = hp?.optJSONObject("service")
        val hport = hsvc?.optInt("port", 5000)?.takeIf { it > 0 } ?: 5000
        val hext = hsvc?.optInt("ext_port", 0) ?: 0
        val smart = hs.optJSONObject("smartdns")
        val controlHost = hs.optJSONObject("env")?.optString("control_host").orEmpty()

        // 2) 후보 주소 (앞쪽일수록 우선)
        val c = mutableListOf<Cand>()
        smart?.optJSONArray("lan")?.let { a ->
            for (i in 0 until a.length()) c += Cand("https://${a.getString(i)}:$port", "내부망", false)
        }
        server.optJSONArray("interface")?.let { a ->
            for (i in 0 until a.length()) {
                val ip = a.getJSONObject(i).optString("ip")
                if (valid(ip) && isPrivate(ip)) {
                    c += Cand("https://$ip:$port", "내부망", true)
                    c += Cand("http://$ip:$hport", "내부망", false)
                }
            }
        }
        val wanPorts = listOf(ext, port).filter { it > 0 }.distinct()
        smart?.optString("host")?.takeIf { valid(it) }?.let { h ->
            wanPorts.forEach { p -> c += Cand("https://$h:$p", "외부", false) }
        }
        listOf(server.optString("ddns"), server.optString("fqdn")).filter { valid(it) }.distinct().forEach { h ->
            wanPorts.forEach { p -> c += Cand("https://$h:$p", "외부", false) }
        }
        server.optJSONObject("external")?.optString("ip")?.takeIf { valid(it) }?.let { ip ->
            wanPorts.forEach { p -> c += Cand("https://$ip:$p", "외부", true) }
            listOf(hext, hport).filter { it > 0 }.distinct().forEach { p -> c += Cand("http://$ip:$p", "외부", false) }
        }

        // 3) 동시에 확인하고, 응답한 것 중 우선순위가 가장 높은 것
        val ok = coroutineScope {
            c.distinctBy { it.url }.map { cand ->
                async { withTimeoutOrNull(5000) { if (check(cand.url, serverId)) cand else null } }
            }.awaitAll()
        }.filterNotNull().firstOrNull()
        if (ok != null) return@withContext Resolved(ok.url, ok.route, ok.insecure)

        // 4) 직접 연결이 안 되면 시놀로지 릴레이
        val tunnel = JSONArray().put(req("request_tunnel", id, "dsm_portal_https"))
        for (h in listOf(controlHost, "global.quickconnect.to").filter { it.isNotBlank() }.distinct()) {
            val t = serv(h, tunnel)?.optJSONObject(0) ?: continue
            val s = t.optJSONObject("service") ?: continue
            val rp = s.optInt("relay_port", 0).takeIf { it > 0 } ?: 443
            val relay = mutableListOf<Cand>()
            s.optString("relay_dn").takeIf { valid(it) }?.let { relay += Cand("https://$it:$rp", "릴레이", false) }
            s.optString("relay_ip").takeIf { valid(it) }?.let { relay += Cand("https://$it:$rp", "릴레이", true) }
            relay.firstOrNull { check(it.url, serverId) }?.let {
                return@withContext Resolved(it.url, it.route, it.insecure)
            }
        }
        throw Exception("QuickConnect로 NAS에 연결하지 못했어요.\nNAS가 켜져 있고 인터넷에 연결돼 있는지 확인해 주세요.")
    }
}

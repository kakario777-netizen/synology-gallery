package com.kakario.synogallery

import android.content.Context
import android.content.SharedPreferences
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 시놀로지가 영어로 주는 장소 이름을 한글로 바꿈.
 * 그 장소 사진 한 장의 위치를 폰의 지도 기능(Geocoder)으로 한글·영어 두 번 찾아보고,
 * 영어 이름과 같은 칸(시·도·구·나라)을 한글 결과에서 가져온다. 결과는 저장해 둠.
 */
object PlaceNames {
    private lateinit var ctx: Context
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        ctx = c.applicationContext
        sp = ctx.getSharedPreferences("place_names_ko", Context.MODE_PRIVATE)
    }

    fun cached(id: Long, english: String): String? = sp.getString("${id}_$english", null)

    suspend fun korean(id: Long, english: String): String = withContext(Dispatchers.IO) {
        cached(id, english)?.let { return@withContext it }
        val name = try {
            countryKo(english) ?: run {
                val item = Syno.firstInPlace(id)
                val lat = item?.lat
                val lon = item?.lon
                if (lat != null && lon != null) fromCoords(lat, lon, english) else null
            }
        } catch (_: Exception) {
            null
        }
        if (name != null) sp.edit().putString("${id}_$english", name).apply()
        name ?: english
    }

    /** 나라 이름이면 바로 한글 나라 이름 */
    private fun countryKo(english: String): String? {
        val e = english.trim().lowercase()
        for (code in Locale.getISOCountries()) {
            val loc = Locale("", code)
            if (loc.getDisplayCountry(Locale.ENGLISH).lowercase() == e) return loc.getDisplayCountry(Locale.KOREAN)
        }
        return when (e) {
            "korea", "republic of korea", "korea, republic of" -> "대한민국"
            "usa", "united states of america" -> "미국"
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun fromCoords(lat: Double, lon: Double, english: String): String? {
        if (!Geocoder.isPresent()) return null
        val ko = Geocoder(ctx, Locale.KOREAN).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
        val en = Geocoder(ctx, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull()
        val fields: List<(Address) -> String?> = listOf(
            { it.locality }, { it.subLocality }, { it.subAdminArea }, { it.adminArea },
            { it.thoroughfare }, { it.featureName }, { it.countryName },
        )
        val target = norm(english)
        if (en != null) {
            for (f in fields) {
                val e = f(en) ?: continue
                val n = norm(e)
                if (n.isNotEmpty() && (n == target || n.contains(target) || target.contains(n))) {
                    f(ko)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        // 못 맞추면 가장 대표적인 이름(시 → 도 → 나라)
        return ko.locality ?: ko.subAdminArea ?: ko.adminArea ?: ko.countryName
    }

    private fun norm(s: String) = s.lowercase()
        .replace("-si", "").replace("-gun", "").replace("-gu", "").replace("-do", "")
        .replace(" city", "").replace(" province", "").replace(" county", "")
        .replace("[^a-z]".toRegex(), "")
}

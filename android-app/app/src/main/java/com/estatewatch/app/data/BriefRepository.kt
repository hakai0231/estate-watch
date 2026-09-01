package com.estatewatch.app.data

import android.content.Context
import com.estatewatch.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 브리핑을 어디서 읽어올지 한 군데서 결정한다.
 *
 *   1) 내려받아 저장해 둔 최신본 (filesDir/brief.json)
 *   2) 없으면 APK 에 같이 넣어둔 스냅샷 (assets/brief.json)
 *
 * 새로고침에 실패해도 기존에 보던 내용은 그대로 남는다.
 */
class BriefRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("estate-watch", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "brief.json")

    var briefUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BRIEF_URL
        set(value) = prefs.edit().putString(KEY_URL, value.trim()).apply()

    val lastSyncedAt: Long get() = prefs.getLong(KEY_SYNCED, 0L)

    val isUsingBundledSnapshot: Boolean get() = !cacheFile.exists()

    fun load(): Brief {
        val raw = if (cacheFile.exists()) runCatching { cacheFile.readText() }.getOrNull() else null
        val text = raw ?: runCatching {
            context.assets.open(BUNDLED).bufferedReader().use { it.readText() }
        }.getOrNull()
        return text?.let { runCatching { Brief.parse(it) }.getOrDefault(Brief.EMPTY) } ?: Brief.EMPTY
    }

    /** 네트워크에서 새로 받아온다. 성공하면 저장하고 Brief 를, 실패하면 사유를 돌려준다. */
    fun refresh(): Result<Brief> = runCatching {
        val connection = (URL(briefUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("서버 응답 ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val brief = Brief.parse(body)          // 먼저 파싱해 보고, 멀쩡할 때만 저장한다
            if (brief.isEmpty) throw IllegalStateException("받아온 브리핑이 비어 있습니다")
            cacheFile.writeText(body)
            prefs.edit().putLong(KEY_SYNCED, System.currentTimeMillis()).apply()
            brief
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val KEY_URL = "brief_url"
        const val KEY_SYNCED = "synced_at"
        const val BUNDLED = "brief.json"
    }
}

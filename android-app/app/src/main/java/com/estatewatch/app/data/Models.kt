package com.estatewatch.app.data

import org.json.JSONArray
import org.json.JSONObject

/** 브리핑 한 부. 뉴스 탭 + 청약홈 탭에 필요한 모든 것이 여기 들어 있다. */
data class Brief(
    val date: String,
    val newsUpdatedAt: String,
    val applyUpdatedAt: String,
    val editorNote: String,
    val news: List<NewsItem>,
    val results: List<ApplyResult>,
    val upcoming: List<Upcoming>
) {
    val isEmpty: Boolean get() = news.isEmpty() && results.isEmpty() && upcoming.isEmpty()

    companion object {
        val EMPTY = Brief("", "", "", "", emptyList(), emptyList(), emptyList())

        fun parse(raw: String): Brief {
            val root = JSONObject(raw)
            val news = root.optJSONObject("news") ?: JSONObject()
            val apply = root.optJSONObject("apply") ?: JSONObject()
            return Brief(
                date = news.optString("date"),
                newsUpdatedAt = news.optString("updatedAt"),
                applyUpdatedAt = apply.optString("updatedAt"),
                editorNote = news.optString("editorNote"),
                news = news.optJSONArray("items").map { NewsItem.parse(it) },
                results = apply.optJSONArray("results").map { ApplyResult.parse(it) },
                upcoming = apply.optJSONArray("upcoming").map { Upcoming.parse(it) }
            )
        }
    }
}

data class NewsItem(
    val rank: Int,
    val category: String,
    val title: String,
    val outlet: String,
    val publishedAt: String,
    val byline: String,
    val summary: String,
    val points: List<String>,
    val body: List<String>,
    val url: String
) {
    companion object {
        fun parse(o: JSONObject) = NewsItem(
            rank = o.optInt("rank"),
            category = o.optString("category"),
            title = o.optString("title"),
            outlet = o.optString("outlet"),
            publishedAt = o.optString("publishedAt"),
            byline = o.optString("byline"),
            summary = o.optString("summary"),
            points = o.optJSONArray("points").strings(),
            body = o.optJSONArray("body").strings(),
            url = o.optString("url")
        )
    }
}

/** 주택형 한 줄. 경쟁률은 미달이면 null 이다. */
data class UnitType(
    val type: String,
    val units: Int,
    val generalUnits: Int,
    val price: String,
    val pyeongPrice: String,
    val rate: Double?
) {
    companion object {
        fun parse(o: JSONObject) = UnitType(
            type = o.optString("type"),
            units = o.optInt("units"),
            generalUnits = o.optInt("generalUnits"),
            price = o.optString("price"),
            pyeongPrice = o.optString("pyeongPrice"),
            rate = o.optDoubleOrNull("rate")
        )
    }
}

/** 마감된 청약의 성적표. */
data class ApplyResult(
    val name: String,
    val region: String,
    val address: String,
    val builder: String,
    val receipt: String,
    val mainType: String,
    val mainPrice: String,
    val mainPricePyeong: String,
    val totalUnits: Int,
    val generalUnits: Int,
    val applicants: Int,
    val avgRate: Double?,
    val maxRate: Double?,
    val verdict: String,
    val tone: String,
    val byType: List<UnitType>,
    val detailUrl: String,
    val lat: Double?,
    val lon: Double?
) {
    companion object {
        fun parse(o: JSONObject) = ApplyResult(
            name = o.optString("name"),
            region = o.optString("region"),
            address = o.optString("address"),
            builder = o.optString("builder"),
            receipt = o.optString("receipt"),
            mainType = o.optString("mainType"),
            mainPrice = o.optString("mainPrice"),
            mainPricePyeong = o.optString("mainPricePyeong"),
            totalUnits = o.optInt("totalUnits"),
            generalUnits = o.optInt("generalUnits"),
            applicants = o.optInt("applicants"),
            avgRate = o.optDoubleOrNull("avgRate"),
            maxRate = o.optDoubleOrNull("maxRate"),
            verdict = o.optString("verdict"),
            tone = o.optString("tone"),
            byType = o.optJSONArray("byType").map { UnitType.parse(it) },
            detailUrl = o.optString("detailUrl"),
            lat = o.optDoubleOrNull("lat"),
            lon = o.optDoubleOrNull("lon")
        )
    }
}

/** 아직 접수 중이거나 곧 접수하는 단지. */
data class Upcoming(
    val name: String,
    val region: String,
    val address: String,
    val builder: String,
    val status: String,
    val receipt: String,
    val mainType: String,
    val mainPrice: String,
    val totalUnits: Int,
    val priceRange: String,
    val movein: String,
    val byType: List<UnitType>,
    val detailUrl: String,
    val lat: Double?,
    val lon: Double?
) {
    companion object {
        fun parse(o: JSONObject) = Upcoming(
            name = o.optString("name"),
            region = o.optString("region"),
            address = o.optString("address"),
            builder = o.optString("builder"),
            status = o.optString("status"),
            receipt = o.optString("receipt"),
            mainType = o.optString("mainType"),
            mainPrice = o.optString("mainPrice"),
            totalUnits = o.optInt("totalUnits"),
            priceRange = o.optString("priceRange"),
            movein = o.optString("movein"),
            byType = o.optJSONArray("byType").map { UnitType.parse(it) },
            detailUrl = o.optString("detailUrl"),
            lat = o.optDoubleOrNull("lat"),
            lon = o.optDoubleOrNull("lon")
        )
    }
}

// ── JSON 도우미 ────────────────────────────────────────────────────────────

private fun <T> JSONArray?.map(block: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }.map(block)
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}

/** JSON 의 null 과 "값 없음"을 구분한다. 경쟁률 null 은 '미달'을 뜻하므로 0으로 뭉개면 안 된다. */
private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

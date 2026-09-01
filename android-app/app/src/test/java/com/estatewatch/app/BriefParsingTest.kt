package com.estatewatch.app

import com.estatewatch.app.data.Brief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 실제 배포되는 brief.json 을 그대로 읽어서 파싱한다.
 * 수집 스크립트가 형식을 바꾸면 여기서 먼저 깨진다.
 */
class BriefParsingTest {

    private val brief: Brief by lazy {
        val file = File("src/main/assets/brief.json")
        assertTrue("assets/brief.json 이 없습니다. python scripts/build.py 를 먼저 실행하세요.", file.exists())
        Brief.parse(file.readText())
    }

    @Test
    fun `브리핑이 비어 있지 않다`() {
        assertTrue(brief.news.isNotEmpty())
        assertTrue(brief.results.isNotEmpty())
        assertTrue(brief.date.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun `뉴스는 제목 본문 링크를 모두 갖는다`() {
        brief.news.forEach { item ->
            assertTrue("제목 없음", item.title.isNotBlank())
            assertTrue("${item.title}: 본문 없음", item.body.isNotEmpty())
            assertTrue("${item.title}: 링크가 http 로 시작하지 않음", item.url.startsWith("http"))
        }
    }

    @Test
    fun `분양결과는 주요평형과 판정을 갖는다`() {
        brief.results.forEach { row ->
            assertTrue("${row.name}: 지역 없음", row.region.isNotBlank())
            assertTrue("${row.name}: 주요평형 없음", row.mainType.isNotBlank())
            assertTrue("${row.name}: 가격 없음", row.mainPrice.isNotBlank())
            assertTrue("${row.name}: 판정 없음", row.verdict.isNotBlank())
            assertTrue("${row.name}: 주택형 목록 비어 있음", row.byType.isNotEmpty())
        }
    }

    @Test
    fun `미달 주택형의 경쟁률은 0이 아니라 null 이다`() {
        val short = brief.results
            .flatMap { it.byType }
            .filter { it.generalUnits > 0 && it.rate == null }
        // 경쟁률이 null 인 주택형은 '미달'을 뜻한다. 0.0 으로 뭉개지면 화면에서 마감처럼 보인다.
        short.forEach { assertNull("${it.type} 경쟁률이 null 이어야 함", it.rate) }

        val closed = brief.results.flatMap { it.byType }.mapNotNull { it.rate }
        assertTrue("경쟁률이 하나도 파싱되지 않았습니다", closed.isNotEmpty())
        closed.forEach { assertTrue("경쟁률이 음수: $it", it > 0) }
    }

    @Test
    fun `예정단지는 세대수와 접수일정을 갖는다`() {
        brief.upcoming.forEach { row ->
            assertTrue("${row.name}: 세대수가 0", row.totalUnits > 0)
            assertTrue("${row.name}: 접수일정 없음", row.receipt.isNotBlank())
            assertTrue("${row.name}: 상태값 이상 (${row.status})", row.status in setOf("접수중", "접수예정"))
        }
    }

    @Test
    fun `깨진 JSON 이 와도 앱이 죽지 않는다`() {
        assertEquals(Brief.EMPTY.news, Brief.parse("{}").news)
        assertNotNull(Brief.parse("""{"news":{"items":[]},"apply":{}}"""))
    }
}

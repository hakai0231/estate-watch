package com.estatewatch.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.estatewatch.app.data.ApplyResult
import com.estatewatch.app.data.Brief
import com.estatewatch.app.data.BriefRepository
import com.estatewatch.app.data.NewsItem
import com.estatewatch.app.data.UnitType
import com.estatewatch.app.data.Upcoming
import com.estatewatch.app.ui.UiKit
import com.estatewatch.app.ui.UiKit.MATCH
import com.estatewatch.app.ui.UiKit.WRAP
import com.estatewatch.app.ui.color
import com.estatewatch.app.ui.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private enum class Tab(val label: String) { NEWS("뉴스"), APPLY("청약홈"), REDEV("정비사업") }

    private lateinit var repo: BriefRepository
    private lateinit var root: LinearLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var stage: FrameLayout
    private lateinit var dateline: TextView

    private var brief: Brief = Brief.EMPTY
    private var tab = Tab.NEWS
    private var refreshing = false
    private var detailOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = BriefRepository(this)
        brief = repo.load()

        root = UiKit.column(this) {
            setBackgroundColor(color(R.color.paper))
            layoutParams = LinearLayout.LayoutParams(MATCH, MATCH)
        }
        root.addView(buildMasthead())
        tabRow = buildTabs()
        root.addView(tabRow, UiKit.stacked(this, top = 10, bottom = 12).apply {
            leftMargin = dp(14); rightMargin = dp(14)
        })
        stage = FrameLayout(this)
        root.addView(stage, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)

        render()
        if (brief.isEmpty || staleForToday()) refresh(silent = true)
    }

    // enableOnBackInvokedCallback 을 끈 상태라 이 경로가 실제로 호출된다.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (detailOpen) {
            detailOpen = false
            render()
        } else {
            super.onBackPressed()
        }
    }

    // ── 상단 ──────────────────────────────────────────────────────────────

    private fun buildMasthead(): View = UiKit.row(this) {
        setPadding(dp(16), dp(18), dp(14), dp(12))
        val titles = UiKit.column(context) {
            addView(TextView(context).apply {
                text = "부동산 조간 브리핑"
                textSize = 22f
                setTextColor(color(R.color.ink))
                setTypeface(typeface, Typeface.BOLD)
            })
            dateline = UiKit.faint(context, "", 11.5f)
            addView(dateline, UiKit.stacked(context, top = 2))
        }
        addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(UiKit.quietButton(context, "새로고침") { refresh(silent = false) })
        addView(UiKit.quietButton(context, "설정") { showSettings() }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            leftMargin = dp(6)
        })
    }

    private fun buildTabs(): LinearLayout = UiKit.row(this).apply {
        Tab.entries.forEachIndexed { index, entry ->
            val button = UiKit.tabButton(context, entry.label, entry == tab) {
                if (tab != entry || detailOpen) {
                    tab = entry
                    detailOpen = false
                    render()
                }
            }
            addView(button, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                if (index > 0) leftMargin = dp(6)
            })
        }
    }

    private fun refreshTabs() {
        tabRow.removeAllViews()
        Tab.entries.forEachIndexed { index, entry ->
            val button = UiKit.tabButton(this, entry.label, entry == tab && !detailOpen) {
                tab = entry
                detailOpen = false
                render()
            }
            tabRow.addView(button, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                if (index > 0) leftMargin = dp(6)
            })
        }
    }

    // ── 화면 그리기 ───────────────────────────────────────────────────────

    private fun render() {
        refreshTabs()
        dateline.text = datelineText()
        show(
            when (tab) {
                Tab.NEWS -> newsScreen()
                Tab.APPLY -> applyScreen()
                Tab.REDEV -> redevScreen()
            }
        )
    }

    private fun show(content: View) {
        stage.removeAllViews()
        stage.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun datelineText(): String {
        val date = brief.date.ifBlank { "—" }
        val synced = repo.lastSyncedAt
        val when0 = when {
            refreshing -> "받아오는 중…"
            synced > 0L -> DateUtils.getRelativeTimeSpanString(
                synced, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            ).toString() + " 갱신"
            repo.isUsingBundledSnapshot -> "설치 시 기본 자료"
            else -> "갱신 이력 없음"
        }
        return "$date · $when0"
    }

    // ── 뉴스 탭 ───────────────────────────────────────────────────────────

    private fun newsScreen(): View {
        val list = UiKit.column(this)
        if (brief.editorNote.isNotBlank()) {
            list.addView(
                UiKit.sunkenBox(this) {
                    addView(UiKit.muted(context, brief.editorNote, 13.5f))
                },
                UiKit.stacked(this, bottom = 12)
            )
        }
        if (brief.news.isEmpty()) {
            list.addView(emptyNote("오늘 받아온 뉴스가 없습니다."))
        }
        brief.news.forEach { item -> list.addView(newsCard(item), UiKit.stacked(this, bottom = 9)) }
        return UiKit.scroller(this, list)
    }

    private fun newsCard(item: NewsItem): View = UiKit.card(this) {
        isClickable = true
        setOnClickListener { openNews(item) }

        addView(UiKit.row(context) {
            addView(UiKit.chip(context, item.category))
            addView(UiKit.faint(context, "  ${item.outlet}  ·  ${item.publishedAt}", 11.5f))
        })
        addView(
            UiKit.heading(context, "${item.rank}. ${item.title}", 16.5f),
            UiKit.stacked(context, top = 7)
        )
        addView(UiKit.muted(context, item.summary, 13.5f), UiKit.stacked(context, top = 6))
        addView(
            UiKit.faint(context, "전문 읽기 →", 12f).apply {
                setTextColor(color(R.color.accent))
                setTypeface(typeface, Typeface.BOLD)
            },
            UiKit.stacked(context, top = 9)
        )
    }

    private fun openNews(item: NewsItem) {
        detailOpen = true
        refreshTabs()
        val page = UiKit.column(this)
        page.addView(backBar("뉴스로"))

        page.addView(UiKit.row(this) {
            addView(UiKit.chip(context, item.category))
            addView(UiKit.faint(context, "  ${item.outlet}  ·  ${item.publishedAt}", 11.5f))
        }, UiKit.stacked(this, top = 4))

        page.addView(UiKit.heading(this, item.title, 20f), UiKit.stacked(this, top = 8))
        if (item.byline.isNotBlank()) {
            page.addView(UiKit.faint(this, item.byline, 12f), UiKit.stacked(this, top = 5))
        }

        if (item.points.isNotEmpty()) {
            page.addView(UiKit.sunkenBox(this) {
                addView(UiKit.caption(context, "핵심"))
                item.points.forEach { point ->
                    addView(
                        UiKit.muted(context, "· $point", 13.5f),
                        UiKit.stacked(context, top = 5)
                    )
                }
            }, UiKit.stacked(this, top = 14, bottom = 4))
        }

        item.body.forEach { paragraph ->
            page.addView(UiKit.body(this, paragraph, 15f), UiKit.stacked(this, top = 13))
        }

        page.addView(
            UiKit.linkButton(this, "기사 원문 열기 ↗") { openUrl(item.url) },
            UiKit.stacked(this, top = 20, bottom = 8)
        )
        page.addView(UiKit.faint(this, item.url, 11f))
        show(UiKit.scroller(this, page))
    }

    // ── 청약홈 탭 ─────────────────────────────────────────────────────────

    private fun applyScreen(): View {
        val list = UiKit.column(this)

        list.addView(sectionTitle("최근 분양결과", "1순위 경쟁률 · 최근 마감순"))
        if (brief.results.isEmpty()) list.addView(emptyNote("경쟁률이 공개된 단지가 없습니다."))
        brief.results.forEach { list.addView(resultCard(it), UiKit.stacked(this, bottom = 9)) }

        list.addView(sectionTitle("청약 예정·접수중", "입주자모집공고가 나온 단지"), UiKit.stacked(this, top = 18))
        if (brief.upcoming.isEmpty()) list.addView(emptyNote("공고가 나온 예정 단지가 없습니다."))
        brief.upcoming.forEach { list.addView(upcomingCard(it), UiKit.stacked(this, bottom = 9)) }

        return UiKit.scroller(this, list)
    }

    private fun resultCard(row: ApplyResult): View = UiKit.card(this) {
        isClickable = true
        setOnClickListener { openApplyDetail(row.name, row.address, row.builder, row.byType, true, row.detailUrl) }

        addView(UiKit.row(context) {
            addView(UiKit.chip(context, row.region))
            addView(UiKit.gap(context, 0), LinearLayout.LayoutParams(dp(7), 1))
            addView(UiKit.heading(context, row.name, 15f), LinearLayout.LayoutParams(0, WRAP, 1f))
        })
        addView(
            UiKit.badge(context, row.verdict, row.tone),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(7) }
        )
        addView(
            UiKit.dataStrip(
                context,
                listOf(
                    Triple("주요평형", row.mainType, null),
                    Triple("가격", row.mainPrice, null),
                    Triple("평균 경쟁률", rateText(row.avgRate), rateColor(row.avgRate)),
                    Triple("최고 경쟁률", rateText(row.maxRate), null)
                ),
                highlight = 2
            ),
            UiKit.stacked(context, top = 9)
        )
        addView(
            UiKit.faint(
                context,
                "${row.receipt} 접수 · 총 ${row.totalUnits.grouped()}세대(일반 ${row.generalUnits.grouped()}) · 1순위 ${row.applicants.grouped()}건",
                11.5f
            ),
            UiKit.stacked(context, top = 8)
        )
    }

    private fun upcomingCard(row: Upcoming): View = UiKit.card(this) {
        isClickable = true
        setOnClickListener { openApplyDetail(row.name, row.address, row.builder, row.byType, false, row.detailUrl) }

        addView(UiKit.row(context) {
            addView(UiKit.chip(context, row.region))
            addView(UiKit.gap(context, 0), LinearLayout.LayoutParams(dp(7), 1))
            addView(UiKit.heading(context, row.name, 15f), LinearLayout.LayoutParams(0, WRAP, 1f))
        })
        addView(
            UiKit.badge(context, row.status, if (row.status == "접수중") "live" else "soon"),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(7) }
        )
        addView(
            UiKit.dataStrip(
                context,
                listOf(
                    Triple("주요평형", row.mainType, null),
                    Triple("가격", row.mainPrice, null),
                    Triple("세대수", row.totalUnits.grouped(), null),
                    Triple("접수", row.receipt, null)
                ),
                highlight = 2
            ),
            UiKit.stacked(context, top = 9)
        )
        addView(
            UiKit.faint(context, "전체 분양가 ${row.priceRange} · 입주 ${row.movein}", 11.5f),
            UiKit.stacked(context, top = 8)
        )
    }

    private fun openApplyDetail(
        name: String,
        address: String,
        builder: String,
        types: List<UnitType>,
        withRate: Boolean,
        url: String
    ) {
        detailOpen = true
        refreshTabs()
        val page = UiKit.column(this)
        page.addView(backBar("청약홈으로"))
        page.addView(UiKit.heading(this, name, 19f), UiKit.stacked(this, top = 6))
        page.addView(UiKit.muted(this, address, 13f), UiKit.stacked(this, top = 6))
        page.addView(UiKit.faint(this, "시공 $builder", 12.5f), UiKit.stacked(this, top = 4))

        page.addView(UiKit.caption(this, "주택형별 분양가"), UiKit.stacked(this, top = 20, bottom = 6))
        page.addView(typeTable(types, withRate))

        page.addView(
            UiKit.linkButton(this, "청약홈 공고 상세 열기 ↗") { openUrl(url) },
            UiKit.stacked(this, top = 20)
        )
        show(UiKit.scroller(this, page))
    }

    private fun typeTable(types: List<UnitType>, withRate: Boolean): View = UiKit.column(this) {
        background = UiKit.rounded(color(R.color.surface), dp(12), color(R.color.line), dp(1))
        setPadding(dp(13), dp(11), dp(13), dp(11))

        addView(tableRow(
            listOf("주택형", "세대수", "분양가", if (withRate) "1순위" else "평당가"),
            header = true, tint = null
        ))
        types.forEach { unit ->
            addView(UiKit.divider(context), UiKit.stacked(context, top = 8, bottom = 8))
            addView(tableRow(
                listOf(
                    unit.type,
                    unit.units.grouped(),
                    unit.price,
                    if (withRate) rateText(unit.rate) else unit.pyeongPrice
                ),
                header = false,
                tint = if (withRate && unit.rate == null) R.color.bad else null
            ))
        }
    }

    private fun tableRow(cells: List<String>, header: Boolean, tint: Int?): LinearLayout =
        UiKit.row(this).apply {
            cells.forEachIndexed { index, text ->
                val view = TextView(context).apply {
                    this.text = text
                    textSize = if (header) 10.5f else 13f
                    gravity = if (index == 0) Gravity.START else Gravity.END
                    setTextColor(
                        when {
                            header -> color(R.color.faint)
                            tint != null && index == cells.lastIndex -> color(tint)
                            else -> color(R.color.ink)
                        }
                    )
                    if (!header) setTypeface(typeface, Typeface.BOLD)
                }
                addView(view, LinearLayout.LayoutParams(0, WRAP, if (index == 0) 1.2f else 1f))
            }
        }

    // ── 정비사업 탭 ───────────────────────────────────────────────────────

    private fun redevScreen(): View {
        val page = UiKit.column(this)
        page.addView(UiKit.sunkenBox(this) {
            addView(UiKit.body(context, "아직 데이터를 붙이지 않은 탭입니다.", 14f).apply {
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(
                UiKit.muted(context, "아래 기획안을 보고 방향을 정해 주세요. 정해지면 뉴스·청약홈 탭과 똑같이 매일 자동으로 채워집니다.", 13.5f),
                UiKit.stacked(context, top = 6)
            )
        }, UiKit.stacked(this, bottom = 12))

        page.addView(docCard(
            "무엇을 매일 알고 싶은가",
            "정비사업은 가격이 아니라 '단계'가 뉴스입니다.",
            listOf(
                "정비구역 지정 → 추진위 → 조합설립인가 → 시공사 선정 → 사업시행계획인가 → 조합원 분양신청 → 관리처분계획인가 → 이주·철거 → 착공·일반분양",
                "이 탭의 핵심은 \"어제까지 A단계였던 사업장이 오늘 B단계로 넘어갔다\"를 잡아내는 것입니다. 기사 요약이 아니라 상태 변화 감지가 목적입니다."
            )
        ), UiKit.stacked(this, bottom = 10))

        page.addView(docCard(
            "데이터를 어디서 가져오나",
            "신뢰도 순. 1·2번이 원본이고 3·4번은 속보성 보완재입니다.",
            listOf(
                "1. 정비사업 정보몽땅 (cleanup.seoul.go.kr) — 서울시 공식. 구역별 추진단계, 조합 공지, 공람공고까지 공개.",
                "2. 지자체 고시·공고 — 정비구역 지정, 사업시행계획인가, 관리처분계획인가는 반드시 고시로 확정됩니다. 가장 정확한 단계 통과 신호.",
                "3. 건설사 수주 공시·보도자료 — 시공사 선정 총회 결과가 제일 먼저 나옵니다.",
                "4. 뉴스 — 조합 내분, 공사비 증액 분쟁처럼 고시에 잡히지 않는 리스크."
            )
        ), UiKit.stacked(this, bottom = 10))

        page.addView(docCard(
            "화면 구성 — 세 가지 안",
            "셋 중 하나를 고르시면 됩니다.",
            listOf(
                "A안 · 관심 사업장 트래커 (추천) — 관심 사업장 20~30곳의 현재 단계를 한 줄로. 그날 바뀐 곳만 위로.",
                "B안 · 오늘의 정비사업 이벤트 — 어제 하루 전국의 단계 변동·고시·시공사 선정을 타임라인으로.",
                "C안 · 시공사 수주전 트래커 — 입찰 일정·참여사·조건만 모아서.",
                "제안 — B안으로 시작해 관심 단지를 모으고, 20곳쯤 쌓이면 A안으로 전환."
            )
        ), UiKit.stacked(this, bottom = 10))

        page.addView(docCard(
            "시작하려면 이것만 알려주세요",
            "",
            listOf(
                "1. 지역 범위 — 서울 전역인지, 특정 구인지, 수도권까지인지",
                "2. 관심 단계 — 초기(구역지정·조합설립)인지, 중기(시공사 선정·사업시행인가)인지, 후기(관리처분·일반분양)인지",
                "3. 보는 목적 — 투자 판단인지, 수주 영업인지, 분양성 검토인지"
            )
        ))
        return UiKit.scroller(this, page)
    }

    private fun docCard(title: String, subtitle: String, lines: List<String>): View =
        UiKit.card(this, padding = 16) {
            addView(UiKit.heading(context, title, 16f))
            if (subtitle.isNotBlank()) {
                addView(UiKit.faint(context, subtitle, 12.5f), UiKit.stacked(context, top = 4))
            }
            lines.forEach { line ->
                addView(UiKit.muted(context, line, 13.5f), UiKit.stacked(context, top = 11))
            }
        }

    // ── 공통 ──────────────────────────────────────────────────────────────

    private fun sectionTitle(title: String, hint: String): View = UiKit.column(this) {
        addView(UiKit.heading(context, title, 17f))
        addView(UiKit.faint(context, hint, 11.5f), UiKit.stacked(context, top = 3, bottom = 9))
        addView(UiKit.divider(context), UiKit.stacked(context, bottom = 11))
    }

    private fun backBar(label: String): View =
        UiKit.quietButton(this, "‹  $label") {
            detailOpen = false
            render()
        }.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        }

    private fun emptyNote(text: String): View = UiKit.faint(this, text, 13f).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(26), 0, dp(26))
    }

    private fun rateText(value: Double?): String = when {
        value == null -> "미달"
        value == value.toLong().toDouble() -> "${value.toLong()}:1"
        else -> String.format(Locale.KOREA, "%.2f:1", value)
    }

    private fun rateColor(value: Double?): Int = when {
        value == null -> R.color.bad
        value >= 5 -> R.color.good
        value >= 1 -> R.color.warn
        else -> R.color.bad
    }

    private fun Int.grouped(): String = String.format(Locale.KOREA, "%,d", this)

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("링크를 열 수 있는 앱이 없습니다") }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    /** 오늘 날짜 브리핑이 아니면 조용히 한 번 받아온다. */
    private fun staleForToday(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Calendar.getInstance().time)
        return brief.date != today
    }

    // ── 새로고침 · 설정 ───────────────────────────────────────────────────

    private fun refresh(silent: Boolean) {
        if (refreshing) return
        refreshing = true
        dateline.text = datelineText()
        Thread {
            val result = repo.refresh()
            runOnUiThread {
                refreshing = false
                result.onSuccess {
                    brief = it
                    detailOpen = false
                    render()
                    if (!silent) toast("최신 브리핑을 받았습니다")
                }.onFailure { error ->
                    dateline.text = datelineText()
                    if (!silent) toast("받아오지 못했습니다 — ${error.message ?: "네트워크 오류"}")
                }
            }
        }.start()
    }

    private fun showSettings() {
        detailOpen = true
        refreshTabs()
        val page = UiKit.column(this)
        page.addView(backBar("돌아가기"))
        page.addView(UiKit.heading(this, "설정", 19f), UiKit.stacked(this, top = 6, bottom = 14))

        page.addView(UiKit.caption(this, "브리핑 주소"))
        val field = UiKit.input(this, "https://…/brief.json", repo.briefUrl)
        page.addView(field, UiKit.stacked(this, top = 6))
        page.addView(
            UiKit.muted(this, "매일 아침 갱신된 브리핑 파일 주소입니다. 바꾸면 다음 새로고침부터 이 주소에서 받아옵니다.", 12.5f),
            UiKit.stacked(this, top = 8)
        )
        page.addView(UiKit.linkButton(this, "저장하고 새로고침") {
            repo.briefUrl = field.text.toString()
            detailOpen = false
            refresh(silent = false)
        }, UiKit.stacked(this, top = 16))

        page.addView(UiKit.divider(this), UiKit.stacked(this, top = 24, bottom = 16))
        page.addView(UiKit.caption(this, "지금 보고 있는 자료"))
        page.addView(
            UiKit.muted(this, buildString {
                appendLine("브리핑 날짜: ${brief.date.ifBlank { "없음" }}")
                appendLine("뉴스 기준: ${brief.newsUpdatedAt.ifBlank { "없음" }}")
                appendLine("청약 기준: ${brief.applyUpdatedAt.ifBlank { "없음" }}")
                append(if (repo.isUsingBundledSnapshot) "출처: 설치 시 포함된 기본 자료" else "출처: 내려받은 최신본")
            }, 12.5f),
            UiKit.stacked(this, top = 6)
        )
        show(UiKit.scroller(this, page))
    }
}

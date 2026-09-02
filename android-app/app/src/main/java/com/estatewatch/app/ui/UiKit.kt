package com.estatewatch.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.estatewatch.app.R

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

fun Context.color(id: Int) = getColor(id)

/**
 * 화면을 만드는 데 쓰는 조각들. 웹 브리핑 페이지와 같은 팔레트·간격을 쓴다.
 */
object UiKit {

    fun column(context: Context, block: LinearLayout.() -> Unit = {}): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            block()
        }

    fun row(context: Context, block: LinearLayout.() -> Unit = {}): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            block()
        }

    fun scroller(context: Context, content: View): ScrollView = ScrollView(context).apply {
        isFillViewport = true
        setPadding(context.dp(14), 0, context.dp(14), context.dp(28))
        addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
    }

    // ── 글자 ──────────────────────────────────────────────────────────────

    fun heading(context: Context, text: String, size: Float = 20f) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(context.color(R.color.ink))
        setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    fun body(context: Context, text: String, size: Float = 15f) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(context.color(R.color.ink))
        setLineSpacing(context.dp(5).toFloat(), 1f)
    }

    fun muted(context: Context, text: String, size: Float = 13f) = body(context, text, size).apply {
        setTextColor(context.color(R.color.muted))
    }

    fun faint(context: Context, text: String, size: Float = 12f) = body(context, text, size).apply {
        setTextColor(context.color(R.color.faint))
    }

    fun caption(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 10.5f
        letterSpacing = .06f
        setTextColor(context.color(R.color.faint))
    }

    // ── 상자 ──────────────────────────────────────────────────────────────

    fun card(context: Context, padding: Int = 14, block: LinearLayout.() -> Unit): LinearLayout =
        column(context) {
            setPadding(context.dp(padding), context.dp(padding), context.dp(padding), context.dp(padding))
            background = rounded(
                context.color(R.color.surface), context.dp(12),
                context.color(R.color.line), context.dp(1)
            )
            block()
        }

    fun sunkenBox(context: Context, padding: Int = 13, block: LinearLayout.() -> Unit): LinearLayout =
        column(context) {
            setPadding(context.dp(padding), context.dp(padding), context.dp(padding), context.dp(padding))
            background = rounded(context.color(R.color.surface_sunk), context.dp(10))
            block()
        }

    /** 지역 같은 짧은 꼬리표. */
    fun chip(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(context.color(R.color.accent))
        setPadding(context.dp(7), context.dp(2), context.dp(7), context.dp(3))
        background = rounded(context.color(R.color.accent_soft), context.dp(5))
    }

    /** 마감/미달 같은 판정 뱃지. tone 은 good · warn · bad · live · soon. */
    fun badge(context: Context, text: String, tone: String) = TextView(context).apply {
        val (fg, bg) = when (tone) {
            "good" -> R.color.good to R.color.good_soft
            "warn" -> R.color.warn to R.color.warn_soft
            "bad", "live" -> R.color.bad to R.color.bad_soft
            "soon" -> R.color.accent to R.color.accent_soft
            else -> R.color.faint to R.color.surface_sunk
        }
        this.text = text
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(context.color(fg))
        setPadding(context.dp(9), context.dp(3), context.dp(9), context.dp(4))
        background = rounded(context.color(bg), context.dp(20))
    }

    /**
     * 지역/평형/가격/경쟁률 처럼 4칸으로 나눠 보여주는 띠.
     * highlight 로 지정한 칸만 크고 색이 들어간다.
     */
    fun dataStrip(
        context: Context,
        cells: List<Triple<String, String, Int?>>,
        highlight: Int = -1,
        weights: List<Float> = emptyList(),
        subs: List<String?> = emptyList()
    ): LinearLayout = row(context) {
        background = rounded(
            context.color(R.color.line), context.dp(9),
            context.color(R.color.line), context.dp(1)
        )
        cells.forEachIndexed { index, (key, value, tintColor) ->
            val isHero = index == highlight
            val cell = column(context) {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(context.dp(4), context.dp(8), context.dp(4), context.dp(9))
                setBackgroundColor(
                    context.color(if (isHero) R.color.surface else R.color.surface_sunk)
                )
                addView(caption(context, key).apply { gravity = Gravity.CENTER })
                addView(TextView(context).apply {
                    text = value
                    textSize = if (isHero) 16f else 13.5f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(context.color(tintColor ?: R.color.ink))
                })
                subs.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { sub ->
                    addView(TextView(context).apply {
                        text = sub
                        textSize = 10.5f
                        gravity = Gravity.CENTER
                        setTextColor(context.color(R.color.faint))
                    })
                }
            }
            addView(cell, LinearLayout.LayoutParams(0, WRAP, weights.getOrElse(index) { 1f }).apply {
                if (index > 0) leftMargin = context.dp(1)
                topMargin = context.dp(1)
                bottomMargin = context.dp(1)
                if (index == 0) leftMargin = context.dp(1)
                if (index == cells.lastIndex) rightMargin = context.dp(1)
            })
        }
    }

    /**
     * 단지 위치 지도. OpenStreetMap 을 쓴다 — API 키가 필요 없고, 한국 지도도
     * 아파트 단지명·지하철역까지 나올 만큼 충실하다. 손가락으로 확대·이동된다.
     */
    fun map(context: Context, lat: Double, lon: Double, heightDp: Int = 260): View {
        val span = 0.005                      // 대략 반경 400m
        val url = "https://www.openstreetmap.org/export/embed.html" +
            "?bbox=" + (lon - span) + "%2C" + (lat - span / 2) +
            "%2C" + (lon + span) + "%2C" + (lat + span / 2) +
            "&layer=mapnik&marker=" + lat + "%2C" + lon

        val web = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setBackgroundColor(context.color(R.color.surface_sunk))
            // 지도를 만지는 동안 바깥 스크롤이 가로채지 않게 한다.
            setOnTouchListener { view, _ ->
                view.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            loadUrl(url)
        }

        return column(context) {
            setPadding(context.dp(1), context.dp(1), context.dp(1), context.dp(1))
            background = rounded(
                context.color(R.color.line), context.dp(12),
                context.color(R.color.line), context.dp(1)
            )
            addView(web, LinearLayout.LayoutParams(MATCH, context.dp(heightDp)))
        }
    }

    // ── 조작 ──────────────────────────────────────────────────────────────

    fun tabButton(context: Context, text: String, selected: Boolean, onClick: () -> Unit) =
        TextView(context).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.color(if (selected) R.color.surface else R.color.muted))
            setPadding(context.dp(4), context.dp(10), context.dp(4), context.dp(11))
            background = rounded(
                context.color(if (selected) R.color.accent else R.color.surface),
                context.dp(9),
                context.color(if (selected) R.color.accent else R.color.line),
                context.dp(1)
            )
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun linkButton(context: Context, text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        textSize = 14f
        isAllCaps = false
        minHeight = context.dp(48)
        setTextColor(context.color(R.color.accent))
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(
            context.color(R.color.accent_soft), context.dp(10),
            context.color(R.color.accent), context.dp(1)
        )
        setOnClickListener { onClick() }
    }

    /** 카드 아래에 붙는 작은 동작 버튼. "평형별 상세 >" 처럼 쓴다. */
    fun cardAction(context: Context, text: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.color(R.color.accent))
            setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(11))
            background = rounded(
                context.color(R.color.accent_soft), context.dp(8),
                context.color(R.color.line), context.dp(1)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    fun quietButton(context: Context, text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        textSize = 13f
        isAllCaps = false
        minHeight = context.dp(42)
        setTextColor(context.color(R.color.muted))
        background = rounded(
            context.color(R.color.surface), context.dp(9),
            context.color(R.color.line), context.dp(1)
        )
        setOnClickListener { onClick() }
    }

    fun input(context: Context, hint: String, value: String) = EditText(context).apply {
        this.hint = hint
        setText(value)
        textSize = 14f
        setSingleLine()
        setTextColor(context.color(R.color.ink))
        setHintTextColor(context.color(R.color.faint))
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        background = rounded(
            context.color(R.color.surface), context.dp(10),
            context.color(R.color.line), context.dp(1)
        )
    }

    fun divider(context: Context) = View(context).apply {
        setBackgroundColor(context.color(R.color.line))
        layoutParams = LinearLayout.LayoutParams(MATCH, context.dp(1))
    }

    fun gap(context: Context, height: Int) = Space(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, context.dp(height))
    }

    fun stacked(context: Context, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = context.dp(top)
            bottomMargin = context.dp(bottom)
        }

    fun rounded(color: Int, radius: Int, strokeColor: Int? = null, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null && stroke > 0) setStroke(stroke, strokeColor)
        }

    fun transparent(): GradientDrawable = GradientDrawable().apply { setColor(Color.TRANSPARENT) }

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}

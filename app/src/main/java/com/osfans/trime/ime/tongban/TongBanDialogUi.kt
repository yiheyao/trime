/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.linearLayout
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityEnd
import splitties.views.gravityStart
import splitties.views.horizontalPadding
import splitties.views.padding
import splitties.views.textAppearance
import splitties.views.verticalPadding

/**
 * 童伴智能问题浮窗 UI
 *
 * 层级：在键盘内部，仅覆盖键盘区域，高度 = 键盘高度 * 2/5
 */
class TongBanDialogUi(
    override val ctx: Context,
    private val onQuery: (String) -> Unit,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit,
) : Ui {
    /** 标题栏：标题 + 关闭按钮 */
    val titleBar: LinearLayout
    val titleText: TextView
    val closeButton: TextView

    /** 输入行：单行 EditText + 查询按钮 */
    val inputEditText: EditText
    val queryButton: TextView

    /** 回复区域：滚动视图，包含若干回复卡片 */
    val responseContainer: LinearLayout
    val responseScroll: ScrollView
    val loadingBar: ProgressBar

    /** 适配内容到容器高度 */
    private var maxContentHeight: Int = Int.MAX_VALUE

    override val root: FrameLayout = frameLayout {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ContextCompat.getColor(ctx, android.R.color.background_light))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor("#CCCCCC"))
        }
        // 子内容 layout
        val content = linearLayout {
            orientation = LinearLayout.VERTICAL
            verticalPadding = dp(8)
        }
        add(
            content,
            lParams(matchParent, matchParent),
        )

        // 标题栏
        titleBar = content.linearLayout {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            horizontalPadding = dp(12)
            verticalPadding = dp(4)
            backgroundColor = Color.parseColor("#F5F5F5")
        }
        titleText = titleBar.textView {
            text = ctx.getString(R.string.tongban_dialog_title)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        closeButton = titleBar.textView {
            text = "×"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#666666"))
            setPadding(dp(8), 0, dp(8), 0)
            contentDescription = "close"
            // 在主 View 中通过 setOnClickListener 绑定
        }

        // 输入行
        val inputRow = content.linearLayout {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            horizontalPadding = dp(12)
            verticalPadding = dp(8)
        }
        inputEditText = EditText(ctx).apply {
            isSingleLine = true
            hint = ctx.getString(R.string.tongban_input_hint)
            setBackgroundColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#999999"))
            setTextColor(Color.parseColor("#222222"))
            imeOptions = EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        inputRow.addView(inputEditText)
        queryButton = inputRow.textView {
            text = ctx.getString(R.string.tongban_query_button)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#FF9800"))
                cornerRadius = dp(4).toFloat()
            }
            // 初始置灰
            isEnabled = false
            alpha = 0.4f
        }

        // Loading 条
        loadingBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        content.addView(
            loadingBar,
            LinearLayout.LayoutParams(matchParent, dp(2)),
        )

        // 回复区
        responseScroll = ScrollView(ctx).apply {
            isFillViewport = true
            visibility = View.GONE
        }
        responseContainer = linearLayout {
            orientation = LinearLayout.VERTICAL
            horizontalPadding = dp(12)
            verticalPadding = dp(8)
        }
        responseScroll.addView(
            responseContainer,
            FrameLayout.LayoutParams(matchParent, wrapContent),
        )
        content.addView(
            responseScroll,
            LinearLayout.LayoutParams(matchParent, 0, 1f),
        )
    }

    /**
     * 监听输入变化，更新查询按钮状态
     */
    fun bindInputWatcher() {
        inputEditText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val hasText = !s.isNullOrBlank()
                    queryButton.isEnabled = hasText
                    queryButton.alpha = if (hasText) 1f else 0.4f
                }
            },
        )
    }

    fun showLoading() {
        loadingBar.visibility = View.VISIBLE
        queryButton.isEnabled = false
        queryButton.alpha = 0.4f
    }

    fun hideLoading() {
        loadingBar.visibility = View.GONE
        // 重新评估按钮状态
        val hasText = !inputEditText.text.isNullOrBlank()
        queryButton.isEnabled = hasText
        queryButton.alpha = if (hasText) 1f else 0.4f
    }

    fun showResponses(responses: List<String>) {
        responseContainer.removeAllViews()
        if (responses.isEmpty()) return
        responseScroll.visibility = View.VISIBLE
        for (r in responses) {
            responseContainer.addView(buildResponseCard(r))
        }
    }

    fun showError(message: String) {
        responseContainer.removeAllViews()
        responseScroll.visibility = View.VISIBLE
        val tv = TextView(ctx).apply {
            text = message
            setTextColor(Color.parseColor("#D32F2F"))
            textSize = 14f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        responseContainer.addView(tv)
    }

    fun clearResponses() {
        responseContainer.removeAllViews()
        responseScroll.visibility = View.GONE
    }

    private fun buildResponseCard(text: String): View {
        val card = linearLayout {
            orientation = LinearLayout.VERTICAL
            verticalPadding = dp(6)
        }
        val textView = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#222222"))
            textSize = 14f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        card.addView(
            textView,
            LinearLayout.LayoutParams(matchParent, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        // 插入按钮
        val insertBtn = TextView(ctx).apply {
            this.text = ctx.getString(R.string.tongban_insert_button)
            textSize = 13f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setTextColor(Color.parseColor("#1976D2"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener { onInsert(text) }
        }
        card.addView(
            insertBtn,
            LinearLayout.LayoutParams(matchParent, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravityEnd()
            },
        )
        return card
    }

    fun pasteFromClipboard() {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(ctx)?.toString()
            if (!text.isNullOrBlank()) {
                inputEditText.setText(text.trim())
                inputEditText.setSelection(inputEditText.text.length)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getInputText(): String = inputEditText.text?.toString() ?: ""

    fun clearInput() {
        inputEditText.setText("")
    }

    fun setMaxHeight(heightPx: Int) {
        maxContentHeight = heightPx
        root.maxHeight = heightPx
    }
}

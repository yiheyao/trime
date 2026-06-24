/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * 童伴智能问题浮窗 UI
 *
 * 层级：在键盘内部，仅覆盖键盘区域，高度 = 键盘高度 * 2/5
 */
class TongBanDialogUi(
    private val context: Context,
    private val onQuery: (String) -> Unit,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * context.resources.displayMetrics.density

    /** 标题栏：标题 + 关闭按钮 */
    val titleBar: LinearLayout
    val titleText: TextView
    val closeButton: TextView

    /** 输入行 */
    val inputEditText: EditText
    val queryButton: Button

    /** 回复区域 */
    val responseContainer: LinearLayout
    val responseScroll: ScrollView
    val loadingBar: ProgressBar

    val root: FrameLayout

    init {
        val container = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#FAFAFA"))
                cornerRadius = dpF(8f)
                setStroke(dp(1), Color.parseColor("#CCCCCC"))
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        container.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // 标题栏
        titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        titleText = TextView(context).apply {
            text = "童伴智能问题"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        closeButton = TextView(context).apply {
            text = "×"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#666666"))
            setPadding(dp(12), 0, dp(12), 0)
            isClickable = true
            isFocusable = true
        }
        titleBar.addView(titleText)
        titleBar.addView(closeButton)
        content.addView(
            titleBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // 输入行
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        inputEditText = EditText(context).apply {
            isSingleLine = true
            hint = "请输入您的问题…"
            setBackgroundColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#999999"))
            setTextColor(Color.parseColor("#222222"))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        queryButton = Button(context).apply {
            text = "查询"
            setTextColor(Color.WHITE)
            isEnabled = false
            alpha = 0.4f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#FF9800"))
                cornerRadius = dpF(4f)
            }
            setPadding(dp(16), dp(4), dp(16), dp(4))
            setOnClickListener { onQuery("") }
        }
        val inputParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        }
        inputRow.addView(inputEditText, inputParams)
        inputRow.addView(
            queryButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            inputRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // Loading 条
        loadingBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        content.addView(
            loadingBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(2),
            ),
        )

        // 回复区
        responseScroll = ScrollView(context).apply {
            isFillViewport = true
            visibility = View.GONE
        }
        responseContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        responseScroll.addView(
            responseContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            responseScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        root = container
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
        val tv = TextView(context).apply {
            text = message
            setTextColor(Color.parseColor("#D32F2F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        responseContainer.addView(tv)
    }

    fun clearResponses() {
        responseContainer.removeAllViews()
        responseScroll.visibility = View.GONE
    }

    private fun buildResponseCard(text: String): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val textView = TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#222222"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        card.addView(
            textView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        // 插入按钮
        val insertBtn = TextView(context).apply {
            this.text = "插入"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setTextColor(Color.parseColor("#1976D2"))
            setTypeface(typeface, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setOnClickListener { onInsert(text) }
        }
        card.addView(
            insertBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.END
            },
        )
        return card
    }

    fun pasteFromClipboard() {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData? = cm.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
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
        val lp = root.layoutParams
        if (lp != null) {
            lp.height = heightPx
            root.layoutParams = lp
        }
    }
}

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
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 童伴智能问题浮窗 UI
 *
 * - 顶部：单行输入（× 输入框 查询）—— 状态 1（"小弹窗"）时只有这一行
 * - 下方：可滚动响应框（TextView inside ScrollView）—— 状态 2（"大弹窗"）时显示
 * - 弹窗 height 由 layoutParams.height 决定（[50dp, 280dp]），响应框 weight=1 占满剩余空间
 * - 状态切换时 TongBanManager 用 ValueAnimator 把弹窗 height 在 50dp ↔ 280dp 之间平滑过渡，
 *   与 InputView 端 keyboardView 高度动画同步；两者最终高度一致（都 280dp），实现"无感替换"
 */
class TongBanDialogUi(
    private val context: Context,
    private val onQuery: (String) -> Unit,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onBack: () -> Unit,
) {
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * context.resources.displayMetrics.density

    /** 输入行（状态 1 显示，状态 2 隐藏） */
    val inputRow: LinearLayout
    val inputEditText: TextView
    val inputPlaceholder: TextView
    val queryButton: Button
    /** 状态 1 的关闭按钮（×） */
    val closeButton: TextView

    /** 响应区顶部工具栏（状态 2 显示）：← 返回 + × 关闭 */
    val responseToolbar: LinearLayout
    val backButton: TextView
    val closeResponseButton: TextView

    /** 响应框（可滚动） */
    val responseScroll: ScrollView
    val responseText: TextView

    /** 插入按钮（状态 2 显示在右下角） */
    val insertButton: Button

    /** contentArea 容器：状态 2 显示，承载工具栏 + 响应区 + 插入按钮 */
    private lateinit var contentAreaRef: LinearLayout

    val root: FrameLayout

    /** 当前打字机流式输出的 Handler；切换查询/关闭弹窗时清空 */
    private var streamHandler: android.os.Handler? = null

    init {
        // 弹窗容器：直接占满 IME 底部键盘区域，无 padding/margin/border，
        // 视觉上与原键盘背景无缝融合。
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }

        // 单行：关闭按钮 + 输入框 + 查询按钮（状态 1 唯一可见区域；状态 2 整体隐藏）
        inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        // 关闭按钮（×）—— 状态 1（输入行）用
        closeButton = TextView(context).apply {
            text = "×"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#666666"))
            setPadding(dp(8), 0, dp(8), 0)
            isClickable = true
            isFocusable = true
        }
        // 输入框：使用 TextView（无焦点，避免 IME 重建）
        // 长按输入框直接从剪贴板粘贴内容（带震动反馈）
        inputEditText = TextView(context).apply {
            text = ""
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpF(6f)
                // 虚线描边，提示用户该区域是"可粘贴"区域
                setStroke(
                    dp(1),
                    Color.parseColor("#BBBBBB"),
                    dpF(4f),
                    dpF(4f),
                )
            }
            setTextColor(Color.parseColor("#222222"))
            textSize = 14f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            // 长按触发粘贴
            isLongClickable = true
            isClickable = false
            isFocusable = false
            setOnLongClickListener {
                pasteFromClipboard()
                try {
                    performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                } catch (e: Exception) {
                    // ignore
                }
                true
            }
        }
        // 占位文本：与 inputEditText 同一布局位置，通过 visibility 切换
        inputPlaceholder = TextView(context).apply {
            text = "长按此处粘贴 / 输入问题…"
            setTextColor(Color.parseColor("#999999"))
            textSize = 14f
            isClickable = false
            isFocusable = false
        }
        // 查询按钮
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

        // inputEditText + placeholder 用 FrameLayout 叠加
        val inputWrapper = FrameLayout(context)
        inputWrapper.addView(
            inputEditText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        inputWrapper.addView(
            inputPlaceholder,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                leftMargin = dp(10)
            },
        )

        inputRow.addView(
            closeButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        inputRow.addView(
            inputWrapper,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginStart = dp(4); marginEnd = dp(8) },
        )
        inputRow.addView(
            queryButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // 响应框：ScrollView + TextView（状态 2 时显示）
        responseText = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#222222"))
            textSize = 14f
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextIsSelectable(true) // 允许选择/复制
        }
        responseScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            // 响应区有圆角白底，模拟"卡片"风格
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = dpF(10f)
            }
            addView(
                responseText,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        // 插入按钮（状态 2 显示在右下角，胶囊风格）
        insertButton = Button(context).apply {
            text = "✓ 插入"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#5B8DEF"))
                cornerRadius = dpF(20f)
            }
            setPadding(dp(18), dp(8), dp(18), dp(8))
            isAllCaps = false
            setOnClickListener {
                val text = responseText.text?.toString().orEmpty()
                onInsert(text)
            }
        }

        // 状态 2 顶部工具栏：← 返回 + 弹性空白 + × 关闭
        backButton = TextView(context).apply {
            text = "←"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#333333"))
            setPadding(dp(8), 0, dp(8), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onBack() }
        }
        closeResponseButton = TextView(context).apply {
            text = "×"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#333333"))
            setPadding(dp(8), 0, dp(8), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }
        responseToolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        responseToolbar.addView(
            backButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        responseToolbar.addView(
            View(context),
            LinearLayout.LayoutParams(
                0,
                0,
                1f, // 占满中间空白
            ),
        )
        responseToolbar.addView(
            closeResponseButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // 弹窗 root：LinearLayout(vertical)
        // - inputRow：固定高度 ~50dp（仅状态 1 显示）
        // - contentArea（vertical）：状态 2 显示
        //   - responseToolbar：顶部工具栏（返回 + 关闭）
        //   - responseScroll：weight=1，占满中间
        //   - insertButtonRow：底部右下角插入按钮
        // 状态 1：弹窗 height = 50dp，仅 inputRow 可见，弹窗紧贴键盘上方无空白
        // 状态 2：弹窗 height = 键盘 height（~280dp），inputRow GONE，
        //   contentArea 占满整个弹窗高度
        container.addView(
            inputRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // contentArea（vertical）：工具栏 + 响应卡片 + 插入按钮
        val contentArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        // 1. 工具栏（顶部）
        contentArea.addView(
            responseToolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        // 2. 响应卡片（占满中间）
        contentArea.addView(
            responseScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f, // weight=1，垂直占满剩余空间
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            },
        )
        // 3. 插入按钮行（右下角）
        val insertRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        insertRow.addView(
            insertButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        contentArea.addView(
            insertRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            },
        )

        contentAreaRef = contentArea
        container.addView(
            contentArea,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f, // weight=1，垂直占满 inputRow 之下剩余空间
            ),
        )

        root = FrameLayout(context).apply {
            addView(
                container,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    /** 同步查询按钮启用状态（供 appendInputText / setInputText 调用） */
    fun updateQueryButtonState() {
        val hasText = !inputEditText.text.isNullOrBlank()
        queryButton.isEnabled = hasText
        queryButton.alpha = if (hasText) 1f else 0.4f
        inputPlaceholder.visibility = if (hasText) View.GONE else View.VISIBLE
    }

    /** 显示响应内容（一次性赋值） */
    fun showResponse(text: String) {
        cancelStream()
        responseText.text = text
        responseText.setTextColor(Color.parseColor("#222222"))
        // 自动滚到底部
        responseScroll.post {
            responseScroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    /**
     * 打字机式流式输出响应内容。
     * - 每 [intervalMs] 追加若干字符（按汉字/ASCII 智能计 1 字 = 1 步）
     * - 期间可被 [cancelStream] 中断（新的查询或取消时）
     * - 完成后自动滚到底部
     */
    fun showResponseStream(
        text: String,
        intervalMs: Long = 20L,
        charsPerTick: Int = 2,
    ) {
        cancelStream()
        if (text.isEmpty()) {
            showResponse("")
            return
        }
        responseText.text = ""
        streamHandler?.removeCallbacksAndMessages(null)
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        streamHandler = h
        var idx = 0
        val n = text.length
        val step = charsPerTick.coerceAtLeast(1)
        h.postDelayed(object : Runnable {
            override fun run() {
                // 如果中途被替换（新的流/新查询）直接退出
                if (streamHandler !== h) return
                if (idx >= n) {
                    responseScroll.post {
                        responseScroll.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                    return
                }
                val end = (idx + step).coerceAtMost(n)
                responseText.append(text.substring(idx, end))
                idx = end
                h.postDelayed(this, intervalMs)
            }
        }, intervalMs)
    }

    /** 中断当前的打字机流式输出（如果有） */
    fun cancelStream() {
        streamHandler?.removeCallbacksAndMessages(null)
        streamHandler = null
    }

    /**
     * 显示响应区（状态 1 → 状态 2）。
     * - 隐藏 inputRow（查询输入条）：查询已提交，输入条不再显示
     * - 显示 contentArea（工具栏 + 响应卡片 + 插入按钮）
     * 调用方需保证弹窗 height 已经动画到与键盘一致（通常通过 [TongBanManager.expandToFull]）。
     */
    fun showResponseArea() {
        contentAreaRef.visibility = View.VISIBLE
        // 状态 2：输入行（含 × / 输入框 / 查询按钮）整体隐藏
        inputRow.visibility = View.GONE
    }

    /**
     * 隐藏响应区（状态 2 → 状态 1 或彻底关闭）。
     * - 显示 inputRow（恢复输入条）
     * - 隐藏 contentArea
     */
    fun hideResponseArea() {
        contentAreaRef.visibility = View.GONE
        // 状态 1：输入行可见
        inputRow.visibility = View.VISIBLE
    }

    /** 当前响应区是否可见 */
    fun isResponseAreaVisible(): Boolean = contentAreaRef.visibility == View.VISIBLE

    /**
     * 在响应框内显示"思考中…"占位内容，1s 内还没结果就用这个给用户预提示。
     * 真正的响应到达后会被 [showResponse] / [showResponseStream] 覆盖。
     */
    fun showPendingHint(hint: String) {
        cancelStream()
        responseText.text = hint
        responseText.setTextColor(Color.parseColor("#9E9E9E"))
    }

    /**
     * 隐藏响应框（清空文字 + 重置颜色 + 隐藏响应区）。弹窗 height 由 TongBanManager
     * 同步收缩到 SMALL_DIALOG_HEIGHT（50dp），responseScroll GONE 后 weight=1 不分配空间。
     */
    fun hideResponse() {
        cancelStream()
        responseText.text = ""
        responseText.setTextColor(Color.parseColor("#222222"))
        hideResponseArea()
    }

    fun pasteFromClipboard() {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData? = cm.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) {
                inputEditText.text = text.trim()
                // 刷新按钮状态（查询按钮启用 + 隐藏 placeholder）
                updateQueryButtonState()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getInputText(): String = inputEditText.text?.toString() ?: ""

    fun clearInput() {
        inputEditText.setText("")
        updateQueryButtonState()
    }

    /** 隐藏粘贴按钮（已废弃：不再有独立粘贴按钮，保留方法以兼容旧调用） */
    @Suppress("unused")
    fun hidePasteButton() {
        // 不再需要显式隐藏"粘贴"按钮；长按输入框会直接粘贴。
    }

    fun setMaxHeight(heightPx: Int) {
        // 弹窗 height 由 TongBanManager 控制，保留方法以兼容旧调用
    }

    /**
     * 弹窗关闭动画：只做 alpha 淡出（height 收缩由 TongBanManager.animateDialogHeight
     * 与 InputView 端 keyboardView 高度动画同步进行）。
     */
    fun animateClose(durationMs: Long = 220L, onEnd: () -> Unit) {
        cancelStream()
        val r = root
        r.animate().cancel()
        r.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                r.alpha = 1f
                onEnd()
            }
            .start()
    }
}

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
 * - 顶部：单行输入（× 输入框 查询）
 * - 下方：可滚动响应框（TextView inside ScrollView），长按复制
 * - 总高度受 IME 限制，响应框最多 200dp，剩余空间给键盘
 */
class TongBanDialogUi(
    private val context: Context,
    private val onQuery: (String) -> Unit,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * context.resources.displayMetrics.density
    private val closeAnimHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 输入行 */
    val inputEditText: TextView
    val inputPlaceholder: TextView
    val queryButton: Button
    val closeButton: TextView

    /** 响应框（可滚动） */
    val responseScroll: ScrollView
    val responseText: TextView
    private val insertButton: Button

    val root: FrameLayout

    /** 当前打字机流式输出的 Handler；切换查询/关闭弹窗时清空 */
    private var streamHandler: android.os.Handler? = null

    init {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#FAFAFA"))
                cornerRadius = dpF(8f)
                setStroke(dp(1), Color.parseColor("#CCCCCC"))
            }
        }

        // 单行：关闭按钮 + 输入框 + 查询按钮
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        // 关闭按钮（×）
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
                // 长按直接粘贴剪贴板内容，不再弹额外的"粘贴"按钮
                pasteFromClipboard()
                // 给用户一个长按体感的震动反馈
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

        // 响应框：ScrollView + TextView
        responseText = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#222222"))
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextIsSelectable(true) // 允许选择/复制
            // 不需要 focusable，避免触发 IME
        }
        responseScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(
                responseText,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            // 响应框默认隐藏，输入后才显示
            visibility = View.GONE
        }

        // 插入按钮：点击后将回复内容插入到聊天输入框
        insertButton = Button(context).apply {
            text = "插入"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#4CAF50"))
                cornerRadius = dpF(4f)
            }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            visibility = View.GONE
            setOnClickListener {
                val resp = responseText.text?.toString().orEmpty()
                if (resp.isNotEmpty()) {
                    onInsert(resp)
                }
            }
        }

        container.addView(
            inputRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        container.addView(
            responseScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
            ).apply {
                // 默认权重 0（隐藏时不占空间），但可以动态改为有值
                height = dp(180)
                topMargin = dp(4)
            },
        )
        container.addView(
            insertButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.END
                topMargin = dp(4)
                bottomMargin = dp(8)
                marginEnd = dp(12)
            },
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
        responseScroll.visibility = View.VISIBLE
        insertButton.visibility = View.VISIBLE
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
        responseScroll.visibility = View.VISIBLE
        insertButton.visibility = View.VISIBLE
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
     * 在响应框内显示"思考中…"占位内容，1s 内还没结果就用这个给用户预提示。
     * 真正的响应到达后会被 [showResponse] / [showResponseStream] 覆盖。
     */
    fun showPendingHint(hint: String) {
        cancelStream()
        responseText.text = hint
        responseScroll.visibility = View.VISIBLE
        insertButton.visibility = View.GONE
    }

    /** 隐藏响应框 */
    fun hideResponse() {
        cancelStream()
        responseScroll.visibility = View.GONE
        insertButton.visibility = View.GONE
        responseText.text = ""
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
        // 单行结构，高度由内容决定，保留方法以兼容旧调用
    }

    /**
     * 弹窗淡出 + 向下平移（视觉上"沉回"输入法键盘消失前的位置）。
     * 与 InputView 端 keyboardView 高度 0 → fullKeyboardHeight 展开动画同步，
     * 实现"插入后弹窗平滑过渡到键盘"的无感替换效果。
     */
    fun animateClose(durationMs: Long = 200L, onEnd: () -> Unit) {
        // 取消任何正在跑的打字机流式输出，避免动画过程中还更新文字
        cancelStream()
        val r = root
        r.animate().cancel()
        val dy = r.height.toFloat()
        r.animate()
            .alpha(0f)
            .translationY(dy)
            .setDuration(durationMs)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                // 重置视觉状态，供下次打开时正常显示
                r.alpha = 1f
                r.translationY = 0f
                onEnd()
            }
            .start()
    }
}
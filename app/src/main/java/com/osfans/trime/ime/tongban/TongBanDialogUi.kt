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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 童伴智能问题浮窗 UI（精简版）
 *
 * - 单行结构：输入框 + 查询按钮
 * - 高度小（≈ 56dp），不遮挡键盘主体
 * - 查询反馈（loading/error/响应）由 Controller 通过 Toast 输出
 */
class TongBanDialogUi(
    private val context: Context,
    private val onQuery: (String) -> Unit,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * context.resources.displayMetrics.density

    /** 输入行 */
    val inputEditText: TextView
    val inputPlaceholder: TextView
    val queryButton: Button
    val closeButton: TextView

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
        inputEditText = TextView(context).apply {
            text = ""
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpF(6f)
            }
            setTextColor(Color.parseColor("#222222"))
            textSize = 14f
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        // 占位文本
        inputPlaceholder = TextView(context).apply {
            text = "请输入您的问题…"
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

        container.addView(
            inputRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        root = container
    }

    /** 同步查询按钮启用状态（供 appendInputText / setInputText 调用） */
    fun updateQueryButtonState() {
        val hasText = !inputEditText.text.isNullOrBlank()
        queryButton.isEnabled = hasText
        queryButton.alpha = if (hasText) 1f else 0.4f
        inputPlaceholder.visibility = if (hasText) View.GONE else View.VISIBLE
    }

    fun pasteFromClipboard() {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData? = cm.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) {
                inputEditText.text = text.trim()
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
        // 单行结构，高度由内容决定，保留方法以兼容旧调用
    }
}

/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import com.osfans.trime.ime.dependency.InputDependencyManager
import org.kodein.di.instance
import timber.log.Timber

/**
 * 童伴浮窗管理器
 *
 * - 持有 TongBanDialogUi + Controller
 * - 提供 show / hide / cancel 接口
 * - 处理插入回填（将 response 文本写入当前 InputConnection）
 */
class TongBanManager(
    private val context: Context,
) {
    companion object {
        @Volatile
        private var INSTANCE: TongBanManager? = null

        /** 当前实例（用于 TrimeInputMethodService.commitText 拦截文本） */
        fun getInstance(): TongBanManager? = INSTANCE

        /** 由 commitText 调用：检查弹窗是否打开且可输入 */
        @JvmStatic
        fun isShowingAndFocused(): Boolean = INSTANCE?.isShowingAndFocusedInternal() ?: false

        /** 由 commitText 调用：追加文字到弹窗 EditText */
        @JvmStatic
        fun appendInputText(text: String) {
            INSTANCE?.appendInputTextInternal(text)
        }
    }

    init {
        INSTANCE = this
    }
    private val service: com.osfans.trime.ime.core.TrimeInputMethodService by
        InputDependencyManager.getInstance().di.instance()
    private val tongBanService = TongBanService(context)
    private var ui: TongBanDialogUi? = null
    private var controller: TongBanDialogController? = null
    private var parentContainer: FrameLayout? = null
    private var container: FrameLayout? = null

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    /** 当前浮窗是否可见 */
    var isShowing: Boolean = false
    private var lastKeyboardHeightPx: Int = 0
        private set

    /**
     * 初始化容器和 UI
     */
    fun setupContainer(parent: FrameLayout) {
        if (container != null) return
        parentContainer = parent
        val containerLayout = FrameLayout(context).apply {
            visibility = View.GONE
        }
        val dialog = TongBanDialogUi(
            context = context,
            onQuery = { /* 由 controller 内部处理 */ },
            onInsert = { text -> onInsert(text) },
            onClose = { hide() },
        )
        val ctrl = TongBanDialogController(context, dialog, tongBanService)
        ctrl.onClosed = { hide() }
        // 初始时给对话框一个默认高度，避免 MATCH_PARENT 导致的循环依赖
        containerLayout.addView(
            dialog.root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(160),
                Gravity.TOP,
            ),
        )
        parent.addView(
            containerLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        container = containerLayout
        ui = dialog
        controller = ctrl
    }

    /**
     * 显示浮窗
     */
    fun show(keyboardHeightPx: Int) {
        val c = container ?: run {
            android.widget.Toast.makeText(context, "container is null", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val u = ui ?: run {
            android.widget.Toast.makeText(context, "ui is null", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val ctrl = controller ?: run {
            android.widget.Toast.makeText(context, "ctrl is null", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // 浮窗高度 = 键盘高度 * 2/5
        val dialogHeight = (keyboardHeightPx * 2 / 5).coerceAtLeast(dp(110))
        u.setMaxHeight(dialogHeight)
        // 调整容器内浮窗 view 的高度和位置
        val flp = (u.root.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(160),
                Gravity.TOP,
            )
        flp.height = dialogHeight
        flp.width = FrameLayout.LayoutParams.MATCH_PARENT
        flp.gravity = Gravity.TOP
        u.root.layoutParams = flp
        ctrl.open()
        parentContainer?.visibility = View.VISIBLE
        // 关键：扩展 IME 整体高度 = 原键盘高度 + 弹窗高度，使弹窗和键盘并存且都不被遮挡
        extendImeHeight(keyboardHeightPx, dialogHeight)
        // 动态设置外层容器高度 = 弹窗高度
        val parentLp = parentContainer?.layoutParams
        if (parentLp != null) {
            parentLp.height = dialogHeight
            parentContainer?.layoutParams = parentLp
        }
        c.visibility = View.VISIBLE
        u.root.visibility = View.VISIBLE
        c.bringToFront()
        c.requestLayout()
        u.root.requestLayout()
        // 关键：强制刷新 IME 输入连接，使后续键盘输入路由到弹窗的 EditText，而不是原 WeChat 的输入框
        try {
            u.inputEditText.post {
                u.inputEditText.requestFocus()
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                imm?.restartInput(u.inputEditText)
            }
        } catch (_: Throwable) { }
        isShowing = true
        // 调试：监听 layout 完成后再读尺寸
        u.root.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
            private var reported = false
            override fun onLayoutChange(
                v: android.view.View,
                l: Int, t: Int, r: Int, b: Int,
                ol: Int, ot: Int, orr: Int, ob: Int,
            ) {
                if (!reported && v.width > 0 && v.height > 0) {
                    reported = true
                    val loc = IntArray(2).also { v.getLocationOnScreen(it) }
                    android.widget.Toast.makeText(
                        context,
                        "show OK, dh=$dialogHeight, real=${v.width}x${v.height}, xy=(${loc[0]},${loc[1]})",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    v.removeOnLayoutChangeListener(this)
                }
            }
        })
    }

    /**
     * 隐藏浮窗
     */
    fun hide(keyboardHeightPx: Int = lastKeyboardHeightPx) {
        val c = container ?: return
        val ctrl = controller ?: return
        ctrl.close()
        c.visibility = View.GONE
        // 恢复 IME 高度
        if (keyboardHeightPx > 0) restoreImeHeight(keyboardHeightPx)
        isShowing = false
    }

    /** 切换显示 */
    fun toggle(keyboardHeightPx: Int) {
        lastKeyboardHeightPx = keyboardHeightPx
        if (isShowing) hide(keyboardHeightPx) else show(keyboardHeightPx)
    }

    /** 是否弹窗正在显示且输入框已聚焦（用于 commitText 路由判断） */
    fun isShowingAndFocusedInternal(): Boolean {
        if (!isShowing) return false
        val et = ui?.inputEditText ?: return false
        return et.hasFocus() || et.requestFocus()
    }

    /** 由 service.commitText 调用：将文字追加到弹窗 EditText（不走系统 InputConnection） */
    fun appendInputTextInternal(text: String) {
        if (!isShowing) return
        val et = ui?.inputEditText ?: return
        // 在 UI 线程执行 append
        et.post {
            val cur = et.text?.toString().orEmpty()
            val cleaned = cleanInput(text)
            if (cleaned.isEmpty()) return@post
            // 限制 200 个汉字长度
            val merged = (cur + cleaned)
            val maxLen = 200
            val truncated = if (merged.length > maxLen) merged.substring(0, maxLen) else merged
            et.setText(truncated)
            et.setSelection(truncated.length)
        }
    }

    /** 输入清洗：去除首尾空白、换行、特殊符号 */
    private fun cleanInput(text: String): String {
        return text
            .replace(Regex("[\\r\\n\\t]+"), "")
            .trim()
    }

    /** 扩展 IME 整体高度 = 键盘高度 + 弹窗高度 */
    private fun extendImeHeight(keyboardHeightPx: Int, popupHeightPx: Int) {
        val totalHeight = keyboardHeightPx + popupHeightPx
        service.setImeHeight(totalHeight)
        android.widget.Toast.makeText(context, "ime height=$totalHeight", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 恢复 IME 高度到弹窗未打开时的尺寸 */
    private fun restoreImeHeight(keyboardHeightPx: Int) {
        service.resetImeHeight(keyboardHeightPx)
    }

    /** 强制取消进行中的请求（不关闭浮窗） */
    fun cancelInflight() {
        controller?.cancelRequest()
    }

    /** 销毁资源（幂等） */
    fun release() {
        if (controller == null && ui == null && container == null) return
        controller?.release()
        controller = null
        ui = null
        container = null
        isShowing = false
    }

    private fun onInsert(text: String) {
        try {
            val ic: InputConnection? = service.currentInputConnection
            if (ic != null) {
                ic.commitText(text, 1)
            }
        } catch (e: Exception) {
            Timber.w(e, "insert text failed")
        }
        hide()
    }
}

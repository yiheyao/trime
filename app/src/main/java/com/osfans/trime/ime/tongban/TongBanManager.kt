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
import com.osfans.trime.BuildConfig
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

        /** 由 onKey 调用：删除弹窗输入框最后一个字符 */
        @JvmStatic
        fun deleteLastChar() {
            INSTANCE?.deleteLastCharInternal()
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
    /** 暴露给 TrimeInputMethodService.onComputeInsets 用于读取 IME 顶部位置 */
    internal val parentContainerPublic: FrameLayout? get() = parentContainer
    private var container: FrameLayout? = null

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    /** 当前浮窗是否可见 */
    var isShowing: Boolean = false
    private var lastKeyboardHeightPx: Int = 0
        private set

    /**
     * 查询提交回调：点击"查询"按钮后由 controller 触发，InputView 收到后隐藏键盘。
     * 弹窗关闭时由 [hide] 触发 InputView 恢复键盘。
     */
    var onQuerySubmitted: (() -> Unit)? = null

    /**
     * 弹窗关闭回调：弹窗从显示变为隐藏时触发，InputView 收到后恢复键盘显示。
     */
    var onDialogClosed: (() -> Unit)? = null

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
        // 桥接 controller → manager：点击查询后通知 InputView 隐藏键盘
        ctrl.onQueryStarted = {
            onQuerySubmitted?.invoke()
        }
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
        // 浮窗高度（单行 UI，不需要固定高度，由内容自适应）
        u.setMaxHeight(0)
        // 调整容器内浮窗 view 的高度和位置：wrap_content 让 dialog 高度由内容决定
        val flp = (u.root.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            )
        flp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        flp.width = FrameLayout.LayoutParams.MATCH_PARENT
        flp.gravity = Gravity.TOP
        u.root.layoutParams = flp
        ctrl.open()
        parentContainer?.visibility = View.VISIBLE
        // tongBanContainer 在 InputView 中由 topOfParent 约束固定在顶部，高度 wrap_content
        // = dialog 高度。InputView 整体高度 = 弹窗 + 键盘，IME 窗口自动向上扩展。
        val parentLp = parentContainer?.layoutParams
        if (parentLp != null) {
            parentLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            parentContainer?.layoutParams = parentLp
        }
        c.visibility = View.VISIBLE
        u.root.visibility = View.VISIBLE
        c.bringToFront()
        c.requestLayout()
        u.root.requestLayout()
        // 弹窗显示时，需要让 InputView 重新布局并触发 onComputeInsets 更新 touchable region
        // （否则弹窗点击事件会穿透到下层聊天窗口）
        service.inputViewPublic?.requestLayout()
        // TextView 不需要焦点，所有键盘输入已通过 commitText 拦截路由到此处
        try {
            u.updateQueryButtonState()
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
                    if (BuildConfig.DEBUG) {
                        val loc = IntArray(2).also { v.getLocationOnScreen(it) }
                        android.widget.Toast.makeText(
                            context,
                            "弹窗 ${v.width}x${v.height}, y=${loc[1]}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
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
        // 恢复 tongBanContainer 高度为 wrap_content（弹窗关闭后键盘完整显示）
        val parentLp = parentContainer?.layoutParams
        if (parentLp != null) {
            parentLp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            parentContainer?.layoutParams = parentLp
        }
        // 隐藏外层容器（不影响 IME 高度）
        parentContainer?.visibility = View.GONE
        val wasShowing = isShowing
        isShowing = false
        // 弹窗关闭后重新布局，让 onComputeInsets 把 touchable region 收回 keyboardView
        service.inputViewPublic?.requestLayout()
        // 通知 InputView 恢复键盘
        if (wasShowing) onDialogClosed?.invoke()
    }

    /** 切换显示 */
    fun toggle(keyboardHeightPx: Int) {
        lastKeyboardHeightPx = keyboardHeightPx
        if (isShowing) hide(keyboardHeightPx) else show(keyboardHeightPx)
    }

    /**
     * 是否弹窗正在显示（用于 commitText 路由判断）。
     * 注意：弹窗内部使用 TextView 而非 EditText，不持有焦点；
     * 弹窗打开期间，所有键盘输入都应路由到弹窗，所以只看 isShowing 即可。
     */
    fun isShowingAndFocusedInternal(): Boolean {
        return isShowing && ui != null
    }

    /** 由 service.commitText 调用：将文字追加到弹窗 TextView（不走系统 InputConnection） */
    fun appendInputTextInternal(text: String) {
        if (!isShowing) return
        val tv = ui?.inputEditText ?: return
        // 在 UI 线程执行 append
        tv.post {
            val cur = tv.text?.toString().orEmpty()
            val cleaned = cleanInput(text)
            if (cleaned.isEmpty()) return@post
            // 限制 200 个汉字长度
            val merged = (cur + cleaned)
            val maxLen = 200
            val truncated = if (merged.length > maxLen) merged.substring(0, maxLen) else merged
            tv.text = truncated
            ui?.updateQueryButtonState()
        }
    }

    /** 删除弹窗输入框最后一个字符（退格键） */
    fun deleteLastCharInternal() {
        if (!isShowing) return
        val tv = ui?.inputEditText ?: return
        tv.post {
            val cur = tv.text?.toString().orEmpty()
            if (cur.isEmpty()) return@post
            tv.text = cur.dropLast(1)
            ui?.updateQueryButtonState()
        }
    }

    /** 输入清洗：去除首尾空白、换行、特殊符号 */
    private fun cleanInput(text: String): String {
        return text
            .replace(Regex("[\\r\\n\\t]+"), "")
            .trim()
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

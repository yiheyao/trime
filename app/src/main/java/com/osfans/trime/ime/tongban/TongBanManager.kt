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
    /** 弹窗展开后"完整"高度（包含响应 + 插入按钮），用于动画目标值 */
    private var fullDialogHeight: Int = 0
    private var dialogHeightAnim: android.animation.ValueAnimator? = null

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
            // 通知 InputView 收起键盘（height H → 0 动画）
            // 弹窗 size 由 wrap_content 决定（响应区 + 插入按钮始终 VISIBLE，placeholder 文字），
            // 点查询瞬间弹窗 size 已经是 300dp 稳定状态，不会再有"分阶段扩展"视觉变化。
            onQuerySubmitted?.invoke()
        }
        // 用户点 ×：走平滑关闭（弹窗淡出 + 键盘展开）
        ctrl.onCloseClicked = {
            dismissAndRestore()
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
        // 弹窗 layoutParams.height = WRAP_CONTENT（弹窗 size 由内容决定）。
        // 响应区 + 插入按钮 始终 VISIBLE（响应未到时显示 placeholder 灰色文字），
        // 所以 wrap_content 本身就是"完整高度"，show 时刻弹窗 size 已经是 300dp 稳定状态，
        // 不会因响应到达而发生 height 跳变。
    }

    /** 弹窗 height 同步动画：用于 dismissAndRestore 关闭流程，
     * 弹窗 height 从 fullDialogHeight 收缩到 0，与 InputView 端 keyboardView
     * height 0 → fullKeyboardHeight 展开动画同步。
     */
    fun animateDialogHeightToZero(durationMs: Long = 220L) {
        val root = ui?.root ?: return
        val flp = root.layoutParams as? FrameLayout.LayoutParams ?: return
        val startH = root.height
        if (startH <= 0) return
        dialogHeightAnim?.cancel()
        dialogHeightAnim = android.animation.ValueAnimator.ofInt(startH, 0).apply {
            duration = durationMs
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                val h = (it.animatedValue as Int).coerceAtLeast(0)
                flp.height = h
                root.layoutParams = flp
            }
            start()
        }
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
        // 清理弹窗 alpha + height（避免动画中间态残留）
        dialogHeightAnim?.cancel()
        dialogHeightAnim = null
        val root = ui?.root
        if (root != null) {
            val flp = root.layoutParams as? FrameLayout.LayoutParams
            if (flp != null) {
                flp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                root.layoutParams = flp
            }
            root.alpha = 1f
        }
        // 弹窗关闭后重新布局，让 onComputeInsets 把 touchable region 收回 keyboardView
        service.inputViewPublic?.requestLayout()
        // 通知 InputView 恢复键盘
        if (wasShowing) onDialogClosed?.invoke()
    }

    /**
     * 平滑关闭：弹窗 alpha 淡出 + 键盘同步展开（无感替换输入法键盘）。
     * 弹窗 size = WRAP_CONTENT 稳定在 300dp（响应区/插入按钮始终 VISIBLE + placeholder），
     * 所以关闭时只做 alpha 淡出，height 不变（关闭后 GONE 不占空间）。
     */
    fun dismissAndRestore() {
        val u = ui ?: return hide()
        if (!isShowing) return
        // 通知 InputView 展开键盘（height 0 → fullKeyboardHeight 动画）
        onDialogClosed?.invoke()
        // 弹窗 alpha 淡出（与键盘展开同步）
        u.animateClose {
            hide()
        }
    }

    /**
     * IME 关闭时强制重置弹窗状态（不触发 onDialogClosed 回调，避免 keyboardView 干扰）。
     * 解决场景：系统返回键关闭 IME 时，弹窗 view 仍残留 visibility=VISIBLE，
     * manager.isShowing 仍 = true，下次点"童"按钮时 toggle 走 hide 分支，弹窗 反而消失。
     */
    fun forceReset() {
        val c = container ?: return
        controller?.cancelRequest()
        c.visibility = View.GONE
        parentContainer?.visibility = View.GONE
        ui?.root?.visibility = View.GONE
        dialogHeightAnim?.cancel()
        dialogHeightAnim = null
        // 弹窗 height 还原 wrap_content（让下次 show 时不受影响）
        val root = ui?.root
        if (root != null) {
            val flp = root.layoutParams as? FrameLayout.LayoutParams
            if (flp != null) {
                flp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                root.layoutParams = flp
            }
            root.alpha = 1f
        }
        isShowing = false
        // 不调 onDialogClosed（避免 setKeyboardVisible 干扰）
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
        // 1. 立刻把响应文本写入聊天输入框
        try {
            val ic: InputConnection? = service.currentInputConnection
            if (ic != null) {
                ic.commitText(text, 1)
            }
        } catch (e: Exception) {
            Timber.w(e, "insert text failed")
        }
        // 2. 弹窗淡出 + 键盘同步展开（无感替换输入法键盘）
        dismissAndRestore()
    }
}

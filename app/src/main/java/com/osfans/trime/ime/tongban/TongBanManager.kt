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

        /** 弹窗小高度：仅 inputRow 一行（约 50dp），与键盘之间无空白 */
        const val SMALL_DIALOG_HEIGHT_DP = 50
        /** 弹窗 height / 键盘 height 动画时长，与 InputView.setKeyboardVisible 保持一致 */
        const val HEIGHT_ANIM_DURATION_MS = 220L
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
     * 弹窗"小"高度（仅 inputRow，约 50dp）。状态 1 时使用，紧贴键盘上方，
     * responseScroll 处于 GONE，弹窗与键盘之间无空白。
     */
    private var smallDialogHeight: Int = 0
    /**
     * 弹窗"完整"高度（与键盘一致），用于 expandToFull 动画目标值。
     * 由 [show] / [expandToFull] 调用方传入的 keyboardHeightPx 决定。
     */
    private var fullDialogHeight: Int = 0
    private var dialogHeightAnim: android.animation.ValueAnimator? = null
    /** 标记当前是否处于展开状态（响应区可见 + 弹窗占满键盘区域） */
    private var isExpanded: Boolean = false

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
            onClose = { dismissAndRestore() },
            onBack = { collapseToSmall() },
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
     * 显示浮窗（状态 1：小弹窗，仅 inputRow）。
     * - 弹窗 height 固定为 [SMALL_DIALOG_HEIGHT_DP]（约 50dp）
     * - responseScroll 保持 GONE，不占空间，弹窗与键盘之间无空白
     * - 弹窗紧贴 keyboardView 上方（InputView 端 above(keyboardView) 约束）
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
        lastKeyboardHeightPx = keyboardHeightPx
        smallDialogHeight = dp(SMALL_DIALOG_HEIGHT_DP)
        fullDialogHeight = keyboardHeightPx.takeIf { it > 0 } ?: smallDialogHeight

        // 取消残留 height 动画
        dialogHeightAnim?.cancel()
        dialogHeightAnim = null

        // 调整容器内浮窗 view 的高度和位置：弹窗 height 固定 SMALL_DIALOG_HEIGHT_DP
        val flp = (u.root.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            )
        flp.height = smallDialogHeight
        flp.width = FrameLayout.LayoutParams.MATCH_PARENT
        flp.gravity = Gravity.TOP
        u.root.layoutParams = flp
        // 状态 1 关键：响应区 GONE，不占空间 → 弹窗仅显示 inputRow
        u.hideResponseArea()
        // 弹窗根 alpha 复位（防止上次动画残留）
        u.root.alpha = 1f
        isExpanded = false

        ctrl.open()
        parentContainer?.visibility = View.VISIBLE
        // tongBanContainer 在 InputView 中约束为 above(keyboardView)，高度 wrap_content
        // = dialog 高度（状态 1 时 = 50dp）。
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
        service.inputViewPublic?.requestLayout()
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
                            "弹窗(状态1) ${v.width}x${v.height}, y=${loc[1]}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    v.removeOnLayoutChangeListener(this)
                }
            }
        })
    }

    /**
     * 展开弹窗到完整高度（状态 2：弹窗 height = 键盘 height，responseScroll 可见）。
     * - 调用时机：用户点击"查询"后，由 InputView.setKeyboardVisible(false) 同步触发。
     * - 弹窗 height 跟随 keyboardView height 同步变化，弹窗 + 键盘 = 恒定 fullDialogHeight，
     *   因此 IME 总高度不变，弹窗始终紧贴 keyboardView 上方覆盖原键盘区域，无"高白框闪烁"。
     * - 弹窗 root 高度由 InputView 端 keyboardView 动画驱动（[syncDialogHeight] 回调）。
     */
    fun expandToFull() {
        val u = ui ?: return
        val root = u.root
        // #region debug-point C:expandToFull
        val kbv = service.inputViewPublic?.keyboardView
        Timber.i("[WXKB-DEBUG] C:expandToFull isExpanded=$isExpanded fullDialogH=$fullDialogHeight kbH=${kbv?.height} kbVis=${kbv?.visibility}")
        // #endregion
        if (fullDialogHeight <= smallDialogHeight) {
            // 没有 keyboardHeightPx 信息时直接展开到合理值
            fullDialogHeight = dp(280)
        }
        // 显示响应区（让 weight=1 开始分配空间）
        u.showResponseArea()
        isExpanded = true
        // 弹窗 height 初始设为 0（让 InputView 端 keyboardView 高度动画驱动后续变化）
        val flp = root.layoutParams as? FrameLayout.LayoutParams ?: return
        dialogHeightAnim?.cancel()
        dialogHeightAnim = null
        flp.height = 0
        root.layoutParams = flp
        root.requestLayout()
        if (BuildConfig.DEBUG) {
            android.widget.Toast.makeText(
                context,
                "弹窗展开(跟随键盘) kb_total=${fullDialogHeight}px",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * 由 InputView 在 keyboardView 高度动画的每一帧调用，让弹窗 height 跟随 keyboardView height：
     * 弹窗 height = fullDialogHeight - keyboardView.currentHeight
     * 当 keyboardView 完全收起（height=0）时，弹窗 height = fullDialogHeight（占满原键盘区域）。
     * 当 keyboardView 完全展开（height=fullDialogHeight）时，弹窗 height = 0。
     */
    fun syncDialogHeight(keyboardHeightPx: Int) {
        if (!isExpanded) return
        val u = ui ?: return
        val root = u.root
        val flp = root.layoutParams as? FrameLayout.LayoutParams ?: return
        val target = (fullDialogHeight - keyboardHeightPx).coerceIn(0, fullDialogHeight)
        if (flp.height != target) {
            flp.height = target
            root.layoutParams = flp
        }
    }

    /**
     * 弹窗 height 同步动画（用于 dismissAndRestore 关闭流程）。
     * 弹窗 height 从 [fullDialogHeight] 收缩到 0，
     * 与 InputView 端 keyboardView height 0 → fullKeyboardHeight 展开动画同步。
     */
    fun animateDialogHeightToZero(durationMs: Long = HEIGHT_ANIM_DURATION_MS) {
        val root = ui?.root ?: return
        val flp = root.layoutParams as? FrameLayout.LayoutParams ?: return
        val startH = if (root.height > 0) root.height else fullDialogHeight
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
        isExpanded = false
        // 清理弹窗 alpha + height（避免动画中间态残留）
        dialogHeightAnim?.cancel()
        dialogHeightAnim = null
        val root = ui?.root
        if (root != null) {
            val flp = root.layoutParams as? FrameLayout.LayoutParams
            if (flp != null) {
                // 还原弹窗 height 为 smallDialogHeight（保证下次 show 时从 50dp 开始）
                flp.height = smallDialogHeight.takeIf { it > 0 } ?: dp(SMALL_DIALOG_HEIGHT_DP)
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
     * 平滑关闭：弹窗 height 收缩到 0 + 键盘同步展开（无感替换输入法键盘）。
     * - 弹窗 height 由 [syncDialogHeight] 跟随 keyboardView 高度反向变化：
     *   keyboardView 高度从 0 → fullKeyboardHeight 时，弹窗 height 从 fullDialogHeight → 0，
     *   两者最终位置/大小一致（弹窗消失 = 键盘显示），实现"无感替换"。
     * - 弹窗 height 收为 0 后由 [hide] 兜底：visibility = GONE、alpha 复位、height 重置为 smallDialogHeight。
     */
    fun dismissAndRestore() {
        val u = ui ?: return hide()
        if (!isShowing) return
        // 先停掉自身的 height 动画（弹窗 height 现在由 syncDialogHeight 跟随 keyboardView）
        dialogHeightAnim?.cancel()
        dialogHeightAnim = null
        // 通知 InputView 展开键盘（height 0 → fullKeyboardHeight 动画），
        // 弹窗 height 会通过 syncDialogHeight 自动同步减少
        onDialogClosed?.invoke()
        if (isExpanded) {
            // 展开态：弹窗 height 由 syncDialogHeight 跟随 keyboardView 减少，
            // 等动画结束后才真正 hide。
            val startH = if (u.root.height > 0) u.root.height else fullDialogHeight
            // 用一个等待 runnable：等弹窗高度归零后再 hide（通常 ~220ms）
            val h = android.os.Handler(android.os.Looper.getMainLooper())
            h.postDelayed({
                // 此时 syncDialogHeight 已经把弹窗 height 设为 0，inputView 的 keyboardView 也展开到 fullKeyboardHeight
                hide()
            }, HEIGHT_ANIM_DURATION_MS + 20L)
        } else {
            // 未展开（小弹窗）：直接 alpha 淡出
            u.animateClose { hide() }
        }
    }

    /**
     * 从状态 2 收缩回状态 1（点击 ← 返回按钮调用）。
     * - 弹窗 height 由 [syncDialogHeight] 跟随 keyboardView 高度变化：
     *   keyboardView height 从 0 → fullKeyboardHeight 时，弹窗 height 从 fullDialogHeight → 0
     *   但我们要让弹窗最终停留在 smallDialogHeight（50dp），所以动画结束后手动设回去。
     * - 键盘同步展开（onDialogClosed → setKeyboardVisible(true)），与弹窗收缩同步进行
     * - 响应文本清空（重新查询时不会显示上一次的响应）
     */
    fun collapseToSmall() {
        val u = ui ?: return
        if (!isShowing || !isExpanded) return
        // 先停掉自身的 height 动画（弹窗 height 由 syncDialogHeight 跟随 keyboardView）
        dialogHeightAnim?.cancel()
        dialogHeightAnim = null
        // 通知 InputView 展开键盘（height 0 → fullKeyboardHeight 动画），
        // 弹窗 height 通过 syncDialogHeight 自动从 fullDialogHeight → 0
        onDialogClosed?.invoke()
        // 动画结束后（约 220ms）：清空响应内容 + 弹窗 height 恢复 smallDialogHeight + 恢复 inputRow
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        h.postDelayed({
            controller?.cancelRequest()
            u.hideResponse()
            // 动画结束后弹窗 height=0（被 syncDialogHeight 设为 0），
            // 这里恢复到 smallDialogHeight（50dp）作为状态 1 的初始高度
            val flp = u.root.layoutParams as? FrameLayout.LayoutParams
            if (flp != null) {
                flp.height = smallDialogHeight.takeIf { it > 0 } ?: dp(SMALL_DIALOG_HEIGHT_DP)
                u.root.layoutParams = flp
            }
            isExpanded = false
        }, HEIGHT_ANIM_DURATION_MS + 20L)
        if (BuildConfig.DEBUG) {
            android.widget.Toast.makeText(
                context,
                "弹窗收缩(跟随键盘)",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * IME 关闭时强制重置弹窗状态（不触发 onDialogClosed 回调，避免 keyboardView 干扰）。
     * 解决场景：系统返回键关闭 IME 时，弹窗 view 仍残留 visibility=VISIBLE，
     * manager.isShowing 仍 = true，下次点"童"按钮时 toggle 走 hide 分支，弹窗 反而消失。
     */
    fun forceReset() {
        val c = container ?: return
        // #region debug-point C:forceReset
        val kbv = service.inputViewPublic?.keyboardView
        Timber.i("[WXKB-DEBUG] C:forceReset entry isShowing=$isShowing isExpanded=$isExpanded kbH=${kbv?.height} kbVis=${kbv?.visibility}")
        // #endregion
        controller?.cancelRequest()
        c.visibility = View.GONE
        parentContainer?.visibility = View.GONE
        ui?.root?.visibility = View.GONE
        dialogHeightAnim?.cancel()
        dialogHeightAnim = null
        isExpanded = false
        // 弹窗 height 还原为 smallDialogHeight（让下次 show 时从 50dp 开始）
        val root = ui?.root
        if (root != null) {
            val flp = root.layoutParams as? FrameLayout.LayoutParams
            if (flp != null) {
                flp.height = smallDialogHeight.takeIf { it > 0 } ?: dp(SMALL_DIALOG_HEIGHT_DP)
                root.layoutParams = flp
            }
            root.alpha = 1f
        }
        isShowing = false
        // 不调 onDialogClosed（避免 setKeyboardVisible 干扰）
        // #region debug-point C:forceReset-exit
        Timber.i("[WXKB-DEBUG] C:forceReset exit (keyboardView NOT restored) kbH=${kbv?.height} kbVis=${kbv?.visibility}")
        // #endregion
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
            // 只去掉换行/制表符，保留空格（用户在查询输入框可能需要输入空格）
            val cleaned = text.replace(Regex("[\\r\\n\\t]+"), "")
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

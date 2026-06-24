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
    private val service: com.osfans.trime.ime.core.TrimeInputMethodService by
        InputDependencyManager.getInstance().di.instance()
    private val tongBanService = TongBanService(context)
    private var ui: TongBanDialogUi? = null
    private var controller: TongBanDialogController? = null
    private var container: FrameLayout? = null

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    /** 当前浮窗是否可见 */
    var isShowing: Boolean = false
        private set

    /**
     * 初始化容器和 UI
     */
    fun setupContainer(parent: FrameLayout) {
        if (container != null) return
        val containerLayout = FrameLayout(context).apply {
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val dialog = TongBanDialogUi(
            context = context,
            onQuery = { /* 由 controller 内部处理 */ },
            onInsert = { text -> onInsert(text) },
            onClose = { hide() },
        )
        val ctrl = TongBanDialogController(context, dialog, tongBanService)
        ctrl.onClosed = { hide() }
        containerLayout.addView(
            dialog.root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
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
        val c = container ?: return
        val u = ui ?: return
        val ctrl = controller ?: return
        // 浮窗高度 = 键盘高度 * 2/5
        val dialogHeight = (keyboardHeightPx * 2 / 5).coerceAtLeast(dp(160))
        u.setMaxHeight(dialogHeight)
        // 调整容器内浮窗 view 的高度
        val flp = (u.root.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        flp.height = dialogHeight
        flp.gravity = Gravity.TOP
        u.root.layoutParams = flp
        ctrl.open()
        c.visibility = View.VISIBLE
        isShowing = true
    }

    /**
     * 隐藏浮窗
     */
    fun hide() {
        val c = container ?: return
        val ctrl = controller ?: return
        ctrl.close()
        c.visibility = View.GONE
        isShowing = false
    }

    /** 切换显示 */
    fun toggle(keyboardHeightPx: Int) {
        if (isShowing) hide() else show(keyboardHeightPx)
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

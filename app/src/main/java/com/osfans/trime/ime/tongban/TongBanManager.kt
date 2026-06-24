/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.Context
import android.view.View
import android.view.inputmethod.InputConnection
import com.osfans.trime.ime.dependency.InputDependencyManager
import org.kodein.di.instance
import splitties.dimensions.dp
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
    private var container: android.widget.FrameLayout? = null

    /** 当前浮窗是否可见 */
    var isShowing: Boolean = false
        private set

    /**
     * 初始化容器和 UI
     */
    fun setupContainer(parent: android.widget.FrameLayout) {
        if (container != null) return
        val containerLayout = android.widget.FrameLayout(context).apply {
            visibility = View.GONE
            // 默认充满父布局上层区域
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val dialog = TongBanDialogUi(
            ctx = context,
            onQuery = { /* 由 controller 内部处理 */ },
            onInsert = { text -> onInsert(text) },
            onClose = { hide() },
        )
        val ctrl = TongBanDialogController(context, dialog, tongBanService)
        ctrl.onClosed = { hide() }
        containerLayout.addView(
            dialog.root,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        parent.addView(
            containerLayout,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
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
        u.root.layoutParams = (u.root.layoutParams
            ?: android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )).apply {
            height = dialogHeight
            gravity = android.view.Gravity.TOP
        }
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

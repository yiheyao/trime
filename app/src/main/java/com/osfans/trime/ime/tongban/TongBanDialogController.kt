/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.Toast
import com.osfans.trime.BuildConfig
import timber.log.Timber

/**
 * 童伴问答浮窗控制器（精简版）
 *
 * - 输入清洗、查询防抖、loading 状态、网络异常展示
 * - 所有 UI 反馈通过 Toast 输出（弹窗只有一行，没有 responseScroll）
 */
class TongBanDialogController(
    private val context: Context,
    private val ui: TongBanDialogUi,
    private val service: TongBanService,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentRequest: TongBanRequest? = null
    private var querying = false
    private var lastNetworkType: String? = null
    /** 当前 pending 提示的回调，响应到达后或取消时需要清掉 */
    private var pendingHandler: Runnable? = null
    /**
     * 查询发起回调：onQueryClick 成功提交后触发，Manager 收到后
     * 会通过 InputView 隐藏键盘，使弹窗独占 IME 区域。
     */
    var onQueryStarted: (() -> Unit)? = null
    /**
     * 关闭按钮点击回调：用户主动点 × 时触发，Manager 收到后
     * 走 dismissAndRestore 路径（弹窗淡出 + 键盘展开）。
     */
    var onCloseClicked: (() -> Unit)? = null

    init {
        ui.queryButton.setOnClickListener { onQueryClick() }
        ui.closeButton.setOnClickListener { onCloseClick() }
        // 长按输入框直接粘贴（监听器在 TongBanDialogUi 中绑定）
        recordNetworkType()
    }

    /** 打开浮窗（每次打开都重置 session） */
    fun open() {
        service.resetSession()
        ui.clearInput()
        ui.hideResponse()
        ui.hidePasteButton()
        querying = false
        currentRequest?.cancel()
        currentRequest = null
        pendingHandler?.let { mainHandler.removeCallbacks(it) }
        pendingHandler = null
    }

    /** 关闭浮窗（取消进行中的请求、重置 session） */
    fun close() {
        cancelRequest()
        service.resetSession()
        ui.clearInput()
        ui.hideResponse()
        ui.hidePasteButton()
        querying = false
    }

    /** 强制中断进行中的请求（APP 切后台等） */
    fun cancelRequest() {
        currentRequest?.cancel()
        currentRequest = null
        // 取消 pending 预提示，避免延迟弹"思考中…"
        pendingHandler?.let { mainHandler.removeCallbacks(it) }
        pendingHandler = null
        querying = false
    }

    fun release() {
        cancelRequest()
    }

    private fun recordNetworkType() {
        lastNetworkType = currentNetworkType()
    }

    /**
     * 仅在 Debug 版本打印 / 弹出调试信息。
     * - Timber.i/d 包在这里 → Release 完全不调用，零开销。
     * - Toast 同样在 Release 屏蔽，避免影响用户。
     */
    private fun debugToast(text: String) {
        if (!BuildConfig.DEBUG) return
        showToastAtTop(text)
    }

    private fun debugLog(format: String, vararg args: Any?) {
        if (!BuildConfig.DEBUG) return
        Timber.tag("TongBan").i(format, *args)
    }

    private fun onQueryClick() {
        debugLog("onQueryClick")
        // 点击查询时震动一下，给用户查询的体感反馈
        performQueryHaptic()
        if (querying) {
            showToastAtTop("查询中，请稍候")
            return
        }
        val raw = ui.getInputText()
        val cleaned = cleanInput(raw)
        debugLog("onQueryClick raw='%s' cleaned='%s'", raw, cleaned)
        if (cleaned.isEmpty()) {
            showToastAtTop("请输入问题")
            return
        }
        // 网络切换检测
        val nowType = currentNetworkType()
        if (lastNetworkType != null && nowType != lastNetworkType) {
            cancelRequest()
            showToastAtTop("网络已切换，请重试")
            lastNetworkType = nowType
            return
        }
        lastNetworkType = nowType

        querying = true
        debugToast("查询中… cleaned='$cleaned'")
        val request = TongBanNetworkClient.newRequest()
        currentRequest = request

        // 1 秒内没拿到响应 → 在响应框显示"思考中…"预提示；
        // 如果 < 1s 就回来了，pendingRunnable 会被 cancel 掉。
        val pendingRunnable = Runnable {
            if (querying) {
                ui.showPendingHint(PENDING_HINT)
            }
        }
        pendingHandler = pendingRunnable
        mainHandler.removeCallbacks(pendingRunnable)
        mainHandler.postDelayed(pendingRunnable, PENDING_DELAY_MS)

        // 通知 Manager 隐藏键盘，让弹窗独占 IME 区域
        onQueryStarted?.invoke()

        service.sendMessage(cleaned, request, object : TongBanNetworkClient.TongBanCallback {
            override fun onResult(result: TongBanResult) {
                mainHandler.post {
                    handleResult(cleaned, result)
                }
            }
        })
    }

    private fun showToastAtTop(text: String) {
        val t = Toast.makeText(context, text, Toast.LENGTH_LONG)
        t.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 200)
        t.show()
    }

    private fun handleResult(originalText: String, result: TongBanResult) {
        // 响应到达：清除 pending 预提示
        pendingHandler?.let { mainHandler.removeCallbacks(it) }
        pendingHandler = null
        querying = false
        currentRequest = null
        debugLog("handleResult %s", result.javaClass.simpleName)
        when (result) {
            is TongBanResult.Success -> {
                val resp = service.parseSuccess(result.body)
                debugLog("Success body=%s", result.body)
                if (resp == null || resp.response.isBlank()) {
                    showToastAtTop("服务器返回为空")
                    ui.showResponse("（服务器返回为空）")
                } else {
                    // 成功响应：打字机流式输出到响应框
                    ui.showResponseStream(resp.response)
                    debugToast("响应: ${resp.response.take(50)}")
                }
            }
            is TongBanResult.Unauthorized -> {
                ui.showResponse("⚠ 未授权 (401)\n请检查登录状态")
                showToastAtTop("未授权 (401)")
            }
            is TongBanResult.RateLimited -> {
                ui.showResponse("⚠ 请求过于频繁 (429)\n请稍后再试")
                showToastAtTop("请求过于频繁 (429)")
            }
            is TongBanResult.ServerError -> {
                ui.showResponse("⚠ 服务器错误 (5xx)\n${result.body.take(200)}")
                showToastAtTop("服务器错误 (5xx)")
            }
            is TongBanResult.UnknownError -> {
                ui.showResponse("⚠ 未知错误\n${result.body.take(200)}")
                showToastAtTop("未知错误")
            }
            TongBanResult.Timeout -> {
                ui.showResponse("⏱ 请求超时\n网络不稳定或服务繁忙，请重试")
                showToastAtTop("请求超时")
            }
            TongBanResult.NoNetwork -> {
                ui.showResponse("📡 无网络\n请检查网络连接后重试")
                showToastAtTop("无网络")
            }
            TongBanResult.Cancelled -> {
                // 不展示任何提示
            }
        }
    }

    private fun onCloseClick() {
        // 优先用 onCloseClicked 路径（Manager 收到后走平滑关闭：弹窗淡出 + 键盘展开）
        // 没有绑定时回退到原 onClosed 路径（立即关闭）
        if (onCloseClicked != null) {
            onCloseClicked?.invoke()
            return
        }
        close()
        onClosed?.invoke()
    }

    /** 关闭时回调，用于通知 InputView 隐藏浮窗 */
    var onClosed: (() -> Unit)? = null

    /**
     * 输入清洗规则
     *  - 去除首尾空白
     *  - 去除换行
     *  - 过滤常见特殊符号
     *  - 限制最大长度 200 个汉字（按字符截断）
     */
    private fun cleanInput(input: String): String {
        if (input.isEmpty()) return ""
        var s = input
        s = s.trim()
        s = s.replace("\n", "").replace("\r", "")
        s = s.replace(Regex("[\\p{Cntrl}]"), "")
        if (s.length > MAX_INPUT_CHARS) {
            s = s.substring(0, MAX_INPUT_CHARS)
        }
        return s
    }

    private fun currentNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                ?: return "unknown"
            val network = cm.activeNetwork ?: return "none"
            val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
            when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * 点击查询按钮时的震动反馈。
     * 优先尝试 HapticFeedbackConstants.CONFIRM（Android 12+），否则回退到 LONG_PRESS。
     * 使用 View 自身的 performHapticFeedback 不需要额外权限。
     */
    private fun performQueryHaptic() {
        val view = ui.queryButton
        val hfc =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        try {
            val flags =
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            view.performHapticFeedback(hfc, flags)
        } catch (e: Exception) {
            // 设备不支持时静默失败，不影响主流程
        }
    }

    companion object {
        const val MAX_INPUT_CHARS = 200
        /** 1s 内没拿到响应就先在响应框显示预提示 */
        const val PENDING_DELAY_MS = 1000L
        const val PENDING_HINT = "🤔 正在思考中…"
    }
}

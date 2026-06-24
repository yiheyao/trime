/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * 童伴问答 Session 管理
 *
 * 生命周期：
 *  - 弹窗首次打开：sessionId = null
 *  - 单次弹窗内多轮：复用并刷新
 *  - 弹窗关闭后重新打开：reset
 *  - 切换聊天/退出页面：reset（外部调用）
 */
class TongBanSession {
    @Volatile
    var sessionId: String? = null
        private set

    fun reset() {
        sessionId = null
    }

    fun update(newSessionId: String?) {
        if (!newSessionId.isNullOrEmpty()) {
            sessionId = newSessionId
        }
    }
}

/**
 * 童伴 API Service
 *
 * 封装对 /api/chat/ 的调用
 */
class TongBanService(private val context: Context) {
    private val session = TongBanSession()

    fun resetSession() {
        session.reset()
    }

    /**
     * 发送消息
     *
     * @param text 清洗后的用户输入
     * @param request 用于取消的请求句柄
     * @param callback 主线程回调
     */
    fun sendMessage(
        text: String,
        request: TongBanRequest,
        callback: TongBanNetworkClient.TongBanCallback,
    ) {
        val openId = OpenIdManager.get(context)
        val payload = JSONObject().apply {
            put("openid", openId)
            put("message", text)
            // null 表示首次提问
            if (session.sessionId == null) {
                put("session_id", JSONObject.NULL)
            } else {
                put("session_id", session.sessionId)
            }
        }
        TongBanNetworkClient.postJsonAsync(
            url = BASE_URL,
            body = payload.toString(),
            request = request,
            callback = object : TongBanNetworkClient.TongBanCallback {
                override fun onResult(result: TongBanResult) {
                    if (result is TongBanResult.Success) {
                        try {
                            val obj = JSONObject(result.body)
                            session.update(obj.optString("session_id", null))
                        } catch (e: Exception) {
                            Timber.w(e, "parse tongban response error")
                        }
                    }
                    callback.onResult(result)
                }
            },
        )
    }

    /**
     * 解析成功响应
     */
    fun parseSuccess(body: String): TongBanResponse? {
        return try {
            val obj = JSONObject(body)
            TongBanResponse(
                sessionId = obj.optString("session_id", null),
                response = obj.optString("response", ""),
                sources = parseSources(obj.optJSONArray("sources")),
            )
        } catch (e: Exception) {
            Timber.w(e, "parse success body failed")
            null
        }
    }

    private fun parseSources(arr: JSONArray?): List<TongBanSource> {
        if (arr == null) return emptyList()
        val list = mutableListOf<TongBanSource>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                TongBanSource(
                    category = o.optString("category", ""),
                    title = o.optString("title", ""),
                    content = o.optString("content", ""),
                    score = o.optDouble("score", 0.0),
                ),
            )
        }
        return list
    }

    companion object {
        const val BASE_URL = "http://124.221.113.165:8000/api/chat/"
    }
}

data class TongBanResponse(
    val sessionId: String?,
    val response: String,
    val sources: List<TongBanSource>,
)

data class TongBanSource(
    val category: String,
    val title: String,
    val content: String,
    val score: Double,
)

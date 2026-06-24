/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

/**
 * OpenID 管理器
 *
 * 规则：
 *  - 首次启动生成并存入 SharedPreferences
 *  - 前缀固定 ime_user_，拼接设备唯一标识（Android_ID + UUID）
 *  - 重装 APP 后重新生成（因为 SharedPreferences 也被清空）
 */
object OpenIdManager {
    private const val PREFS_NAME = "tongban_prefs"
    private const val KEY_OPEN_ID = "open_id"
    private const val PREFIX = "ime_user_"

    @Volatile
    private var cached: String? = null

    @Synchronized
    fun get(context: Context): String {
        cached?.let { return it }
        val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_OPEN_ID, null)
        if (existing != null) {
            cached = existing
            return existing
        }
        val newId = buildNewId(context)
        prefs.edit().putString(KEY_OPEN_ID, newId).apply()
        cached = newId
        return newId
    }

    private fun buildNewId(context: Context): String {
        val androidId: String = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: ""
        } catch (e: SecurityException) {
            ""
        }
        val randomPart = UUID.randomUUID().toString().replace("-", "")
        // androidId 可能为空或为 null，附加随机 UUID 保证唯一
        val combined = if (androidId.isNotBlank()) {
            androidId + "_" + randomPart.take(12)
        } else {
            randomPart.take(16)
        }
        return PREFIX + combined
    }

    /** 用于测试/调试时强制重置 */
    @Synchronized
    fun reset(context: Context) {
        val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_OPEN_ID).apply()
        cached = null
    }
}

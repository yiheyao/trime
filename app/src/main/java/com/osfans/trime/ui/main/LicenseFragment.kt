/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.osfans.trime.R
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.util.Const
import kotlinx.coroutines.launch

class LicenseFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        lifecycleScope.launch {
            val context = preferenceManager.context
            preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
                // 第三方开源许可证列表只保留本项目适用的两条
                addLicensePreference(
                    context = context,
                    title = getString(R.string.license_gpl_3),
                    url = Const.LICENSE_URL,
                )
                addLicensePreference(
                    context = context,
                    title = getString(R.string.license_lgpl_3),
                    url = Const.LICENSE_LGPL_URL,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.disableTopOptionsMenu()
    }

    private fun androidx.preference.PreferenceScreen.addLicensePreference(
        context: Context,
        title: String,
        url: String,
    ) {
        addPreference(
            object : Preference(context) {
                init {
                    isIconSpaceReserved = false
                    this.title = title
                    this.summary = url
                    isSelectable = true
                }

                override fun onBindViewHolder(holder: PreferenceViewHolder) {
                    super.onBindViewHolder(holder)
                    // 移除默认 ellipsize 属性，让 summary 完整显示 URL
                }

                override fun onClick() {
                    // 用 try/catch 包住 startActivity，避免鸿蒙/EMUI 上
                    // 没有 <queries> 声明时抛 SecurityException 导致崩溃
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (t: Throwable) {
                        Toast.makeText(
                            context,
                            "无法打开浏览器，已复制链接到剪贴板：${t.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                        copyToClipboard(context, url)
                    }
                }
            },
        )
    }

    private fun copyToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("license url", text))
        } catch (_: Throwable) {
            // 剪贴板访问在某些 ROM 上也可能失败，静默忽略
        }
    }
}
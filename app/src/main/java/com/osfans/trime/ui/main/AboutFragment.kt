/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.util.Const
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.formatDateTime

class AboutFragment : PaddingPreferenceFragment() {

    @SuppressLint("UseKtx")
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(R.string.current_version, Const.VERSION_NAME) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("${BuildConfig.BUILD_GIT_REPO}/commit/${BuildConfig.BUILD_COMMIT_HASH}"),
                    ),
                )
            }
            addPreference(R.string.librime_version, BuildConfig.LIBRIME_VERSION) {
                val hash = getCommitFromVersionName(BuildConfig.LIBRIME_VERSION)
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("${Const.LIBRIME_URL}/commit/$hash"),
                    ),
                )
            }
            addPreference(R.string.opencc_version, BuildConfig.OPENCC_VERSION) {
                val hash = getCommitFromVersionName(BuildConfig.OPENCC_VERSION)
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("${Const.OPENCC_URL}/commit/$hash"),
                    ),
                )
            }
            addPreference(
                Preference(requireContext()).apply {
                    isIconSpaceReserved = false
                    isCopyingEnabled = true
                    setTitle(R.string.build_info)
                    summary = requireContext().getString(
                        R.string.build_info_format,
                        BuildConfig.BUILDER,
                        BuildConfig.BUILD_COMMIT_HASH,
                        formatDateTime(BuildConfig.BUILD_TIMESTAMP),
                    )
                },
            )
            addCategory("") {
                isIconSpaceReserved = false
                addPreference(
                    com.osfans.trime.util.HtmlSummaryPreference(requireContext()).apply {
                        isIconSpaceReserved = false
                        isCopyingEnabled = true
                        setTitle(R.string.about_oss_compliance_title)
                        // 开源合规声明的简介
                        summary = Html.fromHtml(
                            getString(R.string.about_oss_compliance),
                            Html.FROM_HTML_MODE_COMPACT,
                        )
                    },
                )
                addPreference(
                    R.string.about_oss_compliance_repo_trime,
                    R.string.about_oss_compliance_repo_trime_url,
                ) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(Const.TRIME_REPO_URL),
                        ),
                    )
                }
                addPreference(
                    R.string.about_oss_compliance_repo_rime_emoji,
                    R.string.about_oss_compliance_repo_rime_emoji_url,
                ) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.about_oss_compliance_repo_rime_emoji_url)),
                        ),
                    )
                }
                addPreference(
                    com.osfans.trime.util.HtmlSummaryPreference(requireContext()).apply {
                        isIconSpaceReserved = false
                        isCopyingEnabled = true
                        setTitle(R.string.about_oss_compliance_notes_title)
                        summary = Html.fromHtml(
                            getString(R.string.about_oss_compliance_notes),
                            Html.FROM_HTML_MODE_COMPACT,
                        )
                    },
                )
            }
            addCategory("") {
                isIconSpaceReserved = false
                addPreference(R.string.privacy_policy) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(Const.PRIVACY_POLICY_URL),
                        ),
                    )
                }
                addPreference(R.string.source_code, R.string.git_repo) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(BuildConfig.BUILD_GIT_REPO),
                        ),
                    )
                }
                addPreference(
                    R.string.license,
                    Const.LICENSE_SPDX_ID,
                ) {
                    // 直接导航到 LicenseFragment（避免触发外部 Intent 跳转路径，
                    // 鸿蒙/EMUI 在没有 <queries> 声明时可能抛 SecurityException 导致崩溃）
                    findNavController().navigate(NavigationRoute.License)
                }
                addPreference(
                    R.string.open_source_licenses,
                    R.string.licenses_of_third_party_libraries,
                ) {
                    findNavController().navigate(NavigationRoute.License)
                }
            }
            addCategory("") {
                isIconSpaceReserved = false
                addPreference(R.string.telegram, Const.TELEGRAM_NAME) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(Const.TELEGRAM_URL),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        private val DASH_G_PATTERN = Regex("^(.*-g)([0-9a-f]+)(.*)$")
        private val COMMON_PATTERN = Regex("^([^-]*)(-.*)$")

        private fun getCommitFromVersionName(versionCode: String): String {
            val dashG = DASH_G_PATTERN.find(versionCode)?.groupValues?.get(2)
            val common = COMMON_PATTERN.find(versionCode)?.groupValues?.get(1)
            return dashG ?: common ?: versionCode
        }
    }
}

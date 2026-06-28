/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import com.osfans.trime.BuildConfig

object Const {
    const val VERSION_NAME = "${BuildConfig.BUILD_VERSION_NAME}-${BuildConfig.BUILD_TYPE}"
    const val LICENSE_SPDX_ID = "GPL-3.0-or-later"
    const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
    const val TRIME_REPO_URL = "https://github.com/osfans/trime"
    const val PRIVACY_POLICY_URL = "https://github.com/osfans/trime/blob/develop/PRIVACY.md"
    const val LIBRIME_URL = "https://github.com/rime/librime"
    const val OPENCC_URL = "https://github.com/BYVoid/OpenCC"
    const val TELEGRAM_NAME = "@trime_dev"
    const val TELEGRAM_URL = "https://t.me/trime_dev"
}

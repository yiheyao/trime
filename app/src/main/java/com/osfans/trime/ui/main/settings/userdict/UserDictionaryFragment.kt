/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.osfans.trime.data.userdict.UserDictManager

class UserDictionaryFragment : Fragment() {

    private val ui: UserDictListUi by lazy {
        UserDictListUi(
            requireContext(),
            UserDictManager.getUserDictList(),
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ui.root
}

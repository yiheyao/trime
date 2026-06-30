/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.content.Context
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.dsl.core.Ui
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager

class UserDictListUi(
    override val ctx: Context,
    entries: Array<String>,
) : Ui {

    val adapter by lazy { UserDictListAdapter(entries.toList()) }

    override val root = recyclerView {
        layoutManager = verticalLayoutManager()
        adapter = this@UserDictListUi.adapter
        clipToPadding = false
        backgroundColor = styledColor(android.R.attr.colorBackground)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            updatePadding(bottom = navBars.bottom)
            windowInsets
        }
    }
}

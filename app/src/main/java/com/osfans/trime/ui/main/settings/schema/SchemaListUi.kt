/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.osfans.trime.core.SchemaItem
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.resources.styledDimenPxSize
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager
import splitties.views.setPaddingDp
import splitties.views.textAppearance

class SchemaListUi(
    override val ctx: Context,
    initialEntries: List<SchemaItem>,
) : Ui {
    private val adapter = SchemaListAdapter(initialEntries)

    private val list =
        recyclerView {
            layoutManager = verticalLayoutManager()
            adapter = this@SchemaListUi.adapter
            clipToPadding = false
        }

    override val root: View =
        constraintLayout {
            backgroundColor = styledColor(android.R.attr.colorBackground)
            add(
                list,
                lParams {
                    height = matchParent
                    width = matchParent
                },
            )
        }
}

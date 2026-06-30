/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.core.SchemaItem

class SchemaListAdapter(
    items: List<SchemaItem>,
) : BaseQuickAdapter<SchemaItem, SchemaListAdapter.ViewHolder>(items) {
    inner class ViewHolder(
        ui: SchemaListEntryUi,
    ) : RecyclerView.ViewHolder(ui.root) {
        val nameText = ui.nameText
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(SchemaListEntryUi(context))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: SchemaItem?,
    ) {
        item ?: return
        holder.nameText.text = item.name
    }
}

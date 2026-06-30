/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter

class UserDictListAdapter(
    items: List<String>,
) : BaseQuickAdapter<String, UserDictListAdapter.ViewHolder>(items) {

    class ViewHolder(
        ui: UserDictListEntryUi,
    ) : RecyclerView.ViewHolder(ui.root) {
        val nameText = ui.nameText
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(UserDictListEntryUi(context))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: String?,
    ) {
        val name = item ?: return
        holder.nameText.text = name
    }
}

/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.view.View
import com.osfans.trime.ui.main.settings.ProgressFragment

class SchemaListFragment : ProgressFragment() {
    private lateinit var ui: SchemaListUi

    override suspend fun initialize(): View {
        val available = rime.runOnReady { availableSchemata().toSet() }
        val enabled = rime.runOnReady { enabledSchemata().map { it.id } }
        val entries = available.filter { enabled.contains(it.id) }
        ui = SchemaListUi(requireContext(), initialEntries = entries)
        return ui.root
    }
}

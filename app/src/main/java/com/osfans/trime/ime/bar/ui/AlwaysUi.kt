/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import android.widget.ViewAnimator
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.gravityCenter
import timber.log.Timber

class AlwaysUi(
    override val ctx: Context,
    private val theme: Theme,
    private val onButtonClick: ((String) -> Unit)? = null,
    private val onTongBanClick: (() -> Unit)? = null,
) : Ui {
    enum class State {
        Toolbar,
        Clipboard,
        InlineSuggestion,
    }

    var currentState = State.Toolbar
        private set

    /**
     * 「童」按钮：放在工具栏右端（紧贴圆圈收起按钮左侧）
     * 底色与工具栏原生一致，「童」字深色高亮
     */
    val tongBanButton: TextView = TextView(ctx).apply {
        text = "童"
        textSize = 18f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ColorManager.getColor("hilited_candidate_text_color"))
        // 底色：与工具栏原生一致
        val bgColor = runCatching { ColorManager.getColor("key_back_color") }.getOrNull()
            ?: Color.parseColor("#E0E0E0")
        setBackgroundColor(bgColor)
        isClickable = true
        isFocusable = true
        setPadding(dp(12), dp(4), dp(12), dp(4))
        setOnClickListener { onTongBanClick?.invoke() }
        contentDescription = "tongban"
    }

    private fun toolButton(
        buttonConfig: ToolBar.Button?,
        @DrawableRes icon: Int = 0,
    ): ToolButton = if (buttonConfig != null) {
        ToolButton(ctx, buttonConfig).apply {
            setOnClickListener { onButtonClick?.invoke(buttonConfig.action) }
            val longPressAction = buttonConfig.longPressAction
            if (longPressAction.isNotEmpty()) {
                setOnLongClickListener {
                    onButtonClick?.invoke(longPressAction)
                    true
                }
            }
        }
    } else {
        ToolButton(ctx, icon).apply {
            setOnClickListener { onButtonClick?.invoke("") }
        }
    }

    val buttonsUi = ButtonsBarUi(ctx, theme, onButtonClick)

    val clipboardUi = ClipboardSuggestionUi(ctx)

    val inlineSuggestionsUi = InlineSuggestionsUi(ctx)

    val hideKeyboardButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_drop_down_24)
    private val rightMostButton =
        ViewAnimator(ctx).apply {
            add(hideKeyboardButton, lParams(matchParent, matchParent))
            buttonsUi.firstButton?.let { add(it, lParams(matchParent, matchParent)) }
        }

    private val leftMostButton = toolButton(
        theme.toolBar.primaryButton,
        R.drawable.ic_baseline_more_horiz_24,
    )

    private val animator =
        ViewAnimator(ctx).apply {
            add(buttonsUi.root, lParams(matchParent, matchParent))
            add(clipboardUi.root, lParams(matchParent, matchParent))
            add(inlineSuggestionsUi.root, lParams(matchParent, matchParent))
        }

    override val root: ConstraintLayout = constraintLayout {
        val (leftWidth, leftHeight) = buttonsUi.getButtonSize(theme.toolBar.primaryButton)
        val (rightWidth, rightHeight) = buttonsUi.getButtonSize(theme.toolBar.buttons.firstOrNull())

        add(
            leftMostButton,
            lParams(leftWidth, leftHeight) {
                startOfParent()
                centerVertically()
            },
        )
        add(
            rightMostButton,
            lParams(rightWidth, rightHeight) {
                endOfParent()
                centerVertically()
            },
        )
        // 「童」按钮：紧贴 rightMostButton 左侧（自动成为 endOfParent 的次右元素）
        val tongBanSize = buttonsUi.getButtonSize(theme.toolBar.buttons.firstOrNull(), customDefaultSize = dp(36))
        add(
            tongBanButton,
            lParams(tongBanSize.first.coerceAtLeast(dp(36)), tongBanSize.second.coerceAtLeast(dp(36))) {
                endOfParent()
                centerVertically()
            },
        )
        add(
            animator,
            lParams(matchConstraints, matchParent) {
                after(leftMostButton)
                before(tongBanButton)
                endOfParent()
                centerVertically()
            },
        )
    }.apply {
        updateRightMostButton(State.Toolbar)
    }

    fun updateButtonsStyle() {
        leftMostButton.updateStyle()
        buttonsUi.firstButton?.updateStyle()
        buttonsUi.updateStyle()
    }

    fun updateState(state: State) {
        Timber.d("Switch always ui to $state")
        animator.displayedChild = state.ordinal
        currentState = state
        updateRightMostButton(state)
        updateLeftMostButton(state)
    }

    private fun updateRightMostButton(state: State) {
        val hasFirstButton = buttonsUi.firstButton != null
        val showFirst = hasFirstButton && (theme.toolBar.buttons.isNotEmpty() || state != State.Toolbar)
        rightMostButton.displayedChild = if (showFirst) 1 else 0
    }

    private fun updateLeftMostButton(state: State) {
        val buttonConfig =
            if (state == State.Toolbar) {
                theme.toolBar.primaryButton
            } else {
                theme.toolBar.buttons.firstOrNull()
            }

        val (buttonWidth, buttonHeight) = buttonsUi.getButtonSize(buttonConfig)
        leftMostButton.layoutParams = leftMostButton.layoutParams.apply {
            width = buttonWidth
            height = buttonHeight
        }
    }
}

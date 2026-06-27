/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.ViewAnimator
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.BuildConfig
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
    private var _onTongBanClick: (() -> Unit)? = onTongBanClick
    val tongBanButton: TextView = TextView(ctx).apply {
        text = "童"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        // 使用工具栏原生背景色（与其它工具栏按钮一致）
        setBackgroundColor(Color.TRANSPARENT)
        setTextColor(Color.parseColor("#222222"))
        isClickable = true
        isFocusable = true
        setPadding(dp(12), dp(4), dp(12), dp(4))
        contentDescription = "tongban"
        setOnClickListener {
            Timber.d("童 button clicked")
            // if (BuildConfig.DEBUG) {
            //     android.widget.Toast.makeText(ctx, "童 clicked", android.widget.Toast.LENGTH_SHORT).show()
            // }
            _onTongBanClick?.invoke()
        }
    }

    /**
     * 外部设置「童」按钮点击回调（用于解决初始化时机问题）
     */
    fun setOnTongBanClickListener(listener: (() -> Unit)?) {
        _onTongBanClick = listener
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
        // 「童」按钮：使用 marginEnd 推到 rightMostButton 左侧
        // 童按钮宽度 = 36dp, 与圆圈间隔 4dp
        val tongBanW = dp(36)
        add(
            tongBanButton,
            lParams(tongBanW, tongBanW) {
                endOfParent()
                marginEnd = rightWidth + dp(4)
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

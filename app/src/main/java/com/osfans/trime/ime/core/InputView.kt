/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.R
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.tongban.TongBanManager
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.instance
import timber.log.Timber
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
) : BaseInputView(service, rime, theme) {
    private val keyboardBackground =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    private val placeholderListener = OnClickListener { }

    private val leftPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val rightPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val bottomPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val updateWindowViewHeightJob: Job

    private val themedContext = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)
    private val inputDepMgr = InputDependencyManager.initialize(this, themedContext, theme, service, rime)
    private val di = inputDepMgr.di
    private val broadcaster: InputBroadcaster by di.instance()
    private val popup: PopupDelegate by di.instance()
    private val enterKeyDisplay: EnterKeyDisplayDelegate by di.instance()
    private val preedit: PreeditDelegate by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val inputBar: InputBarDelegate by di.instance()
    private val keyboardWindow: KeyboardWindow by di.instance()
    private val liquidWindow: LiquidWindow by di.instance()
    private val tongBanManager: TongBanManager = TongBanManager(themedContext)

    private val inlinePreeditMode by AppPrefs.defaultInstance().general.inlinePreeditMode
    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    private val keyboardSidePadding = theme.generalStyle.keyboardPadding
    private val keyboardSidePaddingLandscape = theme.generalStyle.keyboardPaddingLand
    private val keyboardBottomPadding = theme.generalStyle.keyboardPaddingBottom
    private val keyboardBottomPaddingLandscape = theme.generalStyle.keyboardPaddingLandBottom

    private val keyboardSidePaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return dp(value)
        }

    private var lastAppearanceState = Triple(false, false, false)

    private fun broadcastKeyAppearanceUpdate() {
        val composing = rime.run { statusCached.isComposing }
        val hasMenu = rime.run { hasMenu }
        val paging = rime.run { paging }
        val current = Triple(composing, hasMenu, paging)
        if (current != lastAppearanceState) {
            lastAppearanceState = current
            broadcaster.onKeyAppearanceUpdate(current.first, current.second, current.third)
        }
    }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardBottomPaddingLandscape else keyboardBottomPadding
            return dp(value)
        }

    val keyboardView: View

    /** 完整键盘高度（首次 layout 后记录），用于键盘展开/收起动画的目标值 */
    private var fullKeyboardHeight: Int = -1
    /** 当前正在跑的键盘高度动画，避免叠加 */
    private var keyboardHeightAnim: ValueAnimator? = null

    init {
        // MUST call before any operation
        inputDepMgr.start()

        // 童伴浮窗：设置点击回调
        inputBar.tongBanClickListener = { toggleTongBan() }

        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        windowManager.cacheResidentWindow(liquidWindow)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        keyboardBackground.imageDrawable = ColorManager.getDrawable("keyboard_background")

        keyboardView =
            constraintLayout {
                id = R.id.keyboard_view
                isMotionEventSplittingEnabled = true
                add(
                    keyboardBackground,
                    lParams {
                        centerInParent()
                    },
                )
                add(
                    inputBar.view,
                    lParams(matchParent, dp(inputBar.themedHeight)) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    leftPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        startOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    rightPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    windowManager.view,
                    lParams {
                        below(inputBar.view)
                        above(bottomPaddingSpace)
                    },
                )
                add(
                    bottomPaddingSpace,
                    lParams {
                        startToEndOf(leftPaddingSpace)
                        endToStartOf(rightPaddingSpace)
                        bottomOfParent()
                    },
                )
            }

        updateWindowViewHeightJob =
            service.lifecycleScope.launch {
                keyboardWindow.currentKeyboardHeight.collect {
                    windowManager.view.updateLayoutParams {
                        height = it
                    }
                }
            }

        updateKeyboardSize()

        add(
            preedit.ui.root,
            lParams(wrapContent, wrapContent) {
                above(keyboardView)
                startOfParent()
            },
        )

        add(
            keyboardView,
            lParams(matchParent, wrapContent) {
                centerHorizontally()
                bottomOfParent()
            },
        )

        // 童伴浮窗容器：紧贴 keyboardView 上方，底部 = 键盘顶部。
        // IME 窗口高度 = 弹窗 + 键盘；弹窗位于键盘正上方，聊天内容
        // 只在最下方被弹窗自身高度遮挡，弹窗与键盘之间没有空白。
        val tongBanContainer = android.widget.FrameLayout(themedContext).apply {
            visibility = View.GONE
        }
        add(
            tongBanContainer,
            lParams(matchParent, wrapContent) {
                centerHorizontally()
                above(keyboardView)
            },
        )
        tongBanManager.setupContainer(tongBanContainer)
        // 显式设置童按钮回调（避免初始化时序问题）
        inputBar.updateTongBanClickListener { toggleTongBan() }
        // 点击查询后：弹窗展开到键盘高度 + 隐藏键盘；两者同步进行实现"无感切换"
        // - 弹窗 height: 50dp → 280dp（= 键盘 height），responseScroll 同步显示
        // - keyboardView height: 280dp → 0
        // - 弹窗与键盘在动画过程中位置/大小完全对称（都在 IME 窗口底部），实现视觉无缝
        tongBanManager.onQuerySubmitted = {
            tongBanManager.expandToFull()
            setKeyboardVisible(false)
        }
        // 弹窗关闭时：恢复键盘
        tongBanManager.onDialogClosed = { setKeyboardVisible(true) }

        // popup 必须放在 tongBanContainer 之下，避免覆盖童伴浮窗
        add(
            popup.root,
            lParams(matchParent, matchParent) {
                centerInParent()
            },
        )
        // 把 tongBanContainer 移到最上层
        tongBanContainer.bringToFront()
    }

    private fun toggleTongBan() {
        val height = currentKeyboardHeightPx()
        if (tongBanManager.isShowing) {
            tongBanManager.hide()
        } else {
            tongBanManager.show(height)
        }
    }

    private fun currentKeyboardHeightPx(): Int {
        return try {
            keyboardView.height.takeIf { it > 0 } ?: dp(280)
        } catch (e: Exception) {
            dp(280)
        }
    }

    /**
     * 平滑切换 keyboardView 区域可见性（无感替换输入法键盘）。
     * 动画期间 keyboardView 高度从 fullKeyboardHeight 渐变到 0（或反向）。
     * 弹窗约束为 above(keyboardView)，会随 keyboardView 高度变化而自动调整位置——
     * 视觉上：键盘区域被弹窗无缝"替换"，无突兀感。
     * 收起前同步隐藏 preedit 候选条；展开后恢复。
     */
    private fun setKeyboardVisible(visible: Boolean) {
        // #region debug-point A:setKeyboardVisible-entry
        Timber.i("[WXKB-DEBUG] A:setKeyboardVisible entry visible=$visible fullKH=$fullKeyboardHeight curH=${keyboardView.height} vis=${keyboardView.visibility} tongBan.isShowing=${tongBanManager.isShowing}")
        // #endregion
        // 首次进入：若还没记录 fullKeyboardHeight，则用当前 keyboardView 高度作为基准
        // 关键：只在 keyboardView 实际有高度时才记录，避免在键盘已收起（height=0）时把 fullKeyboardHeight 污染为 0
        if (fullKeyboardHeight <= 0 && keyboardView.height > 0) {
            fullKeyboardHeight = keyboardView.height
        }
        if (fullKeyboardHeight <= 0) {
            // 还没 layout 过，或 keyboardView 当前高度为 0（键盘已被收起）
            // 用默认值 280dp 作为 fallback，确保键盘能恢复
            if (visible) {
                fullKeyboardHeight = dp(280)
                Timber.i("[WXKB-DEBUG] A:setKeyboardVisible fullKH was 0, using default 280dp=$fullKeyboardHeight")
            } else {
                keyboardView.visibility = View.GONE
                preedit.ui.root.visibility = View.GONE
                requestLayout()
                invalidate()
                // #region debug-point A:setKeyboardVisible-early-return
                Timber.i("[WXKB-DEBUG] A:setKeyboardVisible early-return(no fullKH, hide) vis=${keyboardView.visibility}")
                // #endregion
                return
            }
        }
        val targetH = if (visible) fullKeyboardHeight else 0
        val startH = keyboardView.height
        if (startH == targetH) {
            // 已经在目标状态；若需要"彻底隐藏"则置为 GONE 节省 measure 开销
            if (!visible) {
                keyboardView.visibility = View.GONE
            } else if (keyboardView.visibility != View.VISIBLE) {
                // 关键：若 keyboardView 仍处于 GONE 状态但要求 visible，
                // 必须先把 visibility 切回 VISIBLE，否则 layoutParams.height 不会生效。
                keyboardView.visibility = View.VISIBLE
                val lp = keyboardView.layoutParams as ConstraintLayout.LayoutParams
                lp.height = fullKeyboardHeight
                keyboardView.layoutParams = lp
                requestLayout()
                invalidate()
            }
            // #region debug-point A:setKeyboardVisible-noanim
            Timber.i("[WXKB-DEBUG] A:setKeyboardVisible no-anim startH=$startH targetH=$targetH vis=${keyboardView.visibility}")
            // #endregion
            return
        }

        // preedit 候选条在键盘收起时一起淡出（避免弹窗下移过程中还显示候选条）
        if (!visible) {
            preedit.ui.root.animate().alpha(0f).setDuration(180L).withEndAction {
                preedit.ui.root.visibility = View.GONE
                preedit.ui.root.alpha = 1f
            }.start()
        } else {
            preedit.ui.root.visibility = View.VISIBLE
            preedit.ui.root.alpha = 1f
        }

        // 关键：visible=true 时必须先把 keyboardView 切回 VISIBLE，
        // 否则 GONE 状态下改 layoutParams.height 会被 ConstraintLayout 忽略。
        if (visible && keyboardView.visibility != View.VISIBLE) {
            keyboardView.visibility = View.VISIBLE
        }

        // 取消正在跑的动画（避免叠加）
        keyboardHeightAnim?.cancel()

        keyboardHeightAnim = ValueAnimator.ofInt(startH, targetH).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val h = anim.animatedValue as Int
                val lp = keyboardView.layoutParams as ConstraintLayout.LayoutParams
                lp.height = h
                keyboardView.layoutParams = lp
                // 同步驱动童伴弹窗高度跟随 keyboardView 变化（弹窗 + 键盘 = 恒定高度，
                // 避免弹窗直接跳到全高导致 IME 窗口突然增高露出"白框"）
                tongBanManager.syncDialogHeight(h)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!visible) {
                        keyboardView.visibility = View.GONE
                        // 高度收为 0，节省 measure
                        val lp = keyboardView.layoutParams as ConstraintLayout.LayoutParams
                        lp.height = 0
                        keyboardView.layoutParams = lp
                    } else {
                        // 恢复到记录的完整高度（不依赖 wrap_content 重新 measure，避免高度跳变）
                        val lp = keyboardView.layoutParams as ConstraintLayout.LayoutParams
                        lp.height = fullKeyboardHeight
                        keyboardView.layoutParams = lp
                    }
                    // onComputeInsets 依赖最新 measure，重新计算 touchable region
                    invalidate()
                    Timber.i("[WXKB-DEBUG] A:setKeyboardVisible animEnd visible=$visible kbVis=${keyboardView.visibility} kbH=${keyboardView.height}")
                }
            })
            start()
        }
    }

    private fun updateKeyboardSize() {
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val sidePadding = keyboardSidePaddingPx
        val unset = LayoutParams.UNSET
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = View.GONE
            rightPaddingSpace.visibility = View.GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.visibility = View.VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        inputBar.view.setPadding(sidePadding, 0, sidePadding, 0)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        Timber.tag("TrimePwd").i(
            "startInput restarting=%s, pkg=%s, field=%s, hint=%s, inputType=0x%x, tongBan.isShowing=%s",
            restarting, info.packageName, info.fieldName, info.hintText, info.inputType,
            tongBanManager.isShowing,
        )
        Timber.i("[WXKB-DEBUG] startInput entry restarting=$restarting tongBan.isShowing=${tongBanManager.isShowing} tongBan.isExpanded=${tongBanManager.isExpandedForDebug} kbVis=${keyboardView.visibility} kbH=${keyboardView.height}")
        updateEnterKeyLabel(info)
        broadcaster.onStartInput(info)
        if (!restarting) {
            // 切聊天/重新开始输入：销毁童伴 session
            windowManager.attachWindow(KeyboardWindow)
        }
        // 密码/数字字段（系统锁屏密码、APK 安装确认 PIN 等）必须强制清掉童伴弹窗状态，
        // 否则童伴劫持 commitText 会导致用户输入的数字进不到系统输入框。
        val isSensitiveField = isSensitiveInput(info)
        if (isSensitiveField) {
            Timber.tag("TrimePwd").i("startInput: sensitive field detected, forceReset tongBan")
            tongBanManager.forceReset()
        }
        // 无论是否 restarting，只要童伴弹窗还在显示（如查询超时/无网络后用户重新点击输入框），
        // 都需要隐藏弹窗并恢复键盘，否则键盘无法打开
        if (tongBanManager.isShowing) {
            Timber.i("[WXKB-DEBUG] startInput: tongBan still showing, calling hide() to restore keyboard")
            tongBanManager.hide()
        } else {
            Timber.i("[WXKB-DEBUG] startInput: tongBan not showing, keyboard should be visible")
        }
    }

    /** 判断 EditorInfo 是否为"敏感"输入字段（密码 / 纯数字 / 系统弹窗等） */
    private fun isSensitiveInput(info: EditorInfo): Boolean {
        val type = info.inputType
        val cls = type and android.text.InputType.TYPE_MASK_CLASS
        val variation = type and android.text.InputType.TYPE_MASK_VARIATION
        // 数字密码
        if (cls == android.text.InputType.TYPE_CLASS_NUMBER &&
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) return true
        // 文本密码（含可见/Web 密码）
        if (cls == android.text.InputType.TYPE_CLASS_TEXT &&
            (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        ) return true
        // 纯数字字段（如系统锁屏 PIN 码对话框、APK 安装确认）—— 也按敏感处理
        if (cls == android.text.InputType.TYPE_CLASS_NUMBER) return true
        return false
    }

    /** 判断 EditorInfo 是否为密码输入字段（系统锁屏密码、APK 安装确认等） */
    private fun isPasswordInput(info: EditorInfo): Boolean {
        val type = info.inputType
        val cls = type and android.text.InputType.TYPE_MASK_CLASS
        val variation = type and android.text.InputType.TYPE_MASK_VARIATION
        return (cls == android.text.InputType.TYPE_CLASS_NUMBER &&
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
            (cls == android.text.InputType.TYPE_CLASS_TEXT &&
                (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
    }

    fun finishInput() {
        // 切后台/页面销毁：关闭浮窗，强制中断请求
        tongBanManager.hide()
    }

    fun updateEnterKeyLabel(info: EditorInfo) {
        enterKeyDisplay.updateLabelOnEditorInfo(info)
    }

    override fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                broadcaster.onRimeSchemaUpdated(it.data)

                windowManager.attachWindow(KeyboardWindow)
            }

            is RimeMessage.OptionMessage -> {
                broadcaster.onRimeOptionUpdated(it.data)

                if (it.data.option == "_liquid_keyboard") {
                    ContextCompat.getMainExecutor(service).execute {
                        windowManager.attachWindow(LiquidWindow)
                        liquidWindow.setDataByIndex(0)
                    }
                }
            }
            is RimeMessage.CompositionMessage -> {
                val data = if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) {
                    CompositionProto()
                } else {
                    it.data
                }
                broadcaster.onCompositionUpdate(data)
            }
            is RimeMessage.CandidateMenuMessage -> {
                broadcaster.onCandidateMenuUpdate(it.data)
            }
            is RimeMessage.CandidateListMessage -> {
                val data = if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) {
                    RimeMessage.CandidateListMessage.Data()
                } else {
                    it.data
                }
                broadcaster.onCandidateListUpdate(data)
            }
            else -> {}
        }
        broadcastKeyAppearanceUpdate()
    }

    fun updateSelection(
        start: Int,
        end: Int,
    ) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean = inputBar.handleInlineSuggestions(response)

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        // cancel the notification job and clear all broadcast receivers,
        // implies that InputView should not be attached again after detached.
        updateWindowViewHeightJob.cancel()
        popup.root.removeAllViews()
        // 童伴浮窗：释放资源
        tongBanManager.release()
        inputDepMgr.stop()
        super.onDetachedFromWindow()
    }
}

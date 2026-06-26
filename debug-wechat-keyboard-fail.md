# Debug Session: WeChat Keyboard Launch Failure

**Session ID**: `wechat-keyboard-fail`
**Status**: `[OPEN]`
**Symptom**: 微信（WeChat）输入框点击后，键盘无法被调出显示（用户今天遇到一次）
**Reproduction**: 用户描述曾在 WeChat 输入状态下偶发

## 受影响的代码区域
- [TrimeInputMethodService.kt](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt)
  - `onStartInputView` (L615-)
  - `onFinishInputView` (L660-)
  - `onComputeInsets` (L555-)
  - `forceShowSelf` 调用点 (L843)
- [InputView.kt](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/InputView.kt)
  - `setKeyboardVisible` (L313-)
  - `startInput` (L449-)
- [TongBanManager.kt](file:///d:/project/andriod/trime/app/src/src/main/java/com/osfans/trime/ime/tongban/TongBanManager.kt)
  - `expandToFull` (L233-)
  - `dismissAndRestore` (L350-)
  - `forceReset` (L431-)
- [InputDeviceManager.kt](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/InputDeviceManager.kt)
  - `applyMode` (L60-)

## 初步假设（Hypotheses）

> **Evidence Gate**: 以下假设均为待证伪候选，**未经运行时证据验证**。下一步将加入 instrumentation 日志。

### H1 — 童伴弹窗展开态导致 keyboardView 高度残留 0（最强嫌疑）
**机制**：用户在 WeChat 中点击 "童" 按钮打开浮窗 → 点击 "查询" 触发 `expandToFull()` + `setKeyboardVisible(false)`，该动画在 `onAnimationEnd` 中将 `keyboardView.height = 0` 并置 `visibility = GONE`。
**触发条件**：若用户在弹窗"展开态"（state 2）下切换 WeChat 会话、切换到其他 App、或被系统中断（来电/弹窗/锁屏），导致 `dismissAndRestore()` 没有走完（240ms 后的 `hide()` 没机会执行），`onFinishInputView(finishingInput=true)` 触发 `forceReset()` —— 但 `forceReset` 注释里明确写了 "**不调 onDialogClosed（避免 setKeyboardVisible 干扰）**"，所以 `keyboardView.height=0 + visibility=GONE` 状态被保留。
**重现路径**：WeChat 中打开童伴 → 点查询（弹窗进入 state 2）→ 立刻按 Home/切应用 → 回来点 WeChat 输入框 → 键盘空白。

### H2 — `startInput(restarting=true)` 不重置童伴状态
**机制**：[InputView.kt#L449-L457](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/InputView.kt#L449-L457) 中 `if (!restarting)` 守卫使得重新进入同一输入框（restarting=true）时不会调 `tongBanManager.hide()`，弹窗与 keyboardView 的高度动画可能卡在中间态。
**触发条件**：用户在 WeChat 同一输入框点击空白处收回键盘、再点击输入框。

### H3 — `popup.root` 的 `matchParent/matchParent` 层拦截触摸
**机制**：[InputView.kt#L271-L275](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/InputView.kt#L271-L275) 中 `popup.root` 是 `matchParent/matchParent` + `centerInParent`，虽然 `isClickable=false`，但 popup 子 view 弹出时（`PopupEntryUi`）可能捕获 touch 事件。
**触发条件**：长按某个键，触发 `showCandidateActionMenu`（在 [BaseInputView.kt#L55](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/BaseInputView.kt#L55)），菜单显示期间切走/切回。

### H4 — `onComputeInsets` 在 inputView 异常状态下返回值异常
**机制**：`onComputeInsets` 依赖 `inputView?.keyboardView?.getLocationInWindow()`。若 `keyboardView` 处于 GONE 或高度=0，`inputViewLocation[1]` 可能为 0 或屏幕顶端，导致 `contentTopInsets = 0`，系统认为 IME 占用全屏，WeChat 把 EditText 推出可见区域或隐藏键盘。
**触发条件**：H1 触发的状态下，再被 onConfigurationChanged / onApplyWindowInsets 重新触发时。

### H5 — `superEvaluateInputViewShown()` 返回 false
**机制**：[InputDeviceManager.kt#L97](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/InputDeviceManager.kt#L97) 在 `SYSTEM_DEFAULT` 模式下读取 `service.superEvaluateInputViewShown()`。如果 WeChat 上一次 `finishComposingText` / `monitorCursorAnchor(false)` 调用时序异常，可能让系统短暂认为不应显示 InputView。
**触发条件**：H1 后第一次进 onStartInputView。

## 下一步计划

1. **Instrumentation First**：仅在 5 个观测点插入日志（不改任何业务逻辑），通过 Debug Server 上报 NDJSON。
2. **复现验证**：用户重现一次失败场景，捕获日志。
3. **证据分析**：根据日志判定哪条（哪几条）假设成立。
4. **最小修复**：基于证据做最小改动。
5. **复测对比**：对比修复前后日志。

## 5 个观测点（已埋）

| ID | 文件 / 行 | 触发场景 | 关键字段 |
|----|----------|---------|----------|
| **A** | [InputView.kt#L317-407](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/InputView.kt#L317-L407) | `setKeyboardVisible()` 入口 / 早返回 / 不需要动画 / 动画结束 | `visible`, `fullKH`, `curH`, `vis`, `tongBan.isShowing` |
| **B** | [InputView.kt#L460-471](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/InputView.kt#L460-L471) | `startInput()` 入口 / hide 后 | `restarting`, `tongBan.isShowing`, `kbH`, `kbVis` |
| **C** | [TongBanManager.kt#L249-460](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/tongban/TongBanManager.kt#L249-L460) | `expandToFull()` / `forceReset()` 入口+退出 | `isShowing`, `isExpanded`, `kbH`, `kbVis` |
| **D** | [TrimeInputMethodService.kt#L675-697](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt#L675-L697) | `onFinishInputView()` 入口+退出 | `finishingInput`, `tongBan.isShowing`, `kbH`, `kbVis` |
| **E** | [TrimeInputMethodService.kt#L644-668](file:///d:/project/andriod/trime/app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt#L644-L668) | `onStartInputView()` 入口 / evaluate 后 / 退出 | `restarting`, `useVK`, `useCV`, `isVirtualKB`, `kbH`, `kbVis` |

## 复现操作清单（Cheatsheet）

```bash
# 1. 启动 logcat 过滤
adb logcat -c && adb logcat | grep --line-buffered "WXKB-DEBUG"

# 2. 安装新版本并启用 trime 调试模式
./gradlew :app:installDebug
```

### 场景 A：童伴展开态 + 切走（H1 验证）
1. 打开 WeChat 进任意聊天
2. 点击输入框 → 键盘弹出 ✓
3. 点击键盘上的 **「童」按钮** → 小弹窗出现（state 1）
4. 在弹窗输入几个字 → 点 **「查询」** → 弹窗展开为 state 2（键盘应隐藏）
5. **立刻按 Home 键**（或切到另一个 app）
6. 等几秒 → 回到 WeChat → 点击输入框
7. 观察：
   - 若键盘不出来 → 看 logcat 中 `[WXKB-DEBUG] C:forceReset exit` 这一行的 `kbH=...` 和 `kbVis=...`
   - 关键证据：`kbH=0 kbVis=GONE` 意味着 H1 成立

### 场景 B：重启输入（H2 验证）
1. 同上到 state 2
2. 不切走，而是按返回键收回键盘（不要锁屏）
3. 再次点 WeChat 输入框
4. 观察 `B:startInput` 中的 `restarting=true` 是否触发

### 场景 C：基线场景（无童伴）
1. 不碰童伴，正常进 WeChat → 键盘弹出
2. 切走再回来
3. 观察 `D:onFinishInputView` 应该是 `finishingInput=true`，`kbH` 应该正常

### 场景 D：正常关闭童伴（对照）
1. 打开童伴 → 不点查询 → 点 ×
2. 弹窗应平滑消失，键盘显示
3. 观察 `A:setKeyboardVisible animEnd targetVisible=true` 是否触发

## ⚠️ 用户中止选项

任何阶段用户都可回复 **D (Abort)** 中止调试，我会清理所有 instrumentation 与 debug 文件。
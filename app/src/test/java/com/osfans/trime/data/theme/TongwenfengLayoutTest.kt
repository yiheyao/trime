// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 tongwenfeng.trime.yaml 键盘布局验证。

 主要保护点:
 1. default 布局的 ascii_mode 默认是 0(中文)
 2. "/" 键已改为 Mode_switch,标签为「中」
 3. tools bar 的 ascii_mode_button 已被移除(不再有状态提示)
 4. Mode_switch 在 preset_keys 中是 ascii_mode 切换,label 为「中」
 */
class TongwenfengLayoutTest :
    FunSpec({

        val layoutFile = File("src/main/assets/shared/tongwenfeng.trime.yaml")
        val parsed: Map<String, Any?> = Yaml().load(layoutFile.readText(Charsets.UTF_8))

        @Suppress("UNCHECKED_CAST")
        val presetKeyboards = parsed["preset_keyboards"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val defaultKeyboard = presetKeyboards["default"] as Map<String, Any?>

        test("tongwenfeng.trime.yaml 是合法 YAML 且包含 preset_keyboards") {
            parsed.containsKey("config_version") shouldBe true
            parsed.containsKey("preset_keyboards") shouldBe true
        }

        test("default 布局 ascii_mode = 0(默认中文)") {
            withClue("default.ascii_mode = ${defaultKeyboard["ascii_mode"]}") {
                defaultKeyboard["ascii_mode"] shouldBe 0
            }
        }

        test("'/' 键已替换为 Mode_switch(中英文切换)") {
            @Suppress("UNCHECKED_CAST")
            val keys = defaultKeyboard["keys"] as List<Map<String, Any?>>
            // keys 是扁平列表,找到 click=Mode_switch 的那个键
            val modeSwitchKey = keys.firstOrNull { it["click"] == "Mode_switch" }
                ?: throw AssertionError("未找到 Mode_switch 键")

            withClue("找到的键: $modeSwitchKey") {
                modeSwitchKey["click"] shouldBe "Mode_switch"
                modeSwitchKey["label"] shouldBe "中"
            }
        }

        test("'/' 键无 long_click 属性(已移除长按液态键盘切换)") {
            @Suppress("UNCHECKED_CAST")
            val keys = defaultKeyboard["keys"] as List<Map<String, Any?>>
            val modeSwitchKey = keys.firstOrNull { it["click"] == "Mode_switch" }
                ?: throw AssertionError("未找到 Mode_switch 键")

            modeSwitchKey.containsKey("long_click") shouldBe false
        }

        test("不存在 '/' 键(已被 Mode_switch 替换)") {
            @Suppress("UNCHECKED_CAST")
            val keys = defaultKeyboard["keys"] as List<Map<String, Any?>>
            val slashKeys = keys.filter { it["click"] == "/" }
            slashKeys.size shouldBe 0
        }

        test("Mode_switch 在 preset_keys 中定义,且为 ascii_mode 切换") {
            @Suppress("UNCHECKED_CAST")
            val presetKeys = parsed["preset_keys"] as Map<String, Any?>
            presetKeys.containsKey("Mode_switch") shouldBe true

            @Suppress("UNCHECKED_CAST")
            val modeSwitchDef = presetKeys["Mode_switch"] as Map<String, Any?>
            modeSwitchDef["toggle"] shouldBe "ascii_mode"
            @Suppress("UNCHECKED_CAST")
            val states = modeSwitchDef["states"] as List<String>
            states.contains("中") shouldBe true
        }
    })
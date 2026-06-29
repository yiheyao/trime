// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.schema

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 clover.schema.yaml 结构验证。

 主要保护点:
 1. schema 文件存在且能正常解析
 2. switches 不含 zh_simp_s2t(原始的繁→简转换已移除)
 3. 保留 emoji_suggestion / symbol_support 等开关
 4. filters 列表中包含用于 t2s 的 simplifier 引用
 5. 存在 zh_simp_t2s 配置块(指向 t2s.json)
 6. translator.dictionary 指向 clover(我们创建的简体字典)
 */
class CloverSchemaTest :
    FunSpec({

        val sharedAssetsDir = File("src/main/assets/shared")
        val schemaFile = File(sharedAssetsDir, "clover.schema.yaml")

        test("clover.schema.yaml 文件存在且非空") {
            schemaFile.exists() shouldBe true
            (schemaFile.length() > 0) shouldBe true
        }

        test("clover.schema.yaml 是合法 YAML 且能解析") {
            val parsed: Map<String, Any?> = parseSchema(schemaFile)
            parsed.containsKey("schema") shouldBe true
            parsed.containsKey("switches") shouldBe true
            parsed.containsKey("engine") shouldBe true
        }

        test("schema.name 是四叶草简体拼音") {
            val parsed = parseSchema(schemaFile)
            @Suppress("UNCHECKED_CAST")
            val schemaMeta = parsed["schema"] as Map<String, Any?>
            val name = schemaMeta["name"].toString()
            name shouldBe "🍀️四叶草简体拼音"
        }

        test("移除 zh_simp_s2t 开关(原始的简→繁转换已移除)") {
            val parsed = parseSchema(schemaFile)
            val switches = parsed.switches
            val names = switches.mapNotNull { it["name"]?.toString() }
            names.shouldNotContain("zh_simp_s2t")
        }

        test("switches 不含任何 zh_simp_* 简化器开关(用户不需要切换开关)") {
            val parsed = parseSchema(schemaFile)
            val switches = parsed.switches
            val names = switches.mapNotNull { it["name"]?.toString() }
            names.filter { it.startsWith("zh_simp") } shouldBe emptyList()
        }

        test("ascii_mode 开关存在且默认中文") {
            val parsed = parseSchema(schemaFile)
            val asciiMode = parsed.findSwitch("ascii_mode")
                ?: throw AssertionError("ascii_mode 开关不存在")
            asciiMode["reset"] shouldBe 0
            @Suppress("UNCHECKED_CAST")
            (asciiMode["states"] as List<String>) shouldBe listOf("中", "英")
        }

        test("emoji_suggestion 与 symbol_support 开关存在") {
            val parsed = parseSchema(schemaFile)
            val names = parsed.switches.mapNotNull { it["name"]?.toString() }
            names.shouldContain("emoji_suggestion")
            names.shouldContain("symbol_support")
        }

        test("filters 列表包含 t2s simplifier") {
            val parsed = parseSchema(schemaFile)
            val filters = parsed.engineFilters
            // 当前实现使用 `simplifier@zh_simp_t2s`(无对应 switch,始终激活)
            filters.shouldContain("simplifier@zh_simp_t2s")
        }

        test("zh_simp_t2s 配置块存在且指向 t2s.json") {
            val parsed = parseSchema(schemaFile)
            parsed.containsKey("zh_simp_t2s") shouldBe true
            @Suppress("UNCHECKED_CAST")
            val t2sConfig = parsed["zh_simp_t2s"] as Map<String, Any?>
            t2sConfig["opencc_config"] shouldBe "t2s.json"
            t2sConfig["option_name"] shouldBe "zh_simp_t2s"
        }

        test("translator.dictionary 指向 clover(已简体化)") {
            val parsed = parseSchema(schemaFile)
            @Suppress("UNCHECKED_CAST")
            val translator = parsed["translator"] as Map<String, Any?>
            translator["dictionary"] shouldBe "clover"
        }
    })

// region 测试辅助函数

private fun parseSchema(file: File): Map<String, Any?> =
    Yaml().load(file.readText(Charsets.UTF_8))

private val Map<String, Any?>.switches: List<Map<String, Any?>>
    get() = @Suppress("UNCHECKED_CAST") (this["switches"] as? List<Map<String, Any?>>) ?: emptyList()

private val Map<String, Any?>.engineFilters: List<String>
    get() = @Suppress("UNCHECKED_CAST") ((this["engine"] as? Map<String, Any?>)?.get("filters") as? List<String>) ?: emptyList()

private fun Map<String, Any?>.findSwitch(name: String): Map<String, Any?>? =
    switches.firstOrNull { it["name"] == name }

// endregion
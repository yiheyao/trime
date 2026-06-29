// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.base

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 DataManager 自动写入 user data 目录的 default.custom.yaml 验证。

 通过两条独立路径来校验,避免触发 DataManager.<clinit>(依赖 TrimeApplication):
   1) 解析项目根目录的 default.custom.yaml 模板,确认 schema_list[0] == "luna_pinyin_simp"
   2) 直接文本校验 DataManager.kt 的 SCHEMA_LIST_CUSTOM_PATCH 源码,确认与上面同步
 这样能保证「源码常量」与「设备端落盘的 YAML」内容一致。

 主要保护点:
 1. default.custom.yaml 模板是合法 YAML,顶层为 patch
 2. patch.schema_list[0] = luna_pinyin_simp
    (保证默认输入法为「童伴拼音·简化字」,输「nihao」候选为简体「你好」)
 3. patch.schema_list 同时保留 luna_pinyin 作为后备方案
 4. 源码内 SCHEMA_LIST_CUSTOM_PATCH 常量与上述内容一致(防止以后忘了同步)
 */
class DataManagerTest :
    FunSpec({

        // (0) 路径校验 — Gradle 跑 test 时 cwd 是 app/,default.custom.yaml 在项目根
        val dataManagerSource = File("src/main/java/com/osfans/trime/data/base/DataManager.kt")
        val sampleCustomYaml = File("../default.custom.yaml").canonicalFile

        test("DataManager.kt 源码存在") {
            dataManagerSource.exists() shouldBe true
        }

        test("项目根 default.custom.yaml 模板存在") {
            sampleCustomYaml.exists() shouldBe true
        }

        // (1) 解析 default.custom.yaml 模板
        val sampleYamlText = sampleCustomYaml.readText(Charsets.UTF_8)
        val sampleParsed: Map<String, Any?> = Yaml().load(sampleYamlText)

        test("default.custom.yaml 是合法 YAML,顶层为 patch") {
            sampleParsed.containsKey("patch") shouldBe true
        }

        test("default.custom.yaml schema_list[0] = luna_pinyin_simp") {
            @Suppress("UNCHECKED_CAST")
            val schemaList = (sampleParsed["patch"] as Map<String, Any?>)["schema_list"]
                as List<Map<String, Any?>>
            withClue(schemaList) {
                schemaList.isNotEmpty() shouldBe true
                schemaList[0]["schema"] shouldBe "luna_pinyin_simp"
            }
        }

        test("default.custom.yaml schema_list 仍包含 luna_pinyin 作为后备") {
            @Suppress("UNCHECKED_CAST")
            val schemaList = (sampleParsed["patch"] as Map<String, Any?>)["schema_list"]
                as List<Map<String, Any?>>
            val ids = schemaList.map { it["schema"] }
            withClue(ids) {
                ids.shouldContain("luna_pinyin_simp")
                ids.shouldContain("luna_pinyin")
            }
        }

        // (2) 解析 DataManager.kt 源码里的 SCHEMA_LIST_CUSTOM_PATCH 字符串片段
        val sourceText = dataManagerSource.readText(Charsets.UTF_8)
        // 匹配 SCHEMA_LIST_CUSTOM_PATCH = """..."""  整段(包括前导签名),宽松匹配到下一处 """
        val blockRegex = Regex(
            "SCHEMA_LIST_CUSTOM_PATCH\\s*=\\s*\"\"\"[\\s\\S]*?\"\"\"",
        )
        val sourceYamlBlock = blockRegex.find(sourceText)?.value
        // 去掉前缀 SCHEMA_LIST_CUSTOM_PATCH = """ 与后缀 """
        val sourceYaml = sourceYamlBlock
            ?.removePrefix("SCHEMA_LIST_CUSTOM_PATCH = \"\"\"")
            ?.removeSuffix("\"\"\"")

        test("DataManager.kt 源码内能找到 SCHEMA_LIST_CUSTOM_PATCH 常量") {
            requireNotNull(sourceYamlBlock) {
                "未在 DataManager.kt 中找到 SCHEMA_LIST_CUSTOM_PATCH = \"\"\"...\"\"\" 的字符串常量"
            }
        }

        test("源码常量是合法 YAML 且顶层为 patch") {
            val parsed: Map<String, Any?> = Yaml().load(sourceYaml!!)
            parsed.containsKey("patch") shouldBe true
        }

        test("源码常量 schema_list[0] = luna_pinyin_simp(与模板一致)") {
            val parsed: Map<String, Any?> = Yaml().load(sourceYaml!!)
            @Suppress("UNCHECKED_CAST")
            val schemaList = (parsed["patch"] as Map<String, Any?>)["schema_list"]
                as List<Map<String, Any?>>
            withClue(schemaList) {
                schemaList.isNotEmpty() shouldBe true
                schemaList[0]["schema"] shouldBe "luna_pinyin_simp"
            }
        }

        test("源码 schema_list 也保留 luna_pinyin 后备方案") {
            val parsed: Map<String, Any?> = Yaml().load(sourceYaml!!)
            @Suppress("UNCHECKED_CAST")
            val schemaList = (parsed["patch"] as Map<String, Any?>)["schema_list"]
                as List<Map<String, Any?>>
            val ids = schemaList.map { it["schema"] }
            withClue(ids) {
                ids.shouldContain("luna_pinyin_simp")
                ids.shouldContain("luna_pinyin")
            }
        }
    })

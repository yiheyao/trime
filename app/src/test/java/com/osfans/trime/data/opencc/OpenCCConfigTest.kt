// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.opencc

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 OpenCC 繁→简转换资源验证。

 保护点:
 1. t2s.json 引用了 TSCharacters.ocd2 与 TSPhrases.ocd2
 2. TSCharacters.txt 是合法的 TAB 分隔文件,包含常见繁→简字符映射
 3. TSPhrases.txt 是合法的 TAB 分隔文件,包含常见繁→简词组映射
 4. 关键繁体字(学 / 语 / 时 / 还 / 们 / 说 / 个)在映射表中能找到对应简体
 */
class OpenCCConfigTest :
    FunSpec({

        val openccDir = File("src/main/assets/shared/opencc")
        val t2sJson = File(openccDir, "t2s.json")
        val tsCharacters = File(openccDir, "TSCharacters.txt")
        val tsPhrases = File(openccDir, "TSPhrases.txt")

        test("t2s.json 文件存在且为合法 JSON") {
            t2sJson.exists() shouldBe true
            val root = Json.parseToJsonElement(t2sJson.readText(Charsets.UTF_8)).jsonObject
            root.containsKey("name") shouldBe true
            root.containsKey("conversion_chain") shouldBe true
        }

        test("t2s.json 引用 TSCharacters.ocd2 和 TSPhrases.ocd2") {
            val root = Json.parseToJsonElement(t2sJson.readText(Charsets.UTF_8)).jsonObject
            val allFiles = mutableListOf<String>()
            val chain = root["conversion_chain"]?.jsonArray ?: JsonArray(emptyList())
            for (item in chain) {
                val dict = item.jsonObject["dict"]
                when (dict) {
                    is JsonObject -> {
                        // 单个 dict 引用
                        val f = dict["file"]?.jsonPrimitive?.contentOrNull
                        if (f != null) allFiles += f
                        // 或者嵌套的 group(dicts 列表)
                        val dicts = dict["dicts"]
                        if (dicts is JsonArray) {
                            for (d in dicts) {
                                val ff = d.jsonObject["file"]?.jsonPrimitive?.contentOrNull
                                if (ff != null) allFiles += ff
                            }
                        }
                    }
                    is JsonArray -> {
                        for (d in dict) {
                            val f = d.jsonObject["file"]?.jsonPrimitive?.contentOrNull
                            if (f != null) allFiles += f
                        }
                    }
                    else -> {}
                }
            }
            withClue("实际引用到的 ocd2 文件: $allFiles") {
                allFiles.shouldContain("TSCharacters.ocd2")
                allFiles.shouldContain("TSPhrases.ocd2")
            }
        }

        test("TSCharacters.txt 是合法的 TAB 分隔文件") {
            tsCharacters.exists() shouldBe true
            val mapping = parseTwoColumnTsv(tsCharacters)
            withClue("TSCharacters.txt 映射条目数: ${mapping.size}") {
                (mapping.size > 1000) shouldBe true
            }
        }

        test("TSCharacters.txt 包含常见繁→简字符映射") {
            val mapping = parseTwoColumnTsv(tsCharacters)
            val expected = listOf("學" to "学", "語" to "语", "時" to "时", "還" to "还", "們" to "们", "說" to "说", "個" to "个")
            for ((trad, simp) in expected) {
                withClue("$trad -> $simp") {
                    mapping[trad] shouldBe simp
                }
            }
        }

        test("TSPhrases.txt 是合法的 TAB 分隔文件") {
            tsPhrases.exists() shouldBe true
            val mapping = parseTwoColumnTsv(tsPhrases)
            withClue("TSPhrases.txt 映射条目数: ${mapping.size}") {
                (mapping.size > 50) shouldBe true
            }
        }

        test("TSPhrases.txt 包含常见繁→简词组映射(一目了然 来自一目瞭然, 不了解 来自不瞭解)") {
            val mapping = parseTwoColumnTsv(tsPhrases)
            // 注意:"乾"在 TSPhrases 里有时保持原样,有时转为"干"——取决于语义
            // 使用真实 TSPhrases.txt 中已存在的映射对
            val mappings = listOf(
                "一目瞭然" to "一目了然",   // 瞭→了
                "不瞭解" to "不了解",      // 瞭→了
                "上鍊" to "上链",          // 鍊→链 (注意是 鍊 U+937A,不是 鍾 U+937E)
                "么麼" to "幺麽"           // 麼→麽
            )
            for ((trad, simp) in mappings) {
                withClue("$trad -> $simp") {
                    mapping[trad] shouldBe simp
                }
            }
        }
    })

// region 测试辅助函数

private fun parseTwoColumnTsv(file: File): Map<String, String> {
    val map = mutableMapOf<String, String>()
    file.useLines { lines ->
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split("\t")
            if (parts.size >= 2) {
                map[parts[0]] = parts[1]
            }
        }
    }
    return map
}

// endregion
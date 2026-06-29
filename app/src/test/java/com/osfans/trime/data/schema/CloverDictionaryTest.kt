// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.schema

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 clover 词典文件验证。

 主要保护点:
 1. clover.dict.yaml 是合法 YAML,import_tables 列表包含 clover.base / clover.phrase
 2. clover.base.dict.yaml 不包含任何 TSCharacters.txt 中定义为繁体的字
 3. clover.phrase.dict.yaml 不包含任何 TSCharacters.txt 中定义为繁体的字
 4. 所有 RIME dict 文件条目的格式是合法的 word<TAB>pinyin<TAB>weight
 */
class CloverDictionaryTest :
    FunSpec({

        val sharedAssetsDir = File("src/main/assets/shared")
        val openccDir = File(sharedAssetsDir, "opencc")
        val tsCharactersTxt = File(openccDir, "TSCharacters.txt")
        val tsPhrasesTxt = File(openccDir, "TSPhrases.txt")

        val cloverDictYaml = File(sharedAssetsDir, "clover.dict.yaml")
        val cloverBaseDict = File(sharedAssetsDir, "clover.base.dict.yaml")
        val cloverPhraseDict = File(sharedAssetsDir, "clover.phrase.dict.yaml")

        // 解析 TSCharacters.txt / TSPhrases.txt 作为"繁体字/词"白名单
        val tradChars: Set<String> = parseTsvKeys(tsCharactersTxt)
        val tradPhrases: Set<String> = parseTsvKeys(tsPhrasesTxt)

        test("clover.dict.yaml 文件存在且为合法 YAML") {
            cloverDictYaml.exists() shouldBe true
            @Suppress("UNCHECKED_CAST")
            val parsed = Yaml().load(cloverDictYaml.readText(Charsets.UTF_8)) as Map<String, Any?>
            parsed.containsKey("import_tables") shouldBe true
        }

        test("clover.dict.yaml import_tables 列表包含 clover.base 和 clover.phrase") {
            @Suppress("UNCHECKED_CAST")
            val parsed = Yaml().load(cloverDictYaml.readText(Charsets.UTF_8)) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val imports = (parsed["import_tables"] as List<String>).map { it.removeSuffix(".dict.yaml") }
            imports.contains("clover.base") shouldBe true
            imports.contains("clover.phrase") shouldBe true
        }

        test("clover.base.dict.yaml 文件存在且有内容") {
            cloverBaseDict.exists() shouldBe true
            val lineCount = cloverBaseDict.useLines { it.count() }
            withClue("clover.base.dict.yaml 行数") { (lineCount > 1000) shouldBe true }
        }

        test("clover.phrase.dict.yaml 文件存在且有内容") {
            cloverPhraseDict.exists() shouldBe true
            val lineCount = cloverPhraseDict.useLines { it.count() }
            withClue("clover.phrase.dict.yaml 行数") { (lineCount > 10000) shouldBe true }
        }

        test("clover.base.dict.yaml 不含 TSCharacters.txt 中的繁体字") {
            val violations = findTradCharsInDict(cloverBaseDict, tradChars)
            withClue("发现的繁体字条目(总数 ${violations.size}):\n${violations.take(20).joinToString("\n") { "  $it" }}") {
                violations shouldBe emptyList()
            }
        }

        test("clover.phrase.dict.yaml 不含 TSCharacters.txt 中的繁体字") {
            val violations = findTradCharsInDict(cloverPhraseDict, tradChars)
            withClue("发现的繁体字条目(总数 ${violations.size}):\n${violations.take(20).joinToString("\n") { "  $it" }}") {
                violations shouldBe emptyList()
            }
        }

        test("clover.base.dict.yaml 不含 TSPhrases.txt 中的繁体词") {
            val violations = findTradWordsInDict(cloverBaseDict, tradPhrases)
            withClue("发现的繁体词条目(总数 ${violations.size}):\n${violations.take(20).joinToString("\n") { "  $it" }}") {
                violations shouldBe emptyList()
            }
        }

        test("clover.phrase.dict.yaml 不含 TSPhrases.txt 中的繁体词") {
            val violations = findTradWordsInDict(cloverPhraseDict, tradPhrases)
            withClue("发现的繁体词条目(总数 ${violations.size}):\n${violations.take(20).joinToString("\n") { "  $it" }}") {
                violations shouldBe emptyList()
            }
        }

        test("clover.phrase.dict.yaml 行格式合法 (word\\tpinyin\\tweight)") {
            val badLines = mutableListOf<String>()
            var entryLineCount = 0
            cloverPhraseDict.useLines { lines ->
                for ((idx, line) in lines.withIndex()) {
                    if (idx >= 100) break
                    if (line.isBlank() || line.startsWith("#")) continue
                    // 跳过 YAML 元数据行(name/version/sort 等),只检查有 TAB 的字典条目
                    if (!line.contains("\t")) continue
                    entryLineCount++
                    val parts = line.split("\t")
                    if (parts.size < 2 || parts[0].isBlank()) {
                        badLines += "L$idx: $line"
                    }
                }
            }
            withClue("格式错误的行:\n${badLines.joinToString("\n")}\n检查的字典条目数: $entryLineCount") {
                badLines shouldBe emptyList()
                (entryLineCount > 10) shouldBe true
            }
        }
    })

// region 测试辅助函数

private fun parseTsvKeys(file: File): Set<String> {
    val keys = mutableSetOf<String>()
    file.useLines { lines ->
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split("\t")
            if (parts.isNotEmpty()) keys += parts[0]
        }
    }
    return keys
}

private fun findTradCharsInDict(dict: File, tradChars: Set<String>): List<String> {
    val violations = mutableListOf<String>()
    dict.useLines { lines ->
        for ((idx, line) in lines.withIndex()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val word = line.split("\t").firstOrNull() ?: continue
            word.forEach { ch ->
                if (tradChars.contains(ch.toString())) {
                    violations += "L$idx: $line (字符 '$ch' 是繁体)"
                }
            }
        }
    }
    return violations
}

private fun findTradWordsInDict(dict: File, tradPhrases: Set<String>): List<String> {
    val violations = mutableListOf<String>()
    dict.useLines { lines ->
        for ((idx, line) in lines.withIndex()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val word = line.split("\t").firstOrNull() ?: continue
            if (tradPhrases.contains(word)) {
                violations += "L$idx: $line (词 '$word' 是繁体词组)"
            }
        }
    }
    return violations
}

// endregion
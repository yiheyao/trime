// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * "关于"页面合规文本（开源协议声明）验证。
 *
 * 保护点：
 * 1. 三语言 strings.xml 均包含 about_oss_compliance 和 about_oss_compliance_notes_summary
 * 2. about_oss_compliance 包含 Trime 项目地址和 LGPLv3 许可证链接
 * 3. about_oss_compliance_repo_trime_url 已升级为 https:// 完整 URL
 * 4. about_oss_compliance_notes_summary 包含 rime-cloverpinyin 相关合规要求
 * 5. XML 不含转义错误（如 & 未转义为 &amp;）
 */
class AboutComplianceStringsTest :
    FunSpec({

        val locales = listOf(
            "values/strings.xml"         to "en",
            "values-zh-rCN/strings.xml"   to "zh-CN",
            "values-zh-rTW/strings.xml"   to "zh-TW",
        )

        fun extractStringValue(xmlText: String, name: String): String? {
            // 匹配 <string name="xxx">...</string>，支持跨行和转义字符
            val regex = Regex(
                """<string\s+name="$name"[^>]*>([\s\S]*?)</string>""",
            )
            return regex.find(xmlText)?.groupValues?.get(1)
                ?.replace(Regex("&(amp|lt|gt|quot|apos|#39);")) { m ->
                    when (m.groupValues[1]) {
                        "amp"  -> "&"
                        "lt"   -> "<"
                        "gt"   -> ">"
                        "quot" -> "\""
                        "apos" -> "'"
                        "#39"  -> "'"
                        else   -> m.value
                    }
                }
        }

        fun extractStringAttr(xmlText: String, name: String): String? {
            val regex = Regex("""<string\s+name="$name"[^>]*>""")
            return regex.find(xmlText)?.value
        }

        fun loadXml(locale: String): Pair<String, String> {
            val path = "src/main/res/$locale"
            val file = File(path)
            if (!file.exists()) {
                error("文件不存在: $path")
            }
            return path to file.readText(Charsets.UTF_8)
        }

        for ((locale, label) in locales) {
            val (_, xmlText) = loadXml(locale)

            test("$label: about_oss_compliance 存在且非空") {
                val v = extractStringValue(xmlText, "about_oss_compliance")
                withClue("about_oss_compliance in $locale") {
                    v shouldNotBe null
                    v!!.isNotBlank() shouldBe true
                }
            }

            test("$label: about_oss_compliance 包含 Trime 项目地址") {
                val v = extractStringValue(xmlText, "about_oss_compliance")
                withClue(v) {
                    v!! shouldContain "github.com/osfans/trime"
                    v shouldContain "https://github.com/osfans/trime"
                }
            }

            test("$label: about_oss_compliance 包含 LGPLv3 许可证链接") {
                val v = extractStringValue(xmlText, "about_oss_compliance")
                withClue(v) {
                    v!! shouldContain "lgpl"
                    v shouldContain "gnu.org/licenses"
                }
            }

            test("$label: about_oss_compliance_repo_trime_url 是完整 https:// URL") {
                val v = extractStringValue(xmlText, "about_oss_compliance_repo_trime_url")
                withClue(v) {
                    v!! shouldContain "https://github.com/osfans/trime"
                }
            }

            test("$label: about_oss_compliance_notes_summary 存在且非空") {
                val v = extractStringValue(xmlText, "about_oss_compliance_notes_summary")
                withClue("about_oss_compliance_notes_summary in $locale") {
                    v shouldNotBe null
                    v!!.isNotBlank() shouldBe true
                }
            }

            test("$label: about_oss_compliance_notes_summary 包含 rime-cloverpinyin 合规要求") {
                val v = extractStringValue(xmlText, "about_oss_compliance_notes_summary")
                withClue(v) {
                    v!! shouldContain "rime-cloverpinyin"
                }
            }

            test("$label: 全文 XML 无错误转义（& 未出现在 href 等场景）") {
                // 允许 & 出现在 string 标签内部（如 "A & B"），但不允许在属性值中
                val tagLineRegex = Regex("""(<string[^>]+>)([\s\S]*?)(</string>)""")
                tagLineRegex.findAll(xmlText).forEach { match ->
                    val tagOpen = match.groupValues[1]
                    val content = match.groupValues[2]
                    // 检查属性区域是否有未转义的 &
                    val attrPart = tagOpen.substringAfter('>').ifEmpty { tagOpen }
                    withClue("未转义的 & 出现在属性: $tagOpen") {
                        // 如果属性中有 &，应该是 &amp;
                        attrPart.shouldNotContain(Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#39;)"""))
                    }
                }
            }
        }
    })

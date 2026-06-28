#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 RIME 字典文件中移除所有繁体字/词条目。

依据:OpenCC 的 TSCharacters.txt 和 TSPhrases.txt(繁体→简体映射表)。
只要条目中的汉字部分包含任意「可被繁转简」的字(即该字在 TSCharacters.txt 的 key 中),
就认为该条目是繁体,予以删除。

用法:
    python tools/remove_traditional.py <input.dict.yaml> [output.dict.yaml]
如果不指定 output,则覆盖原文件。
"""

import sys
import os
import re


def load_traditional_chars(ts_file: str) -> set[str]:
    """从 TSCharacters.txt 加载繁体字集合(单字繁体→简体映射)。

    TSCharacters.txt 使用 TAB 分隔,每行格式: <繁体字>\\t<简体字>。
    第一列是繁体字(传统 → 简体的源),第二列是简体字。
    必须只取第一列,不能 split() 后把简体值也算进去。
    """
    trad = set()
    with open(ts_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            # 用 TAB 分割(因为 TSCharacters.txt 是 TAB 分隔)
            parts = line.split("\t")
            if parts and parts[0]:
                trad.add(parts[0])  # 第一列是繁体字
    return trad


def load_traditional_phrases(ts_file: str) -> set[str]:
    """从 TSPhrases.txt 加载繁体词组集合。"""
    trad = set()
    with open(ts_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if parts:
                trad.add(parts[0])
    return trad


CJK_RE = re.compile(r"[\u3400-\u9FFF]")


def has_traditional(word: str, trad_chars: set[str], trad_phrases: set[str]) -> bool:
    """判断汉字字符串中是否包含繁体字。"""
    if word in trad_phrases:
        return True
    for ch in word:
        if CJK_RE.match(ch) and ch in trad_chars:
            return True
    return False


def filter_dict(input_path: str, output_path: str,
                trad_chars: set[str], trad_phrases: set[str]) -> tuple[int, int]:
    """过滤字典文件,返回(保留行数, 删除行数)。"""
    kept = 0
    removed = 0
    with open(input_path, "r", encoding="utf-8") as fin, \
         open(output_path, "w", encoding="utf-8") as fout:
        for line in fin:
            stripped = line.rstrip("\n")
            # 保留空行和注释
            if not stripped.strip() or stripped.lstrip().startswith("#"):
                fout.write(line)
                continue
            # 文件头以 "---" 分隔
            if stripped.startswith("---"):
                fout.write(line)
                continue
            # 提取第一列(汉字部分)
            parts = stripped.split()
            if not parts:
                fout.write(line)
                continue
            word = parts[0]
            # name/version/sort/... 等元数据行
            if ":" in word and not CJK_RE.search(word):
                fout.write(line)
                continue
            # import_tables 等块结构行
            if word in ("import_tables:", "name:", "version:", "sort:"):
                fout.write(line)
                continue
            if word.startswith("-"):
                fout.write(line)
                continue
            if has_traditional(word, trad_chars, trad_phrases):
                removed += 1
            else:
                fout.write(line)
                kept += 1
    return kept, removed


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2] if len(sys.argv) > 2 else input_path

    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ts_chars_file = os.path.join(base_dir, "app/src/main/assets/shared/opencc/TSCharacters.txt")
    ts_phrases_file = os.path.join(base_dir, "app/src/main/assets/shared/opencc/TSPhrases.txt")

    if not os.path.exists(ts_chars_file):
        print(f"错误:未找到 {ts_chars_file}")
        sys.exit(1)
    if not os.path.exists(ts_phrases_file):
        print(f"错误:未找到 {ts_phrases_file}")
        sys.exit(1)

    print(f"加载繁体字表: {ts_chars_file}")
    trad_chars = load_traditional_chars(ts_chars_file)
    print(f"  共 {len(trad_chars)} 个繁体字")

    print(f"加载繁体词组表: {ts_phrases_file}")
    trad_phrases = load_traditional_phrases(ts_phrases_file)
    print(f"  共 {len(trad_phrases)} 个繁体词组")

    print(f"处理文件: {input_path}")
    kept, removed = filter_dict(input_path, output_path, trad_chars, trad_phrases)
    print(f"完成:保留 {kept} 条,删除 {removed} 条")
    print(f"输出: {output_path}")


if __name__ == "__main__":
    main()

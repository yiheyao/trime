#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查字典文件中是否还残留繁体字。"""

import os
import sys

BASE = r"D:\project\andriod\trime"
TS_FILE = os.path.join(BASE, "app/src/main/assets/shared/opencc/TSCharacters.txt")
DICT_DIR = os.path.join(BASE, "app/src/main/assets/shared")


def load_traditional_chars(path):
    """加载繁体字集合(TSCharacters.txt 的 key)。"""
    trad = set()
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if parts:
                trad.add(parts[0])
    return trad


def check_dict_file(path, trad_chars):
    """检查字典文件,统计仍然含繁体字的条目数。"""
    trad_entries = []
    total_entries = 0
    with open(path, "r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            stripped = line.rstrip("\n")
            if not stripped.strip() or stripped.lstrip().startswith("#"):
                continue
            if stripped.startswith("---"):
                continue
            parts = stripped.split()
            if not parts:
                continue
            word = parts[0]
            # 跳过元数据行
            if ":" in word and not any('\u4e00' <= ch <= '\u9fff' for ch in word):
                continue
            if word in ("import_tables:", "name:", "version:", "sort:"):
                continue
            if word.startswith("-"):
                continue
            total_entries += 1
            for ch in word:
                if '\u4e00' <= ch <= '\u9fff' and ch in trad_chars:
                    trad_entries.append((line_no, line.rstrip()))
                    break
    return total_entries, trad_entries


def main():
    trad_chars = load_traditional_chars(TS_FILE)
    print(f"加载繁体字表: 共 {len(trad_chars)} 个")
    print()

    files = [
        ("clover.base.dict.yaml", "原始 base"),
        ("clover.base.dict.yaml.simplified", "处理后 base"),
        ("clover.phrase.dict.yaml", "原始 phrase"),
        ("clover.phrase.dict.yaml.simplified", "处理后 phrase"),
    ]

    for fname, label in files:
        path = os.path.join(DICT_DIR, fname)
        if not os.path.exists(path):
            print(f"{label}: 文件不存在")
            print()
            continue
        total, trad_entries = check_dict_file(path, trad_chars)
        print(f"{label} ({fname})")
        print(f"  总条目: {total}")
        print(f"  含繁体: {len(trad_entries)}")
        if trad_entries:
            print(f"  示例(前 10):")
            for line_no, line in trad_entries[:10]:
                print(f"    第{line_no}行: {line}")
        print()


if __name__ == "__main__":
    main()

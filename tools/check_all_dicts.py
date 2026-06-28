#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查所有字典文件中的繁体字残留。"""

import os
import glob

BASE = r"D:\project\andriod\trime"
TS_FILE = os.path.join(BASE, "app/src/main/assets/shared/opencc/TSCharacters.txt")
TS_PHRASES = os.path.join(BASE, "app/src/main/assets/shared/opencc/TSPhrases.txt")
DICT_DIR = os.path.join(BASE, "app/src/main/assets/shared")


def load_trad(path):
    """加载繁体集合。"""
    trad = set()
    if not os.path.exists(path):
        return trad
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if parts:
                trad.add(parts[0])
            else:
                parts = line.split("\t")
                if parts:
                    trad.add(parts[0])
    return trad


def check_dict(path, trad_chars):
    """检查单个字典文件的繁体条目数。"""
    trad_entries = []
    total = 0
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
            total += 1
            for ch in word:
                if '\u4e00' <= ch <= '\u9fff' and ch in trad_chars:
                    trad_entries.append((line_no, stripped))
                    break
    return total, trad_entries


def main():
    trad_chars = load_trad(TS_FILE)
    trad_phrases = load_trad(TS_PHRASES)
    trad_all = trad_chars | trad_phrases
    print(f"加载繁体字/词组: 字 {len(trad_chars)}, 词组 {len(trad_phrases)}, 共 {len(trad_all)}")
    print()

    dict_files = sorted(glob.glob(os.path.join(DICT_DIR, "*.dict.yaml")))
    print(f"扫描 {len(dict_files)} 个字典文件:")
    print()
    print(f"{'文件':<40} {'条目':>10} {'繁体':>8} 状态")
    print("-" * 75)

    total_trad = 0
    for path in dict_files:
        fname = os.path.basename(path)
        total, trad_entries = check_dict(path, trad_all)
        status = "✅ 干净" if len(trad_entries) == 0 else f"❌ {len(trad_entries)} 条"
        print(f"{fname:<40} {total:>10} {len(trad_entries):>8} {status}")
        total_trad += len(trad_entries)

    print("-" * 75)
    print(f"{'总计':<40} {'':>10} {total_trad:>8}")

    if total_trad > 0:
        print()
        print("详细(有繁体的文件):")
        for path in dict_files:
            fname = os.path.basename(path)
            total, trad_entries = check_dict(path, trad_all)
            if trad_entries:
                print(f"\n  {fname}:")
                for line_no, line in trad_entries[:5]:
                    print(f"    第{line_no}行: {line[:80]}")
                if len(trad_entries) > 5:
                    print(f"    ... 还有 {len(trad_entries) - 5} 条")


if __name__ == "__main__":
    main()

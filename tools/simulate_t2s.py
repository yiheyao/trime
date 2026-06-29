#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟 RIME 的 t2s simplifier,验证转换效果。"""

import os

# 1. 加载 TSCharacters.txt(单字繁体→简体)
trad_chars = {}
with open(r"D:\project\andriod\trime\app\src\main\assets\shared\opencc\TSCharacters.txt",
          encoding="utf-8") as f:
    for line in f:
        parts = line.strip().split("\t")
        if len(parts) >= 2:
            trad_chars[parts[0]] = parts[1]

# 2. 加载 TSPhrases.txt(词组繁体→简体)
trad_phrases = {}
with open(r"D:\project\andriod\trime\app\src\main\assets\shared\opencc\TSPhrases.txt",
          encoding="utf-8") as f:
    for line in f:
        parts = line.strip().split("\t")
        if len(parts) >= 2:
            trad_phrases[parts[0]] = parts[1]


def t2s_convert(text: str) -> str:
    """模拟 OpenCC 的 t2s 转换流程(词组优先,然后单字)。"""
    result = text
    # 先尝试词组转换
    for trad, simp in trad_phrases.items():
        if trad in result:
            result = result.replace(trad, simp)
    # 再尝试单字转换
    for trad, simp in trad_chars.items():
        result = result.replace(trad, simp)
    return result


# 3. 测试用户截图中的候选
test_candidates = [
    "你好", "妳好", "逆蛻", "擫好", "擫", "尼",
    "還是", "還是", "還說", "還算", "海水", "海上", "害死",
    "中山", "中国", "語言", "學習",
]

print("=" * 60)
print("模拟 t2s 转换效果")
print("=" * 60)
print(f"{'原候选':<10} → {'转换后':<10} {'状态'}")
print("-" * 60)
for cand in test_candidates:
    converted = t2s_convert(cand)
    changed = "✓ 已转换" if converted != cand else "× 未变"
    print(f"{cand:<10} → {converted:<10} {changed}")
print()

# 4. 统计转换率
trad_count = sum(1 for c in test_candidates if c != t2s_convert(c))
print(f"转换率: {trad_count}/{len(test_candidates)} = {trad_count/len(test_candidates)*100:.0f}%")
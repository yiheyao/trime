#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Test t2s.json conversion."""
import json

with open(r"D:\project\andriod\trime\app\src\main\assets\shared\opencc\t2s.json", encoding="utf-8") as f:
    d = json.load(f)

data = d.get("conversion_chain", [{}])[0].get("data", {})

# 候选中可能出现的繁体字
test_chars = ["妳", "蛻", "擫", "是", "個", "時", "還", "學", "語", "爲", "個", "們"]
print("检查字符在 t2s.json 的映射:")
for ch in test_chars:
    if ch in data:
        print(f"  {ch} -> {data[ch]} (繁→简)")
    else:
        print(f"  {ch} -> 未映射")
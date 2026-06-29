#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Check specific characters in TSCharacters.txt."""
import os

ts_file = r"D:\project\andriod\trime\app\src\main\assets\shared\opencc\TSCharacters.txt"
ts_phrases = r"D:\project\andriod\trime\app\src\main\assets\shared\opencc\TSPhrases.txt"

# Load TSCharacters.txt (TAB separated)
trad_chars = {}
with open(ts_file, encoding="utf-8") as f:
    for line in f:
        parts = line.strip().split("\t")
        if len(parts) >= 2:
            trad_chars[parts[0]] = parts[1]

# Load TSPhrases.txt
trad_phrases = {}
with open(ts_phrases, encoding="utf-8") as f:
    for line in f:
        parts = line.strip().split("\t")
        if len(parts) >= 2:
            trad_phrases[parts[0]] = parts[1]

# Test specific characters from the screenshot
test_words = ["你好", "妳好", "逆蛻", "擫好", "擫", "尼"]
print("检查用户候选中的繁体字是否在 TSCharacters.txt / TSPhrases.txt 中:")
print()
for word in test_words:
    print(f"  '{word}':")
    for ch in word:
        if ch in trad_chars:
            print(f"    '{ch}' -> '{trad_chars[ch]}' (繁→简)")
        elif ch in trad_phrases:
            print(f"    '{ch}' -> '{trad_phrases[ch]}' (繁词组)")
        else:
            print(f"    '{ch}' -> 未映射 (简体或非繁体)")
    if word in trad_phrases:
        print(f"    整个词组: '{word}' -> '{trad_phrases[word]}'")
    print()
#!/usr/bin/env python3
"""tools/strings_data.py から app/src/{main,monetized}/res/values*/strings.xml を生成する。
使い方: python3 tools/gen_strings.py"""
import os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from strings_data import LANGS, FIXED, S, PL, MON

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src")
HEADER = '<?xml version="1.0" encoding="utf-8"?>\n<!-- 自動生成: tools/gen_strings.py（文言は tools/strings_data.py を編集） -->\n<resources>\n'

def esc(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s[:1] in ("@", "?"): s = "\\" + s
    if s != s.strip(): s = '"' + s + '"'   # 前後の空白を残す
    return s

def check(key, base, other, lang):
    f = lambda x: sorted(re.findall(r"%\d\$[sd]|%[sd]", x))
    if f(base) != f(other): raise SystemExit(f"placeholder mismatch {key} [{lang}]: {base!r} vs {other!r}")

def folder(lang): return "values" if lang == "en" else "values-" + lang

def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp: fp.write(HEADER + body + "</resources>\n")

for lang in LANGS:
    out = []
    if lang == "en":
        for k, v in FIXED.items(): out.append(f'    <string name="{k}" translatable="false">{esc(v)}</string>\n')
    for k, d in S.items():
        if lang not in d: raise SystemExit(f"missing {k} [{lang}]")
        check(k, d["en"], d[lang], lang)
        out.append(f'    <string name="{k}">{esc(d[lang])}</string>\n')
    for k, d in PL.items():
        out.append(f'    <plurals name="{k}">\n')
        for q, v in d[lang].items():
            check(k, d["en"]["other"], v, lang)
            out.append(f'        <item quantity="{q}">{esc(v)}</item>\n')
        out.append("    </plurals>\n")
    write(os.path.join(ROOT, "main", "res", folder(lang), "strings.xml"), "".join(out))
    out = []
    for k, d in MON.items():
        check(k, d["en"], d[lang], lang)
        out.append(f'    <string name="{k}">{esc(d[lang])}</string>\n')
    write(os.path.join(ROOT, "monetized", "res", folder(lang), "strings.xml"), "".join(out))
print("ok:", len(S), "strings,", len(PL), "plurals,", len(LANGS), "languages")

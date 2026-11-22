#!/usr/bin/env python3
"""WCAG 2.1 contrast check for the status badge palette of doc 10 section 5.1.
Usage: python3 docs/specs/verify/check-10-contrast.py   (from the repository root)
Reads the --status-* custom properties from docs/ui-mockups/css/app.css."""
import re, sys
css = open("docs/ui-mockups/css/app.css", encoding="utf-8").read()
def lum(h):
    h = h.lstrip("#"); r, g, b = (int(h[i:i+2], 16) / 255 for i in (0, 2, 4))
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True); return (la + 0.05) / (lb + 0.05)
pairs = re.findall(r"--(status-[a-z-]+|overdue|awaiting)-bg:\s*(#[0-9a-fA-F]{6});\s*--\1-fg:\s*(#[0-9a-fA-F]{6})", css)
fail = 0
for name, bg, fg in pairs:
    r = ratio(bg, fg); ok = r >= 4.5; fail |= not ok
    print(f"{name:26s} bg {bg} fg {fg}  {r:5.2f}:1  {'PASS' if ok else 'FAIL'} (AA normal text >= 4.5)")
print(f"{len(pairs)} pairs checked"); sys.exit(1 if fail or len(pairs) < 12 else 0)

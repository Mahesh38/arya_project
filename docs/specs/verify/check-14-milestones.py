#!/usr/bin/env python3
"""Doc 14 milestone-plan check (doc 14 §11.4, §12 V21).

Every test-case ID of 13a, every FR of doc 03 and every route of doc 09 belongs to exactly one
milestone of doc 14 §11.3, and the counts in the milestone rows and in the §11.1 table are right.

A list item "TC-SM-02…16" means every ID from TC-SM-02 to TC-SM-16 in catalogue order (same group);
"R-80…R-100" every route ID from 80 to 100.

Run from the repository root:  python3 docs/specs/verify/check-14-milestones.py
"""
import os
import re
import sys
from collections import Counter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SPECS = os.path.join(ROOT, "docs", "specs")


def read(name):
    with open(os.path.join(SPECS, name), encoding="utf-8") as f:
        return f.read()


problems = []

# --- reference sets -------------------------------------------------------------------------
catalogue, priority = [], {}
for line in read("13a-test-catalogue.md").splitlines():
    if line.startswith("| TC-"):
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        catalogue.append(cells[0])
        priority[cells[0]] = cells[-1]
frs = [m for m in re.findall(r"^\| \*{0,2}(FR-\d+[a-z]?)\*{0,2} \|", read("03-requirements-srs.md"), re.M)]
doc09 = read("09-web-and-api-contracts.md")
routes_part = doc09.split("## 3. Route catalogue", 1)[1].split("### 3.12", 1)[0]
routes = sorted({int(r) for r in re.findall(r"\bR-(\d+)\b", routes_part)})

# --- doc 14 §11.3 ---------------------------------------------------------------------------
doc14 = read("14-build-and-deployment.md")
plan = doc14.split("### 11.3", 1)[1].split("### 11.4", 1)[0]
milestones = re.split(r"^#### (M\d) — .*$", plan, flags=re.M)[1:]
milestones = dict(zip(milestones[0::2], milestones[1::2]))


def row(text, label):
    m = re.search(r"^\| " + label + r"[^|]*\| (.*) \|$", text, re.M)
    return m.group(0) if m else None


def expand_tc(items):
    out = []
    for item in items:
        if "…" not in item:
            out.append(item)
            continue
        start, end = item.split("…")
        group = start.split("-")[1]
        end = end if end.startswith("TC-") else f"TC-{group}-{end}"
        if start not in catalogue or end not in catalogue:
            problems.append(f"range {item}: endpoint not in 13a")
            continue
        i, j = catalogue.index(start), catalogue.index(end)
        out.extend(x for x in catalogue[i:j + 1] if x.split("-")[1] == group)
    return out


def expand_routes(text):
    out = []
    for a, b in re.findall(r"R-(\d+)(?:…R-(\d+))?", text):
        lo, hi = int(a), int(b or a)
        out.extend(r for r in routes if lo <= r <= hi)
    return out


tc_owner, fr_owner, route_owner = Counter(), Counter(), Counter()
tc_of, fr_of, route_of = {}, {}, {}
counts = {}
for m, text in milestones.items():
    tc_row = row(text, "Test cases")
    if not tc_row:
        problems.append(f"{m}: no 'Test cases' row")
        continue
    label, items = tc_row.split("|")[1], tc_row.split("|")[2]
    ids = expand_tc([t.strip() for t in items.split(",") if t.strip()])
    for x in ids:
        tc_owner[x] += 1
        tc_of.setdefault(x, []).append(m)
    must = sum(1 for x in ids if priority.get(x) == "Must")
    counts[m] = (len(ids), must)
    stated = re.search(r"(\d+) cases, (\d+) Must", label)
    if not stated or (int(stated.group(1)), int(stated.group(2))) != (len(ids), must):
        problems.append(f"{m}: row label '{label.strip()}' but the list expands to {len(ids)} cases, {must} Must")
    fr_row = row(text, "FRs") or ""
    for f in re.findall(r"FR-\d+[a-z]?", fr_row):
        fr_owner[f] += 1
        fr_of.setdefault(f, []).append(m)
    rt_row = row(text, "Routes") or ""
    for r in expand_routes(rt_row.split("|")[2] if rt_row else ""):
        route_owner[r] += 1
        route_of.setdefault(r, []).append(m)

for x in catalogue:
    if tc_owner[x] != 1:
        problems.append(f"{x}: in {tc_owner[x]} milestones {tc_of.get(x, [])}")
for x in tc_owner:
    if x not in priority:
        problems.append(f"{x}: listed in a milestone but not in 13a")
for f in frs:
    if fr_owner[f] != 1:
        problems.append(f"{f}: in {fr_owner[f]} milestones {fr_of.get(f, [])}")
for f in fr_owner:
    if f not in frs:
        problems.append(f"{f}: listed in a milestone but not in doc 03")
for r in routes:
    if route_owner[r] != 1:
        problems.append(f"R-{r:02d}: in {route_owner[r]} milestones {route_of.get(r, [])}")

# --- §11.1 summary table ---------------------------------------------------------------------
summary = doc14.split("### 11.1", 1)[1].split("### 11.2", 1)[0]
for m, (n, must) in counts.items():
    hit = re.search(r"^\| " + m + r" [^|]*\|[^|]*\|[^|]*\| (\d+) \((\d+)\) \|$", summary, re.M)
    if not hit or (int(hit.group(1)), int(hit.group(2))) != (n, must):
        problems.append(f"§11.1: {m} should show {n} ({must})")
total = (sum(c[0] for c in counts.values()), sum(c[1] for c in counts.values()))
if f"**{total[0]} ({total[1]})**" not in summary:
    problems.append(f"§11.1: total should be {total[0]} ({total[1]})")

print(f"milestones: {len(milestones)} ({', '.join(milestones)})")
print(f"test cases: {sum(1 for x in catalogue if tc_owner[x] == 1)}/{len(catalogue)} in exactly one milestone "
      f"({total[1]} Must of {sum(1 for p in priority.values() if p == 'Must')})")
print(f"FRs: {sum(1 for f in frs if fr_owner[f] == 1)}/{len(frs)} in exactly one milestone")
print(f"routes: {sum(1 for r in routes if route_owner[r] == 1)}/{len(routes)} in exactly one milestone")
print("per milestone (cases, Must): " + ", ".join(f"{m} {c[0]}/{c[1]}" for m, c in counts.items()))
if problems:
    print("\nPROBLEMS:")
    print("\n".join(problems))
    sys.exit(1)
print("OK: all checks passed")

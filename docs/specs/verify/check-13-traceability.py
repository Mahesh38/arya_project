#!/usr/bin/env python3
"""Doc 13 traceability check.

Usage: python3 docs/specs/verify/check-13-traceability.py   (from the repository root)

Reads docs 00, 03, 04, 05, 07, 08, 09, 10, 11, 12, 13 and the catalogue 13a and checks:
 1. every test case row of 13a has a unique, well-formed ID and a priority (Must / Later);
 2. every test case traces to at least one ID, and every traced ID exists in the source documents
    (FR, NFR, BR, UC, R, SEC, T01-T13, S1-S12, Q01-Q31, D, ADR) - the reverse check of doc 13 §4.3;
 3. every Must FR has at least one test case;
 4. every use case has at least one acceptance test (TC-UC row);
 5. every transition T01-T13 is traced by a state-machine test (TC-SM row);
 6. every SEC-xx of doc 11 §12 is mapped by its own TC-SEC-xx row;
 7. every route R-xx of doc 09 §3 is covered by its route-test row TC-RT-xx;
 8. every BR, scenario S1-S12, query Q01-Q31 and NFR is traced by at least one test;
 9. the expected matrix of 13a §A.2 agrees with the transition table of doc 05 §3 (statuses and actors);
10. the master matrix of doc 13 §4.1 has one row per Must FR, and every TC/R/UC/SCR/Q ID used in doc 13 exists;
11. the open items K11, U1, U7, U8, U10, K-S3 and T-1 ... T-6 are resolved in doc 13 §3.8;
12. the summary table of doc 13 §3 equals the counts computed from 13a.
Exit code 0 = all checks passed.
"""
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
S = ROOT / "docs/specs"
def read(name): return (S / name).read_text(encoding="utf-8")
d00, d03, d04, d05, d07, d08, d09, d10, d11, d12, d13, d13a = (read(n) for n in (
    "00-specification-plan.md", "03-requirements-srs.md", "04-use-cases.md", "05-domain-model-and-business-rules.md",
    "07-architecture-and-tech-stack.md", "08-database-design.md", "09-web-and-api-contracts.md", "10-ui-screen-spec.md",
    "11-security-spec.md", "12-validation-and-error-handling.md", "13-test-spec-and-traceability.md", "13a-test-catalogue.md"))

failures = []
def check(ok, label, detail=""):
    print(f"[{'PASS' if ok else 'FAIL'}] {label}" + (f" -- {detail}" if detail and not ok else ""))
    if not ok: failures.append(label)

def section(text, start, end=None):
    i = text.index(start)
    j = text.index(end, i + len(start)) if end else len(text)
    return text[i:j]

def cells(line): return [c.strip() for c in line.strip().strip("|").split("|")]

# ---------------------------------------------------------------- source IDs
fr_rows = re.findall(r"^\| (FR-\d+[a-z]?) \|.*\| ([MSC]) \|$", section(d03, "## 1. Functional", "## 2."), re.M)
FR = {f for f, _ in fr_rows}
MUST = [f for f, p in fr_rows if p == "M"]
NFR = set(re.findall(r"^\| (NFR-\d+) \|", d03, re.M))
UC = re.findall(r"^\| (UC-\d+) \|", section(d04, "## 1. Use-case catalogue", "## 2."), re.M)
t_table = section(d05, "## 3. Transition table", "## 4.")
T_ROWS = {m.group(1): cells(m.group(0)) for m in re.finditer(r"^\| (T\d\d) \|.*\|$", t_table, re.M)}
T = sorted(T_ROWS)
BR = set(re.findall(r"^\| (BR-\d+) \|", section(d05, "## 4. Business rules", "## 5."), re.M))
SC = set(re.findall(r"^\| (S\d+) \|", section(d05, "## 5. Worked scenarios"), re.M))
Q = set(re.findall(r"\bQ\d\d\b", d08))
catalogue = section(d09, "## 3. Route catalogue", "## 4.")
R = set(re.findall(r"^\| (R-\d+) \|", catalogue, re.M))
for line in catalogue.splitlines():
    if re.match(r"^\| (Departments|Categories|Areas) \|", line):
        R |= set(re.findall(r"(?<![A-Z])R-\d+", line))
SEC = re.findall(r"^\| (SEC-\d+[a-z]?) \|", section(d11, "## 12. Security test plan", "## 13."), re.M)
D = set(re.findall(r"^\| (D\d+) \|", d00, re.M))
ADR = set(re.findall(r"\*\*(ADR-\d+)\*\*", d07))
SCR = set(re.findall(r"^\| (SCR-\d+) \|", d10, re.M))
T12 = re.findall(r"^\| (T-\d) \|", section(d12, "## 12. Open questions"), re.M)
print(f"Source IDs: {len(FR)} FR ({len(MUST)} Must), {len(NFR)} NFR, {len(UC)} UC, {len(T)} transitions, {len(BR)} BR, "
      f"{len(SC)} scenarios, {len(Q)} queries, {len(R)} routes, {len(SEC)} SEC, {len(D)} decisions, {len(ADR)} ADRs, {len(SCR)} screens")

KNOWN = FR | NFR | set(UC) | set(T) | BR | SC | Q | R | set(SEC) | D | ADR
ID_RE = re.compile(r"(?<![\w-])(?:N?FR-\d+[a-z]?|BR-\d+|UC-\d+|R-\d+|SEC-\d+[a-z]?|T\d\d|S\d+|Q\d\d|D\d+|ADR-\d+)\b")

# ---------------------------------------------------------------- catalogue rows
rows = []
for n, line in enumerate(d13a.splitlines(), 1):
    if line.startswith("| TC-"):
        c = cells(line)
        rows.append({"id": c[0], "cells": c, "traces": c[-2], "pri": c[-1], "line": n})
ids = [r["id"] for r in rows]
dups = [i for i, k in Counter(ids).items() if k > 1]
check(not dups, f"1a. {len(ids)} test case IDs, all unique", f"duplicates: {dups}")
bad = [i for i in ids if not re.fullmatch(r"TC-(SM|BR|UC|RT|SEC|VE|SC|Q|NF|UT|SCAN)-[0-9A-Za-z]+", i)]
check(not bad, "1b. every ID has the form TC-<group>-<n>", f"bad: {bad}")
badpri = [r["id"] for r in rows if r["pri"] not in ("Must", "Later")]
check(not badpri, "1c. every test case has priority Must or Later", f"bad: {badpri}")

traced = defaultdict(set)          # source ID -> test IDs
empty, unknown = [], defaultdict(list)
for r in rows:
    found = ID_RE.findall(r["traces"])
    if not found: empty.append(r["id"])
    for x in found:
        traced[x].add(r["id"])
        if x not in KNOWN: unknown[x].append(r["id"])
check(not empty, f"2a. reverse check: every test case traces to at least one ID ({len(rows)} / {len(rows)})" if not empty
      else "2a. reverse check: every test case traces to at least one ID", f"no trace: {empty}")
check(not unknown, "2b. no unknown IDs in the Traces columns", f"unknown: {dict(unknown)}")
# IDs anywhere in 13a must exist too (steps and expected texts)
stray = sorted({x for x in ID_RE.findall(d13a) if x not in KNOWN})
check(not stray, "2c. no unknown IDs anywhere in 13a", f"unknown: {stray}")

def report(label, family, need):
    missing = [x for x in need if not traced.get(x)]
    check(not missing, f"{label}: {len(need) - len(missing)} / {len(need)} traced", f"missing: {missing}")
    return missing

report("3. Must FRs with at least one test case", "FR", MUST)
print(f"   (all FRs incl. Should/Could: {sum(1 for f in FR if traced.get(f))} / {len(FR)} traced)")
uc_missing = [u for u in UC if not any(t.startswith("TC-UC-") for t in traced.get(u, ()))]
check(not uc_missing, f"4. use cases with an acceptance test: {len(UC) - len(uc_missing)} / {len(UC)}", f"missing: {uc_missing}")
t_missing = [t for t in T if not any(x.startswith("TC-SM-") for x in traced.get(t, ()))]
check(not t_missing, f"5. transitions T01-T13 with a state-machine test: {len(T) - len(t_missing)} / {len(T)}", f"missing: {t_missing}")
sec_missing = [s for s in SEC if "TC-" + s not in traced.get(s, set())]
check(not sec_missing, f"6. SEC tests mapped to their own TC-SEC row: {len(SEC) - len(sec_missing)} / {len(SEC)}", f"missing: {sec_missing}")
rt_rows = {r["id"]: r for r in rows if r["id"].startswith("TC-RT-")}
r_missing = [x for x in sorted(R, key=lambda s: int(s[2:]))
             if not (("TC-RT-" + x[2:]) in rt_rows and x in ID_RE.findall(" ".join(rt_rows["TC-RT-" + x[2:]]["cells"])))]
check(not r_missing, f"7. routes with a route-test row: {len(R) - len(r_missing)} / {len(R)}", f"missing: {r_missing}")
report("8a. business rules", "BR", sorted(BR, key=lambda s: int(s[3:])))
report("8b. scenarios S1-S12", "S", sorted(SC, key=lambda s: int(s[1:])))
report("8c. queries Q01-Q31", "Q", sorted(Q))
report("8d. non-functional requirements", "NFR", sorted(NFR))

# ---------------------------------------------------------------- 9. matrix vs. doc 05 §3
STAT = ["SUBMITTED", "UNDER_REVIEW", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "REOPENED", "CLOSED", "REJECTED", "DUPLICATE", "WITHDRAWN"]
mat = {}
for line in section(d13a, "### A.2", "### A.3").splitlines():
    c = cells(line)
    if len(c) == 11 and re.fullmatch(r"[A-Z_]+", c[0]) and c[0] != "Action":
        mat[c[0]] = dict(zip(STAT, c[1:]))
ACTION_T = {"START_REVIEW": "T02", "ASSIGN": "T03", "REASSIGN": "T04", "START_WORK": "T05", "RESOLVE": "T06",
            "CLOSE": "T07", "REOPEN": "T08", "SEND_BACK": "T10", "REJECT": "T11", "MARK_DUPLICATE": "T12", "WITHDRAW": "T13"}
def from_statuses(row_from):
    if "same as T11" in row_from: row_from = T_ROWS["T11"][2]
    return {s for s in STAT if re.search(r"`%s`" % s, row_from)}
mism = []
for act, t in ACTION_T.items():
    row = T_ROWS[t]
    allowed = {s for s, v in mat.get(act, {}).items() if v not in ("·", "")}
    if allowed != from_statuses(row[2]): mism.append(f"{act}/{t} statuses {sorted(allowed)} vs {sorted(from_statuses(row[2]))}")
    letters = set("".join(mat.get(act, {}).values()))
    actor = row[4]
    for word, letter in (("Admin", "A"), ("Officer", "F"), ("Owner", "O")):
        if word in actor and letter not in letters: mism.append(f"{act}: actor {word} missing")
        if word not in actor and letter in letters and not (letter == "A" and "Admin may also" in actor): mism.append(f"{act}: extra actor {letter}")
officer_only = {s for s, v in mat["REJECT"].items() if "F" in v}
if officer_only != from_statuses(T_ROWS["T11"][4].split("Officer:")[1]): mism.append("REJECT officer statuses")
check(len(mat) == 15 and not mism, f"9. expected matrix (15 actions) agrees with doc 05 §3 for {len(ACTION_T)} transitions",
      f"{len(mat)} actions; {mism}")
cells_allowed = sum(len(v.replace("·", "").replace(" ", "")) for a in mat.values() for v in a.values())
print(f"   allowed (relation, status) cells: {cells_allowed}")

# ---------------------------------------------------------------- 10. doc 13 matrix and IDs
m41 = section(d13, "### 4.1", "### 4.2")
m_rows = {cells(l)[0]: cells(l) for l in m41.splitlines() if re.match(r"^\| FR-", l)}
miss = [f for f in MUST if f not in m_rows]
extra = [f for f in m_rows if f not in MUST]
check(not miss and not extra, f"10a. doc 13 §4.1 has one row per Must FR ({len(m_rows)} / {len(MUST)})", f"missing {miss}, extra {extra}")
no_test = [f for f, c in m_rows.items() if "TC-" not in c[-1]]
check(not no_test, "10b. every row of doc 13 §4.1 names at least one test case", f"{no_test}")
tc_in_13 = set(re.findall(r"\bTC-(?:SM|BR|UC|RT|SEC|VE|SC|Q|NF|UT|SCAN)-[0-9A-Za-z]+\b", d13))
undefined = sorted(tc_in_13 - set(ids))
check(not undefined, f"10c. all {len(tc_in_13)} test case IDs used in doc 13 exist in 13a", f"undefined: {undefined}")
other = {x for x in ID_RE.findall(d13) if x not in KNOWN}
scr_bad = {x for x in re.findall(r"\bSCR-\d+\b", d13) if x not in SCR}
check(not other and not scr_bad, "10d. every FR/UC/R/Q/SEC/BR/D/SCR ID used in doc 13 exists", f"unknown: {sorted(other | scr_bad)}")
for f, c in m_rows.items():   # the test column must only list tests that really trace the FR
    for t in re.findall(r"TC-[A-Z]+-[0-9A-Za-z]+", c[-1]):
        if f not in traced or t not in traced[f]:
            failures.append(f"10e {f} {t}"); print(f"[FAIL] 10e. {t} in the row of {f} does not trace {f}")
print("[PASS] 10e. the test column of §4.1 only lists tests whose Traces contain that FR" if not any(x.startswith("10e") for x in failures) else "")

# ---------------------------------------------------------------- 11. open items
s38 = section(d13, "### 3.8", "## 4.")
items = ["K11", "U1", "U7", "U8", "U10", "K-S3"] + T12
miss = [i for i in items if not re.search(r"\b%s\b" % re.escape(i), s38)]
check(not miss, f"11. open items resolved in doc 13 §3.8: {len(items) - len(miss)} / {len(items)} ({', '.join(items)})", f"missing: {miss}")

# ---------------------------------------------------------------- 12. summary counts
groups = {"A": ["SM"], "B": ["BR"], "C": ["UC"], "D": ["RT"], "E": ["SEC"], "F": ["VE"], "G": ["SC"], "H": ["Q"], "I": ["NF"], "J": ["UT", "SCAN"]}
cnt = Counter()
for r in rows:
    g = r["id"].split("-")[1]
    cnt[(g, r["pri"])] += 1
bad = []
for line in section(d13, "## 3. Test catalogue", "### 3.1").splitlines():
    m = re.match(r"^\| ([A-J]) `", line)
    if m:
        c = cells(line)
        must = sum(cnt[(g, "Must")] for g in groups[m.group(1)]); later = sum(cnt[(g, "Later")] for g in groups[m.group(1)])
        if (int(c[2]), int(c[3]), int(c[4])) != (must, later, must + later): bad.append(f"{m.group(1)}: doc {c[2:5]} vs {must}/{later}")
tm, tl = sum(v for (g, p), v in cnt.items() if p == "Must"), sum(v for (g, p), v in cnt.items() if p == "Later")
tot = re.search(r"\| \*\*Total\*\* \| \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \|", d13)
if not tot or tuple(map(int, tot.groups())) != (tm, tl, tm + tl): bad.append(f"total {tot.groups() if tot else None} vs {tm}/{tl}")
check(not bad, f"12. doc 13 §3 summary equals 13a: {tm} Must + {tl} Later = {tm + tl}", f"{bad}")

print()
print("Test cases per group: " + ", ".join(f"{g} {cnt[(g, 'Must')] + cnt[(g, 'Later')]}" for g in
      ["SM", "BR", "UC", "RT", "SEC", "VE", "SC", "Q", "NF", "UT", "SCAN"]))
fam = Counter()
for x in traced:
    fam[re.match(r"[A-Z]+", x).group()] += 1
print("Distinct IDs traced: " + ", ".join(f"{k} {v}" for k, v in sorted(fam.items())))
print("RESULT: " + ("ALL CHECKS PASSED" if not failures else f"{len(failures)} CHECK(S) FAILED"))
sys.exit(1 if failures else 0)

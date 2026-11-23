#!/usr/bin/env python3
"""One-page test report for the project report and the viva (doc 13 §7.3, doc 14 §11.5, B-8).

Usage (from the repository root, after ./mvnw verify [-Pmysql] [-Pe2e]):
    python3 docs/ci/test-report.py app/target > test-report.md

Reads   target/surefire-reports/TEST-*.xml, target/failsafe-reports/TEST-*.xml and
        target/site/jacoco/jacoco.csv. The Playwright result (list output of -Pe2e) is added by hand.
Prints  Markdown: date, commit, Java version, tests per level (from the class-name suffix, doc 13 §2.9),
        failures, slowest classes, coverage per package and for the bundle.
Needs only the Python 3 standard library.
"""
import csv
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from datetime import datetime, timedelta, timezone

LEVELS = [  # (suffix regex, level name) in the order of doc 13 §2.9
    (r"MySqlIT$", "MySQL"),
    (r"RepositoryTest$", "Repo"),
    (r"FlowTest$", "Flow"),
    (r"(WebTest|RouteTest|AcceptanceTest)$", "Web"),
    (r"HttpTest$", "HTTP"),
    (r"ScanTest$", "Scan"),
    (r"Test$", "Unit"),
]


def level_of(classname: str) -> str:
    simple = classname.rsplit(".", 1)[-1]
    for pattern, name in LEVELS:
        if re.search(pattern, simple):
            return name
    return "Other"


def read_suites(target: str):
    rows = []
    for folder in ("surefire-reports", "failsafe-reports"):
        for path in sorted(glob.glob(os.path.join(target, folder, "TEST-*.xml"))):
            root = ET.parse(path).getroot()
            rows.append({
                "class": root.get("name"),
                "tests": int(root.get("tests", 0)),
                "failures": int(root.get("failures", 0)),
                "errors": int(root.get("errors", 0)),
                "skipped": int(root.get("skipped", 0)),
                "time": float(root.get("time", 0) or 0),
                "failed": [tc.get("name") for tc in root.iter("testcase")
                           if tc.find("failure") is not None or tc.find("error") is not None],
            })
    return rows


def coverage(target: str):
    path = os.path.join(target, "site", "jacoco", "jacoco.csv")
    if not os.path.exists(path):
        return None
    per_pkg = defaultdict(lambda: [0, 0, 0, 0])  # line missed, covered, branch missed, covered
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            p = per_pkg[r["PACKAGE"]]
            p[0] += int(r["LINE_MISSED"]); p[1] += int(r["LINE_COVERED"])
            p[2] += int(r["BRANCH_MISSED"]); p[3] += int(r["BRANCH_COVERED"])
    return per_pkg


def pct(missed: int, covered: int) -> str:
    total = missed + covered
    return "–" if total == 0 else f"{100.0 * covered / total:.1f} %"


def git(*args: str) -> str:
    try:
        return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def main() -> int:
    target = sys.argv[1] if len(sys.argv) > 1 else "app/target"
    suites = read_suites(target)
    ist = timezone(timedelta(hours=5, minutes=30))
    print("# Test report — Smart Grievance\n")
    print("| | |\n|---|---|")
    print(f"| Date | {datetime.now(ist):%d-%b-%Y %I:%M %p} IST |")
    print(f"| Commit | `{git('rev-parse', '--short', 'HEAD')}` ({git('describe', '--tags', '--always')}) |")
    java = [l for l in subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.splitlines()
            if " version " in l]
    print(f"| Java (on PATH) | {java[0] if java else 'unknown'} |")
    print(f"| Reports | `{target}` |\n")

    by_level = defaultdict(lambda: [0, 0, 0, 0, 0.0])
    for s in suites:
        b = by_level[level_of(s["class"])]
        b[0] += s["tests"]; b[1] += s["failures"] + s["errors"]; b[2] += s["skipped"]; b[3] += 1; b[4] += s["time"]
    print("## Tests per level\n")
    print("| Level | Classes | Tests | Failed | Skipped | Time (s) |\n|---|---:|---:|---:|---:|---:|")
    total = [0, 0, 0, 0, 0.0]
    for name in ["Unit", "Repo", "Flow", "Web", "HTTP", "Scan", "MySQL", "Other"]:
        if name in by_level:
            b = by_level[name]
            print(f"| {name} | {b[3]} | {b[0]} | {b[1]} | {b[2]} | {b[4]:.1f} |")
            total = [t + v for t, v in zip(total, [b[0], b[1], b[2], b[3], b[4]])]
    print(f"| **Total** | **{total[3]}** | **{total[0]}** | **{total[1]}** | **{total[2]}** | **{total[4]:.1f}** |\n")

    failed = [(s["class"], n) for s in suites for n in s["failed"]]
    print("## Failures\n")
    print("None." if not failed else "\n".join(f"- `{c}` › {n}" for c, n in failed))
    print("\n## Slowest classes\n")
    print("| Class | Tests | Time (s) |\n|---|---:|---:|")
    for s in sorted(suites, key=lambda s: -s["time"])[:5]:
        print(f"| `{s['class'].rsplit('.', 1)[-1]}` | {s['tests']} | {s['time']:.1f} |")

    cov = coverage(target)
    print("\n## Coverage (JaCoCo, doc 13 §6.1)\n")
    if cov is None:
        print("No `site/jacoco/jacoco.csv` found (run `./mvnw verify`).")
    else:
        print("| Package | Line | Branch |\n|---|---:|---:|")
        bundle = [0, 0, 0, 0]
        for pkg in sorted(cov):
            c = cov[pkg]
            bundle = [a + b for a, b in zip(bundle, c)]
            print(f"| `{pkg}` | {pct(c[0], c[1])} | {pct(c[2], c[3])} |")
        print(f"| **Bundle** | **{pct(bundle[0], bundle[1])}** | **{pct(bundle[2], bundle[3])}** |")
    print("\n## To fill in by hand\n")
    print("- `-Pmysql` run date and result; `-Pe2e` run date and result (Playwright list output)")
    print("- NFR-10 times on the reference laptop (TC-NF-01, doc 14 §11.5)")
    print("- Open defects by severity (doc 13 §7.1); result of the `check-*` scripts; ticked demo checklist (doc 13 §5)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

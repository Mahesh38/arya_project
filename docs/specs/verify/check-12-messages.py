#!/usr/bin/env python3
"""Doc 12 message catalogue check.

Usage: python3 docs/specs/verify/check-12-messages.py   (from the repository root)

1. messages.properties.draft parses, has no duplicate keys, and every key follows
   the naming convention of doc 12 §8.2.
2. Writing rules (doc 12 §8.3): no ASCII apostrophe, no HTML, no mixing of Bean
   Validation parameters ({min}/{max}) with MessageFormat arguments ({0}),
   balanced braces, no exclamation marks.
3. Every key mentioned in doc 12 exists in the draft, and every draft key is
   mentioned in doc 12.
4. Wherever doc 12 shows a key in a table cell followed by a quoted text cell,
   the text equals the draft value.
5. Every notify.* template, filled with the longest allowed arguments, fits into
   notification.message VARCHAR(300) (doc 08 §3.12, doc 12 §8.6).
Exit code 0 = all checks passed.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DRAFT = ROOT / "docs/specs/messages.properties.draft"
DOC = ROOT / "docs/specs/12-validation-and-error-handling.md"

PREFIXES = ("validation", "error", "flash", "login", "notice", "confirm", "notify", "page", "form")
KEY_RE = re.compile(r"^(?:(?:%s)\.[a-z][A-Za-z0-9]*(?:\.[A-Za-z0-9]+)*|typeMismatch(?:\.[A-Za-z0-9]+)*)$" % "|".join(PREFIXES))
# a key in backticks: a prefix plus at least one segment (bare kinds such as `flash` or `page.error` are not keys)
DOC_KEY_RE = re.compile(r"`((?:%s)(?:\.[A-Za-z0-9]+)+|typeMismatch(?:\.[A-Za-z0-9]+)*)`" % "|".join(PREFIXES))
NOT_KEYS = {"page.error"}

# Longest value of each notification argument (doc 12 §8.6): complaint number 15,
# department name 100 (VARCHAR(100)), remark excerpt 120 + "…", title excerpt 100 + "…",
# date "29-Nov-2026" 11, review-window days 2 digits.
NO, DEPT, REMARK, TITLE, DATE, DAYS = 15, 100, 121, 101, 11, 2
NOTIFY_ARGS = {
    "notify.submitted.owner": [NO],
    "notify.submitted.admin": [NO, TITLE],
    "notify.reviewStarted": [NO],
    "notify.assigned.citizen": [NO, DEPT],
    "notify.assigned.officer": [NO],
    "notify.reassigned.citizen": [NO, DEPT, REMARK],
    "notify.reassigned.officer": [NO, REMARK],
    "notify.workStarted": [NO, REMARK],
    "notify.remark": [NO, REMARK],
    "notify.resolved.owner": [NO, REMARK, DATE],
    "notify.resolved.watcher": [NO, REMARK],
    "notify.rejected": [NO, REMARK],
    "notify.duplicate.owner": [NO, NO],
    "notify.duplicate.watcher": [NO, NO],
    "notify.reopened": [NO, REMARK],
    "notify.closed": [NO],
    "notify.autoClosed": [NO, DAYS],
    "notify.sentBack": [NO, DEPT, REMARK],
    "notify.edited": [NO],
    "notify.withdrawn": [NO, REMARK],
}
MAX_NOTIFICATION = 300

problems = []


def load_draft():
    keys = {}
    for n, line in enumerate(DRAFT.read_text(encoding="utf-8").splitlines(), 1):
        s = line.strip()
        if not s or s.startswith("#") or s.startswith("!"):
            continue
        if "=" not in s:
            problems.append(f"draft line {n}: no '='")
            continue
        k, v = s.split("=", 1)
        k, v = k.strip(), v.strip()
        if k in keys:
            problems.append(f"draft: duplicate key {k}")
        keys[k] = v
    return keys


def check_rules(keys):
    for k, v in keys.items():
        if not KEY_RE.match(k):
            problems.append(f"{k}: key does not follow the naming convention")
        if "'" in v:
            problems.append(f"{k}: ASCII apostrophe (use U+2019)")
        if re.search(r"<[A-Za-z/]", v):
            problems.append(f"{k}: looks like HTML")
        if "!" in v:
            problems.append(f"{k}: exclamation mark")
        named = re.search(r"\{(min|max|value)\}", v)
        numbered = re.search(r"\{\d", v)
        if named and numbered:
            problems.append(f"{k}: mixes {{min}}/{{max}} with {{0}} arguments")
        depth = 0
        for ch in v:
            depth += ch == "{"
            depth -= ch == "}"
            if depth < 0:
                break
        if depth != 0:
            problems.append(f"{k}: unbalanced braces")
        if not v:
            problems.append(f"{k}: empty text")


def fill(template, lengths):
    """Replace {n} and {n,choice,...} by strings of the given lengths (choice: longest branch)."""
    def repl(m):
        idx = int(m.group(1))
        return "x" * lengths[idx]
    out = re.sub(r"\{(\d+)(?:,[^{}]*(?:\{[^{}]*\}[^{}]*)*)?\}", repl, template)
    return out


def check_notifications(keys):
    notify = {k for k in keys if k.startswith("notify.")}
    for k in sorted(notify - NOTIFY_ARGS.keys()):
        problems.append(f"{k}: no argument lengths declared in this script")
    for k in sorted(NOTIFY_ARGS.keys() - notify):
        problems.append(f"{k}: declared in the script but missing in the draft")
    worst = []
    for k in sorted(notify & NOTIFY_ARGS.keys()):
        n = len(fill(keys[k], NOTIFY_ARGS[k]))
        worst.append((n, k))
        if n > MAX_NOTIFICATION:
            problems.append(f"{k}: worst case {n} characters > {MAX_NOTIFICATION}")
    return max(worst) if worst else (0, "-")


def check_doc(keys):
    text = DOC.read_text(encoding="utf-8")
    doc_keys = set(DOC_KEY_RE.findall(text)) - NOT_KEYS
    for k in sorted(doc_keys - keys.keys()):
        problems.append(f"doc 12 mentions {k}, which is not in the draft")
    for k in sorted(keys.keys() - doc_keys):
        problems.append(f"draft key {k} is not mentioned in doc 12")
    compared = 0
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in re.split(r"(?<!\\)\|", line.strip())[1:-1]]
        for i, c in enumerate(cells[:-1]):
            m = re.fullmatch(r"`([^`]+)`", c)
            nxt = cells[i + 1]
            if m and m.group(1) in keys and len(nxt) >= 2 and nxt[0] == '"' and nxt[-1] == '"':
                shown = nxt[1:-1].replace("\\|", "|")
                compared += 1
                if shown != keys[m.group(1)]:
                    problems.append(f"{m.group(1)}: doc text differs from draft\n    doc:   {shown}\n    draft: {keys[m.group(1)]}")
    return doc_keys, compared


def main():
    keys = load_draft()
    check_rules(keys)
    worst = check_notifications(keys)
    doc_keys, compared = check_doc(keys)
    by_kind = {}
    for k in keys:
        by_kind[k.split(".")[0]] = by_kind.get(k.split(".")[0], 0) + 1
    print(f"draft keys: {len(keys)}  ({', '.join(f'{p} {n}' for p, n in sorted(by_kind.items()))})")
    print(f"keys mentioned in doc 12: {len(doc_keys)}")
    print(f"doc texts compared with the draft: {compared}")
    print(f"longest notification (worst case): {worst[0]} characters ({worst[1]}), limit {MAX_NOTIFICATION}")
    if problems:
        print(f"\n{len(problems)} problem(s):")
        for p in problems:
            print(" -", p)
        return 1
    print("OK: all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

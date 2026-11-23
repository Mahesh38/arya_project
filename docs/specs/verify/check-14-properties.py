#!/usr/bin/env python3
"""Doc 14 configuration check (doc 14 §5.5, §12 V20).

1. Every app.* name mentioned in docs 03-13 (and Appendix A) is either a key in the property
   drafts (docs/build/*.properties.draft) or listed in doc 14 §5.5 as "not a property".
2. Every key in the property drafts is in the doc 14 §5.5 key inventory, and the other way round.
3. Every segment of every app.* key matches a component of the AppProperties records.
4. Every draft file that doc 14 shows in a code block is embedded verbatim (no drift).

Run from the repository root:  python3 docs/specs/verify/check-14-properties.py
Exit code 0 = all checks passed.
"""
import glob
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SPECS = os.path.join(ROOT, "docs", "specs")
DOC14 = os.path.join(SPECS, "14-build-and-deployment.md")
TOKEN = re.compile(r"(?<![\w/.-])app\.[a-z][a-zA-Z0-9.-]*[a-zA-Z0-9]")

problems = []


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


doc14 = read(DOC14)

# --- names used in docs 03-13 and Appendix A -------------------------------------------------
sources = sorted(glob.glob(os.path.join(SPECS, "0[3-9]-*.md")) + glob.glob(os.path.join(SPECS, "1[0-3]*.md"))
                 + glob.glob(os.path.join(SPECS, "appendix-a-*.md")))
mentioned = {}
for path in sources:
    for name in TOKEN.findall(read(path)):
        name = name.rstrip(".-")
        mentioned.setdefault(name, set()).add(os.path.basename(path)[:3].strip("-"))

# --- keys in the property drafts -------------------------------------------------------------
drafts = sorted(glob.glob(os.path.join(ROOT, "docs", "build", "*.properties.draft")))
keys = {}
for path in drafts:
    for line in read(path).splitlines():
        m = re.match(r"\s*(app\.[a-zA-Z0-9.-]+)\s*=", line)
        if m:
            keys.setdefault(m.group(1), []).append(os.path.basename(path).replace(".draft", ""))

# --- doc 14 §5.5 tables ------------------------------------------------------------------------
sec55 = doc14.split("### 5.5", 1)[1].split("### 5.6", 1)[0]
inventory_part, not_props_part = sec55.split("Names that look like keys", 1)
inventory = set(re.findall(r"^\| `(app\.[a-zA-Z0-9.-]+)` \|", inventory_part, re.M))
not_properties = set()
for row in re.findall(r"^\| (`app\.[^|]+)\|", not_props_part, re.M):
    not_properties.update(re.findall(r"`(app\.[a-zA-Z0-9.-]+)`", row))

# --- check 1: every mentioned name is a key or a known non-property -------------------------
missing = sorted(n for n in mentioned if n not in keys and n not in not_properties)
for n in missing:
    problems.append(f"[1] {n} (mentioned in docs {', '.join(sorted(mentioned[n]))}) is neither a property key "
                    f"nor listed as 'not a property' in doc 14 §5.5")

# --- check 2: property files <-> inventory --------------------------------------------------
for k in sorted(set(keys) - inventory):
    problems.append(f"[2] {k} is in {keys[k]} but not in the doc 14 §5.5 inventory")
for k in sorted(inventory - set(keys)):
    problems.append(f"[2] {k} is in the doc 14 §5.5 inventory but in no property draft")

# --- check 3: keys bind to AppProperties components ------------------------------------------
java = read(os.path.join(ROOT, "docs", "build", "AppProperties.java.draft"))
components = set(re.findall(r"(?:^|[(,])\s*(?:@\w+(?:\([^)]*\))?\s+)*[\w.<>]+\s+(\w+)\s*(?=[,)])", java, re.M))


def camel(segment):
    head, *rest = segment.split("-")
    return head + "".join(r[:1].upper() + r[1:] for r in rest)


for k in sorted(keys):
    for seg in k.split(".")[1:]:
        if camel(seg) not in components:
            problems.append(f"[3] {k}: segment '{seg}' has no record component '{camel(seg)}' in AppProperties")

# --- check 4: embedded drafts are verbatim -----------------------------------------------------
embedded = sorted(set(re.findall(r"`(docs/(?:build|deploy|ci|e2e)/[\w./-]+)`", doc14)))
checked_embeds = 0
for rel in embedded:
    path = os.path.join(ROOT, rel)
    if not os.path.isfile(path) or rel.endswith((".py", "Dockerfile.dockerignore")):
        continue
    content = read(path).replace("\r\n", "\n").rstrip("\n")
    if content in doc14:
        checked_embeds += 1
    elif rel.endswith((".draft", ".sql", ".sh", ".bat", ".service", ".conf", ".yaml", ".example", "Dockerfile")):
        problems.append(f"[4] {rel} is named in doc 14 but its content is not embedded verbatim")

print(f"docs scanned: {len(sources)}; app.* names mentioned: {len(mentioned)}")
print(f"property drafts: {len(drafts)}; distinct app.* keys: {len(keys)}; inventory rows: {len(inventory)}; "
      f"listed as not a property: {len(not_properties)}")
print(f"names covered: {len(mentioned) - len(missing)}/{len(mentioned)} "
      f"({sum(1 for n in mentioned if n in keys)} keys, {sum(1 for n in mentioned if n in not_properties)} not properties)")
print(f"AppProperties components found: {len(components)}; drafts embedded verbatim in doc 14: {checked_embeds}")
if problems:
    print("\nPROBLEMS:")
    print("\n".join(problems))
    sys.exit(1)
print("OK: all checks passed")

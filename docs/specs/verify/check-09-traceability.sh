#!/usr/bin/env bash
# Checks doc 09 traceability against doc 03 (Must FRs) and doc 04 (use cases).
# Usage: bash docs/specs/verify/check-09-traceability.sh   (from the repository root)
set -euo pipefail
D=docs/specs
D03=$D/03-requirements-srs.md
D04=$D/04-use-cases.md
D09=$D/09-web-and-api-contracts.md
fail=0

section() { awk -v s="$1" -v e="$2" 'index($0,s)==1{f=1;next} index($0,e)==1{f=0} f' "$D09"; }

uc_ids=$(awk '/^## 1\. Use-case catalogue/{f=1;next} /^## 2\./{f=0} f' "$D04" | grep -oE '^\| UC-[0-9]+' | sed 's/| //' | sort -u)
must_ids=$(awk '/^## 1\. Functional requirements/{f=1;next} /^## 2\./{f=0} f' "$D03" \
  | grep -E '^\| FR-[0-9]+[a-z]? \|.*\| M \|$' | grep -oE '^\| FR-[0-9]+[a-z]?' | sed 's/| //' | sort -u)

t_uc=$(section '### 11.1' '### 11.2')
t_fr=$(section '### 11.2' '## 12.')
catalogue=$(section '## 3. Route catalogue' '## 4.')

defined=$( { echo "$catalogue" | grep -oE '^\| R-[0-9]+ ' | grep -oP '(?<![A-Z])R-\d+';
             echo "$catalogue" | grep -E '^\| (Departments|Categories|Areas) \|' | grep -oP '(?<![A-Z])R-\d+'; } | sort -u)

check() { # $1 = id list, $2 = table text, $3 = label
  local n=0 ok=0
  for id in $1; do
    n=$((n+1))
    row=$(echo "$2" | grep -E "^\| ${id} \|" || true)
    if [ -n "$row" ] && echo "$row" | grep -qP '(?<![A-Z])R-\d+'; then ok=$((ok+1)); else echo "  MISSING: $id"; fail=1; fi
  done
  echo "$3: $ok / $n mapped to at least one route"
}
check "$uc_ids" "$t_uc" "Use cases (doc 04)"
check "$must_ids" "$t_fr" "Must FRs (doc 03)"

used=$(printf '%s\n%s\n' "$t_uc" "$t_fr" | grep -oP '(?<![A-Z])R-\d+' | sort -u)
undef=$(comm -23 <(echo "$used") <(echo "$defined"))
unused=$(comm -13 <(echo "$used") <(echo "$defined"))
echo "Routes defined in §3: $(echo "$defined" | wc -l)"
echo "Route IDs used in §11 but not defined in §3: $(echo -n "$undef" | grep -c . || true) ${undef//$'\n'/ }"
echo "Routes defined in §3 but absent from §11: $(echo -n "$unused" | grep -c . || true) ${unused//$'\n'/ }"
[ -z "$undef" ] && [ -z "$unused" ] || fail=1
# Every R-ID mentioned anywhere in §3 must be defined
stray=$(comm -23 <(echo "$catalogue" | grep -oP '(?<![A-Z])R-\d+' | sort -u) <(echo "$defined"))
echo "Route IDs mentioned in §3 but not defined: $(echo -n "$stray" | grep -c . || true) ${stray//$'\n'/ }"
[ -z "$stray" ] || fail=1
exit $fail

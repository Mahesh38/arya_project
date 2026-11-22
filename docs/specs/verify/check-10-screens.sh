#!/usr/bin/env bash
# Checks doc 10 (UI screens) against doc 09 (views and routes) and doc 04 (use cases).
# Usage: bash docs/specs/verify/check-10-screens.sh   (from the repository root)
set -euo pipefail
D=docs/specs
D04=$D/04-use-cases.md
D09=$D/09-web-and-api-contracts.md
D10=$D/10-ui-screen-spec.md
fail=0
sec10() { awk -v s="$1" -v e="$2" 'index($0,s)==1{f=1;next} index($0,e)==1{f=0} f' "$D10"; }

# --- V1: views referenced in doc 09 <-> screen templates in doc 10 §2.1
views09=$(grep -oE '`(auth|citizen|complaint|officer|admin|notification|error)/[a-z0-9{}-]+`' "$D09" | tr -d '`' | sort -u \
  | while read -r v; do
      case "$v" in
        'admin/{type}')      printf 'admin/departments\nadmin/categories\nadmin/areas\n' ;;
        'admin/{type}-form') printf 'admin/department-form\nadmin/category-form\nadmin/area-form\n' ;;
        *) echo "$v" ;;
      esac
    done | sort -u)
inv=$(sec10 '### 2.1 Screens' '### 2.2')
screens10=$(echo "$inv" | grep -E '^\| SCR-[0-9]+ ' | awk -F'|' '{print $3}' | grep -oE '[a-z0-9/-]+\.html' | sed 's/\.html$//' | sort -u)
miss10=$(comm -23 <(echo "$views09") <(echo "$screens10"))
miss09=$(comm -13 <(echo "$views09") <(echo "$screens10"))
echo "V1 views in doc 09: $(echo "$views09" | wc -l); screen templates in doc 10: $(echo "$screens10" | wc -l)"
echo "   doc 09 views without a screen: ${miss10:-none}"
echo "   doc 10 screens not a doc 09 view: ${miss09:-none}"
[ -z "$miss10" ] && [ -z "$miss09" ] || fail=1

# --- V2: every route of doc 09 §3 accounted for in doc 10 §2.1 or §2.2
cat09=$(awk '/^## 3\. Route catalogue/{f=1;next} /^## 4\./{f=0} f' "$D09")
routes09=$( { echo "$cat09" | grep -oE '^\| R-[0-9]+ ' | grep -oE 'R-[0-9]+';
              echo "$cat09" | grep -E '^\| (Departments|Categories|Areas) \|' | grep -oE 'R-[0-9]+'; } | sort -u)
expand() { # expands "R-40 … R-55" ranges
  perl -ne 'while(/R-(\d+)\s*(?:…|\.\.\.)\s*R-(\d+)/g){print "R-$_\n" for $1..$2} print "$_\n" for /R-\d+/g'; }
in21=$(echo "$inv" | grep -E '^\| SCR-' | awk -F'|' '{print $6}' | expand | sort -u)
in22=$(sec10 '### 2.2' '### 2.3' | grep -E '^\| R-' | awk -F'|' '{print $2}' | expand | sort -u)
both=$(printf '%s\n%s\n' "$in21" "$in22" | sort -u)
missing=$(comm -23 <(echo "$routes09") <(echo "$both"))
unknown=$(comm -13 <(echo "$routes09") <(echo "$both"))
dupl=$(comm -12 <(echo "$in21") <(echo "$in22"))
echo "V2 routes in doc 09: $(echo "$routes09" | wc -l); in §2.1: $(echo "$in21" | wc -l); in §2.2: $(echo "$in22" | wc -l)"
echo "   not accounted for: ${missing:-none}; unknown IDs: ${unknown:-none}; in both tables: ${dupl:-none}"
[ -z "$missing" ] && [ -z "$unknown" ] && [ -z "$dupl" ] || fail=1

# --- V3: use cases and usability NFRs
ucs=$(awk '/^## 1\. Use-case catalogue/{f=1;next} /^## 2\./{f=0} f' "$D04" | grep -oE '^\| UC-[0-9]+' | sed 's/| //' | sort -u)
t91=$(sec10 '### 9.1' '### 9.2'); t92=$(sec10 '### 9.2' '## 10.')
n=0; ok=0
for u in $ucs; do n=$((n+1)); if echo "$t91" | grep -E "^\| $u " | grep -qE 'SCR-[0-9]+'; then ok=$((ok+1)); else echo "   MISSING $u"; fail=1; fi; done
echo "V3 use cases mapped to screens: $ok / $n"
n=0; ok=0
for r in NFR-20 NFR-21 NFR-22 NFR-23; do n=$((n+1)); if echo "$t92" | grep -q "$r"; then ok=$((ok+1)); else echo "   MISSING $r"; fail=1; fi; done
echo "   usability NFRs mapped: $ok / $n"

# --- V4: every SCR id used in doc 10 is defined in §2.1 (ranges like SCR-42…50 are prose and skipped)
defined=$(echo "$inv" | grep -oE '^\| SCR-[0-9]+' | grep -oE 'SCR-[0-9]+' | sort -u)
used=$(grep -oE 'SCR-[0-9]{2}' "$D10" | sort -u)
undef=$(comm -23 <(echo "$used") <(echo "$defined"))
echo "V4 screens defined: $(echo "$defined" | wc -l); screen IDs used but undefined: ${undef:-none}"
[ -z "$undef" ] || fail=1
exit $fail

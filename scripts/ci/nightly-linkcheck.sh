#!/usr/bin/env bash
#
# nightly-linkcheck.sh -- the documentation link check.
#
# R-DOC-05: "A scheduled or release job shall also run link checking."  This is
# that job, and it is a REAL check rather than a stub, because unlike the rest
# of the nightly pipeline it needs nothing that does not exist yet.
#
# EXIT CODE 0 PROVES NOTHING HERE EITHER.  `sphinx-build -b linkcheck` exits 0
# when every link is fine AND when it found no links to check at all -- a
# mis-pointed source directory, an empty tree, a builder that read nothing.  So
# this script reads output.json and requires a non-zero number of checked links
# and zero broken ones, and it prints what it found.
#
# Redirects are reported but do not fail the build: an upstream that moves a
# URL is information for a maintainer, not a reason to fail a nightly at 03:00.
# Broken links fail.
#
# Usage:
#   bash scripts/ci/nightly-linkcheck.sh
#   bash scripts/ci/nightly-linkcheck.sh --help
#
# Needs network access.
#
# Exit status:
#   0  every link resolved, and there were links to resolve
#   1  at least one broken link
#   2  harness misuse or a broken environment
#   3  the builder exited 0 but checked nothing, or wrote no report

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
VENV="${PROJECT_ROOT}/.venv"
SPHINX_BUILD="${VENV}/bin/sphinx-build"
OUT="${PROJECT_ROOT}/_build/linkcheck"

die() { printf 'nightly-linkcheck.sh: %s\n' "$1" >&2; exit "${2:-2}"; }
usage() { sed -n '3,30p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

case "${1:-}" in
    "")        ;;
    -h|--help) usage; exit 0 ;;
    *)         die "unknown argument: $1 (try --help)" ;;
esac

[ -x "${SPHINX_BUILD}" ] || die "no sphinx-build at ${SPHINX_BUILD}; run scripts/ci/python-env.sh first"

cd -- "${PROJECT_ROOT}"
rm -rf -- "${OUT}"
mkdir -p -- "${OUT}"

printf 'nightly-linkcheck.sh: + %s -n -W -b linkcheck docs %s\n\n' "${SPHINX_BUILD}" "${OUT#"${PROJECT_ROOT}/"}"
status=0
"${SPHINX_BUILD}" -n -W -b linkcheck docs "${OUT}" >"${OUT}/build.log" 2>&1 || status=$?

REPORT="${OUT}/output.json"
[ -f "${REPORT}" ] || { tail -30 -- "${OUT}/build.log" >&2; die "the builder wrote no ${REPORT}" 3; }

PYTHON=""
for candidate in "${VENV}/bin/python" "$(command -v python3 || true)"; do
    [ -n "${candidate}" ] && [ -x "${candidate}" ] && { PYTHON="${candidate}"; break; }
done
[ -n "${PYTHON}" ] || die "no Python 3"

"${PYTHON}" - "${REPORT}" "${status}" <<'PY'
import collections, json, sys

report, build_status = sys.argv[1], int(sys.argv[2])
rows = []
with open(report, encoding="utf-8") as handle:
    for line in handle:
        line = line.strip()
        if line:
            rows.append(json.loads(line))

counts = collections.Counter(row.get("status", "?") for row in rows)
print(f"nightly-linkcheck.sh: {len(rows)} link(s) checked: "
      + ", ".join(f"{status}={n}" for status, n in sorted(counts.items())))

for row in rows:
    if row.get("status") == "redirected":
        print(f"    redirect  {row.get('uri')} -> {row.get('info')}"
              f"  ({row.get('filename')}:{row.get('lineno')})")

broken = [row for row in rows if row.get("status") in ("broken", "timeout")]
for row in broken:
    print(f"    BROKEN    {row.get('uri')}  {row.get('info')}"
          f"  ({row.get('filename')}:{row.get('lineno')})", file=sys.stderr)

checked = counts.get("working", 0) + counts.get("redirected", 0) + len(broken)
if checked == 0:
    print("\nnightly-linkcheck.sh: the builder exited without checking a single external "
          "link. That is not a pass: it means it read nothing.", file=sys.stderr)
    sys.exit(3)
if broken:
    print(f"\nnightly-linkcheck.sh: FAILED -- {len(broken)} broken link(s).", file=sys.stderr)
    sys.exit(1)
if build_status != 0:
    print(f"\nnightly-linkcheck.sh: FAILED -- sphinx-build exited {build_status} with no "
          f"broken link recorded; read the build log.", file=sys.stderr)
    sys.exit(1)
print(f"\nnightly-linkcheck.sh: PASSED -- {checked} external link(s) resolved, none broken.")
PY

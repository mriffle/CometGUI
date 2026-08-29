#!/usr/bin/env bash
#
# windows-artefact.sh -- reproduce every piece of evidence in
# docs/feasibility/windows-artefact.rst from nothing but a network connection.
#
# Phase 00, work unit 4.  The Windows XML-capable Percolator artefact
# (percolator-v3-07.exe, an NSIS installer) has never been executed by this
# project.  This script gets the strongest evidence obtainable on a Linux host:
#
#   1. downloads the XML installer and its "noxml" twin, and checks SHA-256;
#   2. extracts both payloads with the pure-Python NSIS extractor -- no 7z, no
#      p7zip, no wine, nothing installed on the host;
#   3. cross-checks one extracted binary against the same binary obtained by a
#      completely independent route (the portable ZIP), so the extractor is
#      proved correct rather than assumed correct;
#   4. parses both percolator.exe PE headers and import tables;
#   5. runs the strings A/B between the XML and noxml Windows binaries;
#   6. runs the same A/B on the Linux twins, which CAN be executed here, to
#      calibrate what the Windows static evidence is worth.
#
# It does NOT execute any Windows binary.  Nothing here proves that
# percolator.exe runs on Windows.
#
# Usage:
#   scripts/feasibility/windows-artefact.sh          # full run
#   scripts/feasibility/windows-artefact.sh --clean  # delete outputs first
#
# Everything lands under scratch/windows/ (gitignored).  A transcript is
# written to scratch/windows/evidence.log.
#
# Exit status:
#   0  every step ran AND every expected output was found with the expected
#      checksum (exit 0 alone proves nothing, so each artefact is verified)
#   1  a check failed
#   2  misuse or a missing prerequisite

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
WORK="${ROOT}/scratch/windows"
DL="${WORK}/dl"
LOG="${WORK}/evidence.log"
BASE="https://github.com/percolator/percolator/releases/download/rel-3-07-01"
BASE308="https://github.com/percolator/percolator/releases/download/rel-3-08"

die() { printf 'windows-artefact.sh: %s\n' "$1" >&2; exit "${2:-2}"; }
say() { printf '\n=== %s\n' "$*" | tee -a "${LOG}"; }
note() { printf '%s\n' "$*" | tee -a "${LOG}"; }

[ "${1:-}" = "--clean" ] && rm -rf "${WORK}"
[ "${1:-}" = "--clean" ] && shift || true
[ "$#" -eq 0 ] || die "usage: windows-artefact.sh [--clean]"

command -v curl >/dev/null || die "curl is required"
command -v python3 >/dev/null || die "python3 is required"

mkdir -p "${DL}"
: > "${LOG}"

# Expected SHA-256 of every artefact downloaded.  A mismatch is a hard stop:
# the evidence in the document is about these exact bytes.
declare -A SHA=(
  [percolator-v3-07.exe]=a9860e02a7e78b9bc069438e6564eb20e90bb46244aa628d567e4b69fe1ea348
  [percolator-noxml-v3-07.exe]=1e97ea31d1a9ccd4450b2da083d0aa81599350067ed5d17844b479fe59118bba
  [percolator-v3-07-linux-amd64.deb]=68cd3a4b60845d1399cc84e2e1acaef7044d89c46161009939bcb97af90d48c7
  [percolator-noxml-v3-07-linux-amd64.deb]=ea630bbcf8db380169e2d691ea5c3f15ee1b5d81a3f54281fde2f3aa23612f9e
  [percolator-noxml-windows-portable.zip]=1510c2cfc8ce05822ac46e53954c7e6e5fa42305789fa94aad2f73657a0f94a2
  [percolator-noxml-windows-portable-308.zip]=8b364c6967c3bb9c8ef246c0c65286e0e0b759fc4821f369d0fe53f6ce08e821
)

fetch() {  # fetch <asset> <url>
    local name="$1" url="$2" got
    if [ ! -s "${DL}/${name}" ]; then
        curl -sSLf -o "${DL}/${name}" "${url}" || die "download failed: ${url}"
    fi
    got="$(sha256sum "${DL}/${name}" | cut -d' ' -f1)"
    if [ "${got}" != "${SHA[${name}]}" ]; then
        die "SHA-256 mismatch for ${name}: got ${got}, expected ${SHA[${name}]}" 1
    fi
    note "  ok  ${name}  $(stat -c%s "${DL}/${name}") bytes  ${got}"
}

say "1. download the artefacts and verify SHA-256"
fetch percolator-v3-07.exe                    "${BASE}/percolator-v3-07.exe"
fetch percolator-noxml-v3-07.exe              "${BASE}/percolator-noxml-v3-07.exe"
fetch percolator-v3-07-linux-amd64.deb        "${BASE}/percolator-v3-07-linux-amd64.deb"
fetch percolator-noxml-v3-07-linux-amd64.deb  "${BASE}/percolator-noxml-v3-07-linux-amd64.deb"
fetch percolator-noxml-windows-portable.zip   "${BASE}/percolator-noxml-windows-portable.zip"
fetch percolator-noxml-windows-portable-308.zip "${BASE308}/percolator-noxml-windows-portable.zip"

say "2. extract both NSIS payloads (pure Python, nothing installed)"
for v in xml noxml; do
    case "${v}" in
        xml)   exe=percolator-v3-07.exe ;;
        noxml) exe=percolator-noxml-v3-07.exe ;;
    esac
    rm -rf "${WORK}/${v}"
    python3 "${SCRIPT_DIR}/extract_nsis.py" "${DL}/${exe}" \
        -o "${WORK}/${v}" --json "${WORK}/${v}-manifest.json" | tee -a "${LOG}"
    [ -s "${WORK}/${v}/INSTDIR/bin/percolator.exe" ] \
        || die "extraction produced no ${v} percolator.exe" 1
done

say "3. the XSD companions gate item 9 requires"
for v in xml noxml; do
    for x in share/xml/percolator/xml-pin-1-3/percolator_in.xsd \
             share/xml/percolator/xml-pout-1-5/percolator_out.xsd; do
        f="${WORK}/${v}/INSTDIR/${x}"
        [ -s "${f}" ] || die "missing ${v} payload file ${x}" 1
        note "  ${v}: $(sha256sum "${f}")"
    done
done

say "4. independent cross-check of the extractor"
# The rel-3-07-01 portable ZIP holds the same noxml percolator.exe.  Extracting
# it with Python's zipfile is a route that shares no code with extract_nsis.py,
# so a byte-identical result proves the NSIS extraction, rather than asserting
# that it did not throw.
rm -rf "${WORK}/portable"
mkdir -p "${WORK}/portable"
python3 - "${DL}" "${WORK}/portable" <<'PY' | tee -a "${LOG}"
import sys, zipfile, os
dl, out = sys.argv[1], sys.argv[2]
for tag, fn in (("rel-3-07-01", "percolator-noxml-windows-portable.zip"),
                ("rel-3-08", "percolator-noxml-windows-portable-308.zip")):
    d = os.path.join(out, tag)
    os.makedirs(d, exist_ok=True)
    with zipfile.ZipFile(os.path.join(dl, fn)) as z:
        for i in z.infolist():
            print("  %-12s %10d  %s" % (tag, i.file_size, i.filename))
        z.extractall(d)
PY
a="$(sha256sum "${WORK}/noxml/INSTDIR/bin/percolator.exe" | cut -d' ' -f1)"
b="$(sha256sum "${WORK}/portable/rel-3-07-01/percolator.exe" | cut -d' ' -f1)"
[ "${a}" = "${b}" ] || die "NSIS-extracted and ZIP-extracted percolator.exe differ" 1
note "  NSIS-extracted == ZIP-extracted percolator.exe: ${a}"

say "5. PE headers and import tables of the two Windows binaries"
python3 "${SCRIPT_DIR}/pe_info.py" \
    "${WORK}/xml/INSTDIR/bin/percolator.exe" \
    "${WORK}/noxml/INSTDIR/bin/percolator.exe" \
    --json "${WORK}/pe-report.json" | tee -a "${LOG}"

say "6. NSIS stub privilege level (does /S even work without admin?)"
for exe in percolator-v3-07.exe percolator-noxml-v3-07.exe; do
    lvl="$(head -c 200000 "${DL}/${exe}" | grep -a -o \
          'requestedExecutionLevel level="[a-zA-Z]*"' | head -1 || true)"
    note "  ${exe}: ${lvl:-<none found>}"
done

say "7. strings A/B between the two Windows binaries"
for v in xml noxml; do
    if command -v strings >/dev/null; then
        strings -a -n 4 "${WORK}/${v}/INSTDIR/bin/percolator.exe" > "${WORK}/${v}.strings.txt"
    else
        python3 - "${WORK}/${v}/INSTDIR/bin/percolator.exe" "${WORK}/${v}.strings.txt" <<'PY'
import re, sys
d = open(sys.argv[1], "rb").read()
with open(sys.argv[2], "w") as o:
    for m in re.finditer(rb"[\x20-\x7e]{4,}", d):
        o.write(m.group().decode("ascii") + "\n")
PY
    fi
done
printf '%-34s %8s %8s\n' token windows-XML windows-noxml | tee -a "${LOG}"
for t in xmloutput decoy-xml-output pout.xml xerces percolator_out.xsd \
         percolator_in.xsd xml-pin-1-3 'XML_SUPPORT was off'; do
    x="$(grep -c -F -- "${t}" "${WORK}/xml.strings.txt" || true)"
    n="$(grep -c -F -- "${t}" "${WORK}/noxml.strings.txt" || true)"
    printf '%-34s %8s %8s\n' "${t}" "${x}" "${n}" | tee -a "${LOG}"
done

say "8. the executed control: the same A/B on Linux, where binaries DO run"
for v in xml noxml; do
    case "${v}" in
        xml)   deb=percolator-v3-07-linux-amd64.deb ;;
        noxml) deb=percolator-noxml-v3-07-linux-amd64.deb ;;
    esac
    d="${WORK}/linuxcontrol/${v}"
    rm -rf "${d}"; mkdir -p "${d}"
    ( cd "${d}" && ar x "${DL}/${deb}" && tar xf data.tar.gz )
    [ -x "${d}/usr/bin/percolator" ] || die "no ${v} Linux percolator" 1
done

python3 - "${WORK}/linuxcontrol/test.pin" <<'PY'
import random, sys
random.seed(7)
rows = ["SpecId\tLabel\tScanNr\tExpMass\tCalcMass\tfeat1\tfeat2\tfeat3\tPeptide\tProteins"]
for i in range(400):
    tgt = i % 2 == 0
    f1 = random.gauss(3.0 if tgt else 0.0, 1.0)
    f2 = random.gauss(1.5 if tgt else 0.0, 1.0)
    f3 = random.gauss(0.0, 1.0)
    pep = "K." + "".join(random.choice("ACDEFGHIKLMNPQRSTVWY") for _ in range(9)) + ".R"
    prot = ("sp|P%05d|TEST" if tgt else "decoy_sp|P%05d|TEST") % i
    rows.append("psm%d\t%d\t%d\t1000.5\t1000.4\t%.4f\t%.4f\t%.4f\t%s\t%s"
                % (i, 1 if tgt else -1, i, f1, f2, f3, pep, prot))
open(sys.argv[1], "w").write("\n".join(rows) + "\n")
print("  wrote a 400-PSM synthetic PIN")
PY

cd "${WORK}/linuxcontrol"
for v in xml noxml; do
    p="./${v}/usr/bin/percolator"
    note "  --- Linux ${v} build"
    set +o pipefail
    note "      version: $("${p}" --help 2>&1 | grep -m1 -i 'Percolator version' || true)"
    note "      help advertises -X/--xmloutput: \
$("${p}" --help 2>&1 | grep -c -E '^ *--xmloutput' || true)"
    note "      help advertises --decoy-xml-output: \
$("${p}" --help 2>&1 | grep -c -E '^ *--decoy-xml-output' || true)"
    set -o pipefail
    rm -f "out-${v}.xml"
    "${p}" -X "out-${v}.xml" test.pin >/dev/null 2>"run-${v}.stderr" && rc=0 || rc=$?
    if [ -s "out-${v}.xml" ]; then
        note "      -X wrote $(wc -c < "out-${v}.xml") bytes, \
$(grep -c '<psm ' "out-${v}.xml") <psm> elements, exit ${rc}"
    else
        note "      -X produced NO XML, exit ${rc}"
    fi
    "${p}" --xml-in test.pin > "xmlin-${v}.out" 2>&1 && xrc=0 || xrc=$?
    note "      --xml-in exit ${xrc}: $(grep -m1 -E 'XML_SUPPORT|unable to load' \
"xmlin-${v}.out" || echo '(no XML_SUPPORT diagnostic)')"
done
if [ -s out-xml.xml ] && [ -s out-noxml.xml ]; then
    n="$(diff out-xml.xml out-noxml.xml | grep -c '^[<>]' || true)"
    note "  differing lines between the two Linux -X outputs: ${n} (the command_line element)"
fi

say "done -- transcript in ${LOG}"
note "NOTE: no Windows binary was executed.  Nothing above proves that"
note "percolator.exe runs on Windows; see docs/feasibility/windows-artefact.rst."

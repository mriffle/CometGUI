#!/usr/bin/env bash
#
# CometGUI -- Phase 00, work unit 9.
#
#   bash scripts/feasibility/pdv_spike.sh
#
# Two questions, one script:
#
#   1. What are the Limelight converter JAR's REAL arguments?  (Its own
#      --help and --version, captured verbatim.)
#   2. Can PDV's command-line figure-generation mode be driven from a Comet
#      pepXML plus the matching spectra, on this host, and what does it need
#      to run?
#
# Everything it needs is already on disk when work unit 8's
# scripts/feasibility/run_scientific_path.sh has been run; this script never
# re-runs Comet or Percolator.  PDV itself is fetched from its GitHub release
# asset URL (which does not consume the GitHub API rate limit), verified
# against a pinned SHA-256 and unpacked with Python's zipfile -- there is no
# unzip on this host.
#
# This host has no X display.  PDV's "CLI" mode is a Swing JFrame and dies
# with a HeadlessException without one, so the script starts the project-local
# Xvfb that work unit 7 bootstrapped under tools/x11-bookworm-20260829/ and
# runs PDV against it.  Nothing is installed on the host: no apt, no sudo, no
# host-level pip.
#
# Exit status 0 only if every stage produced the output it claims.  Exit code
# 0 from PDV proves nothing -- PDV exits 0 having produced no figure -- so
# every figure is re-read from disk and parsed by
# scripts/feasibility/pdv_spike_helpers.py.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

HELPERS="${SCRIPT_DIR}/pdv_spike_helpers.py"
SPIKE_SRC="${SCRIPT_DIR}/gui-spike"

SCI="${ROOT}/scratch/scientific-path"
CONVERTER="${SCI}/tool/cometPercolator2LimelightXML.jar"
PEPXML="${SCI}/comet/20100614_Velos1_TaGe_SA_K562_3.pep.xml"
MZML="${SCI}/mzml-lf/20100614_Velos1_TaGe_SA_K562_3.mzML"
PSMS="${SCI}/percolator-3.07.1/psms.target.txt"

OUT="${ROOT}/scratch/pdv"
PDV_ZIP="${OUT}/PDV-2.7.0.zip"
PDV_DIR="${OUT}/PDV-2.7.0"
PDV_JAR="${PDV_DIR}/PDV-2.7.0.jar"
PDV_URL="https://github.com/wenbostar/PDV/releases/download/v2.7.0/PDV-2.7.0.zip"
PDV_SHA256="58f95a5663d70e885c59a6aea883d2dd783f9daa3ec0709fc932d09b4a1f7982"
PDV_BYTES=103407417

BUILD="${ROOT}/_build/pdv-spike"
X11_ROOT="${ROOT}/tools/x11-bookworm-20260829/root"
FONT_ROOT="${ROOT}/tools/fontstack-bookworm-20260829/root"

PYTHON="${ROOT}/.venv/bin/python"
[ -x "${PYTHON}" ] || PYTHON="$(command -v python3)"

STAGE_RESULTS=()
FAILED=0
note() { printf '\n=== %s\n' "$1"; }
pass() { printf '[stage PASS] %s\n' "$1"; STAGE_RESULTS+=("PASS  $1"); }
fail() { printf '[stage FAIL] %s\n' "$1" >&2; STAGE_RESULTS+=("FAIL  $1"); FAILED=1; }
die()  { printf 'pdv_spike.sh: %s\n' "$1" >&2; exit 2; }
# A delay primitive that is not sleep(1).
pause() { timeout "$1" tail -f /dev/null >/dev/null 2>&1 || true; }

XVFB_PID=""
DISPLAY_NUM=""
cleanup() {
    if [ -n "${XVFB_PID}" ] && kill -0 "${XVFB_PID}" 2>/dev/null; then
        kill "${XVFB_PID}" 2>/dev/null || true
    fi
    [ -n "${DISPLAY_NUM}" ] && rm -f "/tmp/.X${DISPLAY_NUM}-lock" 2>/dev/null || true
}
trap cleanup EXIT

[ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh missing -- run scripts/feasibility/install-toolchain.sh first"
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
[ -f "${HELPERS}" ] || die "missing ${HELPERS}"
for required in "${CONVERTER}" "${PEPXML}" "${MZML}" "${PSMS}"; do
    [ -s "${required}" ] || die "missing ${required}
Work unit 8's outputs are not on disk.  Regenerate them with:
    bash scripts/feasibility/run_scientific_path.sh"
done
[ -x "${X11_ROOT}/usr/bin/Xvfb" ] || die "no project-local Xvfb at ${X11_ROOT}
Bootstrap it first with:
    bash scripts/feasibility/javafx-smoke.sh
    bash scripts/feasibility/javafx-headed-xvfb.sh"

mkdir -p -- "${OUT}" "${BUILD}"

# ---------------------------------------------------------------------------
note "Stage 1: the Limelight converter's real interface"
java -jar "${CONVERTER}" --help > "${OUT}/converter-help.txt" 2>&1 || true
java -jar "${CONVERTER}" --version > "${OUT}/converter-version.txt" 2>&1 || true
cat -- "${OUT}/converter-help.txt"
printf -- '--version: '; cat -- "${OUT}/converter-version.txt"

missing_opts=()
for opt in -- -c --comet-params -p --percolator-file -o --out-file \
           -d --pepxml-directory -f --fasta-file -q --q-value \
           --import-decoys --independent-decoy-prefix --open-mod -v --verbose \
           -h --help -V --version; do
    [ "${opt}" = "--" ] && continue
    grep -qE "(^|[ ,])${opt}([ ,=]|$)" "${OUT}/converter-help.txt" || missing_opts+=("${opt}")
done
if [ "${#missing_opts[@]}" -eq 0 ] && grep -q 'v2\.8\.1' "${OUT}/converter-version.txt"; then
    pass "Stage 1: converter reports v2.8.1 and every documented option plus -h/--help and -V/--version"
else
    fail "Stage 1: converter help is missing options: ${missing_opts[*]:-none}; version output: $(cat -- "${OUT}/converter-version.txt")"
fi

# A finding worth re-proving: the converter exits 0 when required options are
# absent, so its exit status alone cannot be trusted as a success signal.
set +e
java -jar "${CONVERTER}" > "${OUT}/converter-noargs.txt" 2>&1
noargs_status=$?
set -e
printf 'converter with no arguments: exit=%s, first line: %s\n' \
    "${noargs_status}" "$(head -n2 -- "${OUT}/converter-noargs.txt" | tr -d '\n')"

# ---------------------------------------------------------------------------
note "Stage 2: fetch and verify PDV 2.7.0"
if [ ! -f "${PDV_ZIP}" ]; then
    curl -fsSL --retry 3 -o "${PDV_ZIP}.part" "${PDV_URL}" || die "download failed: ${PDV_URL}"
    mv -- "${PDV_ZIP}.part" "${PDV_ZIP}"
fi
got_sha="$(sha256sum -- "${PDV_ZIP}" | cut -d' ' -f1)"
got_size="$(stat -c%s -- "${PDV_ZIP}")"
printf 'PDV-2.7.0.zip  bytes=%s  sha256=%s\n' "${got_size}" "${got_sha}"
[ "${got_sha}" = "${PDV_SHA256}" ] || die "SHA-256 mismatch: expected ${PDV_SHA256}"
[ "${got_size}" = "${PDV_BYTES}" ] || die "size mismatch: expected ${PDV_BYTES} bytes"

# No unzip on this host.
"${PYTHON}" - "${PDV_ZIP}" "${OUT}" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    z.extractall(sys.argv[2])
    print("extracted %d entries" % len(z.namelist()))
PY
if [ -s "${PDV_JAR}" ]; then
    pass "Stage 2: PDV 2.7.0 verified (${got_size} bytes) and extracted to ${PDV_DIR}"
else
    fail "Stage 2: extraction produced no ${PDV_JAR}"
fi

# ---------------------------------------------------------------------------
note "Stage 3: negative control -- PDV's CLI mode without a display"
unset DISPLAY || true
set +e
java -jar "${PDV_JAR}" -h > "${OUT}/pdv-headless.txt" 2>&1
headless_status=$?
set -e
sed 's/^/    /' "${OUT}/pdv-headless.txt" | head -n 12
if [ "${headless_status}" -ne 0 ] && grep -q 'HeadlessException' "${OUT}/pdv-headless.txt"; then
    pass "Stage 3: PDV's CLI mode requires a display -- HeadlessException, exit ${headless_status}"
else
    fail "Stage 3: expected a HeadlessException without DISPLAY; got exit ${headless_status}"
fi

# ---------------------------------------------------------------------------
note "Stage 4: start the project-local Xvfb"
command -v gcc >/dev/null || die "no gcc; Xvfb cannot be started without the xkbcomp path shim"
SHIM="${BUILD}/xkbcomp-path-shim.so"
gcc -shared -fPIC -O1 -o "${SHIM}" "${SPIKE_SRC}/xkbcomp-path-shim.c"
[ -s "${SHIM}" ] || die "gcc exited 0 but produced no shim"

export LD_LIBRARY_PATH="${X11_ROOT}/usr/lib/x86_64-linux-gnu:${X11_ROOT}/lib/x86_64-linux-gnu:${FONT_ROOT}/usr/lib/x86_64-linux-gnu"
export FONTCONFIG_PATH="${FONT_ROOT}/etc/fonts"
export XDG_DATA_HOME="${FONT_ROOT}/usr/share"
export XDG_CACHE_HOME="${BUILD}/fccache"
mkdir -p -- "${XDG_CACHE_HOME}"

for n in 94 93 92 91 90; do
    if [ ! -e "/tmp/.X${n}-lock" ] && [ ! -S "/tmp/.X11-unix/X${n}" ]; then
        DISPLAY_NUM="${n}"; break
    fi
done
[ -n "${DISPLAY_NUM}" ] || die "no free X display number in 90..94"
mkdir -p /tmp/.X11-unix 2>/dev/null || true
LD_PRELOAD="${SHIM}" \
XKBCOMP_PATH="${X11_ROOT}/usr/bin/xkbcomp" \
XKB_CONFIG_ROOT="${X11_ROOT}/usr/share/X11/xkb" \
    "${X11_ROOT}/usr/bin/Xvfb" ":${DISPLAY_NUM}" -screen 0 1280x1024x24 \
    -nolisten tcp -xkbdir "${X11_ROOT}/usr/share/X11/xkb" \
    > "${BUILD}/xvfb.log" 2>&1 &
XVFB_PID=$!
up=0
for _ in $(seq 1 40); do
    if [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] && kill -0 "${XVFB_PID}" 2>/dev/null; then
        up=1; break
    fi
    kill -0 "${XVFB_PID}" 2>/dev/null || break
    pause 0.25
done
if [ "${up}" -eq 1 ]; then
    pass "Stage 4: Xvfb up on :${DISPLAY_NUM} (pid ${XVFB_PID})"
else
    fail "Stage 4: Xvfb did not come up -- see ${BUILD}/xvfb.log"
    note "Summary"; for r in "${STAGE_RESULTS[@]}"; do printf '  %s\n' "${r}"; done
    exit 1
fi
export DISPLAY=":${DISPLAY_NUM}"

# ---------------------------------------------------------------------------
note "Stage 5: PDV's own CLI usage text"
java -jar "${PDV_JAR}" -h > "${OUT}/pdv-help.txt" 2>&1 || true
cat -- "${OUT}/pdv-help.txt"
if grep -q -- '-ft <arg>' "${OUT}/pdv-help.txt" && grep -q -- '-rt <arg>' "${OUT}/pdv-help.txt"; then
    pass "Stage 5: PDV printed its option list ($(grep -c '^ -' "${OUT}/pdv-help.txt") options)"
else
    fail "Stage 5: PDV did not print a usable option list"
fi

# ---------------------------------------------------------------------------
note "Stage 6: PDV against the mzML directly -- the defect"
RUN_MZML="${OUT}/run-mzml"
rm -rf -- "${RUN_MZML}"; mkdir -p -- "${RUN_MZML}"
TOP_PEPTIDE="$(awk -F'\t' 'NR>1 && $1 ~ /K562_3/ {n=split($5,a,"."); p=a[2]; gsub(/\[[^]]*\]/,"",p); print p; exit}' "${PSMS}")"
[ -n "${TOP_PEPTIDE}" ] || die "could not read a peptide from ${PSMS}"
printf '%s\n' "${TOP_PEPTIDE}" > "${OUT}/peptide.txt"
printf 'peptide under test: %s\n' "${TOP_PEPTIDE}"
set +e
"${PYTHON}" "${HELPERS}" run "${OUT}/metrics-mzml.txt" -- \
    java -jar "${PDV_JAR}" -r "${PEPXML}" -rt 2 -s "${MZML}" -st 2 \
    -i "${OUT}/peptide.txt" -k p -o "${RUN_MZML}" -ft png \
    > "${OUT}/run-mzml.log" 2>&1
mzml_status=$?
set -e
grep -v '^SLF4J' "${OUT}/run-mzml.log" | tail -n 8 | sed 's/^/    /'
n_mzml_png="$(find "${RUN_MZML}" -name '*.png' | wc -l | tr -d ' ')"
printf 'PDV exit status %s; figures produced: %s\n' "${mzml_status}" "${n_mzml_png}"
[ -s "${RUN_MZML}/error.txt" ] && sed 's/^/    error.txt: /' "${RUN_MZML}/error.txt"
if [ "${mzml_status}" -eq 0 ] && [ "${n_mzml_png}" -eq 0 ]; then
    pass "Stage 6: reproduced the defect -- PDV exits 0 on mzML input having written no figure"
else
    fail "Stage 6: expected exit 0 with no figure from the mzML path; got exit ${mzml_status}, ${n_mzml_png} figure(s). Re-read the document: this behaviour may have changed."
fi

# ---------------------------------------------------------------------------
note "Stage 7: convert the mzML to MGF (TITLE = the mzML native id)"
MGF_DIR="${OUT}/mgf"
mkdir -p -- "${MGF_DIR}"
MGF="${MGF_DIR}/$(basename -- "${MZML}" .mzML).mgf"
"${PYTHON}" "${HELPERS}" mgf "${MZML}" "${MGF}"
n_titles="$(grep -c '^TITLE=' "${MGF}" || true)"
if [ -s "${MGF}" ] && [ "${n_titles}" -gt 0 ]; then
    pass "Stage 7: MGF written with ${n_titles} spectra ($(stat -c%s -- "${MGF}") bytes)"
else
    fail "Stage 7: MGF conversion produced nothing usable"
fi

# ---------------------------------------------------------------------------
note "Stage 8: PDV figure generation, selecting by peptide (-k p)"
RUN_PNG="${OUT}/run-mgf"
rm -rf -- "${RUN_PNG}"; mkdir -p -- "${RUN_PNG}"
set +e
"${PYTHON}" "${HELPERS}" run "${OUT}/metrics-png.txt" -- \
    java -jar "${PDV_JAR}" -r "${PEPXML}" -rt 2 -s "${MGF}" -st 1 \
    -i "${OUT}/peptide.txt" -k p -o "${RUN_PNG}" -ft png \
    > "${OUT}/run-mgf.log" 2>&1
png_status=$?
set -e
grep -v '^SLF4J' "${OUT}/run-mgf.log" | tail -n 10 | sed 's/^/    /'
mapfile -t PNGS < <(find "${RUN_PNG}" -name '*.png' | sort)
printf 'PDV exit status %s; figures produced: %s\n' "${png_status}" "${#PNGS[@]}"
set +e
"${PYTHON}" "${HELPERS}" verify-image "${PNGS[@]}"
png_verify=$?
set -e
if [ "${png_status}" -eq 0 ] && [ "${#PNGS[@]}" -gt 0 ] && [ "${png_verify}" -eq 0 ]; then
    pass "Stage 8: ${#PNGS[@]} PNG figure(s) generated and verified from their own headers"
else
    fail "Stage 8: PDV exit ${png_status}, ${#PNGS[@]} figure(s), verifier exit ${png_verify}"
fi

# ---------------------------------------------------------------------------
note "Stage 9: PDV figure generation, selecting by spectrum id (-k s)"
# grep -m1 rather than a pipe into head: under `set -o pipefail` the SIGPIPE
# head sends back to grep would abort the script.
NATIVE_ID="$(grep -m1 -o 'spectrumNativeID="[^"]*"' "${PEPXML}" | sed 's/spectrumNativeID="//; s/"$//')"
printf '%s\n' "${NATIVE_ID}" > "${OUT}/spectrum-id.txt"
printf 'spectrum id under test: %s\n' "${NATIVE_ID}"
RUN_KS="${OUT}/run-spectrum-id"
rm -rf -- "${RUN_KS}"; mkdir -p -- "${RUN_KS}"
set +e
"${PYTHON}" "${HELPERS}" run "${OUT}/metrics-ks.txt" -- \
    java -jar "${PDV_JAR}" -r "${PEPXML}" -rt 2 -s "${MGF}" -st 1 \
    -i "${OUT}/spectrum-id.txt" -k s -o "${RUN_KS}" -ft png \
    > "${OUT}/run-spectrum-id.log" 2>&1
ks_status=$?
set -e
mapfile -t KS_PNGS < <(find "${RUN_KS}" -name '*.png' | sort)
set +e
"${PYTHON}" "${HELPERS}" verify-image "${KS_PNGS[@]}"
ks_verify=$?
set -e
if [ "${ks_status}" -eq 0 ] && [ "${#KS_PNGS[@]}" -gt 0 ] && [ "${ks_verify}" -eq 0 ]; then
    pass "Stage 9: -k s selected by spectrumNativeID and produced ${#KS_PNGS[@]} verified figure(s)"
else
    fail "Stage 9: PDV exit ${ks_status}, ${#KS_PNGS[@]} figure(s), verifier exit ${ks_verify}"
fi

# ---------------------------------------------------------------------------
note "Stage 10: PDF output"
RUN_PDF="${OUT}/run-pdf"
rm -rf -- "${RUN_PDF}"; mkdir -p -- "${RUN_PDF}"
set +e
"${PYTHON}" "${HELPERS}" run "${OUT}/metrics-pdf.txt" -- \
    java -jar "${PDV_JAR}" -r "${PEPXML}" -rt 2 -s "${MGF}" -st 1 \
    -i "${OUT}/peptide.txt" -k p -o "${RUN_PDF}" -ft pdf \
    > "${OUT}/run-pdf.log" 2>&1
pdf_status=$?
set -e
mapfile -t PDFS < <(find "${RUN_PDF}" -name '*.pdf' | sort)
set +e
"${PYTHON}" "${HELPERS}" verify-image "${PDFS[@]}"
pdf_verify=$?
set -e
if [ "${pdf_status}" -eq 0 ] && [ "${#PDFS[@]}" -gt 0 ] && [ "${pdf_verify}" -eq 0 ]; then
    pass "Stage 10: ${#PDFS[@]} PDF figure(s) generated and verified"
else
    fail "Stage 10: PDV exit ${pdf_status}, ${#PDFS[@]} file(s), verifier exit ${pdf_verify}"
fi

# ---------------------------------------------------------------------------
note "Cost of a run (no /usr/bin/time on this host; rusage from wait4)"
for m in mzml png ks pdf; do
    [ -s "${OUT}/metrics-${m}.txt" ] && printf '  %-5s %s' "${m}" "$(cat -- "${OUT}/metrics-${m}.txt")"
done

note "Summary"
for r in "${STAGE_RESULTS[@]}"; do printf '  %s\n' "${r}"; done
if [ "${FAILED}" -ne 0 ]; then
    printf '\npdv_spike.sh: FAILED\n' >&2
    exit 1
fi
printf '\npdv_spike.sh: all stages passed\n'
printf 'evidence under %s\n' "${OUT}"
exit 0

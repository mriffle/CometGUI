#!/usr/bin/env bash
#
# run_scientific_path.sh -- Phase 00, work unit 8.
#
# Proves the scientific path end to end with no GUI:
#
#   mzML + FASTA
#     -> Comet 2026.02.2            -> pepXML + PIN
#     -> Percolator 3.07.1 (-X)     -> pout XML + PSM/peptide/weights
#     -> cometPercolator2LimelightXML.jar v2.8.1
#                                   -> Limelight XML, schema-validated
#
# and, separately:
#
#   * Percolator 3.09 on the same PIN -- PSM/peptide/weights, no XML, and what
#     it does when handed -X anyway;
#   * the 3.07.1 "noxml" build's -X output fed to the Limelight converter;
#   * the -Z / --import-decoys chain behind R-LL-05, in all four combinations.
#
# Everything is written under $RUN_ROOT (default /workspace/scratch/, which is
# gitignored). Nothing is installed on the host.
#
# Exit code 0 proves nothing on its own, so every stage is checked for output
# that exists, is non-empty and has plausible content, and the schema validator
# is itself proven able to fail before its verdict on the real file is trusted.
#
# Usage:
#   scripts/feasibility/run_scientific_path.sh [--skip-fetch]
#
# Exit status:
#   0  every stage produced the expected output and every assertion held
#   1  an assertion failed -- the message says which stage and what was wrong
#   2  a prerequisite is missing (binary, JDK, fixture fetcher)

set -u -o pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

RUN_ROOT="${RUN_ROOT:-${PROJECT_ROOT}/scratch/scientific-path}"
FIXTURE_DIR="${FIXTURE_DIR:-${PROJECT_ROOT}/scratch/fixture}"

COMET="${COMET:-${PROJECT_ROOT}/scratch/upstream/comet.linux.exe}"
PERC_371="${PERC_371:-${PROJECT_ROOT}/scratch/percolator/3.07.1-linux-x86_64/usr/bin/percolator}"
PERC_371_POUT_XSD="${PROJECT_ROOT}/scratch/percolator/3.07.1-linux-x86_64/usr/share/xml/percolator/xml-pout-1-5/percolator_out.xsd"
PERC_309="${PERC_309:-${PROJECT_ROOT}/scratch/percolator/3.09/run-percolator-3.09.sh}"
PERC_NOXML="${PERC_NOXML:-${PROJECT_ROOT}/scratch/windows/linuxcontrol/noxml/usr/bin/percolator}"
ENV_SH="${PROJECT_ROOT}/tools/env.sh"

JAR_URL="https://github.com/yeastrc/limelight-import-comet-percolator/releases/download/v2.8.1/cometPercolator2LimelightXML.jar"
JAR_SHA256="843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1"
JAR_BYTES=2762075
JAR="${RUN_ROOT}/tool/cometPercolator2LimelightXML.jar"

MZML_1_BASE="20100614_Velos1_TaGe_SA_K562_3"
MZML_2_BASE="20100614_Velos1_TaGe_SA_K562_4"
FASTA="${FIXTURE_DIR}/UP000005640_9606.fasta"

SKIP_FETCH=0
[ "${1:-}" = "--skip-fetch" ] && SKIP_FETCH=1

STEP=""
step()  { STEP="$1"; printf '\n=== %s ===\n' "$1"; }
note()  { printf '    %s\n' "$*"; }
fail()  { printf '\nFAILED at [%s]: %s\n' "${STEP}" "$*" >&2; exit 1; }
absent(){ printf '\nPREREQUISITE MISSING: %s\n' "$*" >&2; exit 2; }

# --- assertions --------------------------------------------------------------

assert_file_nonempty() {   # path, description
    [ -f "$1" ] || fail "$2: no such file: $1"
    [ -s "$1" ] || fail "$2: file is empty: $1"
}
assert_no_file() {         # path, description
    [ -e "$1" ] && fail "$2: file should not exist but does: $1"
    return 0
}
assert_min_lines() {       # path, minimum, description
    local n; n=$(wc -l < "$1") || fail "$3: cannot count lines in $1"
    [ "${n}" -ge "$2" ] || fail "$3: $1 has ${n} lines, expected at least $2"
    note "$3: $1 has ${n} lines (>= $2)"
}
assert_eq() {              # actual, expected, description
    [ "$1" = "$2" ] || fail "$3: got '$1', expected '$2'"
    note "$3: $1"
}
assert_ge() {              # actual, minimum, description
    [ "$1" -ge "$2" ] || fail "$3: got $1, expected at least $2"
    note "$3: $1 (>= $2)"
}
assert_grep() {            # pattern, file, description
    grep -q -- "$1" "$2" || fail "$3: pattern '$1' not found in $2"
    note "$3: found '$1'"
}
assert_not_grep() {        # pattern, file, description
    grep -q -- "$1" "$2" && fail "$3: pattern '$1' unexpectedly present in $2"
    note "$3: '$1' absent, as required"
}

pin_rows()   { echo $(( $(wc -l < "$1") - 1 )); }
pin_label()  { tail -n +2 "$1" | awk -F'\t' -v L="$2" '$2==L' | wc -l; }
tab_rows()   { echo $(( $(wc -l < "$1") - 1 )); }
tab_q_below(){ tail -n +2 "$1" | awk -F'\t' -v Q="$2" '$3<Q' | wc -l; }
count_tag()  { grep -o "<$2[ >]" "$1" | wc -l; }

# --- 0. prerequisites --------------------------------------------------------

step "0. prerequisites"
[ -x "${COMET}" ]      || absent "Comet binary at ${COMET}"
[ -x "${PERC_371}" ]   || absent "Percolator 3.07.1 at ${PERC_371}"
[ -f "${PERC_371_POUT_XSD}" ] || absent "percolator_out.xsd at ${PERC_371_POUT_XSD}"
[ -x "${PERC_309}" ]   || absent "Percolator 3.09 wrapper at ${PERC_309}"
[ -x "${PERC_NOXML}" ] || absent "Percolator 3.07.1 noxml build at ${PERC_NOXML}"
[ -f "${ENV_SH}" ]     || absent "JDK environment script at ${ENV_SH}"
# shellcheck disable=SC1090
. "${ENV_SH}"
command -v java >/dev/null 2>&1 || absent "java on PATH after sourcing ${ENV_SH}"
note "comet:      $("${COMET}" 2>&1 | sed -n '2p' | tr -s ' ')"
note "perc 3.07.1:$("${PERC_371}" --help 2>&1 | head -1)"
note "perc 3.09:  $("${PERC_309}" --help 2>&1 | head -1)"
note "perc noxml: $("${PERC_NOXML}" --help 2>&1 | head -1)"
note "java:       $(java -version 2>&1 | head -1)"

mkdir -p "${RUN_ROOT}/tool" || fail "cannot create ${RUN_ROOT}"

# --- 1. fixture --------------------------------------------------------------

step "1. ephemeral feasibility input (NOT the project fixture -- D-006 is open)"
if [ "${SKIP_FETCH}" -eq 0 ]; then
    python3 "${SCRIPT_DIR}/fetch_ephemeral_input.py" \
        || fail "fetch_ephemeral_input.py failed"
else
    note "--skip-fetch given; using whatever is already in ${FIXTURE_DIR}"
fi
assert_file_nonempty "${FIXTURE_DIR}/${MZML_1_BASE}.mzML" "fixture mzML 1"
assert_file_nonempty "${FIXTURE_DIR}/${MZML_2_BASE}.mzML" "fixture mzML 2"
assert_file_nonempty "${FASTA}"                            "fixture FASTA"
FASTA_RECORDS=$(grep -c '^>' "${FASTA}")
assert_eq "${FASTA_RECORDS}" "20652" "FASTA records"

# --- 2. mzML line-ending repair ---------------------------------------------
# The two mzML files are served from a git repository with CRLF line endings.
# Every byte offset in <indexList>, and the <fileChecksum> that certifies the
# file, were computed on the LF form, so the CRLF form is a broken indexedmzML
# and Comet 2026.02.2 refuses it with "parseOffset() 2: Syntax error parsing
# XML".  Stripping the CRs restores both.  The check below proves that rather
# than assuming it, and never relaxes the mzML's own checksum.

step "2. repair the mzML line endings and prove the repair"
NORM_DIR="${RUN_ROOT}/mzml-lf"
mkdir -p "${NORM_DIR}"
for base in "${MZML_1_BASE}" "${MZML_2_BASE}"; do
    python3 - "${FIXTURE_DIR}/${base}.mzML" "${NORM_DIR}/${base}.mzML" <<'PY' || fail "mzML repair/verification failed"
import hashlib, re, sys
src, dst = sys.argv[1], sys.argv[2]
raw = open(src, 'rb').read()
lf = raw.replace(b'\r\n', b'\n')
open(dst, 'wb').write(lf)

def check(data, label):
    m = re.search(rb'<indexListOffset>(\d+)</indexListOffset>', data)
    if not m:
        raise SystemExit('%s: no <indexListOffset>' % label)
    stated = int(m.group(1))
    real = data.find(b'<indexList ')
    end = data.find(b'<fileChecksum>') + len(b'<fileChecksum>')
    sha1 = hashlib.sha1(data[:end]).hexdigest()
    want = re.search(rb'<fileChecksum>([0-9a-f]+)</fileChecksum>', data).group(1).decode()
    return stated, real, sha1, want

s0, r0, c0, w0 = check(raw, 'as-fetched')
s1, r1, c1, w1 = check(lf, 'lf')
print('    as-fetched   : bytes=%d indexListOffset=%d real=%d match=%s fileChecksum match=%s'
      % (len(raw), s0, r0, s0 == r0, c0 == w0))
print('    lf-normalised: bytes=%d indexListOffset=%d real=%d match=%s fileChecksum match=%s'
      % (len(lf), s1, r1, s1 == r1, c1 == w1))
print('    sha256(lf)   : %s' % hashlib.sha256(lf).hexdigest())
if not (s1 == r1 and c1 == w1):
    raise SystemExit('LF-normalised mzML still fails its own index/checksum -- refusing to continue')
PY
done

# --- 3. Comet ----------------------------------------------------------------
# Comet writes <input>.pep.xml and <input>.pin beside each input file, and
# -N<name> is documented as "valid only with one input file".  The run layout
# therefore places symlinks to the inputs inside the run directory so that the
# outputs land there, which is also what the Limelight converter's default
# -d (the Percolator file's own directory) needs.

step "3. Comet 2026.02.2 on both mzML files (multi-file run model)"
COMET_DIR="${RUN_ROOT}/comet"
rm -rf "${COMET_DIR}"; mkdir -p "${COMET_DIR}"
ln -s "${NORM_DIR}/${MZML_1_BASE}.mzML" "${COMET_DIR}/${MZML_1_BASE}.mzML"
ln -s "${NORM_DIR}/${MZML_2_BASE}.mzML" "${COMET_DIR}/${MZML_2_BASE}.mzML"

PARAMS="${COMET_DIR}/comet.params"
sed -e "s|^database_name = .*|database_name = ${FASTA}|" \
    "${SCRIPT_DIR}/comet.params" > "${PARAMS}" || fail "cannot write ${PARAMS}"
assert_grep "database_name = ${FASTA}" "${PARAMS}" "params database_name"
assert_grep "^decoy_search = 1"        "${PARAMS}" "params decoy_search"
assert_grep "^output_pepxmlfile = 1"   "${PARAMS}" "params output_pepxmlfile"
assert_grep "^output_percolatorfile = 1" "${PARAMS}" "params output_percolatorfile"
assert_grep "^fragment_bin_tol = 0.02" "${PARAMS}" "params fragment_bin_tol"

( cd "${COMET_DIR}" && "${COMET}" -P"${PARAMS}" \
      "${MZML_1_BASE}.mzML" "${MZML_2_BASE}.mzML" ) \
    > "${COMET_DIR}/comet.stdout.txt" 2> "${COMET_DIR}/comet.stderr.txt"
COMET_RC=$?
cat "${COMET_DIR}/comet.stdout.txt"
[ "${COMET_RC}" -eq 0 ] || fail "Comet exited ${COMET_RC}"

TOTAL_PIN_ROWS=0; TOTAL_T=0; TOTAL_D=0
for base in "${MZML_1_BASE}" "${MZML_2_BASE}"; do
    assert_file_nonempty "${COMET_DIR}/${base}.pep.xml" "Comet pepXML (${base})"
    assert_file_nonempty "${COMET_DIR}/${base}.pin"     "Comet PIN (${base})"
    r=$(pin_rows   "${COMET_DIR}/${base}.pin")
    t=$(pin_label  "${COMET_DIR}/${base}.pin" 1)
    d=$(pin_label  "${COMET_DIR}/${base}.pin" -1)
    note "PIN ${base}: rows=${r} target=${t} decoy=${d}"
    # A misconfigured search exits 0 with nothing useful.  Demand a real
    # population on both labels before believing anything downstream.
    assert_ge "${r}" 1000 "PIN rows (${base})"
    assert_ge "${t}" 500  "PIN target rows (${base})"
    assert_ge "${d}" 300  "PIN decoy rows (${base})"
    TOTAL_PIN_ROWS=$(( TOTAL_PIN_ROWS + r )); TOTAL_T=$(( TOTAL_T + t )); TOTAL_D=$(( TOTAL_D + d ))
    assert_grep 'search_hit hit_rank="1"' "${COMET_DIR}/${base}.pep.xml" "pepXML has search hits (${base})"
done
note "PIN totals: rows=${TOTAL_PIN_ROWS} target=${TOTAL_T} decoy=${TOTAL_D}"
assert_grep "DECOY_" "${COMET_DIR}/${MZML_1_BASE}.pin" "decoy_prefix in PIN protein names"

step "3b. Comet -N<name> with two input files (documented as single-file only)"
ND="${RUN_ROOT}/comet-Ntest"; rm -rf "${ND}"; mkdir -p "${ND}"
ln -s "${NORM_DIR}/${MZML_1_BASE}.mzML" "${ND}/${MZML_1_BASE}.mzML"
ln -s "${NORM_DIR}/${MZML_2_BASE}.mzML" "${ND}/${MZML_2_BASE}.mzML"
( cd "${ND}" && "${COMET}" -P"${PARAMS}" -Nboth \
      "${MZML_1_BASE}.mzML" "${MZML_2_BASE}.mzML" ) > "${ND}/out.txt" 2>&1
N_RC=$?
note "exit code with -Nboth and two inputs: ${N_RC}"
[ "${N_RC}" -eq 0 ] || fail "expected Comet to exit 0 (it ignores -N silently); got ${N_RC}"
assert_no_file "${ND}/both.pep.xml" "-N base name was NOT honoured"
assert_file_nonempty "${ND}/${MZML_1_BASE}.pep.xml" "per-input pepXML written instead"
note "FINDING: -N is silently ignored with >1 input file; it does not error."

# --- 4. Percolator 3.07.1, XML-capable build --------------------------------

step "4. Percolator 3.07.1 -- pout XML, PSM, peptide and weights output"
P1="${RUN_ROOT}/percolator-3.07.1"
rm -rf "${P1}"; mkdir -p "${P1}"
"${PERC_371}" \
    -X "${P1}/percolator.pout.xml" \
    -m "${P1}/psms.target.txt"     -M "${P1}/psms.decoy.txt" \
    -r "${P1}/peptides.target.txt" -B "${P1}/peptides.decoy.txt" \
    -w "${P1}/weights.txt" \
    "${COMET_DIR}/${MZML_1_BASE}.pin" "${COMET_DIR}/${MZML_2_BASE}.pin" \
    > "${P1}/percolator.stdout.txt" 2> "${P1}/percolator.stderr.txt"
[ $? -eq 0 ] || { tail -20 "${P1}/percolator.stderr.txt" >&2; fail "Percolator 3.07.1 exited non-zero"; }

for f in percolator.pout.xml psms.target.txt psms.decoy.txt \
         peptides.target.txt peptides.decoy.txt weights.txt; do
    assert_file_nonempty "${P1}/${f}" "Percolator 3.07.1 output ${f}"
done
PSM_ROWS=$(tab_rows "${P1}/psms.target.txt")
PSM_Q01=$(tab_q_below "${P1}/psms.target.txt" 0.01)
PEP_ROWS=$(tab_rows "${P1}/peptides.target.txt")
PEP_Q01=$(tab_q_below "${P1}/peptides.target.txt" 0.01)
W_ROWS=$(tab_rows "${P1}/weights.txt")
assert_ge "${PSM_ROWS}" 1000 "3.07.1 target PSM rows"
assert_ge "${PSM_Q01}"  200  "3.07.1 target PSMs at q<0.01"
assert_ge "${PEP_ROWS}" 500  "3.07.1 target peptide rows"
assert_ge "${PEP_Q01}"  100  "3.07.1 target peptides at q<0.01"
assert_ge "${W_ROWS}"   6    "3.07.1 weights rows (3 CV bins x header+2)"
assert_grep "lnExpect" "${P1}/weights.txt" "weights carry feature names"
note "example target PSMs:"
head -3 "${P1}/psms.target.txt" | sed 's/^/      /'
POUT_PSMS=$(count_tag "${P1}/percolator.pout.xml" psm)
POUT_PEPS=$(count_tag "${P1}/percolator.pout.xml" peptide)
assert_eq "${POUT_PSMS}" "${PSM_ROWS}" "pout <psm> count equals target PSM rows (no -Z)"
assert_eq "${POUT_PEPS}" "${PEP_ROWS}" "pout <peptide> count equals target peptide rows (no -Z)"
assert_not_grep 'decoy="true"' "${P1}/percolator.pout.xml" "pout without -Z carries no decoys"

# --- 5. the Limelight XML schema validator ----------------------------------
# The Limelight schema is not published as a standalone download; it ships
# inside the converter JAR as limelight-xml.xsd.  Fetch the JAR (checksum
# verified), extract the XSD, and build a validator -- then PROVE the validator
# can fail before trusting any verdict it gives.

step "5. Limelight converter JAR and the schema validator"
if [ -f "${JAR}" ] && [ "$(sha256sum "${JAR}" | cut -d' ' -f1)" = "${JAR_SHA256}" ]; then
    note "JAR already present and checksum-verified: ${JAR}"
else
    curl -sSL --fail -o "${JAR}" "${JAR_URL}" || fail "cannot download ${JAR_URL}"
fi
GOT_SHA=$(sha256sum "${JAR}" | cut -d' ' -f1)
GOT_BYTES=$(stat -c%s "${JAR}")
assert_eq "${GOT_SHA}"   "${JAR_SHA256}" "converter JAR SHA-256"
assert_eq "${GOT_BYTES}" "${JAR_BYTES}"  "converter JAR size in bytes"

SCHEMA_DIR="${RUN_ROOT}/schema"; mkdir -p "${SCHEMA_DIR}"
python3 - "${JAR}" "${SCHEMA_DIR}/limelight-xml.xsd" <<'PY' || fail "cannot extract limelight-xml.xsd from the JAR"
import hashlib, sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
d = z.read('limelight-xml.xsd')
open(sys.argv[2], 'wb').write(d)
print('    limelight-xml.xsd: %d bytes, sha256 %s' % (len(d), hashlib.sha256(d).hexdigest()))
PY
assert_file_nonempty "${SCHEMA_DIR}/limelight-xml.xsd" "Limelight XSD"

VAL_DIR="${RUN_ROOT}/validator"; mkdir -p "${VAL_DIR}"
cat > "${VAL_DIR}/ValidateXml.java" <<'JAVA_EOF'
// Validate an XML document against an XSD with javax.xml.validation.
// Exit 0 only when the document is schema-valid; 1 when it is not.
import java.io.File;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

public class ValidateXml {
    static int errors = 0, fatals = 0, warnings = 0;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: ValidateXml <schema.xsd> <document.xml>");
            System.exit(2);
        }
        File xsd = new File(args[0]), doc = new File(args[1]);
        if (!xsd.isFile()) { System.err.println("no such schema: " + xsd); System.exit(2); }
        if (!doc.isFile()) { System.err.println("no such document: " + doc); System.exit(2); }
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Validator v = sf.newSchema(xsd).newValidator();
        v.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) { warnings++; report("WARNING", e); }
            public void error(SAXParseException e) { errors++; report("ERROR", e); }
            public void fatalError(SAXParseException e) throws SAXParseException {
                fatals++; report("FATAL", e); throw e;
            }
            void report(String kind, SAXParseException e) {
                if (errors + fatals + warnings <= 10)
                    System.out.println(kind + " line " + e.getLineNumber()
                        + " col " + e.getColumnNumber() + ": " + e.getMessage());
            }
        });
        try {
            v.validate(new StreamSource(doc));
        } catch (org.xml.sax.SAXException e) {
            System.out.println("aborted: " + e.getMessage());
        }
        System.out.println("schema:   " + xsd.getAbsolutePath());
        System.out.println("document: " + doc.getAbsolutePath() + " (" + doc.length() + " bytes)");
        System.out.println("result:   fatals=" + fatals + " errors=" + errors + " warnings=" + warnings);
        if (fatals + errors > 0) { System.out.println("VERDICT:  INVALID"); System.exit(1); }
        System.out.println("VERDICT:  VALID");
    }
}
JAVA_EOF
validate() {  # schema, document -> exit status of the validator
    java "${VAL_DIR}/ValidateXml.java" "$1" "$2"
}

# --- 6. the converter --------------------------------------------------------

step "6. cometPercolator2LimelightXML v2.8.1 -> Limelight XML"
LL="${RUN_ROOT}/limelight"; rm -rf "${LL}"; mkdir -p "${LL}"
java -jar "${JAR}" \
    -c "${PARAMS}" \
    -p "${P1}/percolator.pout.xml" \
    -d "${COMET_DIR}" \
    -f "${FASTA}" \
    -o "${LL}/limelight.xml" \
    -v > "${LL}/converter.stdout.txt" 2> "${LL}/converter.stderr.txt"
CONV_RC=$?
tr '\t' '\n' < "${LL}/converter.stderr.txt" | grep -v 'FASTA entries\.\.\.$' | tail -8 | sed 's/^/    /'
[ "${CONV_RC}" -eq 0 ] || fail "converter exited ${CONV_RC}"
assert_file_nonempty "${LL}/limelight.xml" "Limelight XML"

step "7. prove the schema validator can fail, then use it"
CORR="${RUN_ROOT}/corrupt"; rm -rf "${CORR}"; mkdir -p "${CORR}"
python3 - "${LL}/limelight.xml" "${CORR}" <<'PY' || fail "cannot build the deliberately corrupted documents"
import sys
src, out = sys.argv[1], sys.argv[2]
d = open(src, 'rb').read()
i = d.find(b'<search_program_info>')
assert i > 0
open(out + '/bogus-element.xml', 'wb').write(d[:i] + b'<not_in_the_schema/>\n    ' + d[i:])
j = d.find(b'>', d.find(b'<limelight_input'))
head = d[:j + 1]
assert b'fasta_filename=' in head
open(out + '/missing-attribute.xml', 'wb').write(
    d.replace(head[head.find(b'<limelight_input'):j + 1], b'<limelight_input>', 1))
k = d.rfind(b'</limelight_input>')
open(out + '/truncated.xml', 'wb').write(d[:k])
print('    wrote 3 deliberately corrupted copies')
PY
for bad in bogus-element missing-attribute truncated; do
    if validate "${SCHEMA_DIR}/limelight-xml.xsd" "${CORR}/${bad}.xml" | sed 's/^/    /'; then
        fail "the validator accepted the deliberately corrupted ${bad}.xml -- a validator that cannot fail proves nothing"
    fi
    note "validator correctly REJECTED ${bad}.xml"
done
validate "${SCHEMA_DIR}/limelight-xml.xsd" "${LL}/limelight.xml" | sed 's/^/    /' \
    || fail "the real Limelight XML failed schema validation"

step "8. Limelight XML content counts, cross-checked against Percolator"
python3 - "${LL}/limelight.xml" "${PSM_ROWS}" "${PEP_ROWS}" "${PSM_Q01}" "${PEP_Q01}" <<'PY' || fail "Limelight XML content did not match the Percolator output"
import sys, xml.etree.ElementTree as ET
from collections import Counter
path = sys.argv[1]
psm_rows, pep_rows, psm_q01, pep_q01 = (int(x) for x in sys.argv[2:6])
c = Counter(); pq = []; eq = []
for _, el in ET.iterparse(path, events=('end',)):
    c[el.tag] += 1
    if el.tag == 'filterable_psm_annotation' and el.get('annotation_name') == 'q-value':
        pq.append(float(el.get('value')))
    if el.tag == 'filterable_reported_peptide_annotation' and el.get('annotation_name') == 'q-value':
        eq.append(float(el.get('value')))
got = dict(psm=c['psm'], reported_peptide=c['reported_peptide'],
           matched_protein=c['matched_protein'],
           peptide_modification=c['peptide_modification'],
           psm_q01=sum(1 for x in pq if x < 0.01),
           pep_q01=sum(1 for x in eq if x < 0.01))
for k, v in sorted(got.items()):
    print('    %-22s %d' % (k, v))
bad = []
if got['psm'] != psm_rows: bad.append('psm %d != percolator target PSM rows %d' % (got['psm'], psm_rows))
if got['reported_peptide'] != pep_rows: bad.append('reported_peptide %d != percolator target peptide rows %d' % (got['reported_peptide'], pep_rows))
if got['psm_q01'] != psm_q01: bad.append('psm q<0.01 %d != percolator %d' % (got['psm_q01'], psm_q01))
if got['pep_q01'] != pep_q01: bad.append('peptide q<0.01 %d != percolator %d' % (got['pep_q01'], pep_q01))
if got['matched_protein'] < 100: bad.append('only %d matched_protein elements' % got['matched_protein'])
if bad:
    raise SystemExit('    MISMATCH: ' + '; '.join(bad))
print('    all counts consistent with the Percolator output')
PY

step "9. the Percolator pout XML against the XSD shipped in the same package"
if validate "${PERC_371_POUT_XSD}" "${P1}/percolator.pout.xml" | sed 's/^/    /'; then
    note "pout XML validates against the shipped percolator_out.xsd"
else
    note "FINDING: the pout XML written by 3.07.1 does NOT validate against the"
    note "percolator_out.xsd shipped in the same .deb. Recorded, not suppressed."
fi

# --- 10. converter -d default ------------------------------------------------

step "10. converter -d default is the Percolator file's own directory"
DT="${RUN_ROOT}/dtest"; rm -rf "${DT}"; mkdir -p "${DT}"
java -jar "${JAR}" -c "${PARAMS}" -p "${P1}/percolator.pout.xml" \
     -f "${FASTA}" -o "${DT}/nodir.xml" > "${DT}/a.out" 2> "${DT}/a.err"
[ $? -ne 0 ] || fail "expected the converter to fail with -d omitted and no pepXML beside the pout file"
assert_grep "May need to specify data directory with -d option" "${DT}/a.err" "-d default error message"
cp "${P1}/percolator.pout.xml" "${COMET_DIR}/percolator.pout.xml"
java -jar "${JAR}" -c "${PARAMS}" -p "${COMET_DIR}/percolator.pout.xml" \
     -f "${FASTA}" -o "${DT}/withdir.xml" > "${DT}/b.out" 2> "${DT}/b.err"
[ $? -eq 0 ] || { tail -5 "${DT}/b.err" >&2; fail "converter failed with the pout file beside the pepXML files"; }
assert_file_nonempty "${DT}/withdir.xml" "Limelight XML with -d defaulted"
if diff -q <(grep -v '<conversion_program ' "${LL}/limelight.xml") \
           <(grep -v '<conversion_program ' "${DT}/withdir.xml") >/dev/null; then
    note "identical to the -d run apart from the <conversion_program> provenance element"
else
    fail "the -d-defaulted output differs from the explicit -d output by more than provenance"
fi

# --- 11. Percolator 3.09 -----------------------------------------------------

step "11. Percolator 3.09 -- PSM, peptide and weights, and demonstrably no XML"
P3="${RUN_ROOT}/percolator-3.09"; rm -rf "${P3}"; mkdir -p "${P3}"
"${PERC_309}" \
    -m "${P3}/psms.target.txt"     -M "${P3}/psms.decoy.txt" \
    -r "${P3}/peptides.target.txt" -B "${P3}/peptides.decoy.txt" \
    -w "${P3}/weights.txt" \
    "${COMET_DIR}/${MZML_1_BASE}.pin" "${COMET_DIR}/${MZML_2_BASE}.pin" \
    > "${P3}/percolator.stdout.txt" 2> "${P3}/percolator.stderr.txt"
[ $? -eq 0 ] || { tail -20 "${P3}/percolator.stderr.txt" >&2; fail "Percolator 3.09 exited non-zero"; }
for f in psms.target.txt psms.decoy.txt peptides.target.txt peptides.decoy.txt weights.txt; do
    assert_file_nonempty "${P3}/${f}" "Percolator 3.09 output ${f}"
done
P3_PSM=$(tab_rows "${P3}/psms.target.txt");     P3_PSM_Q=$(tab_q_below "${P3}/psms.target.txt" 0.01)
P3_PEP=$(tab_rows "${P3}/peptides.target.txt"); P3_PEP_Q=$(tab_q_below "${P3}/peptides.target.txt" 0.01)
assert_ge "${P3_PSM}"   1000 "3.09 target PSM rows"
assert_ge "${P3_PSM_Q}" 200  "3.09 target PSMs at q<0.01"
assert_ge "${P3_PEP_Q}" 100  "3.09 target peptides at q<0.01"
assert_ge "$(tab_rows "${P3}/weights.txt")" 6 "3.09 weights rows"
note "example 3.09 target PSMs:"
head -3 "${P3}/psms.target.txt" | sed 's/^/      /'
XMLS=$(find "${P3}" -name '*.xml' | wc -l)
assert_eq "${XMLS}" "0" "XML files written by 3.09"
HELPX=$("${PERC_309}" --help 2>&1 | grep -c -E 'xmloutput|decoy-xml-output')
assert_eq "${HELPX}" "0" "occurrences of xmloutput/decoy-xml-output in 3.09 --help"

step "11b. what 3.09 does when a caller passes -X / --xmloutput / -Z anyway"
for flag in "-X ${P3}/must-not-appear.xml" "--xmloutput ${P3}/must-not-appear.xml" "-Z"; do
    # shellcheck disable=SC2086
    "${PERC_309}" ${flag} "${COMET_DIR}/${MZML_1_BASE}.pin" \
        > "${P3}/flag.out" 2> "${P3}/flag.err"
    rc=$?
    [ "${rc}" -ne 0 ] || fail "3.09 accepted '${flag}' -- expected a non-zero exit"
    assert_grep "is invalid" "${P3}/flag.err" "3.09 rejects '${flag}' (exit ${rc})"
done
assert_no_file "${P3}/must-not-appear.xml" "3.09 wrote no XML when handed -X"

step "11c. 3.07.1 vs 3.09 on the same PIN"
for n in psms.target peptides.target weights; do
    if cmp -s "${P1}/${n}.txt" "${P3}/${n}.txt"; then
        note "${n}.txt: byte-identical between 3.07.1 and 3.09"
    else
        note "${n}.txt: differs between 3.07.1 and 3.09"
    fi
done
note "3.07.1 target PSMs q<0.01 = ${PSM_Q01}; 3.09 = ${P3_PSM_Q}"
note "3.07.1 target peptides q<0.01 = ${PEP_Q01}; 3.09 = ${P3_PEP_Q}"
python3 - "${P1}/psms.target.txt" "${P3}/psms.target.txt" <<'PY' || fail "3.07.1/3.09 column comparison failed"
import sys
from collections import Counter
def load(p):
    rows = [l.rstrip('\n').split('\t') for l in open(p)]
    return rows[0], rows[1:]
h, a = load(sys.argv[1]); _, b = load(sys.argv[2])
if len(a) != len(b):
    raise SystemExit('    row counts differ: %d vs %d' % (len(a), len(b)))
c = Counter()
for x, y in zip(a, b):
    for i, (u, v) in enumerate(zip(x, y)):
        if u != v:
            c[h[i]] += 1
print('    rows compared: %d' % len(a))
print('    columns that differ: %s' % (dict(c) or 'none'))
PY

# --- 12. the noxml build -----------------------------------------------------

step "12. does the 3.07.1 'noxml' build's -X output feed the converter?"
NX="${RUN_ROOT}/percolator-noxml"; rm -rf "${NX}"; mkdir -p "${NX}"
"${PERC_NOXML}" \
    -X "${NX}/percolator.pout.xml" \
    -m "${NX}/psms.target.txt"     -M "${NX}/psms.decoy.txt" \
    -r "${NX}/peptides.target.txt" -B "${NX}/peptides.decoy.txt" \
    -w "${NX}/weights.txt" \
    "${COMET_DIR}/${MZML_1_BASE}.pin" "${COMET_DIR}/${MZML_2_BASE}.pin" \
    > "${NX}/percolator.stdout.txt" 2> "${NX}/percolator.stderr.txt"
[ $? -eq 0 ] || { tail -20 "${NX}/percolator.stderr.txt" >&2; fail "noxml build exited non-zero under -X"; }
assert_file_nonempty "${NX}/percolator.pout.xml" "noxml build pout XML"
if diff -q <(sed 's|<command_line>.*</command_line>|X|' "${P1}/percolator.pout.xml") \
           <(sed 's|<command_line>.*</command_line>|X|' "${NX}/percolator.pout.xml") >/dev/null; then
    note "noxml pout XML is byte-identical to the XML-capable build's apart from <command_line>"
else
    note "FINDING: noxml pout XML differs from the XML-capable build's by more than <command_line>"
fi
java -jar "${JAR}" -c "${PARAMS}" -p "${NX}/percolator.pout.xml" -d "${COMET_DIR}" \
     -f "${FASTA}" -o "${LL}/limelight-from-noxml.xml" \
     > "${LL}/converter-noxml.stdout.txt" 2> "${LL}/converter-noxml.stderr.txt"
[ $? -eq 0 ] || fail "the converter REJECTED XML produced by the noxml build"
assert_file_nonempty "${LL}/limelight-from-noxml.xml" "Limelight XML from noxml-produced pout"
validate "${SCHEMA_DIR}/limelight-xml.xsd" "${LL}/limelight-from-noxml.xml" | sed 's/^/    /' \
    || fail "Limelight XML from the noxml build failed schema validation"
if diff -q <(grep -v '<conversion_program ' "${LL}/limelight.xml") \
           <(grep -v '<conversion_program ' "${LL}/limelight-from-noxml.xml") >/dev/null; then
    note "RESULT: the converter ACCEPTS noxml-produced XML and the Limelight XML is"
    note "identical to the XML-capable build's apart from <conversion_program>."
else
    fail "the Limelight XML from the noxml build differs by more than provenance"
fi

# --- 13. the -Z / --import-decoys chain (R-LL-05) ---------------------------

step "13. R-LL-05: -Z and --import-decoys, all four combinations"
DEC="${RUN_ROOT}/decoy-chain"; rm -rf "${DEC}"; mkdir -p "${DEC}"

note "13a. Percolator 3.07.1 given -Z but NOT -X"
"${PERC_371}" -Z -m "${DEC}/z-only.psms.txt" "${COMET_DIR}/${MZML_1_BASE}.pin" \
    > "${DEC}/z-only.out" 2> "${DEC}/z-only.err"
ZRC=$?
note "exit code = ${ZRC} (help says -Z is 'Only available if -X is set')"
[ "${ZRC}" -eq 0 ] || fail "expected 3.07.1 to silently ignore -Z without -X"
note "FINDING: -Z without -X is silently ignored; it does not error."

note "13b. Percolator with -X -Z, converter WITHOUT --import-decoys"
"${PERC_371}" -X "${DEC}/withZ.pout.xml" -Z \
    -m "${DEC}/withZ.psms.txt" -r "${DEC}/withZ.peptides.txt" -w "${DEC}/withZ.weights.txt" \
    "${COMET_DIR}/${MZML_1_BASE}.pin" "${COMET_DIR}/${MZML_2_BASE}.pin" \
    > "${DEC}/withZ.out" 2> "${DEC}/withZ.err"
[ $? -eq 0 ] || fail "Percolator with -X -Z exited non-zero"
assert_file_nonempty "${DEC}/withZ.pout.xml" "pout XML with -Z"
ZPSMS=$(count_tag "${DEC}/withZ.pout.xml" psm)
assert_ge "${ZPSMS}" "$(( PSM_ROWS + 1 ))" "pout <psm> count with -Z (targets + decoys)"
assert_grep 'decoy="true"' "${DEC}/withZ.pout.xml" "pout with -Z carries decoys"
java -jar "${JAR}" -c "${PARAMS}" -p "${DEC}/withZ.pout.xml" -d "${COMET_DIR}" \
     -f "${FASTA}" -o "${DEC}/ll-Z-noimport.xml" > "${DEC}/c1.out" 2> "${DEC}/c1.err"
[ $? -ne 0 ] || fail "expected the converter to reject a -Z pout without --import-decoys"
assert_grep "Unable to find any comet results for reported peptide" "${DEC}/c1.err" \
    "converter rejects -Z pout without --import-decoys"
note "FINDING: -Z without --import-decoys is a HARD FAILURE. The pairing is bidirectional."

note "13c. converter WITH --import-decoys over Comet internal decoys (decoy_search=1)"
java -jar "${JAR}" -c "${PARAMS}" -p "${DEC}/withZ.pout.xml" -d "${COMET_DIR}" \
     -f "${FASTA}" -o "${DEC}/ll-Z-import.xml" --import-decoys \
     > "${DEC}/c2.out" 2> "${DEC}/c2.err"
[ $? -ne 0 ] || fail "expected --import-decoys to fail: Comet's internal decoys are not in the FASTA"
assert_grep "protein names were not found in FASTA" "${DEC}/c2.err" \
    "--import-decoys needs the decoy proteins in the FASTA"
note "FINDING: --import-decoys is incompatible with Comet decoy_search=1 plus a target-only FASTA."

note "13d. --import-decoys with an externally concatenated target+decoy FASTA"
CFASTA="${DEC}/concat_target_decoy.fasta"
python3 - "${FASTA}" "${CFASTA}" <<'PY' || fail "cannot build the concatenated decoy FASTA"
import sys
src, dst = sys.argv[1], sys.argv[2]
recs, h, seq = [], None, []
for line in open(src):
    if line.startswith('>'):
        if h is not None: recs.append((h, ''.join(seq)))
        h, seq = line.rstrip('\n'), []
    else:
        seq.append(line.strip())
if h is not None: recs.append((h, ''.join(seq)))
with open(dst, 'w') as o:
    for h, s in recs:
        o.write(h + '\n')
        for i in range(0, len(s), 60): o.write(s[i:i+60] + '\n')
    for h, s in recs:
        o.write('>DECOY_' + h[1:] + '\n')
        r = s[::-1]
        for i in range(0, len(r), 60): o.write(r[i:i+60] + '\n')
print('    concatenated FASTA: %d target + %d reversed decoy records' % (len(recs), len(recs)))
PY
DCOMET="${DEC}/comet"; mkdir -p "${DCOMET}"
ln -sf "${NORM_DIR}/${MZML_1_BASE}.mzML" "${DCOMET}/${MZML_1_BASE}.mzML"
ln -sf "${NORM_DIR}/${MZML_2_BASE}.mzML" "${DCOMET}/${MZML_2_BASE}.mzML"
sed -e "s|^database_name = .*|database_name = ${CFASTA}|" \
    -e "s|^decoy_search = 1|decoy_search = 0|" "${PARAMS}" > "${DCOMET}/comet.params"
( cd "${DCOMET}" && "${COMET}" -P"${DCOMET}/comet.params" \
      "${MZML_1_BASE}.mzML" "${MZML_2_BASE}.mzML" ) > "${DCOMET}/out.txt" 2>&1
[ $? -eq 0 ] || { tail -5 "${DCOMET}/out.txt" >&2; fail "Comet against the concatenated FASTA exited non-zero"; }
DT_ROWS=$(pin_label "${DCOMET}/${MZML_1_BASE}.pin" 1)
DD_ROWS=$(pin_label "${DCOMET}/${MZML_1_BASE}.pin" -1)
assert_ge "${DT_ROWS}" 500 "concatenated-FASTA PIN target rows"
assert_ge "${DD_ROWS}" 300 "concatenated-FASTA PIN decoy rows (Label set from decoy_prefix)"
"${PERC_371}" -X "${DEC}/concat.pout.xml" -Z \
    -m "${DEC}/concat.psms.txt" -r "${DEC}/concat.peptides.txt" -w "${DEC}/concat.weights.txt" \
    "${DCOMET}/${MZML_1_BASE}.pin" "${DCOMET}/${MZML_2_BASE}.pin" \
    > "${DEC}/concat.out" 2> "${DEC}/concat.err"
[ $? -eq 0 ] || fail "Percolator on the concatenated-FASTA PINs exited non-zero"
java -jar "${JAR}" -c "${DCOMET}/comet.params" -p "${DEC}/concat.pout.xml" -d "${DCOMET}" \
     -f "${CFASTA}" -o "${DEC}/ll-concat-decoys.xml" --import-decoys \
     > "${DEC}/c3.out" 2> "${DEC}/c3.err"
[ $? -eq 0 ] || { tr '\t' '\n' < "${DEC}/c3.err" | grep -v 'FASTA entries' | tail -3 >&2; \
                  fail "--import-decoys failed even with a concatenated target+decoy FASTA"; }
assert_file_nonempty "${DEC}/ll-concat-decoys.xml" "Limelight XML with imported decoys"
validate "${SCHEMA_DIR}/limelight-xml.xsd" "${DEC}/ll-concat-decoys.xml" | sed 's/^/    /' \
    || fail "the decoy-importing Limelight XML failed schema validation"
python3 - "${DEC}/ll-concat-decoys.xml" <<'PY' || fail "no decoy PSMs were imported"
import sys, xml.etree.ElementTree as ET
from collections import Counter
c = Counter()
for _, el in ET.iterparse(sys.argv[1], events=('end',)):
    if el.tag == 'psm': c[el.get('is_decoy')] += 1
print('    psm is_decoy counts: %s' % dict(c))
if c.get('true', 0) < 100:
    raise SystemExit('    only %d decoy PSMs imported' % c.get('true', 0))
PY
note "FINDING: --import-decoys works only with an externally concatenated target+decoy FASTA."

note "13e. --import-decoys against a pout produced WITHOUT -Z"
"${PERC_371}" -X "${DEC}/concat-noZ.pout.xml" \
    -m "${DEC}/concat-noZ.psms.txt" -w "${DEC}/concat-noZ.weights.txt" \
    "${DCOMET}/${MZML_1_BASE}.pin" "${DCOMET}/${MZML_2_BASE}.pin" \
    > "${DEC}/concat-noZ.out" 2> "${DEC}/concat-noZ.err"
[ $? -eq 0 ] || fail "Percolator (concatenated FASTA, no -Z) exited non-zero"
java -jar "${JAR}" -c "${DCOMET}/comet.params" -p "${DEC}/concat-noZ.pout.xml" -d "${DCOMET}" \
     -f "${CFASTA}" -o "${DEC}/ll-noZ-import.xml" --import-decoys \
     > "${DEC}/c4.out" 2> "${DEC}/c4.err"
C4=$?
if [ "${C4}" -eq 0 ]; then
    python3 - "${DEC}/ll-noZ-import.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
from collections import Counter
c = Counter()
for _, el in ET.iterparse(sys.argv[1], events=('end',)):
    if el.tag == 'psm': c[el.get('is_decoy')] += 1
print('    psm is_decoy counts: %s' % dict(c))
PY
    note "FINDING: --import-decoys over a pout with no decoys SUCCEEDS SILENTLY and imports none."
else
    note "FINDING: --import-decoys over a pout with no decoys exits ${C4}."
fi

# --- summary -----------------------------------------------------------------

step "SUMMARY"
{
    echo "run_scientific_path.sh -- $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "run root                       ${RUN_ROOT}"
    echo "FASTA records                  ${FASTA_RECORDS}"
    echo "PIN rows (both files)          ${TOTAL_PIN_ROWS} (target ${TOTAL_T}, decoy ${TOTAL_D})"
    echo "3.07.1 target PSM rows         ${PSM_ROWS}"
    echo "3.07.1 target PSMs q<0.01      ${PSM_Q01}"
    echo "3.07.1 target peptide rows     ${PEP_ROWS}"
    echo "3.07.1 target peptides q<0.01  ${PEP_Q01}"
    echo "3.07.1 weights rows            ${W_ROWS}"
    echo "3.09 target PSM rows           ${P3_PSM}"
    echo "3.09 target PSMs q<0.01        ${P3_PSM_Q}"
    echo "3.09 target peptides q<0.01    ${P3_PEP_Q}"
    echo "3.09 XML files written         ${XMLS}"
    echo "Limelight XML                  ${LL}/limelight.xml"
} | tee "${RUN_ROOT}/summary.txt"

printf '\nAll stages produced output and every assertion held.\n'
exit 0

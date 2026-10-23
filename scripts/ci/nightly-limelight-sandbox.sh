#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# nightly-limelight-sandbox.sh -- the D-007 nightly Limelight-sandbox slot
# (PHASE-14 unit 9; design decision P14-13).
#
# D-007, decided 2026-08-30: "A local fake endpoint is the default, and every
# run uses it.  ... A nightly sandbox run is provisioned for but not
# configured: no sandbox instance has been named, and naming one is the
# owner's, not an agent's.  Phase 14 leaves the slot wired and skipping with a
# stated reason -- not silently passing -- so that supplying a URL later is
# configuration rather than authorship."
#
# NOT CONFIGURED (today, and whenever COMETGUI_LIMELIGHT_SANDBOX_URL is empty):
# this step says so, names why and what configuring it takes, and exits 70
# through scripts/ci/stub-lib.sh.  That is the honest choice the pipeline's
# rules leave, and it is deliberate:
#   * scripts/ci/run-pipeline-locally.sh (the pipeline control) requires a step
#     to exit 0 unless it is a stub, and a stub to exit 70; check-workflows.py
#     tells the two apart by the reference to stub-lib.sh;
#   * an unconfigured slot that exited 0 would be a green step that uploaded
#     nothing -- the silent pass D-007 forbids -- so it must not exit 0;
#   * exit 70, the stub status, is the one non-zero status both rules accept,
#     and on GitHub it shows the slot red, naming D-007, in its own job, which
#     no other job waits on.
# Neither rule was changed to make room for it.
#
# CONFIGURED (COMETGUI_LIMELIGHT_SANDBOX_URL set): it runs the slot's upload
# test, LimelightSandboxUploadTest#theConfiguredSandboxAcceptsTheUpload in
# cometgui-tools -- one upload through the product's own LimelightUploadClient,
# with the module's loopback guard lifted for the sandbox's host alone -- and
# requires its surefire report to show that the test RAN and PASSED.  It
# exits 0 only then, 1 otherwise.  It needs, all from the owner:
#   COMETGUI_LIMELIGHT_SANDBOX_URL       the sandbox's web application, https://
#                                        only (refused otherwise: the submit key
#                                        is in every request, and the slot is
#                                        never the local fake)
#   COMETGUI_LIMELIGHT_SANDBOX_PROJECT   the sandbox project's id
#   COMETGUI_LIMELIGHT_SANDBOX_KEY_FILE  a file holding the submit key (never
#                                        printed)
#   COMETGUI_LIMELIGHT_SANDBOX_XML       the Limelight XML file to upload
# nightly.yml passes the URL and the project in from GitHub repository
# VARIABLES (vars.), which hold no secret.  It cannot pass the key:
# check-workflows.py forbids any `secrets.` reference in a workflow (D-008:
# where CometGUI publishes is a person's decision, not a job's), so on GitHub a
# configured URL without a key FAILS here, saying that -- allowing exactly one
# credential into the nightly workflow is an owner decision, not an agent's.
# The XML, on a runner, is a conversion of a search of D-006's CI fixture,
# which is OPEN; the local fixture must not be presented as it.
#
# THERE IS NO DEFAULT.  No URL, host, project or key is written anywhere in
# this repository; an empty variable is "not configured".
#
# A note for the pipeline control: this file references stub-lib.sh, so
# run-pipeline-locally.sh always classifies it as a stub and expects 70.  Run
# with the variables exported, the step is real and exits 0 or 1, and the local
# control reports it as unexpected -- correctly: the slot is then no longer a
# stub, and whoever configures it moves the not-configured branch out (or
# re-classifies the step) in the same change.
#
# Exit status: 70 not configured (stub-lib.sh); 0 configured, and the upload
# test ran and passed; 1 configured, and it did not (or the configuration is
# incomplete); 2 misuse.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

[ "$#" -eq 0 ] || { printf 'nightly-limelight-sandbox.sh: takes no arguments; it is configured by environment (see its header)\n' >&2; exit 2; }

if [ -z "${COMETGUI_LIMELIGHT_SANDBOX_URL:-}" ]; then
    exec bash "${SCRIPT_DIR}/stub-lib.sh" \
        --script "nightly-limelight-sandbox.sh" \
        --phase  "PHASE-14" \
        --installed-by "PHASE-14" \
        --owns   "upload one Limelight XML file to a Limelight SANDBOX through the product's client (LimelightSandboxUploadTest) every night -- D-007's provisioned slot" \
        --spec   "DECISIONS.rst, D-007 (a nightly sandbox run is provisioned for but not configured)" \
        --waits-for "NOT CONFIGURED: COMETGUI_LIMELIGHT_SANDBOX_URL is empty. D-007: no Limelight sandbox instance has been named, and naming one is the project owner's decision, not an agent's. No default exists; this step uploads nothing." \
        --waits-for "a submit key for that sandbox: check-workflows.py forbids any secrets. reference in a workflow (D-008), so passing one into this job is itself an owner decision" \
        --ci-needs "repository variables COMETGUI_LIMELIGHT_SANDBOX_URL (https://) and COMETGUI_LIMELIGHT_SANDBOX_PROJECT, which nightly.yml already passes in; COMETGUI_LIMELIGHT_SANDBOX_KEY_FILE; COMETGUI_LIMELIGHT_SANDBOX_XML -- on a runner, a conversion of D-006's CI fixture (OPEN), never the local fixture" \
        --meanwhile "every automated upload goes to the D-007 local fake on loopback: LimelightUploadClientTest, LimelightSandboxUploadTest#theSamePathAgainstTheFake (this slot's own upload call), and step 27 of both tiers' canonical scenario (bash scripts/verify-e2e-gates.sh)" \
        --completed-by "the owner names a sandbox and supplies its URL, project and key (configuration, not code: the configured branch of this script is already written)"
fi

die() { printf 'nightly-limelight-sandbox.sh: FAILED: %s\n' "$1" >&2; exit 1; }

url="${COMETGUI_LIMELIGHT_SANDBOX_URL}"
case "${url}" in
    https://?*) ;;
    *) die "COMETGUI_LIMELIGHT_SANDBOX_URL must be the https:// address of a Limelight SANDBOX (D-007), not '${url}': the submit key is in every request, and this slot is never the local fake" ;;
esac
[ -n "${COMETGUI_LIMELIGHT_SANDBOX_PROJECT:-}" ] \
    || die "COMETGUI_LIMELIGHT_SANDBOX_URL is set, so COMETGUI_LIMELIGHT_SANDBOX_PROJECT must be too"
case "${COMETGUI_LIMELIGHT_SANDBOX_PROJECT}" in
    *[!0-9]*) die "COMETGUI_LIMELIGHT_SANDBOX_PROJECT must be a project id (digits), not '${COMETGUI_LIMELIGHT_SANDBOX_PROJECT}'" ;;
esac
key_file="${COMETGUI_LIMELIGHT_SANDBOX_KEY_FILE:-}"
[ -n "${key_file}" ] \
    || die "COMETGUI_LIMELIGHT_SANDBOX_URL is set but COMETGUI_LIMELIGHT_SANDBOX_KEY_FILE is not. On GitHub there is no way to provide it today: check-workflows.py forbids a secrets. reference in any workflow (D-008), and letting exactly one credential into nightly.yml is the owner's decision"
[ -s "${key_file}" ] || die "COMETGUI_LIMELIGHT_SANDBOX_KEY_FILE names no readable, non-empty file"
xml="${COMETGUI_LIMELIGHT_SANDBOX_XML:-}"
[ -n "${xml}" ] \
    || die "COMETGUI_LIMELIGHT_SANDBOX_XML is not set: the slot needs a Limelight XML file to upload. On a runner that is a conversion of a search of D-006's CI fixture, whose half of D-006 is OPEN; the local fixture must not be presented as it"
[ -s "${xml}" ] || die "COMETGUI_LIMELIGHT_SANDBOX_XML names no readable, non-empty file: ${xml}"
case "${xml}" in /*) ;; *) xml="${PWD}/${xml}" ;; esac
case "${key_file}" in /*) ;; *) key_file="${PWD}/${key_file}" ;; esac

if [ ! -f "${ROOT}/tools/env.sh" ]; then
    bash "${SCRIPT_DIR}/toolchain.sh" || die "the project toolchain could not be provisioned"
fi
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"

readonly TEST_CLASS="LimelightSandboxUploadTest"
readonly TEST_METHOD="theConfiguredSandboxAcceptsTheUpload"
readonly REPORT="${ROOT}/cometgui-tools/target/surefire-reports/TEST-org.cometgui.tools.limelight.${TEST_CLASS}.xml"
log="${ROOT}/_build/nightly-limelight-sandbox/maven.log"
mkdir -p -- "$(dirname -- "${log}")"
rm -f -- "${REPORT}"

printf 'nightly-limelight-sandbox.sh: uploading to the configured sandbox %s, project %s (D-007)\n' \
    "${url}" "${COMETGUI_LIMELIGHT_SANDBOX_PROJECT}"
rc=0
( cd -- "${ROOT}" && mvn -B -pl cometgui-tools -am test \
    -Dtest="${TEST_CLASS}#${TEST_METHOD}" -Dsurefire.failIfNoSpecifiedTests=false \
    -Dcometgui.limelight.sandbox.url="${url}" \
    -Dcometgui.limelight.sandbox.project="${COMETGUI_LIMELIGHT_SANDBOX_PROJECT}" \
    -Dcometgui.limelight.sandbox.keyFile="${key_file}" \
    -Dcometgui.limelight.sandbox.xml="${xml}" ) >"${log}" 2>&1 || rc=$?
# Exit 0 proves nothing: the test must have RUN (not been skipped) and passed.
[ -s "${REPORT}" ] || die "Maven exited ${rc} and wrote no report for ${TEST_CLASS}; see ${log}"
verdict="$(python3 - "${REPORT}" "${TEST_METHOD}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

outcome = "absent"
for case in ET.parse(sys.argv[1]).getroot().iter("testcase"):
    if case.get("name") != sys.argv[2]:
        continue
    if case.find("failure") is not None or case.find("error") is not None:
        outcome = "failed"
    elif case.find("skipped") is not None:
        outcome = "skipped"
    else:
        outcome = "passed"
print(outcome)
PYTHON
)"
[ "${rc}" -eq 0 ] && [ "${verdict}" = "passed" ] \
    || die "the sandbox upload test ${verdict} (Maven exit ${rc}); see ${log}"
printf 'nightly-limelight-sandbox.sh: PASSED -- the sandbox accepted the upload (%s#%s ran and passed)\n' \
    "${TEST_CLASS}" "${TEST_METHOD}"

#!/usr/bin/env bash
#
# nightly-windows-raw.sh -- DELIBERATE STUB installed by PHASE-01, kept by
# PHASE-15 because what it waits for is a runner and a fixture, not work.
#
# Needs a Windows runner and a real RAW fixture. Neither exists here: this
# environment has no non-Linux machine and D-006's CI half (fixture data) is
# open.  It says so through stub-lib.sh --waits-for, which the hardening
# harness's census (scripts/verify-hardening-gates.sh, control K) requires of
# every PHASE-15 stub.
#
# Exits 70 (never 0) via scripts/ci/stub-lib.sh, naming PHASE-15 as the owner.
# Replacing this file with the real check is PHASE-15's work; making it exit 0
# to turn a pipeline green is a gate weakening and a rejection at sign-off.

set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/stub-lib.sh" \
    --script "nightly-windows-raw.sh" \
    --phase  "PHASE-15" \
    --owns   "run the Windows Thermo RAW search smoke test on a Windows runner" \
    --spec   "specification.rst, Nightly pipeline; AC-TST-11 (\"healthy when that feature ships\")" \
    --waits-for "a Windows runner on which the product probes Comet's THERMO_RAW_WINDOWS: Comet reads Thermo RAW only on Windows, with CometWrapper.dll and the ThermoFisher.* companions beside it, and there is no Windows machine in this environment" \
    --waits-for "a Thermo RAW fixture: D-006's CI fixture set (DECISIONS.rst, D-006: that half is OPEN) holds none, and the local fixture under scratch/fixture is mzML and is NOT the CI fixture" \
    --ci-needs "nightly.yml's windows job (windows-latest) with the offered Windows Comet installed by the Tool Manager, and a RAW file fetched by checksum under a licence the owner has accepted for CI use" \
    --meanwhile "nothing runs it: the version matrix records feature:thermo-raw:<comet>:windows-x86-64 as UNVERIFIED with this reason (bash scripts/matrix/run-matrix.sh)"

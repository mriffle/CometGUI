#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# build-bridge.sh -- jar the TEST-ONLY loopback bridge (Phase 14 unit 6;
# design decision P14-11; specification R-TEST-06) from cometgui-app's
# compiled TEST classes.  The jar is a Java agent, attached at launch to the
# unmodified installed CometGUI with
#     JAVA_TOOL_OPTIONS=-javaagent:<jar>=port-file=<absolute path>
# It is never packaged; scripts/ci/release-no-test-bridge.sh fails a packaged
# artefact that holds any of it.
#
#   bash scripts/e2e/build-bridge.sh
#        compile cometgui-app's tests (Maven, offline, -pl cometgui-app -am),
#        read their test class path, and write
#        _build/e2e/bridge/cometgui-e2e-bridge.jar
#   bash scripts/e2e/build-bridge.sh --classes DIR --class-path CP [--out JAR]
#        no Maven: jar from an already compiled test-classes DIR, taking JUnit's
#        jars from the class path CP (how the end-to-end test builds its own,
#        from the classes it is running)
#   bash scripts/e2e/build-bridge.sh --help
#
# WHAT THE JAR HOLDS, and nothing else:
#   org/cometgui/app/e2e/bridge/**   the agent, its server, the dialog hook
#                                    (test classes ending in Test excluded)
#   org/cometgui/app/uidriver/**     the ONE driver it runs (RobotFxUiDriver,
#                                    RunningApplication, Await, ...); the
#                                    TestFX driver and the tests excluded
#   org/junit/jupiter/api/**, org/opentest4j/**, org/apiguardian/**
#                                    the driver's assertions, which the packaged
#                                    application's class path does not have
#   META-INF/MANIFEST.MF             Premain-Class: org.cometgui.app.e2e.bridge.BridgeAgent
#
# EXIT 0 PROVES NOTHING, so the jar is listed afterwards and must hold the
# agent, the server, the robot driver and JUnit's Assertions, must name the
# agent as its Premain-Class, and must hold no product class and no TestFX.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
AGENT_CLASS="org.cometgui.app.e2e.bridge.BridgeAgent"
DEFAULT_OUT="${ROOT}/_build/e2e/bridge/cometgui-e2e-bridge.jar"

usage() { sed -n '7,38p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
die() { printf 'build-bridge.sh: FAILED: %s\n' "$*" >&2; exit 1; }

classes=""; class_path=""; out="${DEFAULT_OUT}"
while [ "$#" -gt 0 ]; do
    case "$1" in
        --classes) classes="${2:?--classes needs a directory}"; shift 2 ;;
        --class-path) class_path="${2:?--class-path needs a value}"; shift 2 ;;
        --out) out="${2:?--out needs a path}"; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) printf 'build-bridge.sh: unknown argument %s\n' "$1" >&2; usage >&2; exit 2 ;;
    esac
done
if { [ -n "${classes}" ] && [ -z "${class_path}" ]; } || { [ -z "${classes}" ] && [ -n "${class_path}" ]; }; then
    die "--classes and --class-path go together"
fi

[ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run scripts/feasibility/install-toolchain.sh"
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"
[ -x "${JAVA_HOME}/bin/jar" ] || die "the project JDK at ${JAVA_HOME} has no jar tool"

case "${out}" in /*) ;; *) out="${PWD}/${out}" ;; esac
mkdir -p -- "$(dirname -- "${out}")"
work="$(mktemp -d "$(dirname -- "${out}")/.build-bridge.XXXXXX")"
trap 'rm -rf -- "${work}"' EXIT

if [ -z "${classes}" ]; then
    # One reactor invocation, from the root (.mvn/maven.config's relative local
    # repository resolves against the working directory).  build-classpath
    # writes each module's test class path beside that module's target/.
    log="$(dirname -- "${out}")/maven.log"
    (cd -- "${ROOT}" && mvn -B -o -pl cometgui-app -am test-compile dependency:build-classpath \
        -Dmdep.includeScope=test -Dmdep.outputFile=target/e2e-bridge.classpath) > "${log}" 2>&1 \
        || { tail -30 "${log}" >&2; die "Maven failed; see ${log}"; }
    grep -q 'BUILD SUCCESS' "${log}" || die "Maven exited 0 without BUILD SUCCESS; see ${log}"
    classes="${ROOT}/cometgui-app/target/test-classes"
    [ -s "${ROOT}/cometgui-app/target/e2e-bridge.classpath" ] || die "Maven wrote no test class path for cometgui-app"
    class_path="$(cat "${ROOT}/cometgui-app/target/e2e-bridge.classpath")"
fi

[ -f "${classes}/org/cometgui/app/e2e/bridge/BridgeAgent.class" ] \
    || die "${classes} holds no compiled org/cometgui/app/e2e/bridge/BridgeAgent.class"
[ -f "${classes}/org/cometgui/app/uidriver/RobotFxUiDriver.class" ] \
    || die "${classes} holds no compiled org/cometgui/app/uidriver/RobotFxUiDriver.class"

# JUnit's jars, exactly one of each, from the class path.
declare -A wanted=([junit-jupiter-api]="" [opentest4j]="" [apiguardian-api]="")
IFS=':' read -r -a entries <<< "${class_path}"
for entry in "${entries[@]}"; do
    name="$(basename -- "${entry}")"
    for artefact in "${!wanted[@]}"; do
        if [[ "${name}" =~ ^${artefact}-[0-9][^/]*\.jar$ ]]; then
            [ -z "${wanted[${artefact}]}" ] || die "two ${artefact} jars on the class path: ${wanted[${artefact}]} and ${entry}"
            wanted[${artefact}]="${entry}"
        fi
    done
done
for artefact in "${!wanted[@]}"; do
    [ -n "${wanted[${artefact}]}" ] && [ -f "${wanted[${artefact}]}" ] \
        || die "no ${artefact} jar on the class path (the driver's assertions need it)"
done

# Stage: the two packages' classes, minus tests and the TestFX driver; then
# JUnit's classes, without their manifests, signatures or module descriptors.
stage="${work}/stage"
mkdir -p -- "${stage}"
copied=0
for package in org/cometgui/app/e2e/bridge org/cometgui/app/uidriver; do
    mkdir -p -- "${stage}/${package}"
    for class in "${classes}/${package}"/*.class; do
        base="$(basename -- "${class}" .class)"
        top="${base%%\$*}"
        case "${top}" in *Test|TestFxUiDriver) continue ;; esac
        cp -- "${class}" "${stage}/${package}/"
        copied=$((copied + 1))
    done
done
for artefact in junit-jupiter-api opentest4j apiguardian-api; do
    (cd -- "${stage}" && jar --extract --file "${wanted[${artefact}]}")
done
rm -rf -- "${stage}/META-INF"
find "${stage}" -name 'module-info.class' -delete

cat > "${work}/manifest.txt" <<EOF
Premain-Class: ${AGENT_CLASS}
Can-Redefine-Classes: false
Can-Retransform-Classes: false
Implementation-Title: CometGUI end-to-end test bridge -- TEST ONLY, never shipped (R-TEST-06)
EOF
rm -f -- "${out}"
jar --create --file "${out}" --manifest "${work}/manifest.txt" -C "${stage}" .

# What was written.
listing="${work}/listing.txt"
jar --list --file "${out}" > "${listing}"
for required in org/cometgui/app/e2e/bridge/BridgeAgent.class org/cometgui/app/e2e/bridge/BridgeServer.class \
    org/cometgui/app/e2e/bridge/BridgeDialogs.class org/cometgui/app/e2e/bridge/DialogsTransformer.class \
    org/cometgui/app/uidriver/RobotFxUiDriver.class org/cometgui/app/uidriver/RunningApplication.class \
    org/cometgui/app/uidriver/Await.class org/junit/jupiter/api/Assertions.class \
    org/opentest4j/AssertionFailedError.class; do
    grep -qx -- "${required}" "${listing}" || die "${out} lacks ${required}"
done
if grep -E '^org/testfx/|TestFxUiDriver|^org/cometgui/.*Test(\.class$|\$)' "${listing}" > "${work}/unwanted.txt"; then
    die "${out} holds test or TestFX classes: $(sed -n '1,5p' "${work}/unwanted.txt" | paste -sd' ' -)"
fi
others="$(grep '\.class$' "${listing}" | grep -Ev '^(org/cometgui/app/e2e/bridge/|org/cometgui/app/uidriver/|org/junit/jupiter/api/|org/opentest4j/|org/apiguardian/)' || true)"
[ -z "${others}" ] || die "${out} holds classes outside its five packages: $(printf '%s\n' "${others}" | sed -n '1,5p' | paste -sd' ' -)"
(cd -- "${work}" && jar --extract --file "${out}" META-INF/MANIFEST.MF)
grep -qx "Premain-Class: ${AGENT_CLASS}" <(tr -d '\r' < "${work}/META-INF/MANIFEST.MF") \
    || die "${out}'s manifest does not name ${AGENT_CLASS} as its Premain-Class"

bridge_classes="$(grep -c '^org/cometgui/app/e2e/bridge/.*\.class$' "${listing}" || true)"
driver_classes="$(grep -c '^org/cometgui/app/uidriver/.*\.class$' "${listing}" || true)"
junit_classes="$(grep -Ec '^org/(junit|opentest4j|apiguardian)/.*\.class$' "${listing}" || true)"
echo "build-bridge.sh: PASSED -- ${out} ($(stat -c %s "${out}") bytes): ${bridge_classes} bridge classes, ${driver_classes} driver classes, ${junit_classes} JUnit/opentest4j/apiguardian classes (${copied} copied from ${classes}); Premain-Class ${AGENT_CLASS}"

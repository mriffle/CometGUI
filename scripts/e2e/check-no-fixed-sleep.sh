#!/usr/bin/env bash
#
# check-no-fixed-sleep.sh -- Phase 14 exit-gate item 6: "No test uses a fixed
# sleep; all waits are on observable state."
#
# It reads the COMPILED end-to-end classes (org.cometgui.app.e2e..) and the
# driver classes they run (org.cometgui.app.uidriver..), plus every test class of
# cometgui-app they reference, with javap from the project JDK -- so a static
# import, a method reference or a helper cannot hide a call -- and scans their
# sources too. It fails on Thread.sleep, TimeUnit.*.sleep, LockSupport.park*,
# Object.wait, a timed Thread.join, TestFX's sleeps and WaitForAsyncUtils, and the timed-delay
# equivalents (Timer.schedule, a scheduled executor, CompletableFuture's
# delayedExecutor, a PauseTransition). The one timed wait permitted is the poll
# of the single wait primitive -- org.cometgui.app.uidriver.Await.pause --
# matched by exact class AND method, never by file (design decision P14-8).
#
# Usage:
#   bash scripts/e2e/check-no-fixed-sleep.sh             check the real tree
#   bash scripts/e2e/check-no-fixed-sleep.sh --classes DIR --sources DIR
#                                                        check another copy
#   bash scripts/e2e/check-no-fixed-sleep.sh --self-test inject each form into
#                                                        copies and require it
#                                                        rejected, then check
#                                                        the real tree
#   bash scripts/e2e/check-no-fixed-sleep.sh --help
#
# Needs compiled test classes: mvn -B -o -pl cometgui-app test-compile (a class
# missing or older than its source is exit 2, never a pass).
#
# Exit status:
#   0  no fixed wait outside Await.pause (the self-test: every control caught
#      and the real tree clean)
#   1  at least one found, each named: class, method, call (the self-test: a
#      control was not injected, not caught, or caught with the wrong
#      diagnostic -- a harness failure, never a pass)
#   2  misuse: bad arguments, no javap or python, missing or stale classes, or a
#      scan that did not see the permitted wait it knows is there

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCANNER="${ROOT}/scripts/e2e/no_fixed_sleep.py"
SELFTEST_DIR="${ROOT}/_build/e2e-no-sleep-selftest"

usage() {
    sed -n '3,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

misuse() {
    printf 'check-no-fixed-sleep: %s\n' "$1" >&2
    exit 2
}

toolchain() {
    if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/javap" ]]; then
        # shellcheck disable=SC1091
        . "${ROOT}/tools/env.sh" >/dev/null 2>&1 || misuse "cannot source tools/env.sh"
    fi
    [[ -x "${JAVA_HOME:-}/bin/javap" ]] || misuse "no javap under JAVA_HOME=${JAVA_HOME:-unset}"
    JAVAP="${JAVA_HOME}/bin/javap"
    JAVAC="${JAVA_HOME}/bin/javac"
    PYTHON="${ROOT}/.venv/bin/python"
    [[ -x "${PYTHON}" ]] || PYTHON="$(command -v python3 || true)"
    [[ -n "${PYTHON}" && -x "${PYTHON}" ]] || misuse "no python (.venv/bin/python or python3)"
}

check() { # classes sources
    "${PYTHON}" "${SCANNER}" --classes "$1" --sources "$2" --javap "${JAVAP}"
}

# ------------------------------------------------------------------ self-test --

FAILURES=0
CONTROLS=0

harness_failure() {
    printf '  HARNESS FAILURE: %s\n' "$1"
    FAILURES=$(( FAILURES + 1 ))
}

# A fresh copy of the real classes and sources, with modification times kept so
# the copy is exactly as fresh as the tree it came from.
fresh_copy() { # name -> sets COPY
    COPY="${SELFTEST_DIR}/$1"
    rm -rf -- "${COPY}"
    mkdir -p -- "${COPY}"
    cp -a -- "${REAL_CLASSES}" "${COPY}/classes"
    cp -a -- "${REAL_SOURCES}" "${COPY}/sources"
}

# Writes a source file into the copy and compiles it into the copy's classes.
inject() { # relative-source-path content [extra classpath]
    local file="${COPY}/sources/$1"
    mkdir -p -- "$(dirname -- "${file}")"
    printf '%s\n' "$2" > "${file}"
    "${JAVAC}" -nowarn -d "${COPY}/classes" -cp "${COPY}/classes${3:+:$3}" \
        -sourcepath "${COPY}/sources" "${file}" > "${COPY}/javac.log" 2>&1 \
        || { cat "${COPY}/javac.log"; harness_failure "the injected $1 did not compile"; return 1; }
}

# Requires the injected call to be in the compiled class (or, for a source-only
# control, absent from it), so that a control whose defect never landed is a
# harness failure rather than a pass.
require_bytecode() { # class-relative-path grep-pattern present|absent
    local text
    text="$("${JAVAP}" -v -p -c "${COPY}/classes/$1.class" 2>&1)"
    if [[ "$3" == present ]] && ! grep -q -- "$2" <<<"${text}"; then
        harness_failure "the injection is not in $1.class (no '$2' in its bytecode)"
        return 1
    fi
    if [[ "$3" == absent ]] && grep -q -- "$2" <<<"${text}"; then
        harness_failure "the source-only control's '$2' reached $1.class"
        return 1
    fi
}

# Runs the check on the copy and requires the exit status and a diagnostic.
expect() { # name status diagnostic
    CONTROLS=$(( CONTROLS + 1 ))
    local output status
    set +e
    output="$(check "${COPY}/classes" "${COPY}/sources" 2>&1)"
    status=$?
    set -e
    if [[ "${status}" -ne "$2" ]]; then
        printf '%s\n' "${output}" | sed 's/^/    | /'
        harness_failure "control '$1': exit ${status}, expected $2"
        return 0
    fi
    if ! grep -qF -- "$3" <<<"${output}"; then
        printf '%s\n' "${output}" | sed 's/^/    | /'
        harness_failure "control '$1': exit $2 but no diagnostic containing: $3"
        return 0
    fi
    printf '  ok  %-44s exit %s, says: %s\n' "$1" "${status}" "$(grep -F -- "$3" <<<"${output}" | head -1 | cut -c1-150)"
}

scenario() { # class-name body [imports]
    printf 'package org.cometgui.app.e2e.scenario;\n%s\npublic final class %s {\n%s\n}\n' "${3:-}" "$1" "$2"
}

self_test() {
    REAL_CLASSES="${ROOT}/cometgui-app/target/test-classes"
    REAL_SOURCES="${ROOT}/cometgui-app/src/test/java"
    [[ -d "${REAL_CLASSES}/org/cometgui/app/e2e" ]] \
        || misuse "no compiled end-to-end classes -- run: mvn -B -o -pl cometgui-app test-compile"
    local testfx
    testfx="$(find "${ROOT}/_build/m2repo/org/testfx/testfx-core" -name 'testfx-core-*.jar' \
        ! -name '*sources*' 2>/dev/null | head -1)"
    [[ -n "${testfx}" ]] || misuse "no testfx-core jar under _build/m2repo"
    rm -rf -- "${SELFTEST_DIR}"
    mkdir -p -- "${SELFTEST_DIR}"
    echo "check-no-fixed-sleep --self-test: each control is a copy under ${SELFTEST_DIR#"${ROOT}"/}"

    fresh_copy baseline
    expect "baseline: an unmodified copy" 0 "PASSED"

    fresh_copy thread-sleep
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    void waitForTheRun() throws InterruptedException { Thread.sleep(5000); }')" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'java/lang/Thread.sleep' present \
        && expect "Thread.sleep in a scenario class" 1 \
            "FOUND in bytecode: org.cometgui.app.e2e.scenario.InjectedScenario.waitForTheRun calls invokestatic java/lang/Thread.sleep:(J)V"

    fresh_copy timeunit-sleep
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    void settle() throws InterruptedException { TimeUnit.SECONDS.sleep(2); }' \
        'import java.util.concurrent.TimeUnit;')" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'java/util/concurrent/TimeUnit.sleep' present \
        && expect "TimeUnit.SECONDS.sleep" 1 \
            "FOUND in bytecode: org.cometgui.app.e2e.scenario.InjectedScenario.settle calls invokevirtual java/util/concurrent/TimeUnit.sleep:(J)V"

    fresh_copy static-import
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    void quietly() throws InterruptedException { sleep(100); }' \
        'import static java.lang.Thread.sleep;')" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'java/lang/Thread.sleep' present \
        && expect "a statically imported sleep" 1 \
            "FOUND in bytecode: org.cometgui.app.e2e.scenario.InjectedScenario.quietly calls invokestatic java/lang/Thread.sleep:(J)V"

    fresh_copy method-reference
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    interface Napper { void nap(long millis) throws InterruptedException; }
    void later() throws InterruptedException { Napper napper = Thread::sleep; napper.nap(10); }')" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'REF_invokeStatic java/lang/Thread.sleep' present \
        && expect "a method reference Thread::sleep" 1 \
            "FOUND in bytecode: org.cometgui.app.e2e.scenario.InjectedScenario.later calls invokedynamic (method reference) REF_invokeStatic java/lang/Thread.sleep:(J)V"

    fresh_copy timed-join
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    void idle() throws InterruptedException { Thread.currentThread().join(200); }')" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'java/lang/Thread.join' present \
        && expect "a timed Thread.join on the current thread" 1 \
            "FOUND in bytecode: org.cometgui.app.e2e.scenario.InjectedScenario.idle calls invokevirtual java/lang/Thread.join:(J)V"

    fresh_copy park-nanos
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    void spin() { LockSupport.parkNanos(1_000_000L); }' \
        'import java.util.concurrent.locks.LockSupport;')" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'java/util/concurrent/locks/LockSupport.parkNanos' present \
        && expect "LockSupport.parkNanos" 1 \
            "FOUND in bytecode: org.cometgui.app.e2e.scenario.InjectedScenario.spin calls invokestatic java/util/concurrent/locks/LockSupport.parkNanos:(J)V"

    fresh_copy object-wait
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    private final Object lock = new Object();
    void hold() throws InterruptedException { synchronized (lock) { lock.wait(250); } }')" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'java/lang/Object.wait' present \
        && expect "Object.wait(250)" 1 \
            "FOUND in bytecode: org.cometgui.app.e2e.scenario.InjectedScenario.hold calls invokevirtual java/lang/Object.wait:(J)V"

    fresh_copy testfx-sleep
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    void letTestFxWait() { WaitForAsyncUtils.sleep(1, TimeUnit.SECONDS); }' \
        'import java.util.concurrent.TimeUnit;
import org.testfx.util.WaitForAsyncUtils;')" "${testfx}" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'org/testfx/util/WaitForAsyncUtils.sleep' present \
        && expect "TestFX's WaitForAsyncUtils.sleep" 1 \
            "FOUND in bytecode: org.cometgui.app.e2e.scenario.InjectedScenario.letTestFxWait calls invokestatic org/testfx/util/WaitForAsyncUtils.sleep:(JLjava/util/concurrent/TimeUnit;)V"

    fresh_copy helper-sleep
    inject org/cometgui/app/testing/InjectedHelper.java \
        'package org.cometgui.app.testing;
public final class InjectedHelper {
    public static void breather() throws InterruptedException { Thread.sleep(300); }
}' \
        && inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
            '    void viaAHelper() throws InterruptedException { InjectedHelper.breather(); }' \
            'import org.cometgui.app.testing.InjectedHelper;')" \
        && require_bytecode org/cometgui/app/testing/InjectedHelper 'java/lang/Thread.sleep' present \
        && expect "a sleep in a test helper a scenario calls" 1 \
            "FOUND in bytecode: org.cometgui.app.testing.InjectedHelper.breather calls invokestatic java/lang/Thread.sleep:(J)V"

    fresh_copy await-second-sleep
    local await_source="${COPY}/sources/org/cometgui/app/uidriver/Await.java"
    local injured
    injured="$(sed 's|^    private Await() {}|    private Await() {}\n\n    static void nap() throws InterruptedException {\n        Thread.sleep(50);\n    }|' "${await_source}")"
    inject org/cometgui/app/uidriver/Await.java "${injured}" \
        && require_bytecode org/cometgui/app/uidriver/Await 'nap()' present \
        && expect "a second sleep in Await, outside pause" 1 \
            "FOUND in bytecode: org.cometgui.app.uidriver.Await.nap calls invokestatic java/lang/Thread.sleep:(J)V"

    fresh_copy source-only
    inject org/cometgui/app/e2e/scenario/InjectedScenario.java "$(scenario InjectedScenario \
        '    void deadCode() throws InterruptedException { if (false) { Thread.sleep(1000); } }')" \
        && require_bytecode org/cometgui/app/e2e/scenario/InjectedScenario 'java/lang/Thread.sleep' absent \
        && expect "a sleep the compiler removed (source scan)" 1 \
            "FOUND in source: org/cometgui/app/e2e/scenario/InjectedScenario.java:4: sleep("

    fresh_copy stale-class
    touch -- "${COPY}/sources/org/cometgui/app/uidriver/Await.java"
    touch -d '2000-01-01' -- "${COPY}/classes/org/cometgui/app/uidriver/Await.class"
    expect "a class older than its source (misuse)" 2 "is older than its source"

    COPY="${SELFTEST_DIR}/no-such-copy"
    expect "no compiled classes at all (misuse)" 2 "no compiled classes at"

    echo
    echo "check-no-fixed-sleep --self-test: ${CONTROLS} controls, ${FAILURES} harness failure(s)"
    echo "check-no-fixed-sleep --self-test: the real tree:"
    set +e
    check "${REAL_CLASSES}" "${REAL_SOURCES}"
    local real=$?
    set -e
    if [[ "${FAILURES}" -ne 0 ]]; then
        echo "check-no-fixed-sleep --self-test: FAILED -- a control was not caught as required"
        exit 1
    fi
    if [[ "${real}" -ne 0 ]]; then
        echo "check-no-fixed-sleep --self-test: FAILED -- the real tree does not pass (exit ${real})"
        exit 1
    fi
    echo "check-no-fixed-sleep --self-test: PASSED -- every one of the ${CONTROLS} controls caught, and the real tree clean"
}

# ----------------------------------------------------------------------- main --

CLASSES="${ROOT}/cometgui-app/target/test-classes"
SOURCES="${ROOT}/cometgui-app/src/test/java"
MODE=check
while [[ $# -gt 0 ]]; do
    case "$1" in
        --help|-h) usage; exit 0 ;;
        --self-test) MODE=self-test; shift ;;
        --classes) [[ $# -ge 2 ]] || misuse "--classes needs a directory"; CLASSES="$2"; shift 2 ;;
        --sources) [[ $# -ge 2 ]] || misuse "--sources needs a directory"; SOURCES="$2"; shift 2 ;;
        *) misuse "unknown argument: $1 (see --help)" ;;
    esac
done

toolchain
if [[ "${MODE}" == self-test ]]; then
    self_test
else
    check "${CLASSES}" "${SOURCES}"
fi

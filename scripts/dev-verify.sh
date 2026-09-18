#!/usr/bin/env bash
#
# CometGUI -- the INNER-LOOP check.  Runs only what the current change can
# actually break.
#
#   bash scripts/dev-verify.sh                  what changed vs HEAD
#   bash scripts/dev-verify.sh --since main     what changed vs a ref
#   bash scripts/dev-verify.sh --mutation       ... and PIT, incrementally
#   bash scripts/dev-verify.sh --help
#
# THIS IS NOT A GATE.  It exists because `scripts/build.sh` takes ~23 minutes,
# roughly 17 of them PIT, and running all of it after every edit spends wall
# clock re-proving code nobody touched.  It selects; selection is exactly how a
# check stops being reached, and a check that is never reached is as inert as
# one that can never go red.  So:
#
#   * a unit is signed off, and a phase gate is graded, by `scripts/build.sh`
#     and `scripts/verify-all-gates.sh` IN FULL.  Never by this script.
#   * every run ends with what it did NOT run, named.
#   * selecting nothing is a FAILURE here, not a pass.  A selective runner that
#     runs zero tests and exits 0 is this project's signature defect with a new
#     coat of paint, so this script proves that tests actually executed during
#     THIS run rather than reading the exit code.
#
# It changes no POM and no gate script.  The PIT knobs it uses -- withHistory,
# historyInputFile, threads -- are command-line properties of pitest-maven
# 1.30.0 (verified in the plugin descriptor), so the committed configuration is
# untouched and the gate run is unaffected.

set -Eeuo pipefail

readonly SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly M2REPO="${ROOT}/_build/m2repo"
readonly WORKDIR="${ROOT}/_build/dev-verify"

# Modules whose POM switches the mutation gate on.  Kept in step with the
# `cometgui.mutation.skip` property; verified against the POMs at run time
# rather than trusted, because a stale list here would silently skip PIT.
readonly MUTATION_MODULES=(
    cometgui-domain
    cometgui-provenance
    cometgui-process
    cometgui-tools
    cometgui-install
    cometgui-workflow
)

SINCE=""
FORCED_MODULES=()
RUN_MUTATION=0
PIT_THREADS=""
MVN_GOAL="test"
FORCE=0
DRY_RUN=0

die() { printf '\nFATAL: %s\n' "$*" >&2; exit 1; }
note() { printf '   %s\n' "$*"; }
banner() {
    printf '\n---------------------------------------------------------------\n'
    printf ' %s\n' "$*"
    printf -- '---------------------------------------------------------------\n'
}

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- run only what the current change can break.

  --since <ref>     compare against <ref> (default: the working tree vs HEAD)
  --module <name>   force a module into the set (repeatable); skips detection
  --mutation        also run PIT over the affected mutation-gated modules,
                    incrementally, using a history file under _build/
  --threads <n>     PIT threads (default: half the cores, capped at 32)
  --verify          run 'verify' instead of 'test' (adds failsafe and JaCoCo
                    checks for the selected modules)
  --dry-run         print the selection and what would be skipped, run nothing
                    (works while another build is live, which is when you most
                    need to know what this would do)
  --force           run even while another Maven build is in progress
  -h, --help        this message

NOT a gate.  Unit sign-off and the phase gate run scripts/build.sh and
scripts/verify-all-gates.sh in full.
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --since)   SINCE="${2:-}"; [ -n "${SINCE}" ] || die "--since needs a ref"; shift 2 ;;
        --module)  FORCED_MODULES+=("${2:-}"); [ -n "${2:-}" ] || die "--module needs a name"; shift 2 ;;
        --mutation) RUN_MUTATION=1; shift ;;
        --threads) PIT_THREADS="${2:-}"; shift 2 ;;
        --verify)  MVN_GOAL="verify"; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        --force)   FORCE=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "unknown option '$1' (try --help)" ;;
    esac
done

# ------------------------------------------------- do not collide with a build
# This project has paid for concurrent work in the same tree more than once.
# A second Maven writing the same target/ directories does not usually fail --
# it produces a number nobody can trust, which is worse.
#
# Two things this guard got wrong when it was first written, both found by
# running it rather than by reading it:
#   * `build\.sh` also matches `docs-build.sh`, so the docs gate looked like a
#     build. The pattern is anchored on the path now.
#   * it matched the shell that invoked it, and any stale shell whose command
#     line merely MENTIONS the script. Own PID and every ancestor are excluded.
build_in_flight() {
    local -a ancestry=()
    local p="$$" ppid
    while [ -n "${p}" ] && [ "${p}" -gt 1 ] 2>/dev/null; do
        ancestry+=("${p}")
        ppid="$(awk '{print $4}' "/proc/${p}/stat" 2>/dev/null || true)"
        [ -n "${ppid}" ] || break
        p="${ppid}"
    done

    # Match on what is EXECUTING, argument by argument -- never on a substring
    # of the whole command line. A shell that once ran `grep ... scripts/build.sh`
    # keeps that text in its own argv and looked like a running build; so did
    # anything mentioning docs-build.sh. Both were false positives found by
    # running this, and a guard that cries wolf is a guard someone switches off.
    local d pid arg matched
    for d in /proc/[0-9]*; do
        pid="${d#/proc/}"
        [ "${pid}" = "$$" ] && continue
        matched=0
        for a in "${ancestry[@]}"; do [ "${pid}" = "${a}" ] && matched=-1; done
        [ "${matched}" -eq -1 ] && continue
        [ -r "${d}/cmdline" ] || continue
        while IFS= read -r -d '' arg; do
            case "${arg}" in
                org.codehaus.plexus.classworlds.launcher.Launcher) matched=1; break ;;
                */scripts/build.sh|scripts/build.sh)               matched=1; break ;;
                # The gate harnesses run Maven inside sandboxes under _build/
                # and share _build/m2repo. This guard missed them on
                # 2026-09-18: it called the tree quiet at 01:26:08 while
                # verify-all-gates.sh still had 173 seconds to run, and a
                # timing was taken across the overlap and had to be thrown
                # away. A suite that builds is a build.
                */scripts/verify-all-gates.sh|scripts/verify-all-gates.sh) matched=1; break ;;
                */scripts/verify-*-gates.sh|scripts/verify-*-gates.sh)     matched=1; break ;;
            esac
        done < "${d}/cmdline" 2>/dev/null
        [ "${matched}" -eq 1 ] || continue
        printf '%s %s\n' "${pid}" "$(tr '\0' ' ' < "${d}/cmdline" 2>/dev/null)"
    done
}

if [ "${FORCE}" -eq 0 ] && [ "${DRY_RUN}" -eq 0 ]; then
    IN_FLIGHT="$(build_in_flight)"
    if [ -n "${IN_FLIGHT}" ]; then
        printf '\n' >&2
        printf 'REFUSING TO RUN: another Maven build is in progress in this tree.\n' >&2
        printf 'Two builds sharing target/ produce a figure nobody can trust, which is\n' >&2
        printf 'worse than a slow one. In flight right now:\n\n' >&2
        printf '%s\n' "${IN_FLIGHT}" | cut -c1-140 | sed 's/^/    /' >&2
        printf '\nWait for it, or --force if you know it is unrelated.\n' >&2
        exit 2
    fi
fi

[ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run scripts/build.sh once to bootstrap the toolchain."
# shellcheck source=/dev/null
. "${ROOT}/tools/env.sh"
command -v mvn >/dev/null 2>&1 || die "no mvn on PATH after sourcing tools/env.sh"

mkdir -p -- "${WORKDIR}"

# --------------------------------------------------------- what changed, then
banner "What changed"

changed_files() {
    if [ -n "${SINCE}" ]; then
        git -C "${ROOT}" diff --name-only "${SINCE}...HEAD"
        git -C "${ROOT}" diff --name-only "${SINCE}"
    else
        git -C "${ROOT}" diff --name-only HEAD
        git -C "${ROOT}" ls-files --others --exclude-standard
    fi
}

mapfile -t CHANGED < <(changed_files | sed '/^$/d' | sort -u)

if [ "${#CHANGED[@]}" -eq 0 ] && [ "${#FORCED_MODULES[@]}" -eq 0 ]; then
    note "nothing changed against ${SINCE:-HEAD}, and no --module was forced."
    note "Nothing to run. This is not a pass: there was no change to grade."
    exit 0
fi

printf '   %s file(s) changed against %s:\n' "${#CHANGED[@]}" "${SINCE:-HEAD (working tree)}"
printf '     %s\n' "${CHANGED[@]:0:40}"
[ "${#CHANGED[@]}" -gt 40 ] && printf '     ... and %s more\n' "$(( ${#CHANGED[@]} - 40 ))"

# --------------------------------------------------------------- classify them
declare -a JAVA_MODULES=() DOC_FILES=() CI_SCRIPTS=() SHELL_SCRIPTS=() OTHER=()
POM_ROOT_CHANGED=0

for f in "${CHANGED[@]:-}"; do
    case "${f}" in
        pom.xml)
            POM_ROOT_CHANGED=1 ;;
        cometgui-*/*)
            m="${f%%/*}"
            case "${f}" in
                *.java|*/pom.xml|*/src/*) JAVA_MODULES+=("${m}") ;;
                *) OTHER+=("${f}") ;;
            esac ;;
        *.rst|docs/*|phases/*|handoffs/*)
            DOC_FILES+=("${f}") ;;
        scripts/ci/*)
            CI_SCRIPTS+=("${f}") ;;
        scripts/*.sh|scripts/*/*.sh)
            SHELL_SCRIPTS+=("${f}") ;;
        *)
            OTHER+=("${f}") ;;
    esac
done

for m in "${FORCED_MODULES[@]:-}"; do
    [ -d "${ROOT}/${m}" ] || die "--module ${m}: no such module directory"
    JAVA_MODULES+=("${m}")
done

mapfile -t JAVA_MODULES < <(printf '%s\n' "${JAVA_MODULES[@]:-}" | sed '/^$/d' | sort -u)

# --------------------------------------------------------------- route non-Java
banner "What that means"

RAN_SOMETHING=0
declare -a NOT_RUN=()

if [ "${#DOC_FILES[@]}" -gt 0 ]; then
    note "${#DOC_FILES[@]} documentation file(s) -> the strict Sphinx gate can go red for this."
fi
if [ "${#CI_SCRIPTS[@]}" -gt 0 ]; then
    note "${#CI_SCRIPTS[@]} CI script(s) changed. Run that script's own --self-test:"
    printf '     %s\n' "${CI_SCRIPTS[@]}"
    NOT_RUN+=("the CI scripts' own self-tests -- this script does not guess their flags")
fi
if [ "${#SHELL_SCRIPTS[@]}" -gt 0 ]; then
    note "${#SHELL_SCRIPTS[@]} shell script(s) changed -> scripts/verify-shell-gates.sh"
    NOT_RUN+=("scripts/verify-shell-gates.sh")
fi
if [ "${#OTHER[@]}" -gt 0 ]; then
    note "${#OTHER[@]} file(s) this script does not classify; it makes no claim about them:"
    printf '     %s\n' "${OTHER[@]:0:10}"
    NOT_RUN+=("anything implied by the unclassified files above")
fi

if [ "${POM_ROOT_CHANGED}" -eq 1 ]; then
    note "THE ROOT pom.xml CHANGED. Every module inherits it, so the blast radius"
    note "is the whole reactor and selective testing cannot be justified."
    note "Run: bash scripts/build.sh"
    exit 3
fi

# Documentation-only change: run the gate that can actually go red, not Maven.
if [ "${#JAVA_MODULES[@]}" -eq 0 ]; then
    if [ "${#DOC_FILES[@]}" -gt 0 ]; then
        banner "Documentation gate (no Java changed, so no Maven)"
        if [ "${DRY_RUN}" -eq 1 ]; then
            note "WOULD RUN: bash scripts/ci/docs-build.sh"
        else
            bash "${ROOT}/scripts/ci/docs-build.sh"
            RAN_SOMETHING=1
        fi
        NOT_RUN+=("the Maven build -- no Java or module POM changed")
    else
        note "No Java and no documentation changed. Nothing here to run."
        NOT_RUN+=("the Maven build and the documentation gate -- neither is implicated")
    fi
else
    # ------------------------------------------------------------ the Maven run
    banner "Maven: the changed modules, their dependencies and their dependents"
    printf '   changed:  %s\n' "${JAVA_MODULES[*]}"
    printf '   selector: -pl %s -am -amd  (deps AND dependents: a change here can\n' "$(IFS=,; echo "${JAVA_MODULES[*]}")"
    printf '             break anything downstream, and this says so rather than hiding it)\n'
    printf '   goal:     %s, with no `clean` -- unchanged sources are not recompiled\n' "${MVN_GOAL}"

    if [ "${DRY_RUN}" -eq 1 ]; then
        note "WOULD RUN: mvn -B -o -pl $(IFS=,; echo "${JAVA_MODULES[*]}") -am -amd ${MVN_GOAL}"
        [ "${RUN_MUTATION}" -eq 1 ] && note "WOULD RUN: PIT with a history file, over the mutation-gated subset"
        NOT_RUN+=("everything -- this was a --dry-run")
        banner "What this run did NOT do"
        NOT_RUN+=("Spotless, Checkstyle and SpotBugs")
        NOT_RUN+=("ArchUnit layering rules and the per-class census")
        NOT_RUN+=("JaCoCo's population audit")
        NOT_RUN+=("scripts/verify-all-gates.sh -- all 10 controls")
        printf '   %s\n' "${NOT_RUN[@]}"
        printf '\n   %s is not a gate and this was not a sign-off.\n\n' "${SCRIPT_NAME}"
        exit 0
    fi

    STARTED_AT="$(date +%s)"
    # Marker file: mtime comparisons below are against this, not against the
    # clock, so a filesystem with coarse timestamps cannot fake freshness.
    touch -- "${WORKDIR}/.run-started"

    set +e
    mvn -B -o -Dmaven.repo.local="${M2REPO}" \
        -pl "$(IFS=,; echo "${JAVA_MODULES[*]}")" -am -amd \
        "${MVN_GOAL}" 2>&1 | tee "${WORKDIR}/maven.log"
    MVN_STATUS="${PIPESTATUS[0]}"
    set -e

    # ---- exit code 0 proves nothing: prove tests RAN, and ran in THIS run ----
    banner "Did anything actually run?"
    fresh_reports=0 fresh_tests=0 fresh_failures=0
    while IFS= read -r report; do
        fresh_reports=$(( fresh_reports + 1 ))
        counts="$(sed -n 's/.*tests="\([0-9]*\)".*failures="\([0-9]*\)".*errors="\([0-9]*\)".*/\1 \2 \3/p' "${report}" | head -1)"
        [ -n "${counts}" ] || continue
        fresh_tests=$(( fresh_tests + $(echo "${counts}" | cut -d' ' -f1) ))
        fresh_failures=$(( fresh_failures + $(echo "${counts}" | cut -d' ' -f2) + $(echo "${counts}" | cut -d' ' -f3) ))
    done < <(find "${ROOT}" -path "${ROOT}/_build" -prune -o \
                  -path '*/target/surefire-reports/TEST-*.xml' -newer "${WORKDIR}/.run-started" -print 2>/dev/null)

    printf '   %s surefire report(s) written during this run, %s test(s), %s failure(s)/error(s)\n' \
        "${fresh_reports}" "${fresh_tests}" "${fresh_failures}"

    if [ "${MVN_STATUS}" -ne 0 ]; then
        printf '\n   Maven exited %s. Full log: %s\n' "${MVN_STATUS}" "${WORKDIR}/maven.log"
        exit "${MVN_STATUS}"
    fi
    if [ "${fresh_tests}" -eq 0 ]; then
        printf '\n'
        printf 'FAILED: Maven exited 0 having run ZERO tests in this run.\n' >&2
        printf 'A selective runner that selects nothing and reports success is exactly the\n' >&2
        printf 'defect this project keeps finding. Treat this as red: check the module\n' >&2
        printf 'selection above, then fall back to scripts/build.sh.\n' >&2
        exit 4
    fi
    RAN_SOMETHING=1
    printf '   elapsed: %ss\n' "$(( $(date +%s) - STARTED_AT ))"

    # ------------------------------------------------------------ optional PIT
    if [ "${RUN_MUTATION}" -eq 1 ]; then
        banner "PIT, incrementally, over the affected mutation-gated modules"
        declare -a PIT_MODULES=()
        for m in "${JAVA_MODULES[@]}"; do
            for g in "${MUTATION_MODULES[@]}"; do
                if [ "${m}" = "${g}" ]; then
                    # Verify against the POM rather than trusting the list above.
                    if grep -q '<cometgui.mutation.skip>false</cometgui.mutation.skip>' "${ROOT}/${m}/pom.xml"; then
                        PIT_MODULES+=("${m}")
                    else
                        note "${m} is in this script's list but its POM no longer switches mutation on; skipping and saying so."
                    fi
                fi
            done
        done

        if [ "${#PIT_MODULES[@]}" -eq 0 ]; then
            note "none of the changed modules is mutation-gated; PIT not run."
            NOT_RUN+=("PIT -- no changed module switches the mutation gate on")
        else
            if [ -z "${PIT_THREADS}" ]; then
                cores="$(nproc 2>/dev/null || echo 4)"
                PIT_THREADS=$(( cores / 2 )); [ "${PIT_THREADS}" -gt 32 ] && PIT_THREADS=32
                [ "${PIT_THREADS}" -lt 1 ] && PIT_THREADS=1
            fi
            mkdir -p -- "${WORKDIR}/pit-history"
            for m in "${PIT_MODULES[@]}"; do
                hist="${WORKDIR}/pit-history/${m}.history"
                printf '\n   %s  (threads=%s, history=%s)\n' "${m}" "${PIT_THREADS}" \
                    "$( [ -f "${hist}" ] && echo "reusing $(wc -c < "${hist}") bytes" || echo "none yet -- this run is full" )"
                mvn -B -o -Dmaven.repo.local="${M2REPO}" -pl "${m}" \
                    -Dcometgui.pit.threads="${PIT_THREADS}" \
                    -DhistoryInputFile="${hist}" -DhistoryOutputFile="${hist}" \
                    test-compile org.pitest:pitest-maven:mutationCoverage \
                    > "${WORKDIR}/pit-${m}.log" 2>&1 \
                    || { sed -n '/ERROR/p' "${WORKDIR}/pit-${m}.log" | head -20
                         die "PIT failed in ${m}. Full log: ${WORKDIR}/pit-${m}.log"; }
                xml="${ROOT}/${m}/target/pit-reports/mutations.xml"
                [ -f "${xml}" ] || die "PIT exited 0 in ${m} but wrote no mutations.xml."
                total="$(grep -c '<mutation ' "${xml}" || true)"
                killed="$(grep -c "status='KILLED'" "${xml}" || true)"
                [ "${total}" -gt 0 ] || die "PIT generated 0 mutations in ${m} and exited 0. That is a vacuous pass."
                printf '     %s/%s killed\n' "${killed}" "${total}"
            done
            NOT_RUN+=("PIT in every module except: ${PIT_MODULES[*]}")
            NOT_RUN+=("a HISTORY-FREE PIT run -- the incremental result is an inner-loop
       signal; the gate run in scripts/build.sh uses no history file")
        fi
    else
        NOT_RUN+=("PIT entirely (no --mutation). It is ~17 of build.sh's ~23 minutes.")
    fi
fi

# ------------------------------------------------------------------- the truth
banner "What this run did NOT do"
NOT_RUN+=("Spotless, Checkstyle and SpotBugs")
NOT_RUN+=("ArchUnit layering rules and the per-class census")
NOT_RUN+=("JaCoCo's population audit -- the shape re-running a gate cannot catch")
NOT_RUN+=("the real-tool integration tests, the SBOM and the dependency scan")
NOT_RUN+=("scripts/verify-all-gates.sh -- all 10 controls")
printf '   %s\n' "${NOT_RUN[@]}"

printf '\n'
printf '   %s is not a gate and this was not a sign-off.\n' "${SCRIPT_NAME}"
printf '   A unit is signed off, and a phase gate is graded, by:\n'
printf '       bash scripts/build.sh && bash scripts/verify-all-gates.sh\n'
printf '   run in full, on a quiet tree.\n\n'

[ "${RAN_SOMETHING}" -eq 1 ] || exit 0

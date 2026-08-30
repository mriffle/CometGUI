#!/usr/bin/env python3
"""Generate the 400-PSM synthetic PIN used as Percolator's input.

Phase 00 residue, work unit U1.  This file used to be a Python heredoc
embedded in ``scripts/feasibility/windows-artefact.sh``.  It was factored out
because a GitHub ``windows-latest`` runner needs the *same* input: a
cross-platform comparison whose two sides are fed different files proves
nothing, and two copies of a generator are two things that drift.  There is now
one generator, and both platforms call it.

The recorded Linux evidence in ``docs/feasibility/windows-artefact.rst``
(143 729 / 143 733-byte ``-X`` outputs, 200 ``<psm>`` elements) is evidence
about *this exact input*.  Do not "improve" the data, the column set, the seed,
the formatting or the row count: doing so silently invalidates that evidence.
The pinned SHA-256 below is the guard.

Usage::

    make_synthetic_pin.py OUTPUT [--rows N] [--seed N]
                          [--print-sha256] [--expect-sha256 HEX]
    make_synthetic_pin.py --rounding-margin

Standard library only, Python 3.9+, no third-party import: it has to run on a
bare Windows runner with nothing installed.

Exit status:
    0  the file was written (and matched --expect-sha256, if given)
    1  --expect-sha256 was given and the output did not match
    2  misuse
"""

from __future__ import annotations

import argparse
import hashlib
import random
import sys
from fractions import Fraction

# --------------------------------------------------------------------------
# The pinned identity of the default output.
#
# This is the SHA-256 of the file produced by `make_synthetic_pin.py OUT`
# with the default --rows 400 --seed 7, and it is byte-for-byte what the
# heredoc in windows-artefact.sh produced before this script replaced it
# (32 466 bytes, 401 lines).  It MUST NOT CHANGE.  Every number quoted in
# docs/feasibility/windows-artefact.rst about Percolator's behaviour was
# measured with this input; a different input makes those numbers describe an
# experiment nobody ran, and makes the Linux/Windows comparison the Windows CI
# job exists to perform meaningless.  If a change here is ever genuinely
# wanted, the evidence has to be re-measured on both platforms first.
# --------------------------------------------------------------------------
EXPECTED_SHA256 = "6643ede48534fcd28c90a1d4e53781e47ba39b0523e9f907ea8e1a63b15af61e"

DEFAULT_ROWS = 400
DEFAULT_SEED = 7

HEADER = ("SpecId\tLabel\tScanNr\tExpMass\tCalcMass"
          "\tfeat1\tfeat2\tfeat3\tPeptide\tProteins")
RESIDUES = "ACDEFGHIKLMNPQRSTVWY"


# --------------------------------------------------------------------------
# Cross-platform byte-identity.  Two distinct hazards; they are not the same
# hazard and they are not handled the same way.
#
# HAZARD (a) -- TEXT-MODE NEWLINE TRANSLATION.  A real defect, fixed here.
#     The heredoc this file replaces wrote its output with `open(path, "w")`.
#     On Windows that is text mode, and the C runtime rewrites every "\n" as
#     "\r\n".  The Windows PIN would therefore have been a *different file*
#     from the Linux PIN.  Measured: 32 867 bytes instead of 32 466 (401
#     added CR bytes) and SHA-256 9465459c... instead of 6643ede4...,
#     with nothing to announce it, and the whole point of the
#     cross-platform run is that both sides get identical input.  Percolator
#     would probably have parsed it anyway, which is what makes this the bad
#     kind of bug: it does not fail, it quietly moves the goalposts.
#     `newline="\n"` below disables the translation on every platform.  The
#     encoding is pinned to ASCII for the same reason: Windows would otherwise
#     pick a locale codepage, and ASCII additionally fails loudly if a
#     non-ASCII character ever enters the data.
#
# HAZARD (b) -- libm.  `random.gauss` calls log/sqrt/cos/sin, and the last bit
#     of those may differ between glibc and MSVC.  Nothing else is at risk:
#     the Mersenne Twister is exact integer arithmetic and platform
#     independent; `random.choice` over a 20-character string goes through
#     `_randbelow` -> `getrandbits`, also exact, so the peptides are
#     identical; `gauss` consumes a value-independent number of `random()`
#     calls (it caches the second variate of each pair), so the two platforms
#     never diverge in stream position; and CPython formats floats with its
#     own correctly-rounded dtoa, not the platform's printf, so `%.4f` of a
#     given double is the same everywhere.  Only the three float columns can
#     move, and only if a value sits within ~1 ULP of a `%.4f` rounding
#     boundary.
#
#     MEASURED, not assumed -- see rounding_margin() and `--rounding-margin`.
#     For all 1200 generated floats, the distance from the value to the
#     nearest `%.4f` rounding boundary was computed exactly (as a Fraction,
#     so the measurement itself introduces no float error) and expressed
#     relative to the value's magnitude.  Worst case, seed 7, 400 rows:
#
#         worst relative margin  1.8435426727402975e-09
#         at value               2.5073499953775933  (boundary 2.50735)
#         double precision eps   2.220446049250313e-16
#         ratio                  8.3e+06
#
#     The nearest any value comes to flipping is about eight million ULPs
#     away -- seven orders of magnitude of headroom over the 1-ULP disagreement
#     libm could plausibly produce.  Byte-identity across glibc and MSVC is
#     therefore safe for this seed and row count.  It is a property of *these*
#     numbers, not a general guarantee: re-run `--rounding-margin` if the seed
#     or row count ever changes, and treat a worst case near 1e-16 as a
#     design failure rather than something to round away.
#
#     The measurement bounds libm.  It does not bound a change of algorithm:
#     if a future CPython reimplements `random.gauss`, the output changes
#     wholesale.  That is what --expect-sha256 is for, and why the CI call
#     asserts it.
# --------------------------------------------------------------------------


def synthesise(rows=DEFAULT_ROWS, seed=DEFAULT_SEED):
    """Return (list_of_lines, list_of_floats) for a synthetic PIN.

    The body is a faithful transcription of the heredoc it replaces; the
    floats are handed back as well so rounding_margin() measures exactly the
    values that get printed, rather than a re-derivation of them.
    """
    rng = random.Random()
    rng.seed(seed)
    lines = [HEADER]
    floats = []
    for i in range(rows):
        tgt = i % 2 == 0
        f1 = rng.gauss(3.0 if tgt else 0.0, 1.0)
        f2 = rng.gauss(1.5 if tgt else 0.0, 1.0)
        f3 = rng.gauss(0.0, 1.0)
        pep = "K." + "".join(rng.choice(RESIDUES) for _ in range(9)) + ".R"
        prot = ("sp|P%05d|TEST" if tgt else "decoy_sp|P%05d|TEST") % i
        lines.append("psm%d\t%d\t%d\t1000.5\t1000.4\t%.4f\t%.4f\t%.4f\t%s\t%s"
                     % (i, 1 if tgt else -1, i, f1, f2, f3, pep, prot))
        floats += [f1, f2, f3]
    return lines, floats


def render(lines):
    """The exact byte content of the PIN, newline convention included."""
    return "\n".join(lines) + "\n"


def write_pin(path, text):
    """Write `text` with LF endings on every platform -- see HAZARD (a)."""
    with open(path, "w", newline="\n", encoding="ascii") as fh:
        fh.write(text)


def rounding_margin(floats):
    """Worst-case distance from a printed float to a `%.4f` boundary.

    `%.4f` rounds at the half-integers of value*10000.  A value's printed
    form can only flip if a perturbation carries it across the nearest such
    boundary, so the distance to that boundary -- taken relative to the
    value's magnitude, which is the scale a 1-ULP error lives on -- is the
    safety margin.  Everything is computed with Fraction, i.e. exactly on the
    double's true value, so the measurement cannot be an artefact of the
    arithmetic used to measure.

    Returns (relative_margin, value, absolute_distance).
    """
    worst = None
    for v in floats:
        if v == 0.0:
            continue                      # 5e-5 away from either boundary
        t = Fraction(v) * 10000            # exact, no rounding here
        k = round(t - Fraction(1, 2))      # nearest boundary is k + 1/2
        dist_t = abs(t - (Fraction(k) + Fraction(1, 2)))
        dist_v = dist_t / 10000
        rel = dist_v / abs(Fraction(v))
        if worst is None or rel < worst[0]:
            worst = (rel, v, dist_v)
    if worst is None:
        raise ValueError("no floats to measure")
    return float(worst[0]), worst[1], float(worst[2])


def report_margin(rows, seed):
    _, floats = synthesise(rows, seed)
    rel, value, dist = rounding_margin(floats)
    eps = sys.float_info.epsilon
    print("rounding-margin measurement (rows=%d, seed=%d)" % (rows, seed))
    print("  floats measured        : %d" % len(floats))
    print("  worst relative margin  : %r" % rel)
    print("  at value               : %r" % value)
    print("  absolute distance      : %r" % dist)
    print("  double precision eps   : %r" % eps)
    print("  ratio margin/eps       : %.3g" % (rel / eps))
    print("  verdict                : %s"
          % ("SAFE -- byte-identity across libm implementations"
             if rel / eps > 1e3 else
             "UNSAFE -- STOP, byte-identity is not safe, redesign"))


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="Generate the 400-PSM synthetic PIN (one generator, "
                    "both platforms).")
    ap.add_argument("output", nargs="?",
                    help="path to write the PIN to")
    ap.add_argument("--rows", type=int, default=DEFAULT_ROWS,
                    help="number of PSM rows (default %d)" % DEFAULT_ROWS)
    ap.add_argument("--seed", type=int, default=DEFAULT_SEED,
                    help="Mersenne Twister seed (default %d)" % DEFAULT_SEED)
    ap.add_argument("--print-sha256", action="store_true",
                    help="print the SHA-256, byte count and line count")
    ap.add_argument("--expect-sha256", metavar="HEX",
                    help="fail with a non-zero exit if the output's SHA-256 "
                         "is not HEX")
    ap.add_argument("--rounding-margin", action="store_true",
                    help="measure the %%.4f rounding margin and exit; see "
                         "HAZARD (b) in this file")
    args = ap.parse_args(argv)

    if args.rounding_margin:
        report_margin(args.rows, args.seed)
        if args.output is None:
            return 0
    if args.output is None:
        ap.error("an output path is required")
    if args.rows < 0:
        ap.error("--rows must not be negative")

    lines, _ = synthesise(args.rows, args.seed)
    text = render(lines)
    write_pin(args.output, text)

    data = text.encode("ascii")
    digest = hashlib.sha256(data).hexdigest()
    print("  wrote a %d-PSM synthetic PIN" % args.rows)
    if args.print_sha256:
        print("  sha256 %s  %d bytes  %d lines"
              % (digest, len(data), len(lines)))

    if args.expect_sha256 is not None:
        want = args.expect_sha256.strip().lower()
        if digest != want:
            sys.stderr.write(
                "make_synthetic_pin.py: SHA-256 mismatch for %s\n"
                "  expected %s\n"
                "  got      %s\n"
                "The generator has drifted from the input the recorded "
                "evidence was measured with.\n" % (args.output, want, digest))
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

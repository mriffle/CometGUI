#!/usr/bin/env python3
"""Decide, without executing it, whether a Percolator binary can emit XML.

Phase 00, work unit 3.  Written because the obvious static test is wrong.

Upstream builds every pre-3.09 release twice from one source tree: once with
``-DXML_SUPPORT=ON`` and once with the default ``OFF``, and publishes the
second under a ``noxml`` name.  The option guards the Xerces-C/XSD code, not
the option *parser*: the ``noxml`` binary still contains the literals
``xmloutput``, ``--decoy-xml-output``, ``pout.xml``, ``stdinput-xml`` and
``percolator_out.xsd``, because the flags are still declared -- they just
fail at run time.  Grepping for those strings therefore says "XML capable"
about a binary that is not, which is exactly the inference the Windows
artefact's capability currently rests on.

Two markers actually discriminate, and they point in opposite directions:

positive
    ``xercesc`` -- the mangled Xerces-C symbol namespace, present in the
    thousands in an ``XML_SUPPORT=ON`` build and absent from a ``noxml`` one,
    because Xerces is statically linked into the XML build.

negative
    ``Compiler flag XML_SUPPORT was off`` -- the diagnostic the ``noxml``
    build prints, present only in the ``noxml`` build.

A 3.09 binary has neither, and no XML option strings at all, because the code
was deleted rather than compiled out.

This is still static evidence.  It is strong enough to contradict a claim, and
it is *not* a substitute for running ``percolator --help`` on the target
platform.  The verdicts say which is which.

Scans raw bytes; does not use ``strings``, ``file``, or any host tool, and
never executes the binary under test.

Usage::

    python3 probe_xml_capability.py FILE [FILE ...]
    python3 probe_xml_capability.py --json FILE
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys

# (label, needle, meaning)
MARKERS = [
    ("xercesc", b"xercesc", "Xerces-C statically linked -> XML_SUPPORT=ON"),
    ("xml_support_off", b"Compiler flag XML_SUPPORT was off",
     "the noxml build's own diagnostic -> XML_SUPPORT=OFF"),
    ("xmloutput", b"xmloutput", "option string; present in BOTH builds"),
    ("decoy_xml_output", b"decoy-xml-output", "option string; present in BOTH"),
    ("stdinput_xml", b"stdinput-xml", "option string; present in BOTH"),
    ("pout_xml", b"pout.xml", "usage text; present in BOTH"),
    ("percolator_out_xsd", b"percolator_out.xsd", "schema name; present in BOTH"),
    ("percolator_in_xsd", b"percolator_in.xsd", "schema name; present in BOTH"),
    ("version_banner", b"Percolator version", "version banner"),
]

DECISIVE_POSITIVE = "xercesc"
DECISIVE_NEGATIVE = "xml_support_off"
XML_OPTION_MARKERS = ("xmloutput", "decoy_xml_output", "percolator_out_xsd")


def count(haystack: bytes, needle: bytes) -> int:
    n, start = 0, 0
    while True:
        i = haystack.find(needle, start)
        if i < 0:
            return n
        n += 1
        start = i + 1


def banner(blob: bytes) -> str | None:
    i = blob.find(b"Percolator version")
    if i < 0:
        return None
    end = blob.find(b"\x00", i)
    chunk = blob[i:end if 0 <= end - i <= 200 else i + 120]
    return chunk.split(b"\n")[0].decode("utf-8", "replace").strip()


def probe(path: str) -> dict:
    blob = open(path, "rb").read()
    counts = {label: count(blob, needle) for label, needle, _ in MARKERS}
    has_options = any(counts[m] for m in XML_OPTION_MARKERS)
    if counts[DECISIVE_NEGATIVE]:
        verdict = "NOT XML-capable"
        why = ("the binary carries the noxml build's own diagnostic "
               "'Compiler flag XML_SUPPORT was off'")
    elif counts[DECISIVE_POSITIVE]:
        verdict = "XML-capable"
        why = ("Xerces-C is statically linked (%d occurrences of 'xercesc') "
               "and the XML_SUPPORT=OFF diagnostic is absent"
               % counts[DECISIVE_POSITIVE])
    elif not has_options:
        verdict = "NOT XML-capable"
        why = ("no XML option strings at all: this is a build from which the "
               "XML/XSD code was removed, not compiled out (3.09 and later)")
    else:
        verdict = "UNDETERMINED"
        why = ("XML option strings are present but neither decisive marker is: "
               "run 'percolator --help' on the target platform")
    return {
        "path": os.path.abspath(path),
        "bytes": len(blob),
        "sha256": hashlib.sha256(blob).hexdigest(),
        "version_banner": banner(blob),
        "marker_counts": counts,
        "verdict": verdict,
        "reason": why,
        "evidence_kind": "static; not a substitute for running --help",
    }


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="+")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args(argv)

    results = [probe(p) for p in args.files]
    if args.json:
        json.dump(results, sys.stdout, indent=1)
        sys.stdout.write("\n")
        return 0
    for r in results:
        print("%s" % r["path"])
        print("   bytes   : %d" % r["bytes"])
        print("   sha256  : %s" % r["sha256"])
        if r["version_banner"]:
            print("   banner  : %s (literal; the version number is formatted at run\n             time and is not a string constant)" % r["version_banner"])
        for label, _, meaning in MARKERS:
            print("   %-19s %6d   %s" % (label, r["marker_counts"][label], meaning))
        print("   VERDICT : %s -- %s" % (r["verdict"], r["reason"]))
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""Percolator ``noxml`` capability sweep -- CometGUI Phase 00, work unit 10.

Answers one question per published Percolator artefact: **can this binary
write Percolator output ("pout") XML?**

The project's strategy assumed that upstream's ``noxml`` twin of each artefact
cannot produce the XML the Limelight converter needs, and therefore that only
the OS *packages* (``.deb``, ``.rpm``, ``.pkg``, NSIS ``.exe``) are usable.
This script tests that assumption artefact by artefact.

What it does
------------

#. Downloads the release assets for ``rel-3-05`` .. ``rel-3-09`` into
   ``scratch/u10/dl`` (idempotent -- an asset already present and of the right
   size is not refetched).  Release-asset downloads do not consume the
   ``api.github.com`` rate limit.
#. Unpacks each one with the signed-off extractors in this directory
   (:mod:`extract_deb`, :mod:`extract_pkg`, :mod:`extract_rpm`,
   :mod:`extract_nsis`) or with :mod:`zipfile`.  Nothing is installed, nothing
   needs root, no installer is run.
#. Scans the ``percolator`` binary inside for byte markers that track the
   pout-XML **writer** and, separately, the pin-XML **reader**.  The scan looks
   for each marker in ASCII/UTF-8 and in UTF-16LE, because Windows PE binaries
   may hold either.
#. **Executes** every binary that is an x86-64 ELF and actually loads on this
   host: prints the version banner, dumps ``--help``, runs ``-X`` on a
   generated PIN file, runs ``-X -Z``, and counts the ``<psm>``/``<peptide>``
   elements written.  For non-Linux binaries no execution is possible here and
   the verdict is explicitly reported as *inferred*.
#. Validates the emitted XML against the shipped ``percolator_out.xsd`` with
   the JDK's own ``javax.xml.validation`` (``noxml-sweep/PoutXsdValidate.java``),
   including a deliberately corrupted document as a negative control -- a
   validator that never fails proves nothing.

Markers
-------

``xmloutput``, ``decoy-xml-output``, ``pout.xml`` and ``percolator_out.xsd``
appear in *both* twins of a release that has the writer, so they do not
separate an XML build from its ``noxml`` twin.  That turns out to be the point:
both twins have the writer.  What they *do* separate is a release that has the
pout writer from one that does not, which is the question that matters.  The
strongest markers are the writer's own literal output fragments --
``<percolator_output`` and ``</percolator_output>`` -- which cannot be present
unless the code that emits them is linked in.

The pin-XML **reader** markers (``xerces``, ``percolator_in.xsd``) are what the
``XML_SUPPORT`` compile flag actually gates, and they are the only thing the
``noxml`` naming reliably predicts.

Usage
-----

::

    python3 scripts/feasibility/noxml_sweep.py                # full sweep
    python3 scripts/feasibility/noxml_sweep.py --no-download  # offline
    python3 scripts/feasibility/noxml_sweep.py --json out.json

Exit status is 0 when the sweep completed *and* the XSD negative control
behaved (the corrupted document was rejected).  Exit code 0 on its own proves
nothing; read the report.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SCRATCH = REPO / "scratch" / "u10"
DL = SCRATCH / "dl"
EX = SCRATCH / "extract"
OUT = SCRATCH / "out"
JAVA_HOME = REPO / "tools" / "liberica-jdk-25.0.4.1+1"
VALIDATOR = HERE / "noxml-sweep" / "PoutXsdValidate.java"
BASE = "https://github.com/percolator/percolator/releases/download"
# Boost 1.66 shared objects extracted from CentOS 8 RPMs by work unit 3.  Used
# only to make upstream's .rpm binaries loadable here; optional.
BOOST166 = REPO / "scratch" / "percolator" / "3.09" / "deps" / "usr" / "lib64"

# --------------------------------------------------------------------------
# The artefact inventory.
#
# (release, platform, twin, asset, container)  --  twin is "xml" for the
# XML_SUPPORT=ON build, "noxml" for its default-build twin, and "single" where
# the release publishes only one build of that artefact.
#
# Only the ``percolator`` component is swept; ``percolator-converters`` and
# ``elude`` are separate programs and do not write pout XML.
# --------------------------------------------------------------------------
ARTEFACTS = [
    # rel-3-09 -- release notes say XML/XSD I/O was removed.
    ("rel-3-09", "linux-x86_64", "single", "percolator-v3-09-linux-amd64.deb", "deb"),
    ("rel-3-09", "linux-x86_64", "single", "percolator-v3-09-linux-x86_64.rpm", "rpm"),
    ("rel-3-09", "macos", "single", "percolator-osx-portable.zip", "zip"),
    ("rel-3-09", "windows-x64", "single", "percolator.exe", "raw"),
    # rel-3-08
    ("rel-3-08", "linux-x86_64", "xml", "percolator-v3-08-linux-amd64.deb", "deb"),
    ("rel-3-08", "linux-x86_64", "noxml", "percolator-noxml-v3-08-linux-amd64.deb", "deb"),
    ("rel-3-08", "macos", "noxml", "percolator-noxml-osx-portable.zip", "zip"),
    ("rel-3-08", "windows-x64", "noxml", "percolator-noxml-windows-portable.zip", "zip"),
    # rel-3-07-01
    ("rel-3-07-01", "linux-x86_64", "xml", "percolator-v3-07-linux-amd64.deb", "deb"),
    ("rel-3-07-01", "linux-x86_64", "noxml", "percolator-noxml-v3-07-linux-amd64.deb", "deb"),
    ("rel-3-07-01", "linux-x86_64", "noxml", "percolator-noxml-ubuntu-portable.zip", "zip"),
    ("rel-3-07-01", "macos", "xml", "percolator-v3-07-osx-x86_64.pkg", "pkg"),
    ("rel-3-07-01", "macos", "noxml", "percolator-noxml-v3-07-osx-x86_64.pkg", "pkg"),
    ("rel-3-07-01", "macos", "noxml", "percolator-noxml-osx-portable.zip", "zip"),
    ("rel-3-07-01", "windows-x64", "xml", "percolator-v3-07.exe", "nsis"),
    ("rel-3-07-01", "windows-x64", "noxml", "percolator-noxml-v3-07.exe", "nsis"),
    ("rel-3-07-01", "windows-x64", "noxml", "percolator-noxml-windows-portable.zip", "zip"),
    # rel-3-06-05
    ("rel-3-06-05", "linux-x86_64", "xml", "percolator-v3-06-linux-amd64.deb", "deb"),
    ("rel-3-06-05", "linux-x86_64", "noxml", "percolator-noxml-v3-06-linux-amd64.deb", "deb"),
    ("rel-3-06-05", "linux-x86_64", "noxml", "percolator-noxml-linux-portable.zip", "zip"),
    ("rel-3-06-05", "linux-x86_64", "noxml", "percolator-noxml-ubuntu-portable.zip", "zip"),
    ("rel-3-06-05", "macos", "xml", "percolator-v3-06-osx-x86_64.pkg", "pkg"),
    ("rel-3-06-05", "macos", "noxml", "percolator-noxml-v3-06-osx-x86_64.pkg", "pkg"),
    ("rel-3-06-05", "macos", "noxml", "percolator-noxml-osx-portable.zip", "zip"),
    ("rel-3-06-05", "windows-x64", "xml", "percolator-v3-06.exe", "nsis"),
    ("rel-3-06-05", "windows-x64", "noxml", "percolator-noxml-v3-06.exe", "nsis"),
    ("rel-3-06-05", "windows-x64", "noxml", "percolator-noxml-windows-portable.zip", "zip"),
    # rel-3-05 -- publishes zip bundles that CONTAIN the OS packages, not
    # portable builds.  The nested packages are unpacked as a second stage.
    ("rel-3-05", "macos", "bundle", "osx.zip", "zip"),
    ("rel-3-05", "windows-x64", "bundle", "win64.zip", "zip"),
]

# Marker sets.  ``out`` markers track the pout-XML WRITER; ``inp`` markers track
# the pin-XML READER, which is what the XML_SUPPORT flag gates.
OUT_MARKERS = [
    "<percolator_output",       # writer's own opening-tag literal
    "</percolator_output>",     # writer's own closing-tag literal
    "/src/xml/percolator_out.xsd",
    "xmloutput",                # long option --xmloutput  (-X)
    "decoy-xml-output",         # long option --decoy-xml-output (-Z)
    "pout.xml",
]
IN_MARKERS = [
    "xerces",
    "percolator_in.xsd",
    "per-colator.com/percolator_in",
    "XML_SUPPORT was off",      # the noxml build's own runtime refusal message
]


# --------------------------------------------------------------------------
# helpers
# --------------------------------------------------------------------------
def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(release: str, asset: str, dest: Path, enabled: bool) -> Path | None:
    """Fetch a release asset unless it is already here."""
    target = dest / f"{release}__{asset}"
    if target.exists() and target.stat().st_size > 0:
        return target
    if not enabled:
        return None
    url = f"{BASE}/{release}/{asset}"
    tmp = target.with_suffix(target.suffix + ".part")
    try:
        urllib.request.urlretrieve(url, tmp)
    except Exception as exc:  # noqa: BLE001 -- reported, not swallowed
        print(f"  ! download failed {url}: {exc}", file=sys.stderr)
        if tmp.exists():
            tmp.unlink()
        return None
    tmp.rename(target)
    return target


def run_extractor(script: str, archive: Path, dest: Path) -> bool:
    dest.mkdir(parents=True, exist_ok=True)
    cmd = [sys.executable, str(HERE / script), str(archive), "--dest", str(dest)]
    if script == "extract_nsis.py":
        cmd = [sys.executable, str(HERE / script), str(archive), "-o", str(dest)]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        print(f"  ! {script} failed on {archive.name}: "
              f"{proc.stderr.strip().splitlines()[-1:] or proc.stdout[-200:]}",
              file=sys.stderr)
        return False
    return True


def unzip(archive: Path, dest: Path) -> bool:
    dest.mkdir(parents=True, exist_ok=True)
    try:
        with zipfile.ZipFile(archive) as zf:
            for info in zf.infolist():
                # Refuse absolute paths and traversal; the archives are
                # upstream's but the extraction must still be safe.
                name = info.filename.replace("\\", "/").lstrip("/")
                # Upstream's rel-3-06-05 macOS archive stores its one member as
                # "../my_build/percolator-noxml/src/percolator".  Traversal
                # components are stripped, not honoured, and not skipped --
                # dropping the member would silently lose the artefact.
                parts = [c for c in Path(name).parts if c not in ("..", ".", "/")]
                if not parts:
                    continue
                out = dest.joinpath(*parts)
                if info.is_dir():
                    out.mkdir(parents=True, exist_ok=True)
                    continue
                out.parent.mkdir(parents=True, exist_ok=True)
                with zf.open(info) as src, open(out, "wb") as dst:
                    shutil.copyfileobj(src, dst)
                if info.external_attr >> 16 & 0o111:
                    out.chmod(0o755)
    except Exception as exc:  # noqa: BLE001
        print(f"  ! unzip failed {archive.name}: {exc}", file=sys.stderr)
        return False
    return True


def find_percolator(root: Path) -> list[Path]:
    """Every file plausibly the percolator executable under ``root``."""
    hits = []
    for p in sorted(root.rglob("*")):
        if not p.is_file():
            continue
        n = p.name.lower()
        if n in ("percolator", "percolator.exe"):
            hits.append(p)
    return hits


def binary_kind(path: Path) -> str:
    with open(path, "rb") as fh:
        head = fh.read(64)
    if head[:4] == b"\x7fELF":
        machine = struct.unpack_from("<H", head, 18)[0]
        arch = {0x3E: "x86-64", 0xB7: "aarch64", 0x03: "i386"}.get(machine, hex(machine))
        return f"ELF/{arch}"
    if head[:2] == b"MZ":
        return "PE"
    if head[:4] in (b"\xcf\xfa\xed\xfe", b"\xce\xfa\xed\xfe"):
        cpu = struct.unpack_from("<I", head, 4)[0]
        arch = {0x01000007: "x86-64", 0x0100000C: "arm64", 0x07: "i386"}.get(cpu, hex(cpu))
        return f"Mach-O/{arch}"
    if head[:4] in (b"\xca\xfe\xba\xbe", b"\xbe\xba\xfe\xca"):
        return "Mach-O/universal"
    return "unknown"


def macho_detail(path: Path) -> dict:
    """cputype/cpusubtype and the minimum OS recorded in the load commands.

    Enough Mach-O to answer "which architecture, and which macOS floor?"
    without ``otool``, ``lipo`` or ``file``, none of which exist here.
    """
    import struct as st
    data = path.read_bytes()
    magic = data[:4]
    if magic not in (b"\xcf\xfa\xed\xfe", b"\xce\xfa\xed\xfe"):
        return {}
    is64 = magic == b"\xcf\xfa\xed\xfe"
    cputype, cpusub, _ft, ncmds, _sz, _fl = st.unpack_from("<iiIIII", data, 4)
    out = {
        "cputype": cputype,
        "arch": {0x01000007: "x86_64", 0x0100000C: "arm64",
                 0x07: "i386"}.get(cputype & 0xFFFFFFFF, hex(cputype)),
        "cpusubtype": cpusub,
    }
    off = 32 if is64 else 28
    for _ in range(ncmds):
        if off + 8 > len(data):
            break
        cmd, cmdsize = st.unpack_from("<II", data, off)
        if cmdsize == 0:
            break
        if cmd == 0x32:  # LC_BUILD_VERSION
            platform, minos, sdk = st.unpack_from("<III", data, off + 8)
            fmt = lambda v: "%d.%d.%d" % ((v >> 16) & 0xFFFF, (v >> 8) & 0xFF, v & 0xFF)
            out["build_version_platform"] = {1: "macOS", 2: "iOS", 3: "tvOS",
                                             6: "macCatalyst"}.get(platform, platform)
            out["minos"] = fmt(minos)
            out["sdk"] = fmt(sdk)
        elif cmd == 0x24:  # LC_VERSION_MIN_MACOSX
            ver, sdk = st.unpack_from("<II", data, off + 8)
            fmt = lambda v: "%d.%d.%d" % ((v >> 16) & 0xFFFF, (v >> 8) & 0xFF, v & 0xFF)
            out["minos"] = fmt(ver)
            out["sdk"] = fmt(sdk)
        off += cmdsize
    return out


def elf_symbol_floor(path: Path) -> dict:
    """Highest GLIBC_/GLIBCXX_ symbol version the ELF binary demands."""
    data = path.read_bytes()

    def highest(prefix: bytes) -> str | None:
        best = None
        for m in re.finditer(re.escape(prefix) + rb"(\d+(?:\.\d+)+)", data):
            v = tuple(int(x) for x in m.group(1).split(b"."))
            if best is None or v > best:
                best = v
        return ".".join(str(x) for x in best) if best else None

    return {"glibc": highest(b"GLIBC_"), "glibcxx": highest(b"GLIBCXX_")}


def scan_markers(path: Path, markers: list[str]) -> dict[str, int]:
    data = path.read_bytes()
    found = {}
    for m in markers:
        n = data.count(m.encode("utf-8"))
        n += data.count(m.encode("utf-16-le"))
        found[m] = n
    return found


def make_pin(path: Path, n_target: int = 200) -> Path:
    """Deterministic synthetic PIN: n targets and n decoys, separable."""
    import random
    rnd = random.Random(20260829)
    aas = "ACDEFGHIKLMNPQRSTVWY"
    lines = ["SpecId\tLabel\tScanNr\tExpMass\tCalcMass\tfeat1\tfeat2\tfeat3\tPeptide\tProteins"]
    for i in range(n_target * 2):
        target = i % 2 == 0
        label = 1 if target else -1
        mu = 1.0 if target else -0.3
        f = [rnd.gauss(mu, 1.0), rnd.gauss(mu * 0.7, 1.0), rnd.gauss(0.0, 0.3)]
        pep = "".join(rnd.choice(aas) for _ in range(9))
        prot = ("sp|P%05d|TEST" % i) if target else ("decoy_sp|P%05d|TEST" % i)
        lines.append("psm%d\t%d\t%d\t1000.5\t1000.4\t%.4f\t%.4f\t%.4f\tK.%s.R\t%s"
                     % (i, label, i, f[0], f[1], f[2], pep, prot))
    path.write_text("\n".join(lines) + "\n")
    return path


def _env_with_libs(extra_lib: Path | None) -> dict:
    env = dict(os.environ)
    if extra_lib is not None:
        prev = env.get("LD_LIBRARY_PATH")
        env["LD_LIBRARY_PATH"] = str(extra_lib) + (":" + prev if prev else "")
    return env


def loads_here(path: Path, extra_lib: Path | None = None) -> tuple[bool, str]:
    """Does this binary actually run on this host?"""
    try:
        proc = subprocess.run([str(path), "--help"], capture_output=True,
                              text=True, timeout=120, env=_env_with_libs(extra_lib))
    except Exception as exc:  # noqa: BLE001
        return False, str(exc)
    text = (proc.stdout or "") + (proc.stderr or "")
    if "Percolator version" in text:
        return True, text
    return False, text.strip()[:300]


def version_banner(text: str) -> str:
    m = re.search(r"Percolator version [^\n]*", text)
    return m.group(0).strip() if m else "(none)"


def execute_probe(binary: Path, pin: Path, tag: str,
                  extra_lib: Path | None = None) -> dict:
    """Run the binary for real: --help, -X, and -X -Z."""
    res: dict = {"executed": True}
    ok, helptext = loads_here(binary)
    res["extra_lib"] = None
    if not ok and extra_lib is not None and extra_lib.is_dir():
        # Upstream's .rpm binaries hard-link Boost 1.66 shared objects that the
        # package does not ship.  Work unit 3 extracted them (no root, nothing
        # installed).  If they are present, retry with them on the search path
        # and say so, rather than reporting an inference where an execution
        # was possible.
        ok2, helptext2 = loads_here(binary, extra_lib)
        if ok2:
            ok, helptext = ok2, helptext2
            res["extra_lib"] = str(extra_lib)
    res["loads"] = ok
    res["banner"] = version_banner(helptext)
    if not ok:
        res["loader_error"] = helptext
        return res
    long_opts = sorted(set(re.findall(r"^ --([A-Za-z0-9-]+)", helptext, re.M)))
    res["long_options"] = long_opts
    res["help_has_xmloutput"] = "xmloutput" in long_opts
    res["help_has_decoy_xml_output"] = "decoy-xml-output" in long_opts
    res["help_has_xml_in"] = "xml-in" in long_opts

    for flag, key in ((["-X"], "X"), (["-X", None, "-Z"], "XZ")):
        xml = OUT / f"{tag}-{key}.xml"
        cmd = [str(binary), "-X", str(xml)] + (["-Z"] if key == "XZ" else []) + [str(pin)]
        lib = Path(res["extra_lib"]) if res["extra_lib"] else None
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=600,
                              env=_env_with_libs(lib))
        entry = {
            "argv": cmd,
            "exit": proc.returncode,
            "path": str(xml),
            "bytes": xml.stat().st_size if xml.exists() else 0,
        }
        if xml.exists() and entry["bytes"] > 0:
            body = xml.read_text(encoding="utf-8", errors="replace")
            entry["root_element_present"] = "<percolator_output" in body
            entry["psm"] = len(re.findall(r"<psm ", body))
            entry["peptide"] = len(re.findall(r"<peptide ", body))
            entry["decoy_true"] = len(re.findall(r'p:decoy="true"', body))
            entry["sha256"] = sha256(xml)
        else:
            entry["stderr_tail"] = (proc.stderr or "")[-400:]
        res[key] = entry
    if not res["help_has_xmloutput"]:
        # A release that dropped -X might still hide the writer behind another
        # switch.  Try the historical spellings and record what the binary said.
        res["rejected_flags"] = {}
        for flag in ("-X", "--xmloutput", "-Z", "--decoy-xml-output", "--xml-in"):
            argv = [str(binary), flag, str(OUT / "hidden-probe.xml"), str(pin)]
            p2 = subprocess.run(argv, capture_output=True, text=True, timeout=120,
                                env=_env_with_libs(Path(res["extra_lib"])
                                                   if res["extra_lib"] else None))
            tail = [ln for ln in (p2.stdout + p2.stderr).splitlines()
                    if ln.startswith("ERROR")]
            res["rejected_flags"][flag] = {"exit": p2.returncode,
                                           "error": tail[-1] if tail else ""}

    # -X wrote real XML with the expected element counts?
    x = res.get("X", {})
    res["emits_pout_xml"] = bool(
        x.get("exit") == 0 and x.get("root_element_present") and x.get("psm", 0) > 0
    )
    z = res.get("XZ", {})
    res["decoys_in_xml"] = bool(
        z.get("exit") == 0 and z.get("decoy_true", 0) > 0
        and z.get("psm", 0) > x.get("psm", 0)
    )
    return res


# --------------------------------------------------------------------------
# The recommended R-PERC-02 capability probe.
#
# Nothing static discriminates.  ``--help`` is IDENTICAL between an
# XML_SUPPORT=ON build and its ``noxml`` twin -- both list ``-X/--xmloutput``,
# ``-Z/--decoy-xml-output`` and ``--xml-in`` -- so a probe that greps the help
# text for ``-X`` cannot tell them apart, and does not need to, because both
# write pout XML.  The marker ``Compiler flag XML_SUPPORT was off`` separates
# the twins correctly but answers the WRONG question: it reports the pin-XML
# *reader*, not the pout-XML *writer*.
#
# The only probe that answers the question the Limelight stage actually asks is
# functional: run the candidate binary on a tiny PIN with ``-X`` and ``-Z`` and
# check that the file it wrote is Percolator pout XML with the PSM and decoy
# counts the input implies.
# --------------------------------------------------------------------------
def capability_probe(binary: Path, workdir: Path,
                     extra_lib: Path | None = None) -> dict:
    """Functional probe: does THIS binary write usable pout XML, here, now?

    Returns a dict with ``pout_xml`` (the Limelight prerequisite),
    ``pout_xml_decoys`` (the ``--import-decoys`` prerequisite) and the evidence
    each verdict rests on.  Every field is derived from output that was
    actually produced, never from exit status alone.
    """
    workdir.mkdir(parents=True, exist_ok=True)
    # 64 targets + 64 decoys.  Percolator needs enough separation to finish
    # cross-validation: at 8 targets it aborts with "median decoy score <=
    # score at 1% FDR", which would make the probe report a capable binary as
    # incapable.  20 is the smallest size that succeeded here; 64 leaves
    # margin and still runs in well under a second.
    n_target = 64
    pin = make_pin(workdir / "probe.pin", n_target=n_target)
    result: dict = {"binary": str(binary), "bytes": binary.stat().st_size,
                    "sha256": sha256(binary)}

    ok, helptext = loads_here(binary)
    if not ok and extra_lib is not None and extra_lib.is_dir():
        ok2, helptext2 = loads_here(binary, extra_lib)
        if ok2:
            ok, helptext = ok2, helptext2
            result["extra_lib"] = str(extra_lib)
    result["loads"] = ok
    result["banner"] = version_banner(helptext)
    if not ok:
        result["pout_xml"] = False
        result["pout_xml_decoys"] = False
        result["reason"] = "binary does not load on this host"
        return result

    def one(extra: list[str], name: str, expect_psms: int) -> dict:
        target = workdir / name
        if target.exists():
            target.unlink()
        argv = [str(binary), "-X", str(target)] + extra + [str(pin)]
        lib = Path(result["extra_lib"]) if result.get("extra_lib") else None
        proc = subprocess.run(argv, capture_output=True, text=True, timeout=300,
                              env=_env_with_libs(lib))
        rec = {"argv": argv, "exit": proc.returncode, "exists": target.exists()}
        if not target.exists() or target.stat().st_size == 0:
            rec["ok"] = False
            rec["why"] = "no output file written"
            rec["stderr_tail"] = (proc.stderr or "")[-300:]
            return rec
        body = target.read_text(encoding="utf-8", errors="replace")
        rec["bytes"] = len(body)
        rec["root"] = "<percolator_output" in body
        rec["ns"] = "http://per-colator.com/percolator_out/" in body
        rec["psm"] = len(re.findall(r"<psm ", body))
        rec["peptide"] = len(re.findall(r"<peptide ", body))
        rec["decoy_true"] = len(re.findall(r'p:decoy="true"', body))
        rec["ok"] = bool(proc.returncode == 0 and rec["root"] and rec["ns"]
                         and rec["psm"] == expect_psms)
        rec["why"] = ("wrote pout XML with the expected %d <psm> elements"
                      % expect_psms) if rec["ok"] else \
                     ("expected %d <psm> elements, found %d"
                      % (expect_psms, rec["psm"]))
        return rec

    result["X"] = one([], "probe-X.xml", n_target)
    result["XZ"] = one(["-Z"], "probe-XZ.xml", n_target * 2)
    result["pout_xml"] = result["X"]["ok"]
    result["pout_xml_decoys"] = bool(
        result["XZ"]["ok"] and result["XZ"].get("decoy_true", 0) > 0)
    return result


def java_validate(xsd: Path, docs: list[Path]) -> tuple[int, str]:
    env = dict(os.environ)
    env["JAVA_HOME"] = str(JAVA_HOME)
    cmd = [str(JAVA_HOME / "bin" / "java"), str(VALIDATOR), str(xsd)] + [str(d) for d in docs]
    proc = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=600)
    return proc.returncode, (proc.stdout or "") + (proc.stderr or "")


# --------------------------------------------------------------------------
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--no-download", action="store_true",
                    help="use only what is already in scratch/u10/dl")
    ap.add_argument("--json", type=Path, default=SCRATCH / "sweep.json")
    ap.add_argument("--skip-validate", action="store_true")
    ap.add_argument("--probe", type=Path, metavar="BINARY", action="append",
                    help="run only the recommended R-PERC-02 capability probe "
                         "against this binary; repeatable")
    args = ap.parse_args()

    if args.probe:
        rc = 0
        for i, b in enumerate(args.probe):
            rep = capability_probe(b, SCRATCH / "probe" / str(i),
                                   extra_lib=BOOST166)
            print(json.dumps(rep, indent=1))
            print("VERDICT %s: pout_xml=%s pout_xml_decoys=%s (%s)"
                  % (b, rep["pout_xml"], rep["pout_xml_decoys"], rep.get("banner")))
            if not rep["pout_xml"]:
                rc = max(rc, 0)  # a negative probe is a result, not a failure
        return rc

    for d in (DL, EX, OUT):
        d.mkdir(parents=True, exist_ok=True)

    pin = make_pin(SCRATCH / "sweep.pin")
    print(f"PIN fixture: {pin} ({pin.stat().st_size} bytes, "
          f"{len(pin.read_text().splitlines()) - 1} PSMs)\n")

    report: dict = {"artefacts": [], "validation": {}, "pin": str(pin)}

    for release, platform, twin, asset, container in ARTEFACTS:
        key = f"{release}/{platform}/{twin}/{asset}"
        print(f"== {key}")
        entry = {"release": release, "platform": platform, "twin": twin,
                 "asset": asset, "container": container, "binaries": []}
        report["artefacts"].append(entry)

        archive = download(release, asset, DL, not args.no_download)
        if archive is None:
            entry["status"] = "not available locally"
            print("   (skipped: not downloaded)")
            continue
        entry["archive"] = str(archive)
        entry["archive_bytes"] = archive.stat().st_size
        entry["archive_sha256"] = sha256(archive)

        dest = EX / f"{release}__{asset}".replace("/", "_")
        if container == "raw":
            dest.mkdir(parents=True, exist_ok=True)
            shutil.copy2(archive, dest / "percolator.exe")
            ok = True
        elif container == "zip":
            ok = unzip(archive, dest)
        elif container == "deb":
            ok = run_extractor("extract_deb.py", archive, dest)
        elif container == "rpm":
            ok = run_extractor("extract_rpm.py", archive, dest)
        elif container == "pkg":
            ok = run_extractor("extract_pkg.py", archive, dest)
        elif container == "nsis":
            ok = run_extractor("extract_nsis.py", archive, dest)
        else:
            ok = False
        if not ok:
            entry["status"] = "extraction failed"
            continue

        # rel-3-05's zips contain OS packages; unpack those too.
        for nested in sorted(dest.rglob("*")):
            if not nested.is_file():
                continue
            if nested.name.startswith("percolator-converters") or nested.name.startswith("elude"):
                continue
            sub = dest / ("nested__" + nested.name)
            if nested.suffix == ".deb":
                run_extractor("extract_deb.py", nested, sub)
            elif nested.suffix == ".pkg":
                run_extractor("extract_pkg.py", nested, sub)
            elif nested.suffix == ".exe" and nested.name != "percolator.exe":
                run_extractor("extract_nsis.py", nested, sub)

        xsds = [str(p.relative_to(dest)) for p in dest.rglob("percolator_out.xsd")]
        entry["ships_pout_xsd"] = xsds
        entry["ships_pin_xsd"] = [str(p.relative_to(dest))
                                  for p in dest.rglob("percolator_in.xsd")]

        bins = find_percolator(dest)
        if not bins:
            entry["status"] = "no percolator binary found"
            print("   ! no percolator binary in payload")
            continue
        entry["status"] = "ok"

        for b in bins:
            kind = binary_kind(b)
            rec = {
                "path": str(b),
                "rel": str(b.relative_to(dest)),
                "bytes": b.stat().st_size,
                "sha256": sha256(b),
                "kind": kind,
                "markers_out": scan_markers(b, OUT_MARKERS),
                "markers_in": scan_markers(b, IN_MARKERS),
            }
            if kind.startswith("Mach-O"):
                rec["macho"] = macho_detail(b)
            elif kind.startswith("ELF"):
                rec["elf"] = elf_symbol_floor(b)
            rec["writer_markers_present"] = (
                rec["markers_out"]["<percolator_output"] > 0
                and rec["markers_out"]["</percolator_output>"] > 0
            )
            rec["reader_markers_present"] = rec["markers_in"]["xerces"] > 0

            if kind == "ELF/x86-64" and os.name == "posix":
                b.chmod(0o755)
                tag = f"{release}_{twin}_{asset}".replace(".", "_").replace("/", "_")
                try:
                    rec["run"] = execute_probe(b, pin, tag, extra_lib=BOOST166)
                except Exception as exc:  # noqa: BLE001
                    rec["run"] = {"executed": True, "error": str(exc)}
            else:
                rec["run"] = {"executed": False,
                              "reason": f"{kind} cannot run on this Linux x86-64 host"}

            verdict_run = rec["run"].get("emits_pout_xml")
            if verdict_run is True:
                rec["verdict"] = "CAN emit pout XML (executed)"
            elif verdict_run is False and rec["run"].get("loads"):
                rec["verdict"] = "CANNOT emit pout XML (executed)"
            elif rec["writer_markers_present"]:
                rec["verdict"] = "CAN emit pout XML (inferred: writer literals present)"
            else:
                rec["verdict"] = "CANNOT emit pout XML (inferred: writer literals absent)"

            print("   %-58s %-16s %9d  %s"
                  % (rec["rel"], kind, rec["bytes"], rec["verdict"]))
            entry["binaries"].append(rec)

    # ---------------- XSD validation, with a negative control ---------------
    if not args.skip_validate:
        xsd = None
        for cand in sorted(EX.rglob("xml-pout-1-5/percolator_out.xsd")):
            xsd = cand
            break
        if xsd is None:
            fallback = REPO / ("scratch/percolator/3.07.1-linux-x86_64/usr/share/xml/"
                               "percolator/xml-pout-1-5/percolator_out.xsd")
            xsd = fallback if fallback.exists() else None
        docs = sorted(OUT.glob("*.xml"))
        if xsd and docs:
            print(f"\n== XSD validation against {xsd}")
            rc, text = java_validate(xsd, docs)
            print(text.rstrip())
            report["validation"]["xsd"] = str(xsd)
            report["validation"]["as_shipped_rc"] = rc
            report["validation"]["as_shipped_output"] = text

            # Negative control 1: corrupt a real document.
            good = docs[0]
            bad = OUT / "NEGATIVE-CONTROL-corrupted.xml"
            body = good.read_text(encoding="utf-8")
            body = body.replace('<q_value>', '<q_value>NOT_A_NUMBER_', 1)
            body = body.replace("<peptides", "<bogus_element/><peptides", 1)
            bad.write_text(body, encoding="utf-8")
            rc_bad, text_bad = java_validate(xsd, [bad])
            print("\n== XSD negative control (deliberately corrupted document)")
            print(text_bad.rstrip())
            report["validation"]["negative_control_rc"] = rc_bad
            report["validation"]["negative_control_output"] = text_bad

            # Positive control: the same schema with the upstream
            # fixed="2"/majorVersion defect patched out, so the writer's real
            # output can be validated on its merits.  The patch RELAXES only
            # that one upstream inconsistency and is applied to a copy; the
            # shipped schema is never modified.
            patched = OUT / "percolator_out.PATCHED-majorVersion.xsd"
            patched.write_text(
                xsd.read_text(encoding="utf-8").replace(
                    'ref="majorVersion" use="required" fixed="2"',
                    'ref="majorVersion" use="required"'),
                encoding="utf-8")
            rc_p, text_p = java_validate(patched, docs)
            rc_pb, text_pb = java_validate(patched, [bad])
            print("\n== XSD validation against the same schema with the "
                  "upstream majorVersion fixed=\"2\" defect relaxed")
            print(text_p.rstrip())
            print("\n== ... and the corrupted document against that same "
                  "relaxed schema")
            print(text_pb.rstrip())
            report["validation"]["patched_rc"] = rc_p
            report["validation"]["patched_output"] = text_p
            report["validation"]["patched_negative_rc"] = rc_pb
            report["validation"]["patched_negative_output"] = text_pb

    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(report, indent=1))
    print(f"\nreport: {args.json}")

    neg = report.get("validation", {}).get("negative_control_rc")
    if neg is not None and neg == 0:
        print("FAIL: the corrupted negative control validated -- "
              "the validator proves nothing.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

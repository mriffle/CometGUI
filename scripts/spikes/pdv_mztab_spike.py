#!/usr/bin/env python3
"""CometGUI -- Phase 11, work unit 1: THE SPIKE.  Throwaway; not product code.

    . tools/env.sh && python3 scripts/spikes/pdv_mztab_spike.py [--only RUN,...]

Question: does PDV 2.7.0's ``denovo-gui`` mode (MztabImport + PdvControlServer)
accept an mzTab generated from Comet + Percolator results, and which
``spectra_ref`` form makes it draw the RIGHT spectrum when 1-based file
position and instrument scan number differ?

What it does, end to end, with nothing installed on the host:

1. Verifies PDV-2.7.0.zip against ``manifests/tools.json`` (SHA-256 and size)
   and every unpacked file against the zip's own entries.
2. Reads the real K562 run with readers written here: Percolator 3.07.1
   ``psms.target.txt``, the Comet pepXML (search hits, modifications, RT),
   and the mzML (index, native id, 1-based position, precursor m/z, peaks).
3. Writes a throwaway mzTab per experiment -- values transcribed from the
   source text, ``null`` where the source is silent -- in several
   ``spectra_ref`` dialects, plus deliberately malformed variants.
4. Starts the project-local Xvfb (tools/x11-bookworm-20260829/, as
   scripts/feasibility/pdv_spike.sh does) and runs PDV ``denovo-gui`` under
   it, once per experiment, each under a hard timeout.
5. Polls ``/ready`` with a deadline, drives ``/select`` (known refs, an
   unknown ref, a missing ref) and ``/shutdown``, and records every status
   code and body.
6. Proves which spectrum PDV actually DRAWS -- which the control API cannot
   say -- with ``PdvObserver`` (scripts/spikes/pdv_mztab_spike/), which runs
   PDV's own unmodified main class in the same JVM and reports PDV's state by
   reflection.  Each displayed spectrum is compared with the mzML spectrum the
   PSM came from (precursor m/z, peak count, total intensity, base peak), and
   the drawn peptide with the pepXML hit (sequence, charge, modification
   sites and masses, theoretical m/z).
7. Samples every socket the PDV process owns (``/proc/<pid>/fd`` +
   ``/proc/<pid>/net/*``) throughout each run, and logs every host-name
   lookup in the observer runs, to answer the analytics question.

8. (Unit 2, R12) Runs the PRODUCT MGF writer, PdvMgfWriter.writeOnePerRow,
   through scripts/spikes/pdv_mztab_spike/PerPsmMgf.java against the built
   cometgui-tools classes, to prove design decision P11-12: one MGF entry per
   mzTab row lets PSMs that share a spectrum each be selected and drawn.

Every experiment has a written expectation; any deviation is a FAIL and the
script exits 1.  PDV's exit status is never used as evidence.  K562 data and
everything derived from it (mzTab, MGF, logs) are written under
``scratch/pdv-mztab-spike/`` -- gitignored, never committed (D-006).

Evidence page: docs/developer/pdv_mztab_spike.rst
"""

import argparse
import hashlib
import importlib.util
import json
import os
import queue
import re
import shutil
import signal
import socket
import struct
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SCRATCH = os.path.join(ROOT, "scratch")
OUT = os.path.join(SCRATCH, "pdv-mztab-spike")          # K562-derived: never committed
BUILD = os.path.join(ROOT, "_build", "pdv-mztab-spike")  # non-data build products

PDV_ZIP = os.path.join(SCRATCH, "pdv", "PDV-2.7.0.zip")
PDV_DIR = os.path.join(SCRATCH, "pdv", "PDV-2.7.0")
PDV_JAR = os.path.join(PDV_DIR, "PDV-2.7.0.jar")
MANIFEST = os.path.join(ROOT, "manifests", "tools.json")

SCI = os.path.join(SCRATCH, "scientific-path")
PEPXML = {3: os.path.join(SCI, "comet", "20100614_Velos1_TaGe_SA_K562_3.pep.xml"),
          4: os.path.join(SCI, "comet", "20100614_Velos1_TaGe_SA_K562_4.pep.xml")}
PSMS = os.path.join(SCI, "percolator-3.07.1", "psms.target.txt")
# The LF-repaired mzML Comet actually searched (pepXML base_name points here) ...
MZML = {3: os.path.join(SCI, "mzml-lf", "20100614_Velos1_TaGe_SA_K562_3.mzML"),
        4: os.path.join(SCI, "mzml-lf", "20100614_Velos1_TaGe_SA_K562_4.mzML")}
# ... and the as-fetched fixture copy (CRLF line endings, D-006).
MZML_FIXTURE_CRLF = os.path.join(SCRATCH, "fixture", "20100614_Velos1_TaGe_SA_K562_3.mzML")

X11_ROOT = os.path.join(ROOT, "tools", "x11-bookworm-20260829", "root")
FONT_ROOT = os.path.join(ROOT, "tools", "fontstack-bookworm-20260829", "root")
SHIM_SRC = os.path.join(ROOT, "scripts", "feasibility", "gui-spike", "xkbcomp-path-shim.c")
HELPERS = os.path.join(ROOT, "scripts", "feasibility", "pdv_spike_helpers.py")
OBSERVER_SRC = os.path.join(ROOT, "scripts", "spikes", "pdv_mztab_spike", "PdvObserver.java")
# R12 (unit 2): the PRODUCT MGF writer, run through a spike-local main against the module's classes.
PERPSM_SRC = os.path.join(ROOT, "scripts", "spikes", "pdv_mztab_spike", "PerPsmMgf.java")
TOOLS_CLASSES = os.path.join(ROOT, "cometgui-tools", "target", "classes")
DOMAIN_CLASSES = os.path.join(ROOT, "cometgui-domain", "target", "classes")

PROTON = 1.007276466812
READY_DEADLINE_S = 150
RUN_DEADLINE_S = 300
DISPLAY_DEADLINE_S = 20

# --------------------------------------------------------------------------
# small utilities
# --------------------------------------------------------------------------


class SpikeFailure(Exception):
    pass


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def log(msg):
    print(msg, flush=True)


def wait_until(predicate, deadline_s, step_s=0.1):
    """Poll ``predicate`` until it returns a truthy value or the deadline passes."""
    end = time.monotonic() + deadline_s
    while True:
        value = predicate()
        if value:
            return value
        if time.monotonic() >= end:
            return None
        threading.Event().wait(step_s)


def load_helpers():
    spec = importlib.util.spec_from_file_location("pdv_spike_helpers", HELPERS)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


# --------------------------------------------------------------------------
# 1. the PDV artefact
# --------------------------------------------------------------------------


def verify_pdv():
    with open(MANIFEST) as f:
        manifest = json.load(f)
    rows = []

    def walk(o):
        if isinstance(o, dict):
            if o.get("tool") == "pdv" and o.get("version") == "2.7.0":
                rows.append(o)
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(manifest)
    if not rows:
        raise SpikeFailure("manifests/tools.json has no pdv 2.7.0 row")
    want = {(r["sha256"], r["sizeBytes"]) for r in rows}
    if len(want) != 1:
        raise SpikeFailure("manifest pdv rows disagree: %r" % want)
    want_sha, want_size = want.pop()
    got_sha, got_size = sha256(PDV_ZIP), os.path.getsize(PDV_ZIP)
    if (got_sha, got_size) != (want_sha, want_size):
        raise SpikeFailure("PDV zip %s/%d != manifest %s/%d" % (got_sha, got_size, want_sha, want_size))
    mismatches = 0
    entries = 0
    with zipfile.ZipFile(PDV_ZIP) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            entries += 1
            p = os.path.join(os.path.dirname(PDV_DIR), info.filename)
            if not os.path.isfile(p) or hashlib.sha256(z.read(info)).hexdigest() != sha256(p):
                mismatches += 1
    if mismatches:
        raise SpikeFailure("%d unpacked PDV files differ from the zip" % mismatches)
    return {"zipSha256": got_sha, "zipBytes": got_size, "manifestRows": len(rows),
            "unpackedFilesVerified": entries, "jarSha256": sha256(PDV_JAR)}


# --------------------------------------------------------------------------
# 2. source readers (spike-local; share nothing with any exporter)
# --------------------------------------------------------------------------


def read_percolator(path):
    rows = []
    with open(path) as f:
        header = f.readline().rstrip("\n").split("\t")
        if header[:6] != ["PSMId", "score", "q-value", "posterior_error_prob", "peptide", "proteinIds"]:
            raise SpikeFailure("unexpected Percolator header %r" % header)
        for n, line in enumerate(f, start=2):
            cols = line.rstrip("\n").split("\t")
            m = re.fullmatch(r"(.+)_(\d+)_(\d+)_(\d+)", cols[0])
            if not m:
                raise SpikeFailure("line %d: PSMId %r is not <base>_<scan>_<charge>_<rank>" % (n, cols[0]))
            rows.append({"line": n, "psmId": cols[0], "base": m.group(1), "scan": int(m.group(2)),
                         "charge": int(m.group(3)), "rank": int(m.group(4)), "score": cols[1],
                         "q": cols[2], "pep": cols[3], "peptide": cols[4], "proteins": cols[5:]})
    return rows


def read_pepxml(path):
    """(start_scan, assumed_charge) -> query with hits by rank; values kept as source text."""
    ns = ""
    out = {}
    for _, el in ET.iterparse(path, events=("end",)):
        tag = el.tag.rsplit("}", 1)[-1]
        if tag != "spectrum_query":
            continue
        q = {"spectrum": el.get("spectrum"), "nativeId": el.get("spectrumNativeID"),
             "startScan": int(el.get("start_scan")), "charge": int(el.get("assumed_charge")),
             "rtSec": el.get("retention_time_sec"), "precursorNeutralMass": el.get("precursor_neutral_mass"),
             "hits": {}}
        ordinal = 0
        for hit in el.iter():
            if hit.tag.rsplit("}", 1)[-1] != "search_hit":
                continue
            ordinal += 1
            proteins = [hit.get("protein")]
            mods = []
            nterm = cterm = None
            for c in hit.iter():
                t = c.tag.rsplit("}", 1)[-1]
                if t == "alternative_protein":
                    proteins.append(c.get("protein"))
                elif t == "modification_info":
                    nterm = c.get("mod_nterm_mass")
                    cterm = c.get("mod_cterm_mass")
                elif t == "mod_aminoacid_mass":
                    delta = c.get("variable") or c.get("static")
                    if c.get("variable") and c.get("static"):
                        raise SpikeFailure("%s: residue %s carries both a static and a variable mod"
                                           % (q["spectrum"], c.get("position")))
                    mods.append({"position": int(c.get("position")), "deltaText": delta,
                                 "kind": "variable" if c.get("variable") else "static"})
            # Keyed by ORDINAL, not hit_rank: Comet gives tied hits the same hit_rank, while the
            # PIN (and so Percolator's PSMId) numbers hits 1..n in file order.
            q["hits"][ordinal] = {"hitRank": int(hit.get("hit_rank")),
                "peptide": hit.get("peptide"), "prev": hit.get("peptide_prev_aa"),
                "next": hit.get("peptide_next_aa"), "proteins": proteins, "mods": mods,
                "ntermMass": nterm, "ctermMass": cterm,
                "calcNeutralPepMass": hit.get("calc_neutral_pep_mass")}
        key = (q["startScan"], q["charge"])
        if key in out:
            raise SpikeFailure("%s: duplicate spectrum_query for scan %d charge %d" % (path, *key))
        out[key] = q
        el.clear()
    return out


def peak_digest(mz, intensity):
    """SHA-256 over (m/z, intensity) pairs sorted by m/z then intensity, 8 big-endian bytes each:
    the digest PdvObserver computes from what PDV drew (R12)."""
    h = hashlib.sha256()
    for m, i in sorted(zip(mz, intensity)):
        h.update(struct.pack(">dd", m, i))
    return h.hexdigest()


def read_mzml(path, helpers):
    """native id -> {index, position, scan, msLevel, selectedIonMzText, peak stats}."""
    out = {}
    position = 0
    for _, el in ET.iterparse(path, events=("end",)):
        if el.tag.rsplit("}", 1)[-1] != "spectrum":
            continue
        position += 1
        native = el.get("id")
        level = sel = iso = None
        for cv in el.iter():
            if cv.tag.rsplit("}", 1)[-1] != "cvParam":
                continue
            acc = cv.get("accession")
            if acc == "MS:1000511":
                level = int(cv.get("value"))
            elif acc == "MS:1000744":
                sel = cv.get("value")
            elif acc == "MS:1000827":
                iso = cv.get("value")
        arrays = helpers._decode_arrays(el)
        mz = arrays.get("mz") or ()
        inten = arrays.get("intensity") or ()
        base = max(range(len(inten)), key=lambda i: inten[i]) if inten else None
        m = re.search(r"scan=(\d+)", native or "")
        out[native] = {"index": int(el.get("index")), "position": position,
                       "scan": int(m.group(1)) if m else None, "msLevel": level,
                       "selectedIonMzText": sel, "isolationTargetMzText": iso, "peakCount": len(mz),
                       "peaksSha256": peak_digest(mz, inten),
                       "totalIntensity": float(sum(inten)),
                       "basePeakMz": mz[base] if base is not None else None}
        el.clear()
    return out


def read_mgf_titles(path):
    titles = []
    with open(path) as f:
        for line in f:
            if line.startswith("TITLE="):
                titles.append(line[6:].rstrip("\n"))
    return titles


# --------------------------------------------------------------------------
# 3. the throwaway mzTab generator
# --------------------------------------------------------------------------

PROFORMA_COL = "opt_global_cv_MS:1003169_proforma_peptidoform_sequence"


def strip_percolator_peptide(text):
    m = re.fullmatch(r"(.)\.(.+)\.(.)", text)
    if not m:
        raise SpikeFailure("Percolator peptide %r is not <prev>.<peptide>.<next>" % text)
    core = m.group(2)
    residues = re.sub(r"\[[^]]*\]", "", core)
    mods = []
    pos = 0
    for tok in re.finditer(r"([A-Z])(\[([^]]*)\])?", core):
        pos += 1
        if tok.group(3) is not None:
            mods.append((pos, float(tok.group(3))))
    if len(residues) != pos:
        raise SpikeFailure("Percolator peptide %r has a modification this spike cannot place" % text)
    return m.group(1), residues, m.group(3), mods


def join_psm(row, pepxml):
    """Join one Percolator PSM to its Comet search hit; fail loudly, naming it."""
    q = pepxml.get((row["scan"], row["charge"]))
    if q is None:
        raise SpikeFailure("%s: no pepXML spectrum_query for scan %d charge %d"
                           % (row["psmId"], row["scan"], row["charge"]))
    hit = q["hits"].get(row["rank"])
    if hit is None:
        raise SpikeFailure("%s: pepXML has no search_hit number %d" % (row["psmId"], row["rank"]))
    prev, residues, nxt, pmods = strip_percolator_peptide(row["peptide"])
    if residues != hit["peptide"] or prev != hit["prev"] or nxt != hit["next"]:
        raise SpikeFailure("%s: Percolator peptide %s != pepXML hit %s.%s.%s"
                           % (row["psmId"], row["peptide"], hit["prev"], hit["peptide"], hit["next"]))
    variable = [(m["position"], float(m["deltaText"])) for m in hit["mods"] if m["kind"] == "variable"]
    if len(variable) != len(pmods) or any(a[0] != b[0] or abs(a[1] - b[1]) > 1e-3
                                          for a, b in zip(sorted(variable), sorted(pmods))):
        raise SpikeFailure("%s: variable mods differ: Percolator %r, pepXML %r" % (row["psmId"], pmods, variable))
    if hit["ntermMass"] or hit["ctermMass"]:
        # PDV's bracket parser has no C-terminal form, and this spike does not need N-term.
        raise SpikeFailure("%s: terminal modification present; this spike cannot represent it" % row["psmId"])
    # Protein lists can disagree between Comet's own PIN (hence Percolator) and its pepXML; the
    # spike transcribes Percolator's and records the disagreement (see prepare()).
    return q, hit


def proforma(hit):
    by_pos = {}
    for m in hit["mods"]:
        if m["position"] in by_pos:
            raise SpikeFailure("two modifications on residue %d of %s" % (m["position"], hit["peptide"]))
        by_pos[m["position"]] = m["deltaText"]
    out = []
    for i, aa in enumerate(hit["peptide"], start=1):
        out.append(aa)
        if i in by_pos:
            d = by_pos[i]
            out.append("[%s%s]" % ("" if d.startswith(("+", "-")) else "+", d))
    return "".join(out)


def chemmod_list(hit):
    if not hit["mods"]:
        return "null"
    return ",".join("%d-CHEMMOD:%s%s" % (m["position"], "" if m["deltaText"].startswith(("+", "-")) else "+",
                                         m["deltaText"]) for m in sorted(hit["mods"], key=lambda m: m["position"]))


def spectra_ref(style, run_index, native, spec_index_info, mgf_ordinal):
    if style == "native":
        return "ms_run[%d]:%s" % (run_index, native)
    if style == "index":
        return "ms_run[%d]:index=%d" % (run_index, spec_index_info["index"])
    if style == "scan-position":
        return "ms_run[%d]:scan=%d" % (run_index, spec_index_info["position"])
    if style == "mgf-index":
        return "ms_run[%d]:index=%d" % (run_index, mgf_ordinal)
    raise SpikeFailure("unknown spectra_ref style %r" % style)


def write_mztab(path, psms, runs, style, calc_mz="omit", location_first=False):
    """psms: list of dicts produced by build_psm(); runs: [(file basename, absolute path)]."""
    lines = []
    mtd = [("mzTab-version", "1.0.0"), ("mzTab-mode", "Summary"), ("mzTab-type", "Identification"),
           ("description", "THROWAWAY Phase 11 spike: Comet + Percolator 3.07.1, generated for PDV only"),
           ("psm_search_engine_score[1]", "[MS, MS:1001492, percolator:score, ]"),
           ("psm_search_engine_score[2]", "[MS, MS:1001491, percolator:Q value, ]"),
           ("psm_search_engine_score[3]", "[MS, MS:1001493, percolator:PEP, ]")]
    for n, (_, absolute) in enumerate(runs, start=1):
        fmt = ("[MS, MS:1001062, Mascot MGF format, ]" if absolute.lower().endswith(".mgf")
               else "[MS, MS:1000584, mzML format, ]")
        idf = ("[MS, MS:1000774, multiple peak list nativeID format, ]" if absolute.lower().endswith(".mgf")
               else "[MS, MS:1000768, Thermo nativeID format, ]")
        loc = ("ms_run[%d]-location" % n, "file://" + absolute)
        rest = [("ms_run[%d]-format" % n, fmt), ("ms_run[%d]-id_format" % n, idf)]
        # PDV keys its run -> file map on every MTD key starting "ms_run", split at '-', so a
        # later ms_run[n]-format line overwrites the location (MztabImport.java:260-262).
        mtd.extend(([loc] + rest) if location_first else (rest + [loc]))
    for k, v in mtd:
        lines.append("MTD\t%s\t%s" % (k, v))
    cols = ["sequence", "PSM_ID", "accession", "unique", "database", "database_version", "search_engine",
            "search_engine_score[1]", "search_engine_score[2]", "search_engine_score[3]", "modifications",
            "retention_time", "charge", "exp_mass_to_charge"]
    if calc_mz != "omit":
        cols.append("calc_mass_to_charge")
    cols += ["uri", "spectra_ref", "pre", "post", "start", "end", PROFORMA_COL, "opt_global_percolator_PSMId"]
    lines.append("PSH\t" + "\t".join(cols))
    for n, p in enumerate(psms, start=1):
        v = {"sequence": p["hit"]["peptide"], "PSM_ID": str(n),
             "accession": ";".join(p["row"]["proteins"]), "unique": "null", "database": "null",
             "database_version": "null",
             "search_engine": "[MS, MS:1002251, Comet, ]|[MS, MS:1001490, Percolator, ]",
             "search_engine_score[1]": p["row"]["score"], "search_engine_score[2]": p["row"]["q"],
             "search_engine_score[3]": p["row"]["pep"], "modifications": chemmod_list(p["hit"]),
             "retention_time": p["query"]["rtSec"] or "null", "charge": str(p["row"]["charge"]),
             "exp_mass_to_charge": p["spectrum"]["selectedIonMzText"] or "null",
             "calc_mass_to_charge": "null", "uri": "null",
             "spectra_ref": spectra_ref(style, p["runIndex"], p["query"]["nativeId"], p["spectrum"],
                                        p.get("mgfOrdinal")),
             "pre": p["hit"]["prev"], "post": p["hit"]["next"], "start": "null", "end": "null",
             PROFORMA_COL: proforma(p["hit"]), "opt_global_percolator_PSMId": p["row"]["psmId"]}
        fields = [v[c] for c in cols]
        if any(f == "" or "\t" in f for f in fields):
            # Java's String.split("\t") drops trailing empty fields, and PDV then indexes past the
            # end of the row (MztabImport.java:448-452): never write an empty field.
            raise SpikeFailure("%s: empty or tab-bearing field" % p["row"]["psmId"])
        lines.append("PSM\t" + "\t".join(fields))
        p["ref"] = v["spectra_ref"]
        p["rowKey"] = str(n - 1)   # PDV's row key is the 0-based data-row number (MztabImport.java:418,425)
    with open(path, "w", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return path


# --------------------------------------------------------------------------
# 4. the display, PDV, and the probes
# --------------------------------------------------------------------------


class Xvfb:
    def __init__(self):
        self.proc = None
        self.display = None
        self.env = None

    def start(self):
        xvfb = os.path.join(X11_ROOT, "usr", "bin", "Xvfb")
        if not os.access(xvfb, os.X_OK):
            raise SpikeFailure("no project-local Xvfb at %s (bootstrap: scripts/feasibility/"
                               "javafx-smoke.sh then javafx-headed-xvfb.sh)" % xvfb)
        gcc = shutil.which("gcc")
        if not gcc:
            raise SpikeFailure("no gcc: the xkbcomp path shim cannot be built")
        os.makedirs(BUILD, exist_ok=True)
        shim = os.path.join(BUILD, "xkbcomp-path-shim.so")
        subprocess.run([gcc, "-shared", "-fPIC", "-O1", "-o", shim, SHIM_SRC], check=True)
        if not os.path.getsize(shim):
            raise SpikeFailure("gcc produced an empty shim")
        for n in range(97, 80, -1):
            if not os.path.exists("/tmp/.X%d-lock" % n) and not os.path.exists("/tmp/.X11-unix/X%d" % n):
                self.display = n
                break
        if self.display is None:
            raise SpikeFailure("no free X display in 81..97")
        env = dict(os.environ)
        env.update({
            "LD_LIBRARY_PATH": ":".join([os.path.join(X11_ROOT, "usr", "lib", "x86_64-linux-gnu"),
                                         os.path.join(X11_ROOT, "lib", "x86_64-linux-gnu"),
                                         os.path.join(FONT_ROOT, "usr", "lib", "x86_64-linux-gnu")]),
            "FONTCONFIG_PATH": os.path.join(FONT_ROOT, "etc", "fonts"),
            "XDG_DATA_HOME": os.path.join(FONT_ROOT, "usr", "share"),
            "XDG_CACHE_HOME": os.path.join(BUILD, "fccache")})
        os.makedirs(env["XDG_CACHE_HOME"], exist_ok=True)
        xenv = dict(env, LD_PRELOAD=shim, XKBCOMP_PATH=os.path.join(X11_ROOT, "usr", "bin", "xkbcomp"),
                    XKB_CONFIG_ROOT=os.path.join(X11_ROOT, "usr", "share", "X11", "xkb"))
        self.log = open(os.path.join(BUILD, "xvfb.log"), "w")
        self.proc = subprocess.Popen([xvfb, ":%d" % self.display, "-screen", "0", "1280x1024x24",
                                      "-nolisten", "tcp", "-xkbdir",
                                      os.path.join(X11_ROOT, "usr", "share", "X11", "xkb")],
                                     stdout=self.log, stderr=subprocess.STDOUT, env=xenv)
        up = wait_until(lambda: os.path.exists("/tmp/.X11-unix/X%d" % self.display) or self.proc.poll() is not None,
                        20)
        if not up or self.proc.poll() is not None:
            raise SpikeFailure("Xvfb did not come up on :%d (see %s)" % (self.display, self.log.name))
        env["DISPLAY"] = ":%d" % self.display
        self.env = env
        log("Xvfb up on :%d (pid %d)" % (self.display, self.proc.pid))

    def stop(self):
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(10)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(10)
        if self.display is not None:
            for p in ("/tmp/.X%d-lock" % self.display,):
                try:
                    os.unlink(p)
                except OSError:
                    pass


def free_port():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))   # never a host proxy


def http(port, path, method="GET"):
    req = urllib.request.Request("http://127.0.0.1:%d%s" % (port, path), method=method)
    try:
        with _OPENER.open(req, timeout=5) as r:
            return {"status": r.status, "body": r.read().decode("utf-8", "replace")}
    except urllib.error.HTTPError as e:
        return {"status": e.code, "body": e.read().decode("utf-8", "replace")}
    except (urllib.error.URLError, OSError) as e:
        return {"status": None, "body": "%s: %s" % (type(e).__name__, getattr(e, "reason", e))}


def _hex_addr(text):
    ip_hex, port_hex = text.split(":")
    port = int(port_hex, 16)
    raw = bytes.fromhex(ip_hex)
    if len(raw) == 4:
        return socket.inet_ntop(socket.AF_INET, raw[::-1]), port
    words = b"".join(raw[i:i + 4][::-1] for i in range(0, 16, 4))
    return socket.inet_ntop(socket.AF_INET6, words), port


def _is_local(ip):
    return (ip.startswith("127.") or ip in ("0.0.0.0", "::", "::1")
            or ip.startswith("::ffff:127.") or ip == "::ffff:0.0.0.0")


class SocketSampler(threading.Thread):
    """Every 50 ms: every socket the process owns, with its remote endpoint."""

    def __init__(self, pid):
        super().__init__(daemon=True)
        self.pid = pid
        self.stop_event = threading.Event()
        self.seen = {}
        self.samples = 0

    def run(self):
        while not self.stop_event.is_set():
            inodes = set()
            try:
                for fd in os.listdir("/proc/%d/fd" % self.pid):
                    try:
                        target = os.readlink("/proc/%d/fd/%s" % (self.pid, fd))
                    except OSError:
                        continue
                    if target.startswith("socket:["):
                        inodes.add(target[8:-1])
                for proto in ("tcp", "tcp6", "udp", "udp6"):
                    with open("/proc/%d/net/%s" % (self.pid, proto)) as f:
                        next(f)
                        for line in f:
                            cols = line.split()
                            if cols[9] in inodes:
                                lip, lport = _hex_addr(cols[1])
                                rip, rport = _hex_addr(cols[2])
                                key = (proto, lip, lport, rip, rport)
                                self.seen.setdefault(key, cols[3])
            except (OSError, StopIteration):
                if not os.path.exists("/proc/%d" % self.pid):
                    return
            self.samples += 1
            self.stop_event.wait(0.05)

    def external(self):
        return sorted({"%s %s:%d -> %s:%d" % k for k in self.seen if not _is_local(k[3])})


class Pdv:
    """One PDV denovo-gui process, with its stdout parsed for observer lines."""

    def __init__(self, name, xenv, mztab, spectra, observer_cp=None):
        self.name = name
        self.dir = os.path.join(OUT, "runs", name)
        self.home = os.path.join(self.dir, "home")
        os.makedirs(self.home, exist_ok=True)
        self.port = free_port()
        java = shutil.which("java")
        jvm = ["-Duser.home=" + self.home]
        args = ["denovo-gui", "--mztab", mztab, "--spectrum", ",".join(spectra), "--port", str(self.port)]
        if observer_cp:
            self.argv = [java] + jvm + ["-cp", observer_cp + os.pathsep + PDV_JAR, "PdvObserver"] + args
        else:
            self.argv = [java] + jvm + ["-jar", PDV_JAR] + args
        env = dict(xenv, HOME=self.home)
        self.stdout_path = os.path.join(self.dir, "stdout.txt")
        self.stderr_path = os.path.join(self.dir, "stderr.txt")
        self.events = []
        self.extra_events = []
        self.lines = []
        self.err = open(self.stderr_path, "w")
        self.proc = subprocess.Popen(self.argv, stdout=subprocess.PIPE, stderr=self.err, env=env,
                                     cwd=self.dir, text=True, bufsize=1)
        self.started = time.monotonic()
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()
        self.sampler = SocketSampler(self.proc.pid)
        self.sampler.start()

    def _read(self):
        with open(self.stdout_path, "w") as out:
            for line in self.proc.stdout:
                out.write(line)
                out.flush()
                self.lines.append(line.rstrip("\n"))
                if line.startswith("PDVOBS "):
                    try:
                        self.events.append(json.loads(line[7:]))
                    except ValueError:
                        self.events.append({"event": "unparseable", "line": line})

    def stderr_text(self):
        self.err.flush()
        with open(self.stderr_path) as f:
            return f.read()

    def displayed(self):
        return [e for e in self.events if e.get("event") == "displayed"]

    def poll_ready(self, deadline_s=READY_DEADLINE_S):
        seen = []

        def probe():
            if self.proc.poll() is not None:
                return {"status": "process-exited", "code": self.proc.returncode}
            r = http(self.port, "/ready")
            if not seen or seen[-1] != (r["status"], r["body"]):
                seen.append((r["status"], r["body"]))
            return r if r["status"] == 200 else None
        result = wait_until(probe, deadline_s, 0.25)
        return {"ready": bool(result and result.get("status") == 200),
                "seconds": round(time.monotonic() - self.started, 2),
                "distinctResponses": [list(s) for s in seen]}

    def quiet(self, seconds=1.0, deadline_s=DISPLAY_DEADLINE_S):
        """Wait (with a deadline) until no display event has arrived for ``seconds``."""
        state = {"n": len(self.displayed()), "t": time.monotonic()}

        def settled():
            n = len(self.displayed())
            if n != state["n"]:
                state.update(n=n, t=time.monotonic())
            return time.monotonic() - state["t"] >= seconds
        return wait_until(settled, deadline_s, 0.05)

    def select(self, ref, expect_display=True):
        if expect_display:
            self.quiet()   # PDV's own first render can arrive late; never mistake it for ours
        before = len(self.displayed())
        r = http(self.port, "/select?ref=" + urllib.parse.quote(ref, safe=""))
        shown = None
        if expect_display:
            got = wait_until(lambda: len(self.displayed()) > before, DISPLAY_DEADLINE_S, 0.05)
            if got:
                self.quiet(0.5)
                after = self.displayed()[before:]
                if len(after) > 1:
                    self.extra_events.append({"ref": ref, "events": len(after)})
                shown = after[-1]
            else:
                shown = None
        else:
            # For an unknown ref nothing must be drawn: watch for a short window.
            wait_until(lambda: len(self.displayed()) > before, 2.0, 0.05)
            shown = self.displayed()[before] if len(self.displayed()) > before else None
        return r, shown

    def stop(self):
        self.sampler.stop_event.set()
        if self.proc.poll() is None:
            self.proc.send_signal(signal.SIGTERM)
            try:
                self.proc.wait(15)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(15)
        self.sampler.join(5)
        self.reader.join(5)
        self.err.close()

    def files_written(self):
        out = []
        for base, _, files in os.walk(self.dir):
            for f in files:
                p = os.path.relpath(os.path.join(base, f), self.dir)
                if p not in ("stdout.txt", "stderr.txt"):
                    out.append(p)
        return sorted(out)


# --------------------------------------------------------------------------
# 5. building the PSM sets
# --------------------------------------------------------------------------


def select_psms(rows3):
    """A modest, deterministic set covering the cases the brief names."""
    picked = []

    def first(pred, why):
        for r in rows3:
            if pred(r) and r not in [p for p, _ in picked]:
                picked.append((r, why))
                return r
        raise SpikeFailure("no Percolator PSM for case: %s" % why)
    unmod = lambda r: "[" not in r["peptide"] and "C" not in r["peptide"].split(".")[1]
    first(lambda r: unmod(r) and r["charge"] == 2 and len(r["proteins"]) == 1, "unmodified, charge 2")
    first(lambda r: unmod(r) and r["charge"] == 3 and len(r["proteins"]) == 1, "unmodified, charge 3")
    first(lambda r: r["charge"] == 4, "charge 4")
    first(lambda r: r["charge"] == 5, "charge 5")
    first(lambda r: r["peptide"].count("M[15.9949]") == 1, "one M[15.9949] oxidation")
    first(lambda r: r["peptide"].count("M[15.9949]") >= 2, "two M[15.9949] oxidations")
    first(lambda r: len(r["proteins"]) > 1, "multi-protein")
    first(lambda r: "C" in r["peptide"].split(".")[1] and "[" not in r["peptide"],
          "cysteine (static carbamidomethyl, not shown in the Percolator peptide)")
    first(lambda r: "C" in r["peptide"].split(".")[1] and "M[15.9949]" in r["peptide"],
          "cysteine plus oxidation")
    first(lambda r: r["scan"] == 11892, "Phase 00's reference spectrum, scan 11892 (index 705, position 706)")
    # A spectrum that carries two target PSMs (two charge states): both rows, in file order.
    scans = {}
    for r in rows3:
        scans.setdefault(r["scan"], []).append(r)
    for r in rows3:
        others = [o for o in scans[r["scan"]] if o is not r]
        if others and r not in [p for p, _ in picked] and others[0] not in [p for p, _ in picked]:
            picked.append((r, "spectrum with two target PSMs: first row"))
            picked.append((others[0], "spectrum with two target PSMs: second row (same spectra_ref)"))
            break
    picked.append((rows3[-1], "lowest-scoring K562_3 target PSM"))
    order = {id(r): i for i, r in enumerate(rows3)}
    picked.sort(key=lambda pw: order[id(pw[0])])
    return picked


def build_psm(row, why, run_index, pepxml, mzml, mgf_titles=None):
    q, hit = join_psm(row, pepxml)
    spec = mzml.get(q["nativeId"])
    if spec is None:
        raise SpikeFailure("%s: native id %r not in the mzML" % (row["psmId"], q["nativeId"]))
    if spec["scan"] != row["scan"] or spec["msLevel"] != 2:
        raise SpikeFailure("%s: mzML spectrum %r is scan %s level %s" % (row["psmId"], q["nativeId"],
                                                                         spec["scan"], spec["msLevel"]))
    p = {"row": row, "why": why, "runIndex": run_index, "query": q, "hit": hit, "spectrum": spec}
    if mgf_titles is not None:
        p["mgfOrdinal"] = mgf_titles.index(q["nativeId"])
    return p


# --------------------------------------------------------------------------
# 6. judging what PDV drew
# --------------------------------------------------------------------------


def judge_display(p, shown, source):
    """Return a list of problems ([] = PDV drew this PSM on its own spectrum)."""
    problems = []
    if shown is None:
        return ["no display event within %ds" % DISPLAY_DEADLINE_S]
    spec = shown.get("spectrum")
    if spec is None:
        return ["PDV drew NO spectrum (border %r)" % shown.get("border")]
    truth = p["spectrum"]
    tol_mz = 1e-6 if source == "mzml" else 2e-5        # the Phase 00 MGF writes %.6f / %.5f
    # PDV's mzML route takes msftbx's PrecursorInfo.getMzTarget(), which is the ISOLATION WINDOW
    # TARGET (MS:1000827); the MGF route takes PEPMASS, which Phase 00's converter writes from the
    # SELECTED ION m/z (MS:1000744).  Each is compared with the value that route really carries.
    want_pre = truth["isolationTargetMzText"] if source == "mzml" else truth["selectedIonMzText"]
    if abs(spec["precursorMz"] - float(want_pre)) > tol_mz:
        problems.append("precursor m/z %r != mzML %s" % (spec["precursorMz"], want_pre))
    if spec["peakCount"] != truth["peakCount"]:
        problems.append("peak count %d != mzML %d" % (spec["peakCount"], truth["peakCount"]))
    rel = abs(spec["totalIntensity"] - truth["totalIntensity"]) / max(truth["totalIntensity"], 1e-12)
    if rel > (1e-9 if source == "mzml" else 1e-5):
        problems.append("total intensity %r != mzML %r" % (spec["totalIntensity"], truth["totalIntensity"]))
    if abs(spec["basePeakMz"] - truth["basePeakMz"]) > tol_mz:
        problems.append("base peak %r != mzML %r" % (spec["basePeakMz"], truth["basePeakMz"]))
    if source == "mgf" and spec["title"] != p["query"]["nativeId"]:
        problems.append("MGF title %r != pepXML native id %r" % (spec["title"], p["query"]["nativeId"]))
    pep = shown.get("peptide") or {}
    if pep.get("sequence") != p["hit"]["peptide"]:
        problems.append("peptide %r != pepXML %r" % (pep.get("sequence"), p["hit"]["peptide"]))
    if pep.get("charge") != p["row"]["charge"]:
        problems.append("charge %r != %d" % (pep.get("charge"), p["row"]["charge"]))
    drawn = sorted((m["site"], m["mass"]) for m in pep.get("modifications", []))
    want = sorted((m["position"], float(m["deltaText"])) for m in p["hit"]["mods"])
    if len(drawn) != len(want) or any(a[0] != b[0] or a[1] is None or abs(a[1] - b[1]) > 1e-9
                                      for a, b in zip(drawn, want)):
        problems.append("modifications drawn %r != pepXML %r" % (drawn, want))
    if pep.get("theoreticMz") is not None:
        z = p["row"]["charge"]
        comet_mz = (float(p["hit"]["calcNeutralPepMass"]) + z * PROTON) / z
        if abs(pep["theoreticMz"] - comet_mz) > 2e-3:
            problems.append("PDV theoretical m/z %.6f != Comet calc %.6f" % (pep["theoreticMz"], comet_mz))
    return problems


# --------------------------------------------------------------------------
# 7. the experiments
# --------------------------------------------------------------------------


class Spike:
    def __init__(self, only):
        self.only = only
        self.results = {"experiments": {}, "failures": []}
        self.helpers = load_helpers()

    def check(self, exp, ok, what):
        self.results["experiments"][exp].setdefault("checks", []).append({"ok": bool(ok), "check": what})
        log("  [%s] %s" % ("PASS" if ok else "FAIL", what))
        if not ok:
            self.results["failures"].append("%s: %s" % (exp, what))

    def prepare(self):
        os.makedirs(OUT, exist_ok=True)
        self.results["pdv"] = verify_pdv()
        log("PDV verified: %s" % json.dumps(self.results["pdv"]))
        rows = read_percolator(PSMS)
        self.rows = {3: [r for r in rows if r["base"].endswith("K562_3")],
                     4: [r for r in rows if r["base"].endswith("K562_4")]}
        self.pepxml = {k: read_pepxml(v) for k, v in PEPXML.items()}
        self.mzml = {k: read_mzml(v, self.helpers) for k, v in MZML.items()}
        for k in (3, 4):
            ms2 = [s for s in self.mzml[k].values() if s["msLevel"] == 2]
            differ = sum(1 for s in self.mzml[k].values() if s["position"] != s["scan"])
            self.results["mzml_%d" % k] = {
                "spectra": len(self.mzml[k]), "ms2": len(ms2),
                "firstScan": min(s["scan"] for s in self.mzml[k].values()),
                "lastScan": max(s["scan"] for s in self.mzml[k].values()),
                "positionDiffersFromScan": differ, "sha256": sha256(MZML[k])}
            if differ != len(self.mzml[k]):
                raise SpikeFailure("K562_%d: position == scan for some spectra; the identity test "
                                   "would prove nothing" % k)
        # MGF re-encoding, Phase 00's converter, unchanged.
        self.mgf = {}
        for k in (3, 4):
            dst = os.path.join(OUT, "mgf", os.path.basename(MZML[k])[:-5] + ".mgf")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if self.helpers.mzml_to_mgf(MZML[k], dst) != 0:
                raise SpikeFailure("MGF conversion failed for %s" % MZML[k])
            self.mgf[k] = (dst, read_mgf_titles(dst))
        census = {}
        for k in (3, 4):
            c = census.setdefault("K562_%d" % k, {"psms": 0, "hitRankNotOrdinal": 0, "proteinListDiffers": 0,
                                                  "examples": []})
            for r in self.rows[k]:
                q, hit = join_psm(r, self.pepxml[k])
                c["psms"] += 1
                if hit["hitRank"] != r["rank"]:
                    c["hitRankNotOrdinal"] += 1
                if sorted(r["proteins"]) != sorted(hit["proteins"]):
                    c["proteinListDiffers"] += 1
                    if len(c["examples"]) < 3:
                        c["examples"].append({"psmId": r["psmId"], "percolator": r["proteins"],
                                              "pepxml": hit["proteins"]})
        self.results["sourceCensus"] = census
        log("source census: %s" % json.dumps(census))
        picked = select_psms(self.rows[3])
        self.set3 = picked
        self.results["psmSet"] = [{"psmId": r["psmId"], "why": why, "peptide": r["peptide"],
                                   "proteins": r["proteins"]} for r, why in picked]
        log("K562_3 PSMs chosen: %d" % len(picked))
        self.results["percolatorK562_3"] = len(self.rows[3])
        scans = {}
        for r in self.rows[3]:
            scans.setdefault(r["scan"], 0)
            scans[r["scan"]] += 1
        self.results["k562_3SpectraWithMoreThanOneTargetPsm"] = sum(1 for v in scans.values() if v > 1)
        self.results["k562_3DistinctSpectraWithTargetPsm"] = len(scans)

    def psms(self, k, picked, run_index, with_mgf=False):
        return [build_psm(r, why, run_index, self.pepxml[k], self.mzml[k],
                          self.mgf[k][1] if with_mgf else None) for r, why in picked]

    def stage(self, name, files):
        """Copy spectrum files into the run's own directory, so PDV's side files land there."""
        d = os.path.join(OUT, "runs", name, "spectra")
        os.makedirs(d, exist_ok=True)
        out = []
        for f in files:
            dst = os.path.join(d, os.path.basename(f))
            shutil.copyfile(f, dst)
            out.append(dst)
        return out

    def wanted(self, name):
        return not self.only or name in self.only

    def run(self, name, description, k_sets, style, source, observer=True, calc_mz="omit",
            location_first=False, expect="ready", spectra_override=None, extra=None):
        if not self.wanted(name):
            return
        log("\n=== %s: %s" % (name, description))
        exp = self.results["experiments"].setdefault(name, {"description": description})
        rundir = os.path.join(OUT, "runs", name)
        shutil.rmtree(rundir, ignore_errors=True)
        os.makedirs(rundir)
        all_psms, spectra = [], []
        for run_index, (k, picked) in enumerate(k_sets, start=1):
            if source == "mgf":
                src = self.mgf[k][0]
            elif spectra_override:
                src = spectra_override[run_index - 1]
            else:
                src = MZML[k]
            spectra.append(src)
            all_psms += self.psms(k, picked, run_index, with_mgf=(source == "mgf"))
        staged = self.stage(name, spectra)
        mztab = write_mztab(os.path.join(rundir, name + ".mztab"), all_psms,
                            [(os.path.basename(s), s) for s in staged], style, calc_mz, location_first)
        exp.update({"mztab": os.path.relpath(mztab, ROOT), "mztabSha256": sha256(mztab),
                    "rows": len(all_psms), "spectraRefStyle": style, "spectrumFiles":
                    [os.path.basename(s) for s in staged], "observer": observer})
        pdv = Pdv(name, self.xvfb.env, mztab, staged, self.observer_cp if observer else None)
        exp["argv"] = [os.path.relpath(a, ROOT) if a.startswith(ROOT) else a for a in pdv.argv]
        try:
            ready = pdv.poll_ready(READY_DEADLINE_S if expect == "ready" else 45)
            exp["ready"] = ready
            log("  /ready: %s after %.1fs; responses %s" % (ready["ready"], ready["seconds"],
                                                          ready["distinctResponses"]))
            if expect != "ready":
                self.check(name, not ready["ready"], "/ready never answers 200 (%s)" % expect)
                err = pdv.stderr_text()
                exp["stderrExcerpt"] = [l for l in err.splitlines() if "Exception" in l or "\tat PDVGUI" in l][:6]
                self.check(name, expect in err, "stderr names %s" % expect)
                r = http(pdv.port, "/select?ref=" + urllib.parse.quote(all_psms[0]["ref"], safe=""))
                exp["selectWhileBroken"] = r
                self.check(name, r["status"] == 503 and r["body"] == "not ready",
                           "/select while not ready -> 503 'not ready' (got %r)" % (r,))
                return
            self.check(name, ready["ready"], "/ready reached 200 by polling")
            if not ready["ready"]:
                exp["stderrExcerpt"] = pdv.stderr_text().splitlines()[-20:]
                return
            if observer:
                initial = wait_until(lambda: pdv.displayed(), 10, 0.05)
                exp["initialDisplay"] = pdv.displayed()[0] if initial else None
            exp["selects"] = []
            first_row_for_ref = {}
            for p in all_psms:
                first_row_for_ref.setdefault(p["ref"], p)
            for p in all_psms:
                r, shown = pdv.select(p["ref"], expect_display=observer)
                entry = {"psmId": p["row"]["psmId"], "why": p["why"], "ref": p["ref"],
                         "expectedRowKey": p["rowKey"], "response": r, "displayed": shown}
                exp["selects"].append(entry)
                self.check(name, r["status"] == 200 and r["body"] == "selected",
                           "%s -> /select %r answered %r" % (p["row"]["psmId"], p["ref"], (r["status"], r["body"])))
                if not observer:
                    continue
                owner = first_row_for_ref[p["ref"]]
                if owner is not p:
                    # Duplicate spectra_ref: PDV keeps the first row (putIfAbsent, MztabImport.java:425).
                    entry["unreachable"] = True
                    self.check(name, shown is not None and shown.get("rowKey") == owner["rowKey"],
                               "%s shares its spectra_ref with %s: PDV shows row %s, not this PSM's row %s"
                               % (p["row"]["psmId"], owner["row"]["psmId"], owner["rowKey"], p["rowKey"]))
                    continue
                if extra == "no-spectrum":
                    ok = (shown is not None and shown.get("spectrum") is None and shown.get("rowKey") == p["rowKey"]
                          and str(shown.get("border", "")).startswith("No spectrum to display"))
                    self.check(name, ok, "%s: row %s selected but NO spectrum drawn (border %r)"
                               % (p["row"]["psmId"], p["rowKey"], (shown or {}).get("border")))
                    continue
                problems = judge_display(p, shown, source)
                if shown is not None and shown.get("rowKey") != p["rowKey"]:
                    problems.append("row %r drawn, expected %r" % (shown.get("rowKey"), p["rowKey"]))
                entry["problems"] = problems
                self.check(name, not problems, "%s drawn on its own spectrum %s%s"
                           % (p["row"]["psmId"], p["query"]["nativeId"],
                              "" if not problems else ": " + "; ".join(problems)))
            unknown = "ms_run[1]:controllerType=0 controllerNumber=1 scan=99999999"
            r, shown = pdv.select(unknown, expect_display=False)
            exp["unknownRef"] = {"ref": unknown, "response": r, "displayed": shown}
            self.check(name, r["status"] == 404 and r["body"] == "unknown ref",
                       "unknown ref -> 404 'unknown ref' (got %r)" % ((r["status"], r["body"]),))
            if observer:
                self.check(name, shown is None, "unknown ref redraws nothing")
            r = http(pdv.port, "/select")
            exp["missingRef"] = r
            self.check(name, r["status"] == 400 and r["body"] == "missing ref",
                       "no ref -> 400 'missing ref' (got %r)" % ((r["status"], r["body"]),))
            r = http(pdv.port, "/select?ref=" + urllib.parse.quote(all_psms[0]["ref"], safe=""), method="POST")
            exp["postSelect"] = r
            self.check(name, r["status"] == 200, "POST /select also accepted (CasanovoGUI uses POST)")
            if name == "R1-jar-mgf":
                r = http(pdv.port, "/shutdown")
                exp["shutdown"] = r
                self.check(name, r["status"] == 200 and r["body"] == "bye", "/shutdown -> 200 'bye'")
                after = wait_until(lambda: http(pdv.port, "/ready")["status"] is None, 5, 0.1)
                exp["controlServerGoneAfterShutdown"] = bool(after)
                self.check(name, bool(after), "control port refuses connections after /shutdown")
                alive = pdv.proc.poll() is None
                exp["processAliveAfterShutdown"] = alive
                self.check(name, alive, "/shutdown stops the server only: the PDV process (and window) "
                                        "is still running and must be terminated by the launcher")
        finally:
            pdv.stop()
            exp["seconds"] = round(time.monotonic() - pdv.started, 1)
            exp["exitStatusAfterTerminate"] = pdv.proc.returncode
            exp["socketSamples"] = pdv.sampler.samples
            exp["selectsWithMoreThanOneRender"] = pdv.extra_events
            exp["socketsSeen"] = sorted("%s %s:%d -> %s:%d [%s]" % (k + (v,)) for k, v in pdv.sampler.seen.items())
            exp["externalSockets"] = pdv.sampler.external()
            exp["dnsLookups"] = [e for e in pdv.events if e.get("event", "").startswith("dns")]
            exp["filesWritten"] = pdv.files_written()
            exp["homeFiles"] = sorted(os.path.relpath(os.path.join(b, f), pdv.home)
                                      for b, _, fs in os.walk(pdv.home) for f in fs)
            exp["stdoutMarkers"] = [l for l in pdv.lines if not l.startswith(("PDVOBS", "SLF4J"))][:12]
            self.check(name, pdv.sampler.samples > 0, "socket sampler ran (%d samples)" % pdv.sampler.samples)
            self.check(name, not exp["externalSockets"],
                       "no socket to a non-loopback address (%s)" % (exp["externalSockets"] or "none"))
            if observer:
                # Looking up this machine's own name (InetAddress.getLocalHost) is recorded but is not
                # contact with a remote host; any other name is.
                own = {"localhost", socket.gethostname()}
                self.check(name, not [d for d in exp["dnsLookups"] if d.get("host") not in own],
                           "no lookup of any host but this machine's own name (%s)"
                           % (sorted({d.get("host") for d in exp["dnsLookups"]}) or "none"))

    def execute(self):
        self.prepare()
        ob = os.path.join(BUILD, "observer", "classes")
        shutil.rmtree(ob, ignore_errors=True)
        os.makedirs(os.path.join(ob, "META-INF", "services"))
        subprocess.run([shutil.which("javac"), "-d", ob, OBSERVER_SRC], check=True)
        with open(os.path.join(ob, "META-INF", "services", "java.net.spi.InetAddressResolverProvider"), "w") as f:
            f.write("PdvObserver$LoggingResolverProvider\n")
        if not os.path.isfile(os.path.join(ob, "PdvObserver.class")):
            raise SpikeFailure("javac produced no PdvObserver.class")
        self.observer_cp = ob
        self.xvfb = Xvfb()
        self.xvfb.start()
        s3 = self.set3
        s4 = [(r, "K562_4 top-scoring PSM %d" % i) for i, r in enumerate(self.rows[4][:2], 1)]
        s4 += [(next(r for r in self.rows[4] if "M[15.9949]" in r["peptide"]), "K562_4 first oxidised PSM")]
        try:
            self.run("R1-jar-mgf", "production shape: unmodified `java -jar` launch, MGF, "
                     "spectra_ref ms_run[1]:index=<MGF 0-based ordinal>; control API semantics",
                     [(3, s3)], "mgf-index", "mgf", observer=False)
            self.run("R2-mzml-native", "subset mzML, spectra_ref = ms_run[1]:<pepXML spectrumNativeID> "
                     "(controllerType=0 controllerNumber=1 scan=<instrument scan>)",
                     [(3, s3)], "native", "mzml", extra="no-spectrum")
            self.run("R3-mzml-index", "subset mzML, spectra_ref = ms_run[1]:index=<mzML 0-based index>",
                     [(3, s3)], "index", "mzml", expect="ArrayIndexOutOfBoundsException")
            self.run("R4-mzml-scanpos", "subset mzML, spectra_ref = ms_run[1]:scan=<1-based file position>",
                     [(3, s3)], "scan-position", "mzml")
            self.run("R5-mgf-index", "MGF (Phase 00 re-encoding), spectra_ref = ms_run[1]:index=<MGF ordinal>",
                     [(3, s3)], "mgf-index", "mgf")
            self.run("R6-multi-mgf", "two MGF files, ms_run[1] = K562_3, ms_run[2] = K562_4",
                     [(3, s3), (4, s4)], "mgf-index", "mgf")
            self.run("R7-multi-mzml", "two subset mzML files, ms_run[1]/[2], scan=<1-based position>",
                     [(3, s3), (4, s4)], "scan-position", "mzml")
            self.run("R8-crlf-fixture", "the as-fetched CRLF fixture mzML, scan=<1-based position>",
                     [(3, s3)], "scan-position", "mzml", spectra_override=[MZML_FIXTURE_CRLF])
            self.run("R9-calc-null", "calc_mass_to_charge column present with the mzTab null value",
                     [(3, s3)], "mgf-index", "mgf", calc_mz="null", expect="NumberFormatException")
            self.run("R10-location-first", "ms_run[1]-location written before ms_run[1]-format / -id_format",
                     [(3, s3)], "scan-position", "mzml", location_first=True, extra="no-spectrum")
            full = [(r, "all K562_3 target PSMs") for r in self.rows[3]]
            if self.wanted("R11-full-mgf"):
                # Every K562_3 target PSM: import at scale, paging (1000 rows/page), and how many
                # PSMs the duplicate-spectra_ref rule makes unreachable.
                self.run_full(full)
            if self.wanted("R12-per-psm-mgf"):
                # Unit 2, design decision P11-12: one MGF entry per mzTab row, written by the
                # PRODUCT writer, so PSMs that share a spectrum each have their own spectra_ref.
                self.run_per_psm(full)
        finally:
            self.xvfb.stop()

    def run_per_psm(self, full):
        name = "R12-per-psm-mgf"
        log("\n=== %s: every K562_3 target PSM (%d), one MGF entry per row by the product writer"
            % (name, len(full)))
        exp = self.results["experiments"].setdefault(
            name, {"description": "every K562_3 target PSM; per-row MGF written by PdvMgfWriter"})
        for path in (os.path.join(TOOLS_CLASSES, "org", "cometgui", "tools", "pdv", "PdvMgfWriter.class"),
                     os.path.join(DOMAIN_CLASSES, "org", "cometgui", "domain", "tools", "ToolVersion.class")):
            if not os.path.isfile(path):
                raise SpikeFailure("R12 runs the product writer and %s is missing: build it with "
                                   "mvn -o -pl cometgui-tools -am install -DskipTests" % path)
        rundir = os.path.join(OUT, "runs", name)
        shutil.rmtree(rundir, ignore_errors=True)
        spectra = os.path.join(rundir, "spectra")
        os.makedirs(spectra)
        psms = self.psms(3, full, 1)
        rows_file = os.path.join(rundir, "rows.txt")
        with open(rows_file, "w", newline="\n") as f:
            for p in psms:
                f.write(p["query"]["nativeId"] + "\n")
        classes = os.path.join(BUILD, "perpsm", "classes")
        shutil.rmtree(classes, ignore_errors=True)
        os.makedirs(classes)
        cp = os.pathsep.join([TOOLS_CLASSES, DOMAIN_CLASSES])
        subprocess.run([shutil.which("javac"), "-cp", cp, "-d", classes, PERPSM_SRC], check=True)
        mgf_name = os.path.basename(MZML[3])[:-5] + ".mgf"
        done = subprocess.run([shutil.which("java"), "-cp", os.pathsep.join([classes, cp]), "PerPsmMgf",
                               MZML[3], rows_file, spectra, mgf_name],
                              capture_output=True, text=True, timeout=300)
        mgf = os.path.join(spectra, mgf_name)
        exp["writer"] = {"stdout": done.stdout.strip(), "stderr": done.stderr.strip()[-2000:],
                         "invokedVia": "scripts/spikes/pdv_mztab_spike/PerPsmMgf.java -> "
                                       "org.cometgui.tools.pdv.PdvMgfWriter.writeOnePerRow (product code)"}
        self.check(name, done.stdout.strip() == "PERPSMMGF %d %s" % (len(psms), mgf),
                   "the product writer reports %d entries in %s (said %r)" % (len(psms), mgf_name,
                                                                             done.stdout.strip()))
        if not os.path.isfile(mgf):
            raise SpikeFailure("the product writer wrote no MGF: %s" % done.stderr[-2000:])
        entries = read_mgf_entries(mgf)
        titles = [e["title"] for e in entries]
        self.check(name, titles == [p["query"]["nativeId"] for p in psms],
                   "entry n's TITLE is row n's pepXML spectrumNativeID, for all %d rows" % len(psms))
        duplicate_titles = len(titles) - len(set(titles))
        exp["duplicateTitles"] = duplicate_titles
        exp["distinctSpectra"] = len(set(titles))
        self.check(name, duplicate_titles == self.results["percolatorK562_3"]
                   - self.results["k562_3DistinctSpectraWithTargetPsm"],
                   "the MGF repeats a title once per extra PSM of a spectrum: %d duplicate titles over "
                   "%d spectra" % (duplicate_titles, len(set(titles))))
        exact = 0
        for e, p in zip(entries, psms):
            truth = p["spectrum"]
            if (e["pepmass"] == truth["selectedIonMzText"]
                    and len(e["peaks"]) == truth["peakCount"]
                    and peak_digest([m for m, _ in e["peaks"]], [i for _, i in e["peaks"]])
                    == truth["peaksSha256"]):
                exact += 1
        self.check(name, exact == len(psms),
                   "every entry's PEPMASS is the mzML's selected-ion text and every peak parses back "
                   "to the mzML's exact double (%d of %d entries, read by the spike's own parser)"
                   % (exact, len(psms)))
        for n, p in enumerate(psms):
            p["mgfOrdinal"] = n
        staged = [mgf]   # already in the run's own directory: PDV's .mgf.cui lands beside it
        mztab = write_mztab(os.path.join(rundir, name + ".mztab"), psms,
                            [(os.path.basename(s), s) for s in staged], "mgf-index")
        refs = {}
        for p in psms:
            refs.setdefault(p["ref"], []).append(p)
        exp.update({"rows": len(psms), "distinctRefs": len(refs), "mztabSha256": sha256(mztab)})
        self.check(name, len(refs) == len(psms),
                   "every row has its own spectra_ref (%d distinct of %d)" % (len(refs), len(psms)))
        # Targets: every row of spectra that carry several PSMs -- the first few in file order, the
        # spectrum with the most PSMs, and spectra whose rows lie on PDV's second page (row >= 1000).
        groups = {}
        for p in psms:
            groups.setdefault(p["query"]["nativeId"], []).append(p)
        shared = [g for g in groups.values() if len(g) > 1]
        chosen = shared[:5]
        biggest = max(shared, key=len)
        if biggest not in chosen:
            chosen.append(biggest)
        late = [g for g in shared if int(g[0]["rowKey"]) >= 1000 and g not in chosen][:2]
        chosen += late
        targets = [p for g in chosen for p in g]
        # PDV's MGF index (compomics-utilities, inside PDV) does not keep a repeated TITLE: it
        # renames the k-th repeat of a title "<title>_k" (k = 1, 2, ...), as observed on 2026-10-10.
        # The expectation below states that rule exactly, so a PDV that changed it would fail here.
        occurrence = {}
        for p in psms:
            k = occurrence.get(p["query"]["nativeId"], 0)
            p["expectedDrawnTitle"] = p["query"]["nativeId"] + ("_%d" % k if k else "")
            occurrence[p["query"]["nativeId"]] = k + 1
        exp["targetSpectra"] = [{"nativeId": g[0]["query"]["nativeId"], "rows": [q["rowKey"] for q in g],
                                 "psms": [q["row"]["psmId"] for q in g]} for g in chosen]
        self.check(name, len(late) == 2 and len(biggest) >= 4,
                   "targets include a spectrum with %d PSMs and %d spectra whose rows start on PDV's "
                   "second page" % (len(biggest), len(late)))
        pdv = Pdv(name, self.xvfb.env, mztab, staged, self.observer_cp)
        try:
            ready = pdv.poll_ready()
            exp["ready"] = ready
            self.check(name, ready["ready"], "/ready reached 200 by polling (%.1fs)" % ready["seconds"])
            if not ready["ready"]:
                exp["stderrExcerpt"] = pdv.stderr_text().splitlines()[-20:]
                return
            initial = wait_until(lambda: pdv.displayed(), 10, 0.05)
            exp["initialDisplay"] = pdv.displayed()[0] if initial else None
            exp["selects"] = []
            drawn_rows = {}
            for p in targets:
                r, shown = pdv.select(p["ref"])
                # judge_display wants the drawn title to be the native id; R12 states PDV's
                # renaming rule instead (above), so that one comparison is replaced, not dropped.
                problems = [x for x in judge_display(p, shown, "mgf") if not x.startswith("MGF title ")]
                spec = (shown or {}).get("spectrum") or {}
                truth = p["spectrum"]
                if spec and spec.get("title") != p["expectedDrawnTitle"]:
                    problems.append("drawn title %r != %r" % (spec.get("title"), p["expectedDrawnTitle"]))
                if shown is not None and shown.get("rowKey") != p["rowKey"]:
                    problems.append("row %r drawn, expected its own row %r" % (shown.get("rowKey"), p["rowKey"]))
                if spec and float(spec["precursorMz"]) != float(truth["selectedIonMzText"]):
                    problems.append("precursor %r is not exactly the mzML's %s"
                                    % (spec["precursorMz"], truth["selectedIonMzText"]))
                if spec and spec.get("peaksSha256") != truth["peaksSha256"]:
                    problems.append("drawn peaks differ from the mzML's exact peaks (digest %s != %s)"
                                    % (spec.get("peaksSha256"), truth["peaksSha256"]))
                drawn_rows.setdefault(p["query"]["nativeId"], set()).add((shown or {}).get("rowKey"))
                exp["selects"].append({"psmId": p["row"]["psmId"], "ref": p["ref"], "row": p["rowKey"],
                                       "response": r, "displayed": shown, "problems": problems})
                self.check(name, r["status"] == 200 and r["body"] == "selected" and not problems,
                           "%s (row %s, shares %s with %d other PSM(s)) -> %r, drawn on its own row with "
                           "the exact spectrum (peaks bit-identical), its own peptide %s, titled %r%s"
                           % (p["row"]["psmId"], p["rowKey"], p["query"]["nativeId"],
                              len(groups[p["query"]["nativeId"]]) - 1, (r["status"], r["body"]),
                              p["hit"]["peptide"], p["expectedDrawnTitle"],
                              "" if not problems else ": " + "; ".join(problems)))
            for g in chosen:
                nid = g[0]["query"]["nativeId"]
                self.check(name, drawn_rows.get(nid) == {q["rowKey"] for q in g},
                           "the %d PSMs of %s were drawn on %d distinct rows (%s)"
                           % (len(g), nid, len(drawn_rows.get(nid, ())), sorted(drawn_rows.get(nid, ()))))
            unknown = "ms_run[1]:index=%d" % len(psms)
            r, shown = pdv.select(unknown, expect_display=False)
            exp["unknownRef"] = {"ref": unknown, "response": r, "displayed": shown}
            self.check(name, r["status"] == 404 and shown is None,
                       "the index one past the last row -> 404, nothing redrawn (got %r)" % ((r["status"], r["body"]),))
        finally:
            pdv.stop()
            exp["externalSockets"] = pdv.sampler.external()
            exp["filesWritten"] = pdv.files_written()
            self.check(name, not exp["externalSockets"], "no socket to a non-loopback address")

    def run_full(self, full):
        name = "R11-full-mgf"
        log("\n=== %s: every K562_3 target PSM (%d), MGF index refs" % (name, len(full)))
        exp = self.results["experiments"].setdefault(name, {"description": "every K562_3 target PSM, MGF"})
        rundir = os.path.join(OUT, "runs", name)
        shutil.rmtree(rundir, ignore_errors=True)
        os.makedirs(rundir)
        psms = self.psms(3, full, 1, with_mgf=True)
        staged = self.stage(name, [self.mgf[3][0]])
        mztab = write_mztab(os.path.join(rundir, name + ".mztab"), psms,
                            [(os.path.basename(s), s) for s in staged], "mgf-index")
        refs = {}
        for p in psms:
            refs.setdefault(p["ref"], []).append(p)
        exp.update({"rows": len(psms), "distinctRefs": len(refs),
                    "psmsUnreachableBySelect": sum(len(v) - 1 for v in refs.values()),
                    "mztabSha256": sha256(mztab)})
        self.check(name, len(psms) == len(full), "every Percolator PSM joined to its pepXML hit and spectrum")
        pdv = Pdv(name, self.xvfb.env, mztab, staged, self.observer_cp)
        try:
            ready = pdv.poll_ready()
            exp["ready"] = ready
            self.check(name, ready["ready"], "/ready reached 200 by polling (%.1fs)" % ready["seconds"])
            if not ready["ready"]:
                return
            # one target per page, last row last: exercises PDV's page switch in selectBySpectraRef
            targets = []
            for want in (0, 1100, len(psms) - 1):
                for p in psms[want:]:
                    if refs[p["ref"]][0] is p:
                        targets.append(p)
                        break
            exp["selects"] = []
            for p in targets:
                r, shown = pdv.select(p["ref"])
                problems = judge_display(p, shown, "mgf")
                if shown is not None and shown.get("rowKey") != p["rowKey"]:
                    problems.append("row %r drawn, expected %r" % (shown.get("rowKey"), p["rowKey"]))
                exp["selects"].append({"psmId": p["row"]["psmId"], "ref": p["ref"], "row": p["rowKey"],
                                       "response": r, "displayed": shown, "problems": problems})
                self.check(name, r["status"] == 200 and not problems,
                           "row %s (%s) -> %r, drawn correctly%s" % (p["rowKey"], p["row"]["psmId"], r["status"],
                                                                     "" if not problems else ": " + "; ".join(problems)))
        finally:
            pdv.stop()
            exp["externalSockets"] = pdv.sampler.external()
            exp["dnsLookups"] = [e for e in pdv.events if e.get("event", "").startswith("dns")]
            exp["filesWritten"] = pdv.files_written()
            self.check(name, not exp["externalSockets"], "no socket to a non-loopback address")


def read_mgf_entries(path):
    """[(title, pepmass text, [(m/z, intensity)])] read independently of the product's reader."""
    entries = []
    with open(path) as f:
        cur = None
        for line in f:
            line = line.rstrip("\n")
            if line == "BEGIN IONS":
                cur = {"title": None, "pepmass": None, "peaks": []}
            elif line == "END IONS":
                entries.append(cur)
                cur = None
            elif cur is not None and line.startswith("TITLE="):
                cur["title"] = line[6:]
            elif cur is not None and line.startswith("PEPMASS="):
                cur["pepmass"] = line[8:]
            elif cur is not None and line[:1].isdigit():
                m, i = line.split(" ")
                if "e" in m.lower() or "e" in i.lower():
                    raise SpikeFailure("MGF peak line %r uses exponent notation" % line)
                cur["peaks"].append((float(m), float(i)))
    return entries


def no_leftovers(spike):
    """Nothing this script started may outlive it."""
    me = os.getpid()
    left = []
    for pid in os.listdir("/proc"):
        if not pid.isdigit() or int(pid) == me:
            continue
        try:
            with open("/proc/%s/cmdline" % pid, "rb") as f:
                cmd = f.read().replace(b"\0", b" ").decode("utf-8", "replace")
        except OSError:
            continue
        if (PDV_JAR in cmd and "denovo-gui" in cmd and OUT in cmd) or \
                (spike.xvfb and spike.xvfb.display is not None and "Xvfb :%d " % spike.xvfb.display in cmd):
            left.append("%s %s" % (pid, cmd[:160]))
    return left


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--only", default="", help="comma-separated experiment names (default: all)")
    args = ap.parse_args(argv)
    if not shutil.which("java") or not shutil.which("javac"):
        print("pdv_mztab_spike: no java/javac on PATH -- source tools/env.sh first", file=sys.stderr)
        return 2
    for p in [PDV_ZIP, PDV_JAR, PSMS, MZML_FIXTURE_CRLF, HELPERS, OBSERVER_SRC, SHIM_SRC, PERPSM_SRC] + \
            list(PEPXML.values()) + list(MZML.values()):
        if not os.path.isfile(p):
            print("pdv_mztab_spike: missing input %s" % p, file=sys.stderr)
            return 2
    spike = Spike({s for s in args.only.split(",") if s})
    spike.xvfb = None
    status = 0
    try:
        spike.execute()
    except SpikeFailure as e:
        spike.results["failures"].append("ABORTED: %s" % e)
        print("pdv_mztab_spike: %s" % e, file=sys.stderr)
        status = 1
    finally:
        left = no_leftovers(spike)
        spike.results["leftoverProcesses"] = left
        if left:
            spike.results["failures"].append("processes left running: %r" % left)
    os.makedirs(OUT, exist_ok=True)
    out = os.path.join(OUT, "results.json")
    with open(out, "w") as f:
        json.dump(spike.results, f, indent=1, sort_keys=True)
    log("\nresults: %s" % os.path.relpath(out, ROOT))
    if spike.results["failures"]:
        log("FAILURES (%d):" % len(spike.results["failures"]))
        for f in spike.results["failures"]:
            log("  " + f)
        return 1
    checks = sum(len(e.get("checks", [])) for e in spike.results["experiments"].values())
    if checks == 0:
        log("no checks ran")
        return 1
    log("pdv_mztab_spike: all %d checks passed across %d experiments"
        % (checks, len(spike.results["experiments"])))
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

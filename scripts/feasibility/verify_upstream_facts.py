#!/usr/bin/env python3
"""Re-verify every row of specification.rst's "Verified Upstream Facts" table
against live upstream sources.

CometGUI PHASE-00, work unit 2. Python 3 standard library only.

Writes a machine-readable result to
``/workspace/scratch/upstream/facts-<date>.json`` and a human-readable summary
to stdout.

Every HTTP response is cached under ``/workspace/scratch/apicache`` so that
re-runs cost no GitHub API quota (unauthenticated GitHub allows 60 requests per
hour for the whole host).  Pass ``--refresh`` to force live re-fetches, or
``--refresh-github`` to refresh only ``api.github.com`` responses.

Verdicts are one of:

CONFIRMED    today's observation matches what specification.rst asserts
CHANGED      today's observation contradicts specification.rst
UNVERIFIED   this run could not establish the fact (reason recorded)

Exit code is deliberately not a proof of anything: read the JSON.
"""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import os
import platform
import re
import subprocess
import sys
import urllib.error
import urllib.request

VERIFICATION_DATE = "2026-08-29"

ROOT = "/workspace"
CACHE_DIR = os.path.join(ROOT, "scratch", "apicache")
WORK_DIR = os.path.join(ROOT, "scratch", "upstream")
OUT_JSON = os.path.join(WORK_DIR, "facts-%s.json" % VERIFICATION_DATE)

USER_AGENT = "CometGUI-phase00-upstream-verify/1.0"
HTTP_TIMEOUT = 90

COMET_TAG = "v2026.02.2"
COMET_LINUX_URL = (
    "https://github.com/UWPR/Comet/releases/download/%s/comet.linux.exe" % COMET_TAG
)
COMET_LINUX_PATH = os.path.join(WORK_DIR, "comet.linux.exe")

# Rows of the specification table that another PHASE-00 work unit owns.  This
# unit must not download or extract those artefacts (duplicate large downloads
# and file collisions), so it records the cross-reference instead of a value.
DELEGATED = {
    "percolator-308-linux-binary": ("unit 3", "docs/feasibility/percolator-artefacts.rst"),
    "percolator-3071-linux-executed": ("unit 3", "docs/feasibility/percolator-artefacts.rst"),
    "percolator-3071-macos-extracted": ("unit 3", "docs/feasibility/percolator-artefacts.rst"),
    "percolator-3071-windows-inferred": ("unit 4", "docs/feasibility/windows-artefact.rst"),
}

REFRESH = "none"          # none | all | github
_HTTP_LOG: list[dict] = []


# --------------------------------------------------------------------------
# HTTP with an on-disk cache
# --------------------------------------------------------------------------

def _cache_paths(url: str):
    stem = hashlib.sha256(url.encode("utf-8")).hexdigest()[:24]
    return (os.path.join(CACHE_DIR, stem + ".body"),
            os.path.join(CACHE_DIR, stem + ".url"))


def _should_refresh(url: str) -> bool:
    if REFRESH == "all":
        return True
    if REFRESH == "github" and "api.github.com" in url:
        return True
    return False


def http_get(url: str, accept: str | None = None):
    """Return (body_bytes, provenance_dict).  body is None on failure."""
    os.makedirs(CACHE_DIR, exist_ok=True)
    body_path, url_path = _cache_paths(url)

    if os.path.exists(body_path) and not _should_refresh(url):
        mtime = datetime.datetime.utcfromtimestamp(
            os.path.getmtime(body_path)).replace(microsecond=0)
        prov = {"url": url, "source": "cache",
                "retrieved_utc": mtime.isoformat() + "Z"}
        _HTTP_LOG.append(prov)
        return open(body_path, "rb").read(), prov

    if accept is None:
        accept = ("application/vnd.github+json"
                  if "api.github.com" in url else "*/*")
    req = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept": accept})
    now = datetime.datetime.utcnow().replace(microsecond=0).isoformat() + "Z"
    try:
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as resp:
            body = resp.read()
            headers = dict(resp.headers)
            status = resp.status
    except urllib.error.HTTPError as exc:
        body = exc.read()
        prov = {"url": url, "source": "live", "retrieved_utc": now,
                "http_status": exc.code,
                "error": body[:300].decode("utf-8", "replace")}
        rate_limited = exc.code in (403, 429) and b"rate limit" in body.lower()
        prov["rate_limited"] = rate_limited
        _HTTP_LOG.append(prov)
        return None, prov
    except Exception as exc:                       # noqa: BLE001 - report, don't crash
        prov = {"url": url, "source": "live", "retrieved_utc": now,
                "error": "%s: %s" % (type(exc).__name__, exc)}
        _HTTP_LOG.append(prov)
        return None, prov

    with open(body_path, "wb") as fh:
        fh.write(body)
    with open(url_path, "w") as fh:
        fh.write(url + "\n")
    prov = {"url": url, "source": "live", "retrieved_utc": now,
            "http_status": status}
    if "X-RateLimit-Remaining" in headers:
        prov["github_ratelimit_remaining"] = headers["X-RateLimit-Remaining"]
    _HTTP_LOG.append(prov)
    return body, prov


def http_json(url: str):
    body, prov = http_get(url)
    if body is None:
        return None, prov
    try:
        return json.loads(body.decode("utf-8")), prov
    except Exception as exc:                       # noqa: BLE001
        prov = dict(prov)
        prov["error"] = "not JSON: %s" % exc
        return None, prov


def http_text(url: str):
    body, prov = http_get(url)
    if body is None:
        return None, prov
    return body.decode("utf-8", "replace"), prov


def releases_atom_fallback(owner_repo: str):
    """Rate-limit-free fallback: the public releases Atom feed."""
    url = "https://github.com/%s/releases.atom" % owner_repo
    text, prov = http_text(url)
    if text is None:
        return None, prov
    entries = []
    for chunk in text.split("<entry>")[1:]:
        tag = re.search(r"<id>tag:github\.com,2008:Repository/\d+/([^<]+)</id>", chunk)
        upd = re.search(r"<updated>([^<]+)</updated>", chunk)
        title = re.search(r"<title>([^<]*)</title>", chunk)
        entries.append({"tag_name": tag.group(1) if tag else None,
                        "updated": upd.group(1) if upd else None,
                        "title": title.group(1) if title else None})
    prov = dict(prov)
    prov["method"] = "releases.atom feed (GitHub API unavailable)"
    return entries, prov


def github_releases(owner_repo: str):
    """Return (releases_list, provenance, method_string)."""
    url = "https://api.github.com/repos/%s/releases?per_page=100" % owner_repo
    data, prov = http_json(url)
    if isinstance(data, list):
        return data, prov, "GitHub releases API (%s)" % url
    entries, prov2 = releases_atom_fallback(owner_repo)
    if entries is not None:
        return entries, prov2, prov2["method"]
    return None, prov, "GitHub releases API failed and Atom fallback failed"


# --------------------------------------------------------------------------
# local process helpers
# --------------------------------------------------------------------------

def run(argv, cwd=None, timeout=180):
    try:
        proc = subprocess.run(argv, cwd=cwd, timeout=timeout,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        return proc.returncode, proc.stdout.decode("utf-8", "replace")
    except FileNotFoundError:
        return 127, "executable not found: %s" % argv[0]
    except subprocess.TimeoutExpired:
        return 124, "timed out after %ss" % timeout


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for block in iter(lambda: fh.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def ensure_comet_binary():
    """Download comet.linux.exe once; reuse it on later runs."""
    os.makedirs(WORK_DIR, exist_ok=True)
    if os.path.exists(COMET_LINUX_PATH) and os.path.getsize(COMET_LINUX_PATH) > 1 << 20:
        return COMET_LINUX_PATH, {"url": COMET_LINUX_URL, "source": "cache",
                                  "sha256": sha256_file(COMET_LINUX_PATH)}
    req = urllib.request.Request(COMET_LINUX_URL, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=600) as resp, \
            open(COMET_LINUX_PATH, "wb") as fh:
        while True:
            block = resp.read(1 << 20)
            if not block:
                break
            fh.write(block)
    os.chmod(COMET_LINUX_PATH, 0o755)
    return COMET_LINUX_PATH, {"url": COMET_LINUX_URL, "source": "live",
                              "sha256": sha256_file(COMET_LINUX_PATH)}


PARAM_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=")


def comet_param_names(binary, flag):
    """Run `comet <flag>` in a scratch dir and return the parameter names."""
    out_dir = os.path.join(WORK_DIR, "paramdump-%s" % flag.lstrip("-"))
    os.makedirs(out_dir, exist_ok=True)
    produced = os.path.join(out_dir, "comet.params.new")
    if os.path.exists(produced):
        os.remove(produced)
    rc, out = run([binary, flag], cwd=out_dir)
    if not os.path.exists(produced):
        return None, rc, out, None
    text = open(produced, "r", encoding="utf-8", errors="replace").read()
    # The parameter block ends at the [COMET_ENZYME_INFO] table.
    head = text.split("[COMET_ENZYME_INFO]")[0]
    names = []
    for line in head.splitlines():
        m = PARAM_RE.match(line)
        if m:
            names.append(m.group(1))
    return names, rc, out, produced


# --------------------------------------------------------------------------
# fact construction
# --------------------------------------------------------------------------

FACTS: list[dict] = []


def fact(fid, subject, spec_claim, method, observed, verdict,
         provenance=None, notes=None, difference=None):
    entry = {
        "id": fid,
        "subject": subject,
        "date_verified": VERIFICATION_DATE,
        "specification_claim": spec_claim,
        "method": method,
        "url": (provenance or {}).get("url"),
        "observed": observed,
        "verdict": verdict,
    }
    if provenance:
        entry["provenance"] = provenance
    if notes:
        entry["notes"] = notes
    if difference:
        entry["difference_from_specification"] = difference
    FACTS.append(entry)
    return entry


def delegated_fact(fid, subject, spec_claim, notes_extra="", url=None):
    unit, doc = DELEGATED[fid]
    fact(fid, subject, spec_claim,
         "not performed by this work unit -- artefact download/extraction is "
         "owned by %s, which records it in %s; the artefact URL below is the "
         "source that unit uses" % (unit, doc),
         None, "UNVERIFIED",
         provenance={"url": url, "source": "delegated", "owning_unit": unit,
                     "owning_document": doc},
         notes="Deliberately not re-checked here: downloading and extracting "
               "the Percolator artefacts a second time would duplicate a large "
               "download and collide on files with %s. %s" % (unit, notes_extra))


# --------------------------------------------------------------------------
# checks
# --------------------------------------------------------------------------

def check_comet(rel_by_tag, releases_prov, releases_method):
    latest = None
    if rel_by_tag:
        published = [r for r in rel_by_tag
                     if not r.get("draft") and r.get("published_at")]
        if published:
            latest = max(published, key=lambda r: r["published_at"])

    # --- row: Comet current release --------------------------------------
    if latest is None:
        fact("comet-current-release", "Comet current release",
             "v2026.02.2, published 2026-08-11.",
             releases_method, None, "UNVERIFIED", releases_prov,
             notes="Release list could not be retrieved.")
    else:
        obs = {"tag_name": latest["tag_name"],
               "published_at": latest.get("published_at"),
               "prerelease": latest.get("prerelease")}
        ok = (latest["tag_name"] == "v2026.02.2"
              and str(latest.get("published_at", "")).startswith("2026-08-11"))
        fact("comet-current-release", "Comet current release",
             "v2026.02.2, published 2026-08-11.",
             releases_method, obs,
             "CONFIRMED" if ok else "CHANGED", releases_prov)

    # --- row: Comet artefacts --------------------------------------------
    if latest is None or "assets" not in latest:
        fact("comet-artefacts", "Comet artefacts",
             "comet.linux.exe, comet.aarch64.linux.exe, comet.macos.exe, "
             "comet.aarch64.macos.exe, comet.win64.exe plus CometWrapper.dll, "
             "ThermoFisher.CommonCore.Data.dll and "
             "ThermoFisher.CommonCore.RawFileReader.dll.",
             releases_method, None, "UNVERIFIED", releases_prov,
             notes="Asset list unavailable (Atom fallback carries no assets).")
    else:
        assets = sorted(({"name": a["name"], "size": a["size"]}
                         for a in latest["assets"]),
                        key=lambda a: a["name"])
        names = {a["name"] for a in assets}
        spec_named = {
            "comet.linux.exe", "comet.aarch64.linux.exe", "comet.macos.exe",
            "comet.aarch64.macos.exe", "comet.win64.exe", "CometWrapper.dll",
            "ThermoFisher.CommonCore.Data.dll",
            "ThermoFisher.CommonCore.RawFileReader.dll"}
        missing = sorted(spec_named - names)
        extra = sorted(names - spec_named)
        diff = None
        verdict = "CONFIRMED"
        if missing:
            verdict = "CHANGED"
            diff = "assets named by the specification that are absent today: %s" % missing
        elif extra:
            diff = ("the release also publishes %s, which the specification's "
                    "row does not list" % extra)
        fact("comet-artefacts", "Comet artefacts",
             "Standalone executables, no archive: the five comet.*.exe builds "
             "plus three Thermo DLL companions.",
             releases_method,
             {"asset_count": len(assets), "assets": assets,
              "specification_named_assets_present": not missing,
              "assets_not_named_by_specification": extra},
             verdict, releases_prov, difference=diff)
    return latest


def check_comet_binary():
    try:
        path, prov = ensure_comet_binary()
    except Exception as exc:                       # noqa: BLE001
        fact("comet-binary-linkage", "Comet binary linkage",
             "comet.linux.exe is statically linked (no NEEDED entries) and "
             "runs on a glibc 2.36 host.",
             "download + readelf -d + execute", None, "UNVERIFIED",
             {"url": COMET_LINUX_URL},
             notes="download failed: %s: %s" % (type(exc).__name__, exc))
        return None

    os.chmod(path, 0o755)
    rc_d, out_d = run(["readelf", "-d", path])
    rc_h, out_h = run(["readelf", "-h", path])
    needed = re.findall(r"\(NEEDED\)\s+Shared library: \[([^\]]+)\]", out_d)
    no_dynamic = "There is no dynamic section in this file" in out_d
    rc_run, out_run = run([path])
    version = None
    m = re.search(r'Comet version\s+"([^"]+)"', out_run)
    if m:
        version = m.group(1)
    glibc_syms = sorted(set(re.findall(r"GLIBC_[0-9][0-9.]*",
                                       run(["readelf", "-V", path])[1])))
    static = no_dynamic and not needed
    ran = version is not None
    fact("comet-binary-linkage", "Comet binary linkage",
         "comet.linux.exe is statically linked (no NEEDED entries) and runs "
         "on a glibc 2.36 host.",
         "downloaded the release asset, `readelf -d` / `readelf -h` / "
         "`readelf -V`, then executed it on this Debian 12 / glibc 2.36 host",
         {"sha256": prov.get("sha256"),
          "size_bytes": os.path.getsize(path),
          "readelf_d": "no dynamic section" if no_dynamic else out_d.strip()[:400],
          "needed_entries": needed,
          "statically_linked": static,
          "elf_header": [l.strip() for l in out_h.splitlines()
                         if l.strip().startswith(("Class:", "Type:", "Machine:"))],
          "glibc_symbol_versions_required": glibc_syms,
          "executed_ok": ran,
          "version_banner": version,
          "host_glibc": host_glibc()},
         "CONFIRMED" if (static and ran) else "CHANGED", prov)

    # --- row: Comet CLI ---------------------------------------------------
    # Option lines in comet's usage block look like "  -P<params> to specify..."
    # or "  -i         create .idx file...": a dash token at the start of an
    # indented line, then whitespace, then a lower-case description word.
    options = re.findall(r"(?m)^\s*(?:options:\s+)?(-[A-Za-z](?:<[a-z]+>)?)\s+[a-z]",
                         out_run)
    formats = None
    mf = re.search(r"Supported input formats include ([^\n]+)", out_run)
    if mf:
        formats = mf.group(1).strip()
    spec_opts = {"-P", "-N", "-D", "-F", "-L", "-i", "-j"}
    seen_opts = {o[:2] for o in options}
    cli_ok = spec_opts <= seen_opts
    fact("comet-cli", "Comet CLI",
         "-P<params>, -N<name> (valid only with one input file), -D<dbase>, "
         "-F/-L scan range, -i fragment-ion index, -j peptide index. Inputs: "
         "mzXML, mzML, Thermo RAW, mgf, ms2/cms2/bms2.",
         "executed `comet.linux.exe` with no arguments",
         {"options": options, "input_formats": formats,
          "usage_text": out_run.strip(),
          "specification_options_all_present": cli_ok},
         "CONFIRMED" if cli_ok else "CHANGED",
         {"url": COMET_LINUX_URL},
         notes="The specification's row omits -p and -q themselves, which are "
               "also options of the same binary.")

    # --- row: Comet parameter dump ---------------------------------------
    p_names, p_rc, p_out, p_file = comet_param_names(path, "-p")
    q_names, q_rc, q_out, q_file = comet_param_names(path, "-q")
    if p_names is None or q_names is None:
        fact("comet-parameter-dump", "Comet parameter dump",
             "-p emits 96 parameters; -q emits 118.",
             "executed `comet -p` and `comet -q`", None, "UNVERIFIED",
             {"url": COMET_LINUX_URL},
             notes="comet did not produce comet.params.new (rc %s / %s)"
                   % (p_rc, q_rc))
    else:
        only_q = sorted(set(q_names) - set(p_names))
        only_p = sorted(set(p_names) - set(q_names))
        spec_extra = sorted(
            ["variable_mod%02d" % n for n in range(6, 16)] +
            ["mass_type_parent", "mass_type_fragment", "num_results",
             "peff_format", "peff_obo", "pinfile_protein_delimiter",
             "print_expect_score", "print_ascorepro_score",
             "spectral_library_name", "spectral_library_ms_level",
             "compoundmods_file", "protein_modslist_file"])
        counts_ok = len(p_names) == 96 and len(q_names) == 118
        extras_ok = only_q == spec_extra and not only_p
        diff = None
        if not counts_ok:
            diff = ("specification says -p 96 / -q 118; observed -p %d / -q %d"
                    % (len(p_names), len(q_names)))
        elif not extras_ok:
            diff = ("the set of parameters -q adds differs: only in -q today %s; "
                    "only in -p today %s" % (only_q, only_p))
        fact("comet-parameter-dump", "Comet parameter dump",
             "-p emits 96 parameters; -q emits 118; -q adds variable_mod06-15 "
             "plus 12 named parameters (22 in total).",
             "executed `comet.linux.exe -p` and `comet.linux.exe -q`, then "
             "counted `name =` lines above the [COMET_ENZYME_INFO] table",
             {"p_count": len(p_names), "q_count": len(q_names),
              "added_by_q_count": len(only_q),
              "added_by_q": only_q,
              "removed_by_q": only_p,
              "matches_specification_extra_list": extras_ok,
              "p_params_file": p_file, "q_params_file": q_file},
             "CONFIRMED" if (counts_ok and extras_ok) else "CHANGED",
             {"url": COMET_LINUX_URL}, difference=diff)
    return path


def host_glibc():
    rc, out = run(["ldd", "--version"])
    m = re.search(r"(\d+\.\d+)", out)
    return m.group(1) if m else None


def is_xml_capable_name(name: str) -> bool:
    """A percolator artefact whose name marks it as the XML build.

    Upstream ships an explicit A/B from rel-3-06 to rel-3-08: `percolator-*`
    (XML) beside a `percolator-noxml-*` twin.  `percolator-converters-*` is a
    separate package (sqt2pin/msgf2pin/tandem2pin), not percolator itself.
    """
    low = name.lower()
    return "noxml" not in low and "converters" not in low


def platform_of(name: str):
    low = name.lower()
    if low.endswith(".deb") or low.endswith(".rpm") or "linux" in low or "ubuntu" in low:
        return "linux"
    if "osx" in low or "mac" in low or low.endswith(".pkg"):
        return "macos"
    if low.endswith(".exe") or "windows" in low or "win" in low:
        return "windows"
    return "unknown"


def check_percolator():
    rels, prov, method = github_releases("percolator/percolator")
    by_tag = {r.get("tag_name"): r for r in (rels or [])}

    # --- row: Percolator current release ---------------------------------
    if not rels:
        fact("percolator-current-release", "Percolator current release",
             "rel-3-09, published 2026-05-21.", method, None, "UNVERIFIED", prov)
        latest = None
    else:
        published = [r for r in rels if r.get("published_at")]
        latest = max(published, key=lambda r: r["published_at"]) if published else None
        obs = {"tag_name": latest["tag_name"],
               "published_at": latest.get("published_at")} if latest else None
        ok = bool(latest and latest["tag_name"] == "rel-3-09"
                  and str(latest.get("published_at", "")).startswith("2026-05-21"))
        fact("percolator-current-release", "Percolator current release",
             "rel-3-09, published 2026-05-21.", method, obs,
             "CONFIRMED" if ok else "CHANGED", prov)

    # --- row: Percolator XML removal --------------------------------------
    quote = "Removed XML/XSD I/O support, which was incompatible with modern C++ toolchains. (#399)"
    body = (by_tag.get("rel-3-09") or {}).get("body")
    if body is None:
        fact("percolator-xml-removal", "Percolator XML removal",
             'rel-3-09 release notes: "%s"' % quote,
             method + " -- release notes body", None, "UNVERIFIED", prov,
             notes="release body unavailable via this route")
    else:
        flat = " ".join(body.split())
        exact = quote in flat
        # Upstream hard-wraps the release body at ~80 columns, mid-sentence, so
        # a byte-exact match can fail on the line break alone.  Compare with all
        # whitespace removed to test the substance, and report both results.
        squash = lambda t: re.sub(r"\s+", "", t)
        squashed = squash(quote) in squash(body)
        bullet = None
        for chunk in re.split(r"(?m)^\*\s*", body):
            if "XML/XSD" in chunk:
                bullet = " ".join(chunk.split())
                break
        raw_line = next((l for l in body.splitlines() if "XML/XSD" in l), None)
        fact("percolator-xml-removal", "Percolator XML removal",
             'Confirmed verbatim in the rel-3-09 release notes: "%s"' % quote,
             method + " -- rel-3-09 release notes body",
             {"bullet_reflowed": bullet,
              "raw_line_as_published": raw_line,
              "exact_byte_match_against_specification_quote": exact,
              "whitespace_normalised_match": squashed,
              "full_body": body},
             "CONFIRMED" if squashed else "CHANGED", prov,
             notes="The upstream release body is hard-wrapped at ~80 columns "
                   "and the break falls inside this sentence: the published "
                   "bytes read \"...modern C++ toolchains\\n. (#399)\". The "
                   "specification quotes the reflowed sentence, so its quote is "
                   "accurate in substance but is not a byte-exact copy.",
             difference=None if exact else
                 "specification presents the quote as verbatim; the published "
                 "release note contains a hard line break between "
                 "\"toolchains\" and \". (#399)\"")

    # --- row: Percolator 3.08 XML artefacts -------------------------------
    r308 = by_tag.get("rel-3-08")
    if not r308 or "assets" not in r308:
        fact("percolator-308-xml-artefacts", "Percolator 3.08 XML artefacts",
             "Exactly five assets; the only XML-capable one is "
             "percolator-v3-08-linux-amd64.deb.", method, None, "UNVERIFIED", prov)
    else:
        assets = sorted(({"name": a["name"], "size": a["size"]}
                         for a in r308["assets"]), key=lambda a: a["name"])
        xml_names = [a["name"] for a in assets if is_xml_capable_name(a["name"])]
        ok = (len(assets) == 5 and xml_names == ["percolator-v3-08-linux-amd64.deb"])
        fact("percolator-308-xml-artefacts", "Percolator 3.08 XML artefacts",
             "The release has exactly five assets; the only XML-capable "
             "published 3.08 artefact is percolator-v3-08-linux-amd64.deb; the "
             "macOS and Windows portable archives are explicitly "
             "percolator-noxml-*.",
             method + " -- full asset list for rel-3-08",
             {"published_at": r308.get("published_at"),
              "asset_count": len(assets), "assets": assets,
              "xml_named_percolator_assets": xml_names},
             "CONFIRMED" if ok else "CHANGED", prov)

    # --- rows owned by other work units -----------------------------------
    delegated_fact(
        "percolator-308-linux-binary", "Percolator 3.08 Linux binary",
        "The .deb extracts without root; ./usr/bin/percolator is 5.0 MB, "
        "version 3.08.0, requires GLIBC_2.38 and GLIBCXX_3.4.32, fails to load "
        "on glibc 2.36.",
        "This unit did independently confirm from the release metadata that "
        "percolator-v3-08-linux-amd64.deb exists and is 4327966 bytes.",
        url="https://github.com/percolator/percolator/releases/download/rel-3-08/percolator-v3-08-linux-amd64.deb")
    delegated_fact(
        "percolator-3071-linux-executed", "3.07.1 Linux build, executed",
        'Extracted without root and run: "Percolator version 3.07.1, Build '
        'Date Jun 20 2024"; help lists -X/--xmloutput and -Z/--decoy-xml-output; '
        "highest required symbol version GLIBC_2.34.",
        "This unit did independently confirm that "
        "percolator-v3-07-linux-amd64.deb (3184992 bytes) is published by "
        "rel-3-07-01.",
        url="https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-v3-07-linux-amd64.deb")
    delegated_fact(
        "percolator-3071-macos-extracted", "3.07.1 macOS build, extracted",
        "The .pkg is a xar! archive with gzip 070707 cpio payload containing "
        "./usr/local/bin/percolator plus percolator_out.xsd and "
        "percolator_in.xsd; x86-64 only.",
        "This unit did independently confirm that "
        "percolator-v3-07-osx-x86_64.pkg (2122306 bytes) is published by "
        "rel-3-07-01.",
        url="https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-v3-07-osx-x86_64.pkg")
    delegated_fact(
        "percolator-3071-windows-inferred", "3.07.1 Windows build, inferred",
        "percolator-v3-07.exe is an NSIS installer; XML capability inferred "
        "from naming and size (1776 KB vs the noxml twin's 1193 KB).",
        "This unit did independently confirm the two asset sizes from the "
        "release metadata: percolator-v3-07.exe 1818841 B (1776.2 KiB) and "
        "percolator-noxml-v3-07.exe 1222439 B (1193.8 KiB), i.e. +48.8%.",
        url="https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-v3-07.exe")

    # --- row: newest XML-capable release covering all three platforms -----
    coverage = []
    for rel in sorted((r for r in (rels or []) if r.get("published_at")),
                      key=lambda r: r["published_at"], reverse=True):
        if "assets" not in rel:
            continue
        # Releases from rel-3-09 onward carry no XML build at all: upstream
        # removed the code, so the noxml/XML A/B naming stopped.  Treat any
        # release at or after rel-3-09 as XML-incapable regardless of naming.
        post_removal = rel["tag_name"] >= "rel-3-09"
        plats = {}
        for a in rel["assets"]:
            if post_removal or not is_xml_capable_name(a["name"]):
                continue
            plats.setdefault(platform_of(a["name"]), []).append(a["name"])
        coverage.append({
            "tag_name": rel["tag_name"],
            "published_at": rel["published_at"][:10],
            "xml_named_assets_by_platform": plats,
            "covers_all_three_tier1": {"linux", "macos", "windows"} <= set(plats),
            "xml_support_removed_upstream": post_removal,
        })
    newest_all3 = next((c for c in coverage if c["covers_all_three_tier1"]), None)
    tag = newest_all3["tag_name"] if newest_all3 else None
    ok = tag == "rel-3-07-01"
    fact("percolator-newest-xml-all-platforms",
         "Newest XML-capable Percolator with binaries on all three tier-1 platforms",
         "3.07.1 (rel-3-07-01, 2024-06-20), publishing "
         "percolator-v3-07-linux-amd64.deb, percolator-v3-07-osx-x86_64.pkg "
         "and percolator-v3-07.exe.",
         method + " -- asset lists for every release, classified by the "
                  "upstream noxml/XML naming A/B",
         {"newest_release_covering_all_three": tag,
          "published_at": newest_all3["published_at"] if newest_all3 else None,
          "assets": newest_all3["xml_named_assets_by_platform"] if newest_all3 else None,
          "per_release_coverage": coverage[:8]},
         "CONFIRMED" if ok else "CHANGED", prov,
         notes="Naming is evidence of XML capability, not proof of it; "
               "unit 3 proves capability by executing the extracted binaries "
               "(docs/feasibility/percolator-artefacts.rst).")

    # --- row: Percolator XML on Windows/macOS -----------------------------
    win_mac = None
    if newest_all3:
        win_mac = {k: v for k, v in
                   newest_all3["xml_named_assets_by_platform"].items()
                   if k in ("windows", "macos")}
    wm_ok = bool(win_mac
                 and win_mac.get("windows") == ["percolator-v3-07.exe"]
                 and win_mac.get("macos") == ["percolator-v3-07-osx-x86_64.pkg"])
    fact("percolator-xml-windows-macos", "Percolator XML on Windows/macOS",
         "The newest XML-capable published builds for those platforms are "
         "percolator-v3-07.exe and percolator-v3-07-osx-x86_64.pkg, from "
         "rel-3-07-01 (2024-06-20).",
         method + " -- release asset lists",
         {"release": tag, "assets": win_mac},
         "CONFIRMED" if wm_ok else "CHANGED", prov)

    # --- row: newest XML-capable overall (rel-3-08-01) --------------------
    tags_url = "https://api.github.com/repos/percolator/percolator/tags?per_page=100"
    tags, tprov = http_json(tags_url)
    tag_names = [t["name"] for t in tags] if isinstance(tags, list) else None
    if tag_names is None:
        fact("percolator-3081-tag-no-release", "Newest XML-capable Percolator overall",
             "rel-3-08-01 is a tag with no GitHub release and therefore no "
             "binary on any platform.",
             "GitHub tags API + releases API", None, "UNVERIFIED", tprov)
    else:
        is_tag = "rel-3-08-01" in tag_names
        has_release = "rel-3-08-01" in by_tag
        sha = next((t["commit"]["sha"] for t in tags
                    if t["name"] == "rel-3-08-01"), None)
        tag_date = tag_subject = None
        if sha:
            curl = ("https://api.github.com/repos/percolator/percolator/"
                    "commits/%s" % sha)
            commit, _cprov = http_json(curl)
            if isinstance(commit, dict) and "commit" in commit:
                tag_date = commit["commit"]["committer"]["date"]
                tag_subject = commit["commit"]["message"].splitlines()[0]
        ok = (is_tag and not has_release
              and str(tag_date or "").startswith("2025-07-08"))
        fact("percolator-3081-tag-no-release",
             "Newest XML-capable Percolator overall",
             "rel-3-08-01 (3.08.1, tagged 2025-07-08) is a tag with no GitHub "
             "release and therefore no binary on any platform.",
             "GitHub tags API (%s) cross-checked against the releases API"
             % tags_url,
             {"rel-3-08-01_in_tags_api": is_tag,
              "rel-3-08-01_in_releases_api": has_release,
              "tag_commit_sha": sha,
              "tag_commit_date": tag_date,
              "tag_commit_subject": tag_subject,
              "tag_count": len(tag_names),
              "release_tags": sorted(by_tag.keys(), reverse=True)[:6]},
             "CONFIRMED" if ok else "CHANGED", tprov)

    # --- row: XML_SUPPORT is a compile option -----------------------------
    option_line = ('option(XML_SUPPORT "Choose to support xml input (slower '
                   'compilation)." OFF)')
    per_tag = {}
    verdicts = []
    urls = []
    for t in ("rel-3-08", "rel-3-08-01", "rel-3-09"):
        url = ("https://raw.githubusercontent.com/percolator/percolator/%s/"
               "CMakeLists.txt" % t)
        urls.append(url)
        text, cprov = http_text(url)
        if text is None:
            per_tag[t] = {"error": cprov.get("error"), "url": url}
            verdicts.append(None)
            continue
        line = next((l.strip() for l in text.splitlines()
                     if l.strip().startswith("option(XML_SUPPORT")), None)
        per_tag[t] = {"url": url, "bytes": len(text),
                      "option_line": line,
                      "XML_SUPPORT_occurrences": text.count("XML_SUPPORT"),
                      "xerces_mentions": len(re.findall(r"(?i)xerces", text))}
        if t == "rel-3-09":
            verdicts.append(line is None and text.count("XML_SUPPORT") == 0)
        else:
            verdicts.append(line == option_line)
    ok = all(v is True for v in verdicts)
    fact("percolator-xml-build-option", "Percolator XML is a build option",
         'option(XML_SUPPORT "Choose to support xml input (slower '
         'compilation)." OFF) at rel-3-08 and rel-3-08-01; the option and its '
         "code are gone entirely in rel-3-09.",
         "fetched CMakeLists.txt from raw.githubusercontent.com at each of the "
         "three tags and read the option line",
         per_tag, "CONFIRMED" if ok else ("UNVERIFIED" if None in verdicts else "CHANGED"),
         {"url": urls[0]})


def check_bioconda():
    api = "https://api.anaconda.org/package/bioconda/percolator"
    data, prov = http_json(api)
    plats = version = None
    if isinstance(data, dict):
        version = data.get("latest_version")
        plats = sorted({(f.get("attrs") or {}).get("subdir") or f.get("subdir")
                        for f in data.get("files", [])
                        if f.get("version") == version})
    meta_url = ("https://raw.githubusercontent.com/bioconda/bioconda-recipes/"
                "master/recipes/percolator/meta.yaml")
    build_url = ("https://raw.githubusercontent.com/bioconda/bioconda-recipes/"
                 "master/recipes/percolator/build.sh")
    meta, _ = http_text(meta_url)
    build, _ = http_text(build_url)
    skip_line = None
    if meta:
        skip_line = next((l.strip() for l in meta.splitlines()
                          if l.strip().startswith("skip:")), None)
    xsd_comment = None
    if build:
        m = re.search(r"#\s*The XSD/Xerces-C based converters.*?(?=\n\n|\ncmake)",
                      build, re.S)
        if m:
            xsd_comment = " ".join(m.group(0).replace("#", " ").split())
    ok = (plats == ["linux-64", "linux-aarch64"] or
          sorted(plats or []) == ["linux-64", "linux-aarch64"]) \
        and skip_line == "skip: True  # [osx]" and bool(xsd_comment)
    fact("bioconda-percolator", "Bioconda Percolator",
         "Provides 3.9 for linux-64 and linux-aarch64 only, skips macOS "
         "(skip: True  # [osx]), and its build script states that the "
         "XSD/Xerces path is deliberately not built.",
         "anaconda.org package API plus the raw bioconda-recipes meta.yaml and "
         "build.sh",
         {"latest_version": version,
          "platforms_for_latest": plats,
          "all_versions": (data or {}).get("versions"),
          "meta_yaml_skip_line": skip_line,
          "meta_yaml_url": meta_url,
          "build_sh_url": build_url,
          "build_sh_xsd_statement": xsd_comment},
         "CONFIRMED" if ok else "CHANGED", prov)


def check_pdv():
    rels, prov, method = github_releases("wenbostar/PDV")
    if not rels:
        fact("pdv-current-release", "PDV current release",
             "v2.7.0, published 2026-08-14 (PDV-2.7.0.zip, ~99 MB).",
             method, None, "UNVERIFIED", prov)
        return
    published = [r for r in rels if r.get("published_at")]
    latest = max(published, key=lambda r: r["published_at"]) if published else rels[0]
    assets = [{"name": a["name"], "size": a["size"],
               "size_mb": round(a["size"] / 1048576.0, 1)}
              for a in latest.get("assets", [])]
    ok = (latest.get("tag_name") == "v2.7.0"
          and str(latest.get("published_at", "")).startswith("2026-08-14")
          and any(a["name"] == "PDV-2.7.0.zip" for a in assets))
    prev = sorted((r for r in published if r is not latest),
                  key=lambda r: r["published_at"], reverse=True)[:1]
    fact("pdv-current-release", "PDV current release",
         "v2.7.0, published 2026-08-14 (PDV-2.7.0.zip, ~99 MB). 2.6.0 is one "
         "release behind.",
         method,
         {"tag_name": latest.get("tag_name"),
          "published_at": latest.get("published_at"),
          "assets": assets,
          "previous_release": (prev[0]["tag_name"] if prev else None),
          "previous_published_at": (prev[0]["published_at"] if prev else None),
          "release_count": len(rels)},
         "CONFIRMED" if ok else "CHANGED", prov,
         notes="This row went stale within a day during specification drafting "
               "(2.6.0 -> 2.7.0); it was re-checked with particular care.")


def check_limelight():
    repo_url = "https://api.github.com/repos/yeastrc/limelight-import-comet-percolator"
    repo, rprov = http_json(repo_url)
    rels, prov, method = github_releases("yeastrc/limelight-import-comet-percolator")
    lic = ((repo or {}).get("license") or {}).get("spdx_id") if isinstance(repo, dict) else None
    latest = None
    if rels:
        published = [r for r in rels if r.get("published_at")]
        latest = max(published, key=lambda r: r["published_at"]) if published else None
    assets = [{"name": a["name"], "size": a["size"]}
              for a in (latest or {}).get("assets", [])]
    ok = (lic == "Apache-2.0"
          and latest is not None
          and latest.get("tag_name") == "v2.8.1"
          and str(latest.get("published_at", "")).startswith("2025-08-19")
          and [a["name"] for a in assets] == ["cometPercolator2LimelightXML.jar"])
    fact("limelight-converter", "Limelight converter",
         "yeastrc/limelight-import-comet-percolator, Apache-2.0, newest "
         "release v2.8.1 (2025-08-19), single asset "
         "cometPercolator2LimelightXML.jar.",
         "GitHub repository API (%s) and %s" % (repo_url, method),
         {"license_spdx_id": lic,
          "latest_release": (latest or {}).get("tag_name"),
          "published_at": (latest or {}).get("published_at"),
          "assets": assets,
          "repo_pushed_at": (repo or {}).get("pushed_at"),
          "release_count": len(rels or [])},
         "CONFIRMED" if ok else ("UNVERIFIED" if lic is None else "CHANGED"),
         rprov)

    readme_url = ("https://raw.githubusercontent.com/yeastrc/"
                  "limelight-import-comet-percolator/master/README.md")
    readme, mprov = http_text(readme_url)
    if readme is None:
        fact("limelight-input-requirement", "Limelight converter input",
             "Still hard-requires Percolator XML.",
             "converter README", None, "UNVERIFIED", mprov)
        fact("limelight-converter-arguments", "Converter arguments",
             "-c/--comet-params, -f/--fasta-file, -p/--percolator-file, "
             "-d/--pepxml-directory, -q/--q-value, -o/--out-file, "
             "--import-decoys, --independent-decoy-prefix, --open-mod, -v.",
             "converter --help", None, "UNVERIFIED", mprov)
        return

    flat = " ".join(readme.split())
    q1 = ("Requires that the Percolator output be represented as XML "
          "(see -X option in Percolator)")
    q2 = "Full path to percolator output XML file"
    tab_hits = re.findall(r"(?i)tab[- ]delimit|\btsv\b|tab[- ]separated", readme)
    ok = q1 in flat and q2 in flat and not tab_hits
    fact("limelight-input-requirement", "Limelight converter input",
         'Still hard-requires Percolator XML: "Requires that the Percolator '
         'output be represented as XML (see -X option in Percolator)", and '
         '-p, --percolator-file is documented as "Full path to percolator '
         'output XML file". No tab-delimited path exists.',
         "fetched the converter README from raw.githubusercontent.com and "
         "searched it for the XML requirement and for any tab-delimited input",
         {"xml_requirement_quote_present": q1 in flat,
          "percolator_file_option_quote_present": q2 in flat,
          "tab_delimited_mentions": tab_hits,
          "readme_url": readme_url,
          "repo_pushed_at": (repo or {}).get("pushed_at"),
          "verbatim": q1 if q1 in flat else None},
         "CONFIRMED" if ok else "CHANGED", mprov,
         notes="The converter's executed --help output is verified by unit 9, "
               "which needs a JDK; the README embeds a copy of that help text "
               "and is what is quoted here.")

    doc_opts = sorted(set(re.findall(r"^\s+(?:(-\w), )?(--[a-z-]+)", readme, re.M)))
    long_opts = sorted({m[1] for m in doc_opts})
    spec_long = ["--comet-params", "--fasta-file", "--percolator-file",
                 "--pepxml-directory", "--q-value", "--out-file",
                 "--import-decoys", "--independent-decoy-prefix", "--open-mod",
                 "--verbose"]  # the specification writes this one as "-v"
    missing = [o for o in spec_long if o not in long_opts]
    extra = [o for o in long_opts if o not in spec_long]
    fact("limelight-converter-arguments", "Converter arguments",
         "-c/--comet-params, -f/--fasta-file, -p/--percolator-file, "
         "-d/--pepxml-directory, -q/--q-value, -o/--out-file, --import-decoys "
         "(requires Percolator -Z), --independent-decoy-prefix, --open-mod, -v.",
         "read the command-line documentation block embedded in the converter "
         "README (raw.githubusercontent.com); the executed --help is unit 9's",
         {"documented_long_options": long_opts,
          "specification_options_missing_today": missing,
          "options_present_but_not_listed_by_specification": extra,
          "import_decoys_requires_Z":
              "percolator must be run with -Z to output decoys" in flat},
         "CONFIRMED" if not missing else "CHANGED", mprov,
         difference=("the specification's list omits %s, which the README "
                     "documents" % extra) if extra else None)


def check_casanovogui():
    repo_url = "https://api.github.com/repos/Noble-Lab/CasanovoGUI"
    repo, rprov = http_json(repo_url)
    if not isinstance(repo, dict) or "full_name" not in repo:
        fact("casanovogui-licence", "CasanovoGUI licence",
             "Noble-Lab/CasanovoGUI (Java, last pushed 2026-08-21) still "
             "publishes no licence: the GitHub licence field is null and no "
             "licence file is detected.",
             "GitHub repository API", None, "UNVERIFIED", rprov)
        return
    branch = repo.get("default_branch", "main")
    tree_url = ("https://api.github.com/repos/Noble-Lab/CasanovoGUI/git/trees/"
                "%s?recursive=1" % branch)
    tree, tprov = http_json(tree_url)
    lic_paths = None
    truncated = None
    entries = None
    if isinstance(tree, dict) and "tree" in tree:
        truncated = tree.get("truncated")
        entries = len(tree["tree"])
        lic_paths = sorted(e["path"] for e in tree["tree"]
                           if re.search(r"(?i)licen[cs]e|copying|notice|unlicen",
                                        e["path"]))
    ok = (repo.get("license") is None and lic_paths == []
          and str(repo.get("pushed_at", "")).startswith("2026-08-21"))
    verdict = "CONFIRMED" if ok else "CHANGED"
    if lic_paths is None:
        verdict = "UNVERIFIED"
    fact("casanovogui-licence", "CasanovoGUI licence",
         "Noble-Lab/CasanovoGUI (Java, last pushed 2026-08-21) still publishes "
         "no licence: the GitHub licence field is null and no licence file is "
         "detected.",
         "GitHub repository API (%s) for the licence field and push date, plus "
         "the recursive git tree API (%s) searched for any "
         "LICENSE/COPYING/NOTICE file" % (repo_url, tree_url),
         {"license_field": repo.get("license"),
          "pushed_at": repo.get("pushed_at"),
          "updated_at": repo.get("updated_at"),
          "created_at": repo.get("created_at"),
          "language": repo.get("language"),
          "default_branch": branch,
          "archived": repo.get("archived"),
          "fork": repo.get("fork"),
          "tree_entries": entries,
          "tree_truncated": truncated,
          "licence_like_paths_in_tree": lic_paths},
         verdict, rprov,
         notes="Evidence for D-001 only. This unit does not answer D-001 and "
               "copied no CasanovoGUI code.")


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------

def main() -> int:
    global REFRESH
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--refresh", action="store_true",
                    help="ignore the HTTP cache entirely and re-fetch everything")
    ap.add_argument("--refresh-github", action="store_true",
                    help="re-fetch only api.github.com responses (costs quota)")
    ap.add_argument("--out", default=OUT_JSON, help="JSON output path")
    args = ap.parse_args()
    REFRESH = "all" if args.refresh else ("github" if args.refresh_github else "none")

    os.makedirs(WORK_DIR, exist_ok=True)
    os.makedirs(CACHE_DIR, exist_ok=True)

    comet_rels, comet_prov, comet_method = github_releases("UWPR/Comet")
    check_comet(comet_rels, comet_prov, comet_method)
    check_comet_binary()
    check_percolator()
    check_bioconda()
    check_pdv()
    check_limelight()
    check_casanovogui()

    counts = {}
    for f in FACTS:
        counts[f["verdict"]] = counts.get(f["verdict"], 0) + 1

    doc = {
        "schema": "cometgui/upstream-facts/1",
        "verification_date": VERIFICATION_DATE,
        "generated_utc": datetime.datetime.utcnow().replace(
            microsecond=0).isoformat() + "Z",
        "specification_revision_verified_against": 2,
        "specification_table_date": "2026-08-28",
        "host": {"platform": platform.platform(),
                 "python": sys.version.split()[0],
                 "glibc": host_glibc()},
        "cache_dir": CACHE_DIR,
        "refresh_mode": REFRESH,
        "fact_count": len(FACTS),
        "verdict_counts": counts,
        "facts": FACTS,
        "http_log": _HTTP_LOG,
    }
    with open(args.out, "w") as fh:
        json.dump(doc, fh, indent=2, sort_keys=False)
        fh.write("\n")

    # ---- human-readable summary ----
    print("CometGUI PHASE-00 unit 2 -- upstream fact re-verification")
    print("Verification date : %s" % VERIFICATION_DATE)
    print("Generated (UTC)   : %s" % doc["generated_utc"])
    print("Host              : %s, glibc %s" % (doc["host"]["platform"],
                                                doc["host"]["glibc"]))
    print("JSON written to   : %s" % args.out)
    print("HTTP requests     : %d (%d live, %d from cache)"
          % (len(_HTTP_LOG),
             sum(1 for h in _HTTP_LOG if h.get("source") == "live"),
             sum(1 for h in _HTTP_LOG if h.get("source") == "cache")))
    print()
    print("%-38s %-11s %s" % ("SUBJECT", "VERDICT", "OBSERVED (abridged)"))
    print("-" * 118)
    for f in FACTS:
        obs = f["observed"]
        if obs is None:
            brief = "(not established by this run)"
        elif isinstance(obs, dict):
            brief = ", ".join("%s=%s" % (k, json.dumps(v)[:46])
                              for k, v in list(obs.items())[:3])
        else:
            brief = str(obs)
        print("%-38s %-11s %s" % (f["subject"][:38], f["verdict"], brief[:66]))
    print("-" * 118)
    print("Totals: " + ", ".join("%s=%d" % kv for kv in sorted(counts.items())))
    print()
    diffs = [f for f in FACTS if f.get("difference_from_specification")
             or f["verdict"] == "CHANGED"]
    if diffs:
        print("Differences from specification.rst:")
        for f in diffs:
            print("  * %s: %s" % (f["subject"],
                                  f.get("difference_from_specification")
                                  or "verdict CHANGED"))
    else:
        print("Differences from specification.rst: none found "
              "(rows marked UNVERIFIED are delegated to other work units).")
    print()
    print("Exit code 0 proves nothing. Read %s." % args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Enumerate every Percolator release and classify every published asset.

Phase 00, work unit 3.  This script exists so that the project's "latest
compatible Percolator" answer is *derived from upstream data*, not copied out
of ``specification.rst``.  Nothing about the answer is hard-coded: the version
that the derivation prints is whatever the classification rules select from
the live release list.

Standard library only, Python 3.11.  No third-party packages, no host tools.

Sources, in order of preference:

``api``
    ``https://api.github.com/repos/percolator/percolator/releases?per_page=100``
    -- exact asset names, byte sizes, publication dates and release notes.
    Unauthenticated GitHub API is limited to 60 requests/hour per host.

``html``
    ``https://github.com/percolator/percolator/releases.atom`` for the tag list
    plus ``.../releases/expanded_assets/<tag>`` for each tag's asset list.
    Not rate limited.  Sizes are the rounded ones GitHub renders, and release
    notes are not available, so the ``xml_removed_by_release_notes`` rule
    cannot fire; the script says so in the output.

Every HTTP response is cached under ``--cache`` so that a re-run costs no API
quota.  Use ``--refresh`` to force a live fetch.

Classification rules (all applied to data, never to a version number):

platform
    From the asset name: macOS markers win over Windows markers, which win
    over Linux markers, so that ``percolator-v3-07-osx-x86_64.pkg`` is macOS
    and not Linux.

component
    ``converters`` if the name contains ``converters`` -- those are the
    sqt2pin/msgf2pin/tandem2pin family, not the ``percolator`` binary itself.
    Otherwise ``percolator``.

packaging
    ``os-package`` for ``.deb``/``.rpm``/``.pkg``, ``nsis-installer`` for a
    versioned ``.exe``, ``raw-binary`` for a bare ``percolator.exe``,
    ``portable-archive`` for ``.zip``/``.tar.gz``.

xml_capable
    ``False`` -- ``noxml`` appears in the asset name (upstream's explicit A/B
    label), or the release notes say XML/XSD I/O was removed.
    ``True``  -- the release also publishes the ``noxml`` twin of exactly this
    asset name, which makes this one the ``-DXML_SUPPORT=ON`` half of the pair.
    ``None``  -- neither; unknown from naming alone, needs payload inspection.

The derivation then reports the newest release that publishes an
``xml_capable is True`` ``percolator``-component asset for all three tier-1
platforms, under the project's two fixed constraints: no source builds, and a
version is only a candidate where upstream publishes a binary for that
platform.  A second, deliberately optimistic derivation treats ``None`` as
capable, so that the effect of the unlabelled assets is visible rather than
buried.

Usage::

    python3 scripts/feasibility/enumerate_percolator_releases.py
    python3 scripts/feasibility/enumerate_percolator_releases.py --source html
    python3 scripts/feasibility/enumerate_percolator_releases.py --refresh
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

REPO = "percolator/percolator"
API_RELEASES = f"https://api.github.com/repos/{REPO}/releases?per_page=100"
API_TAGS = f"https://api.github.com/repos/{REPO}/tags?per_page=100"
ATOM_RELEASES = f"https://github.com/{REPO}/releases.atom"
EXPANDED = "https://github.com/" + REPO + "/releases/expanded_assets/%s"

DEFAULT_CACHE = "/workspace/scratch/apicache"
DEFAULT_OUT = "/workspace/scratch/percolator/releases.json"
USER_AGENT = "CometGUI-phase00-percolator-enumeration/1.0"

TIER1 = ("linux-x86_64", "windows-x64", "macos")

# Release notes wording that means the whole release has no XML at all.
XML_REMOVED_RE = re.compile(r"removed\s+xml(/xsd)?\s+(i/o|input|output|support)",
                            re.IGNORECASE)


# --------------------------------------------------------------------------
# fetching
# --------------------------------------------------------------------------

class Fetcher:
    """Cache-first HTTP GET.  Records which responses were served live."""

    def __init__(self, cache_dir: str, refresh: bool = False) -> None:
        self.cache_dir = cache_dir
        self.refresh = refresh
        self.log: list[dict] = []
        os.makedirs(cache_dir, exist_ok=True)

    def _paths(self, url: str) -> tuple[str, str]:
        k = hashlib.sha256(url.encode()).hexdigest()[:24]
        return (os.path.join(self.cache_dir, k + ".body"),
                os.path.join(self.cache_dir, k + ".url"))

    def get(self, url: str) -> tuple[bytes | None, str]:
        body_path, url_path = self._paths(url)
        if os.path.exists(body_path) and not self.refresh:
            self.log.append({"url": url, "source": "cache", "path": body_path})
            return open(body_path, "rb").read(), "cache"
        req = urllib.request.Request(url, headers={
            "User-Agent": USER_AGENT,
            "Accept": ("application/vnd.github+json"
                       if "api.github.com" in url else "*/*"),
        })
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                body = resp.read()
                remaining = resp.headers.get("X-RateLimit-Remaining")
        except urllib.error.HTTPError as exc:
            detail = exc.read()[:300].decode("utf-8", "replace")
            self.log.append({"url": url, "source": "http-%d" % exc.code,
                             "detail": detail})
            sys.stderr.write("HTTP %d for %s\n" % (exc.code, url))
            return None, "http-%d" % exc.code
        except urllib.error.URLError as exc:
            self.log.append({"url": url, "source": "neterror",
                             "detail": str(exc.reason)})
            return None, "neterror"
        with open(body_path, "wb") as fh:
            fh.write(body)
        with open(url_path, "w") as fh:
            fh.write(url + "\n")
        entry = {"url": url, "source": "live", "path": body_path}
        if remaining is not None:
            entry["ratelimit_remaining"] = remaining
            sys.stderr.write("[api ratelimit remaining %s] %s\n"
                             % (remaining, url))
        self.log.append(entry)
        return body, "live"


# --------------------------------------------------------------------------
# release list acquisition
# --------------------------------------------------------------------------

def releases_from_api(fetch: Fetcher) -> list[dict] | None:
    body, how = fetch.get(API_RELEASES)
    if body is None:
        return None
    try:
        raw = json.loads(body)
    except json.JSONDecodeError:
        return None
    if not isinstance(raw, list):
        return None
    out = []
    for rel in raw:
        out.append({
            "tag": rel["tag_name"],
            "name": rel.get("name"),
            "published_at": rel.get("published_at"),
            "draft": rel.get("draft"),
            "prerelease": rel.get("prerelease"),
            "notes": rel.get("body") or "",
            "notes_available": True,
            "html_url": rel.get("html_url"),
            "assets": [{
                "name": a["name"],
                "size": a["size"],
                "size_exact": True,
                "url": a["browser_download_url"],
                "updated_at": a.get("updated_at"),
            } for a in rel.get("assets", [])],
        })
    return out


ATOM_TAG_RE = re.compile(
    r"<link[^>]+href=\"[^\"]*/releases/tag/([^\"]+)\"", re.IGNORECASE)
HREF_RE = re.compile(r'href="(/[^"]*/releases/download/[^"]+)"')
SIZE_RE = re.compile(r">\s*([0-9]+(?:\.[0-9]+)?)\s*(Bytes|KB|MB|GB)\s*<")
UNIT = {"Bytes": 1, "KB": 1024, "MB": 1024 ** 2, "GB": 1024 ** 3}


def releases_from_html(fetch: Fetcher) -> list[dict] | None:
    """Rate-limit-free fallback: atom feed for tags, expanded_assets per tag."""
    body, _ = fetch.get(ATOM_RELEASES)
    if body is None:
        return None
    text = body.decode("utf-8", "replace")
    tags: list[str] = []
    for m in ATOM_TAG_RE.finditer(text):
        tag = urllib.parse.unquote(m.group(1))
        if tag not in tags:
            tags.append(tag)
    dates = re.findall(r"<updated>([^<]+)</updated>", text)
    out = []
    for i, tag in enumerate(tags):
        page, _ = fetch.get(EXPANDED % tag)
        assets = []
        if page is not None:
            html = page.decode("utf-8", "replace")
            hrefs = HREF_RE.findall(html)
            sizes = SIZE_RE.findall(html)
            for j, href in enumerate(hrefs):
                name = href.rsplit("/", 1)[-1]
                size = None
                if j < len(sizes):
                    size = int(float(sizes[j][0]) * UNIT[sizes[j][1]])
                assets.append({
                    "name": name,
                    "size": size,
                    "size_exact": False,
                    "url": "https://github.com" + href,
                    "updated_at": None,
                })
        out.append({
            "tag": tag,
            "name": tag,
            "published_at": dates[i + 1] if i + 1 < len(dates) else None,
            "draft": None,
            "prerelease": None,
            "notes": "",
            "notes_available": False,
            "html_url": "https://github.com/%s/releases/tag/%s" % (REPO, tag),
            "assets": assets,
        })
    return out


# --------------------------------------------------------------------------
# classification
# --------------------------------------------------------------------------

def parse_version(tag: str) -> tuple[int, ...] | None:
    m = re.fullmatch(r"rel-(\d+)-(\d+)(?:-(\d+))?", tag.strip())
    if not m:
        return None
    parts = [int(m.group(1)), int(m.group(2))]
    parts.append(int(m.group(3)) if m.group(3) else 0)
    return tuple(parts)


def version_string(v: tuple[int, ...] | None) -> str | None:
    if v is None:
        return None
    return "%d.%d.%d" % v if v[2] else "%d.%d" % (v[0], v[1])


MAC_MARKERS = ("osx", "darwin", "macos", "mac-", ".pkg")
WIN_MARKERS = ("windows", "win32", "win64", "win-", ".exe")
LINUX_MARKERS = ("linux", "ubuntu", "centos", "fedora", "debian", "almalinux",
                 ".deb", ".rpm", "amd64", "x86_64")


def classify_platform(name: str) -> str:
    low = name.lower()
    if any(m in low for m in MAC_MARKERS):
        return "macos"
    if any(m in low for m in WIN_MARKERS):
        return "windows-x64"
    if any(m in low for m in LINUX_MARKERS):
        return "linux-x86_64"
    return "unknown"


def classify_packaging(name: str) -> str:
    low = name.lower()
    if low.endswith(".deb"):
        return "os-package-deb"
    if low.endswith(".rpm"):
        return "os-package-rpm"
    if low.endswith(".pkg"):
        return "os-package-pkg"
    if low.endswith(".exe"):
        # A versioned .exe is the NSIS installer; a bare percolator.exe is the
        # binary itself.
        if re.search(r"-v\d+-\d+", low):
            return "nsis-installer"
        return "raw-binary"
    if low.endswith(".zip") or low.endswith(".tar.gz") or low.endswith(".tgz"):
        return "portable-archive"
    return "other"


def classify_component(name: str) -> str:
    return "converters" if "converters" in name.lower() else "percolator"


def noxml_twin_name(name: str) -> str:
    """The name upstream would give the ``noxml`` half of this A/B pair.

    Upstream inserts ``noxml`` as its own dash-separated token immediately
    after the leading ``percolator`` token: ``percolator-v3-07.exe`` pairs
    with ``percolator-noxml-v3-07.exe``.
    """
    if name.lower().startswith("percolator-"):
        return "percolator-noxml-" + name[len("percolator-"):]
    if name.lower().startswith("percolator."):
        return "percolator-noxml." + name[len("percolator."):]
    return "noxml-" + name


def classify_release(rel: dict) -> dict:
    names = {a["name"] for a in rel["assets"]}
    xml_removed = bool(XML_REMOVED_RE.search(rel["notes"]))
    rel = dict(rel)
    rel["version"] = version_string(parse_version(rel["tag"]))
    rel["version_tuple"] = parse_version(rel["tag"])
    rel["xml_removed_by_release_notes"] = xml_removed
    assets = []
    for a in rel["assets"]:
        name = a["name"]
        twin = noxml_twin_name(name)
        if "noxml" in name.lower():
            xml, basis = False, "asset name carries upstream's explicit 'noxml' label"
        elif xml_removed:
            xml, basis = False, "release notes state XML/XSD I/O was removed"
        elif twin in names:
            xml, basis = True, "release also publishes the noxml twin '%s'" % twin
        else:
            xml, basis = None, "no noxml twin published and no release-note evidence; unknown from naming alone"
        assets.append(dict(a, platform=classify_platform(name),
                           packaging=classify_packaging(name),
                           component=classify_component(name),
                           xml_capable=xml, xml_basis=basis))
    rel["assets"] = assets
    return rel


# --------------------------------------------------------------------------
# derivation
# --------------------------------------------------------------------------

def platform_coverage(rel: dict, treat_unknown_as_capable: bool) -> dict:
    cover = {}
    for plat in TIER1:
        hits = [a for a in rel["assets"]
                if a["platform"] == plat
                and a["component"] == "percolator"
                and (a["xml_capable"] is True
                     or (treat_unknown_as_capable and a["xml_capable"] is None))]
        cover[plat] = [a["name"] for a in hits]
    return cover


def derive(releases: list[dict], treat_unknown_as_capable: bool) -> dict:
    ordered = sorted((r for r in releases if r["version_tuple"]),
                     key=lambda r: r["version_tuple"], reverse=True)
    trace = []
    answer = None
    for rel in ordered:
        cover = platform_coverage(rel, treat_unknown_as_capable)
        missing = [p for p in TIER1 if not cover[p]]
        row = {"tag": rel["tag"], "version": rel["version"],
               "published_at": rel["published_at"],
               "asset_count": len(rel["assets"]),
               "xml_capable_by_platform": cover,
               "missing_platforms": missing,
               "qualifies": not missing}
        trace.append(row)
        if not missing and answer is None:
            answer = rel
    return {
        "rule": ("newest release publishing an XML-capable 'percolator' binary "
                 "for every tier-1 platform (%s); no source builds, and a "
                 "version is a candidate only where upstream publishes a "
                 "binary for that platform" % ", ".join(TIER1)),
        "unknown_treated_as_capable": treat_unknown_as_capable,
        "answer_tag": answer["tag"] if answer else None,
        "answer_version": answer["version"] if answer else None,
        "answer_published_at": answer["published_at"] if answer else None,
        "answer_assets": (platform_coverage(answer, treat_unknown_as_capable)
                          if answer else None),
        "trace": trace,
    }


# --------------------------------------------------------------------------
# reporting
# --------------------------------------------------------------------------

def print_report(doc: dict) -> None:
    w = sys.stdout.write
    w("Percolator release enumeration -- %s\n" % doc["generated_at"])
    w("source: %s   (notes available: %s)\n"
      % (doc["source"], doc["release_notes_available"]))
    w("releases seen: %d\n\n" % len(doc["releases"]))
    for rel in doc["releases"]:
        if rel["version_tuple"] and rel["version_tuple"] < doc["floor_tuple"]:
            continue
        w("== %s  (%s)  %s  assets=%d%s\n"
          % (rel["tag"], rel["version"], rel["published_at"],
             len(rel["assets"]),
             "  [release notes: XML/XSD I/O removed]"
             if rel["xml_removed_by_release_notes"] else ""))
        for a in rel["assets"]:
            xml = {True: "XML", False: "noxml", None: "?"}[a["xml_capable"]]
            size = "%10d" % a["size"] if a["size"] is not None else "         ?"
            w("   %-5s %-13s %-16s %-11s %s  %s\n"
              % (xml, a["platform"], a["packaging"], a["component"], size,
                 a["name"]))
        w("\n")
    for key in ("derivation_strict", "derivation_optimistic"):
        d = doc[key]
        w("---- %s ----\n" % key)
        w("rule: %s\n" % d["rule"])
        w("unknown treated as capable: %s\n" % d["unknown_treated_as_capable"])
        for row in d["trace"]:
            if row["version"] is None:
                continue
            vt = tuple(int(x) for x in row["version"].split("."))
            vt = vt + (0,) * (3 - len(vt))
            if vt < doc["floor_tuple"]:
                continue
            w("  %-12s %-7s %s%s\n"
              % (row["tag"], row["version"],
                 "QUALIFIES" if row["qualifies"] else "no",
                 "" if row["qualifies"]
                 else "  (missing: %s)" % ", ".join(row["missing_platforms"])))
        w("  => latest compatible = %s (%s)\n\n"
          % (d["answer_version"], d["answer_tag"]))


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--cache", default=DEFAULT_CACHE)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--source", choices=("auto", "api", "html"), default="auto")
    ap.add_argument("--refresh", action="store_true",
                    help="ignore the cache and fetch live (spends API quota)")
    ap.add_argument("--floor", default="3.5",
                    help="oldest version to print in the report (default 3.5)")
    args = ap.parse_args(argv)

    fetch = Fetcher(args.cache, args.refresh)
    source = None
    releases = None
    if args.source in ("auto", "api"):
        releases = releases_from_api(fetch)
        if releases is not None:
            source = "api"
    if releases is None and args.source in ("auto", "html"):
        releases = releases_from_html(fetch)
        if releases is not None:
            source = "html"
    if releases is None:
        sys.stderr.write("could not obtain the release list from any source\n")
        return 2

    releases = [classify_release(r) for r in releases]
    releases.sort(key=lambda r: (r["version_tuple"] or (0, 0, 0)), reverse=True)

    floor_parts = [int(x) for x in args.floor.split(".")]
    floor = tuple(floor_parts + [0] * (3 - len(floor_parts)))

    doc = {
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "repository": REPO,
        "source": source,
        "release_notes_available": all(r["notes_available"] for r in releases),
        "fetch_log": fetch.log,
        "floor": args.floor,
        "floor_tuple": floor,
        "tier1_platforms": list(TIER1),
        "classification_rules": {
            "platform": "macOS markers %s beat Windows markers %s beat Linux markers %s"
                        % (MAC_MARKERS, WIN_MARKERS, LINUX_MARKERS),
            "component": "'converters' in name -> converters, else percolator",
            "xml_capable": ("False if 'noxml' in the asset name or the release "
                            "notes say XML/XSD I/O was removed; True if the "
                            "release also publishes the noxml twin of this exact "
                            "asset name; None otherwise"),
        },
        "releases": releases,
    }
    doc["derivation_strict"] = derive(releases, treat_unknown_as_capable=False)
    doc["derivation_optimistic"] = derive(releases, treat_unknown_as_capable=True)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w") as fh:
        json.dump(doc, fh, indent=1, sort_keys=False)
        fh.write("\n")
    print_report(doc)
    sys.stderr.write("wrote %s\n" % args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

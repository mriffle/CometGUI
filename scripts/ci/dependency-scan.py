#!/usr/bin/env python3
"""Scan the project's dependencies for known vulnerabilities, using OSV.

Why OSV and not OWASP dependency-check
--------------------------------------
``org.owasp:dependency-check-maven`` builds its database from the NVD.  Without
an NVD API key that takes hours, and a dependency-check run whose database did
not populate reports NO FINDINGS AND EXITS 0.  This project has no credentials
and an agent may not invent any, so a scanner whose failure mode is a silent
clean bill of health is the wrong tool.

OSV (https://osv.dev) needs no key and answers Maven coordinates directly:

    POST https://api.osv.dev/v1/querybatch
    {"queries": [{"package": {"ecosystem": "Maven",
                              "name": "org.apache.logging.log4j:log4j-core"},
                  "version": "2.14.1"}]}

The single most important behaviour in this file
------------------------------------------------
**It must never print "no vulnerabilities found" because it could not ask.**
A scanner that cannot reach its data source has not scanned anything, and
saying so quietly is worse than not running at all.  Three separate defences:

1. every transport failure -- DNS, connection refused, TLS, timeout, a non-200
   status, a body that is not JSON, a results array of the wrong length --
   exits 4 with a banner that says THE SCAN DID NOT RUN.  There is no code path
   from a network error to exit 0;
2. a KNOWN-VULNERABLE CANARY (log4j-core 2.14.1) is appended to every batch.
   If the endpoint answers, and answers that the canary is clean, then the
   endpoint is lying or is not OSV -- a caching proxy, a captive portal, a
   corporate interceptor returning ``{"results":[{},{},...]}``.  That exits 6.
   An endpoint that cannot detect Log4Shell cannot be trusted to detect
   anything, and this is the check that catches a scanner passing for the
   wrong reason;
3. the component list itself is checked: an SBOM with no components exits 3
   rather than reporting a clean scan of nothing.

What it scans
-------------
* every Maven component in the CycloneDX SBOM (``scripts/ci/sbom.sh``), except
  this project's own reactor modules, which are unpublished SNAPSHOTs;
* every coordinate the build downloads through a ``maven-dependency-plugin``
  ``<artifactItem>``.  Those are real downloads that never appear in the
  dependency graph, so the SBOM cannot see them -- ``org.testfx:openjfx-monocle``
  is one today, and a scan that quietly omitted it would be wrong.

The allowlist
-------------
``scripts/ci/security/allowlist.json``.  Every entry needs an ``id``, the
``package`` it applies to, a ``reason`` and a ``date``; ``review_by`` is
optional.  An entry with no reason, a placeholder reason, an unparseable date
or an unknown field is rejected before any network call (exit 5), and so is an
entry that matches nothing -- a stale acceptance is a place for a real finding
to hide.  A ``review_by`` date in the past makes the entry expire: the finding
fails the build again.

Standard library only: the project virtualenv holds Sphinx and nothing else,
and ``docs/requirements.txt`` must not grow a runtime dependency for this.

Usage
-----
    python3 scripts/ci/dependency-scan.py
    python3 scripts/ci/dependency-scan.py --sbom PATH --allowlist PATH
    python3 scripts/ci/dependency-scan.py --endpoint http://127.0.0.1:9   # demo
    python3 scripts/ci/dependency-scan.py --serve-stub PORT --stub-mode empty

Exit status
-----------
0  every scanned component came back clean, and the canary proves the answer
   came from something that can actually find a vulnerability
1  at least one vulnerability was found and is not allowlisted
2  misuse, or a broken environment
3  the SBOM is missing, unparseable, or lists no components
4  THE SCAN DID NOT RUN -- the OSV API could not be reached or did not answer
   in a form this script understands.  Never confused with "clean"
5  the allowlist is invalid: a missing or placeholder reason, a bad date, an
   unknown field, or an entry that matches no finding
6  the canary control failed: the endpoint answered, but reported a component
   with seven known advisories as clean.  The answer cannot be trusted
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import socket
import sys
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from sbom_verify import (  # noqa: E402  (path set above on purpose)
    PURL_RE,
    Problem,
    json_components,
    load_json_bom,
    load_properties,
    plugin_artifact_items,
    pom_files,
)

EXIT_OK = 0
EXIT_VULNERABLE = 1
EXIT_MISUSE = 2
EXIT_SBOM = 3
EXIT_UNREACHABLE = 4
EXIT_ALLOWLIST = 5
EXIT_CANARY = 6

DEFAULT_ENDPOINT = "https://api.osv.dev"
OWN_GROUP = "org.cometgui"
BATCH_SIZE = 200

# The canary. Verified against the live API on 2026-08-29: this exact
# coordinate returns seven advisory ids, among them GHSA-jfh8-c2jp-5v3q
# (CVE-2021-44228, Log4Shell). It is NOT a dependency of this project; it is
# the control that proves the endpoint can find something.
CANARY = ("org.apache.logging.log4j:log4j-core", "2.14.1")
CANARY_MIN_VULNS = 1

ALLOWLIST_REQUIRED = {"id", "package", "reason", "date"}
ALLOWLIST_OPTIONAL = {"review_by", "comment"}
ID_RE = re.compile(r"^[A-Z][A-Z0-9]*-[A-Za-z0-9.\-]+$")
PLACEHOLDER_REASONS = {"", "-", "n/a", "na", "none", "tbd", "todo", "fixme", "xxx", "?"}
MIN_REASON_CHARS = 20


class ScanFailed(Exception):
    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code


def banner(title: str, lines: list[str], stream=sys.stderr) -> None:
    width = 79
    print("", file=stream)
    print("=" * width, file=stream)
    print(f" {title}", file=stream)
    print("=" * width, file=stream)
    for line in lines:
        print(f" {line}", file=stream)
    print("=" * width, file=stream)
    print("", file=stream)


# --------------------------------------------------------------------------
# What to scan
# --------------------------------------------------------------------------

def components_to_scan(root: Path, sbom: Path) -> tuple[list[dict], list[str]]:
    """Returns (coordinates, notes). Raises ScanFailed(3) on a useless SBOM."""
    try:
        doc = load_json_bom(sbom)
        comps = json_components(doc, sbom)
    except Problem as exc:
        raise ScanFailed(EXIT_SBOM, str(exc)) from exc

    coordinates: list[dict] = []
    notes: list[str] = []
    skipped = 0
    for comp in comps:
        group, artifact, version = PURL_RE.match(comp["purl"]).groups()
        version = version.split("?", 1)[0]
        if group == OWN_GROUP:
            skipped += 1
            continue
        coordinates.append({"name": f"{group}:{artifact}", "version": version, "source": "sbom"})
    notes.append(
        f"{len(comps)} component(s) in the SBOM; {skipped} skipped as this project's own "
        f"unpublished reactor modules ({OWN_GROUP}:*)"
    )

    try:
        poms = pom_files(root)
        extras = plugin_artifact_items(poms, load_properties(poms))
    except Problem as exc:
        raise ScanFailed(EXIT_MISUSE, str(exc)) from exc
    for item in sorted(extras):
        group, artifact, version = item.split(":", 2)
        coordinates.append(
            {"name": f"{group}:{artifact}", "version": version, "source": "pom artifactItem"}
        )
    if extras:
        notes.append(
            f"{len(extras)} coordinate(s) added from maven-dependency-plugin <artifactItem> "
            f"blocks, which never enter the dependency graph and so are absent from the SBOM"
        )

    if not coordinates:
        raise ScanFailed(
            EXIT_SBOM,
            "nothing to scan: the SBOM contained no third-party component and the POMs "
            "declare no extra coordinates. Refusing to report a clean scan of nothing.",
        )
    # De-duplicate on (name, version), keeping the first source seen.
    seen, unique = set(), []
    for coordinate in coordinates:
        key = (coordinate["name"], coordinate["version"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(coordinate)
    return unique, notes


# --------------------------------------------------------------------------
# The allowlist
# --------------------------------------------------------------------------

def load_allowlist(path: Path) -> list[dict]:
    if not path.is_file():
        raise ScanFailed(
            EXIT_ALLOWLIST,
            f"no allowlist at {path}. It is checked in on purpose, so that accepting a "
            f"finding is a reviewable change rather than a command-line flag.",
        )
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise ScanFailed(EXIT_ALLOWLIST, f"{path} is not valid JSON: {exc}") from exc
    if not isinstance(doc, dict) or doc.get("schema") != 1:
        raise ScanFailed(EXIT_ALLOWLIST, f"{path}: expected an object with \"schema\": 1")
    entries = doc.get("entries")
    if not isinstance(entries, list):
        raise ScanFailed(EXIT_ALLOWLIST, f"{path}: \"entries\" must be a list")

    today = dt.date.today()
    problems: list[str] = []
    for index, entry in enumerate(entries):
        where = f"{path.name} entry {index}"
        if not isinstance(entry, dict):
            problems.append(f"{where}: not an object")
            continue
        keys = set(entry)
        for missing in sorted(ALLOWLIST_REQUIRED - keys):
            problems.append(f"{where}: no \"{missing}\"")
        for unknown in sorted(keys - ALLOWLIST_REQUIRED - ALLOWLIST_OPTIONAL):
            problems.append(f"{where}: unknown field \"{unknown}\" (a typo silently disables an entry)")
        identifier = entry.get("id", "")
        if identifier and not ID_RE.match(str(identifier)):
            problems.append(f"{where}: id {identifier!r} does not look like an advisory id")
        reason = str(entry.get("reason", "") or "").strip()
        if reason.lower() in PLACEHOLDER_REASONS:
            problems.append(
                f"{where}: reason is empty or a placeholder ({reason!r}). An accepted "
                f"vulnerability must say WHY it is accepted, or it is not accepted."
            )
        elif len(reason) < MIN_REASON_CHARS:
            problems.append(
                f"{where}: reason is {len(reason)} characters ({reason!r}); at least "
                f"{MIN_REASON_CHARS} are required, because a reason nobody can act on is not one"
            )
        for field in ("date", "review_by"):
            value = entry.get(field)
            if value is None:
                continue
            try:
                parsed = dt.date.fromisoformat(str(value))
            except ValueError:
                problems.append(f"{where}: {field} {value!r} is not an ISO yyyy-mm-dd date")
                continue
            if field == "date" and parsed > today:
                problems.append(f"{where}: date {value} is in the future")
    if problems:
        raise ScanFailed(EXIT_ALLOWLIST, "the allowlist is invalid:\n  * " + "\n  * ".join(problems))
    return entries


# --------------------------------------------------------------------------
# The OSV API
# --------------------------------------------------------------------------

def post_json(url: str, payload: dict, timeout: float) -> dict:
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url, data=body, method="POST",
        headers={"Content-Type": "application/json", "User-Agent": "CometGUI-dependency-scan/1"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            raw = response.read()
    except urllib.error.HTTPError as exc:
        detail = ""
        try:
            detail = exc.read()[:400].decode("utf-8", "replace")
        except Exception:  # noqa: BLE001 -- the status is the point, not the body
            pass
        raise ScanFailed(EXIT_UNREACHABLE, f"POST {url} returned HTTP {exc.code} {exc.reason}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise ScanFailed(EXIT_UNREACHABLE, f"POST {url} could not be reached: {exc.reason}") from exc
    except (TimeoutError, socket.timeout) as exc:
        raise ScanFailed(EXIT_UNREACHABLE, f"POST {url} timed out after {timeout}s") from exc
    except OSError as exc:
        raise ScanFailed(EXIT_UNREACHABLE, f"POST {url} failed: {exc}") from exc

    if status != 200:
        raise ScanFailed(EXIT_UNREACHABLE, f"POST {url} returned HTTP {status}")
    try:
        doc = json.loads(raw.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        head = raw[:200].decode("utf-8", "replace")
        raise ScanFailed(
            EXIT_UNREACHABLE,
            f"POST {url} answered with something that is not JSON (a proxy or an error page?): {head!r}",
        ) from exc
    if not isinstance(doc, dict):
        raise ScanFailed(EXIT_UNREACHABLE, f"POST {url} answered with {type(doc).__name__}, expected an object")
    return doc


def get_json(url: str, timeout: float) -> dict | None:
    """Advisory detail. A failure here is a warning, not a verdict: by the time
    it is called the batch query has already succeeded, so the network works and
    the finding stands with or without its human-readable details."""
    request = urllib.request.Request(url, headers={"User-Agent": "CometGUI-dependency-scan/1"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception:  # noqa: BLE001
        return None


def query_osv(endpoint: str, coordinates: list[dict], timeout: float) -> list[dict]:
    """Returns one result dict per coordinate, in order. Canary appended last."""
    queries = [
        {"package": {"ecosystem": "Maven", "name": c["name"]}, "version": c["version"]}
        for c in coordinates
    ]
    queries.append({"package": {"ecosystem": "Maven", "name": CANARY[0]}, "version": CANARY[1]})

    url = endpoint.rstrip("/") + "/v1/querybatch"
    results: list[dict] = []
    for start in range(0, len(queries), BATCH_SIZE):
        chunk = queries[start:start + BATCH_SIZE]
        doc = post_json(url, {"queries": chunk}, timeout)
        chunk_results = doc.get("results")
        if not isinstance(chunk_results, list):
            raise ScanFailed(
                EXIT_UNREACHABLE,
                f"POST {url} answered without a \"results\" array (keys: {sorted(doc)}). "
                f"This is not an OSV response and nothing has been scanned.",
            )
        if len(chunk_results) != len(chunk):
            raise ScanFailed(
                EXIT_UNREACHABLE,
                f"POST {url} answered {len(chunk_results)} result(s) for {len(chunk)} query(ies). "
                f"The answers cannot be matched to the questions, so nothing has been scanned.",
            )
        results.extend(chunk_results)
    return results


def vulns_of(result: object) -> list[str]:
    if not isinstance(result, dict):
        return []
    vulns = result.get("vulns") or []
    ids = []
    for vuln in vulns:
        if isinstance(vuln, dict) and vuln.get("id"):
            ids.append(str(vuln["id"]))
    return ids


# --------------------------------------------------------------------------
# The scan
# --------------------------------------------------------------------------

def scan(args) -> int:
    root = Path(args.root).resolve()
    sbom = Path(args.sbom).resolve() if args.sbom else root / "_build" / "sbom" / "cometgui-sbom.json"
    allowlist_path = Path(args.allowlist).resolve() if args.allowlist else HERE / "security" / "allowlist.json"

    print("=== CometGUI dependency vulnerability scan (OSV) ===")
    print(f"dependency-scan: sbom       {sbom}")
    print(f"dependency-scan: allowlist  {allowlist_path}")
    print(f"dependency-scan: endpoint   {args.endpoint}")
    proxies = {k: v for k, v in os.environ.items() if k.lower() in ("http_proxy", "https_proxy", "no_proxy")}
    print(f"dependency-scan: proxy env  {proxies or 'none'}")

    # The allowlist is validated BEFORE the network call: an invalid allowlist
    # is a defect in the repository and must be reported whether or not OSV is
    # reachable today.
    allowlist = load_allowlist(allowlist_path)
    print(f"dependency-scan: allowlist  {len(allowlist)} entry(ies), all with a reason and a date")

    coordinates, notes = components_to_scan(root, sbom)
    for note in notes:
        print(f"dependency-scan: {note}")
    print(f"dependency-scan: {len(coordinates)} coordinate(s) to query, plus 1 canary\n")
    for coordinate in coordinates:
        print(f"      {coordinate['name']} {coordinate['version']}  [{coordinate['source']}]")
    print()

    results = query_osv(args.endpoint, coordinates, args.timeout)

    # --- the canary control, before any verdict is printed -----------------
    canary_ids = vulns_of(results[-1])
    if len(canary_ids) < CANARY_MIN_VULNS:
        banner(
            "CANARY CONTROL FAILED -- THE SCAN RESULT CANNOT BE TRUSTED",
            [
                f"{args.endpoint} answered, and reported",
                f"    {CANARY[0]} {CANARY[1]}",
                "as having no known vulnerabilities.",
                "",
                "That component is Log4Shell. It has at least seven advisories, among",
                "them GHSA-jfh8-c2jp-5v3q / CVE-2021-44228. An endpoint that cannot",
                "find those cannot find anything, so this run has scanned nothing --",
                "whatever answered is not OSV: a caching proxy, a captive portal, an",
                "interceptor, or a stubbed test double left switched on.",
                "",
                "This is NOT a clean scan and must never be reported as one.",
            ],
        )
        return EXIT_CANARY
    print(f"dependency-scan: canary control OK -- the endpoint reports {len(canary_ids)} "
          f"advisory(ies) for {CANARY[0]} {CANARY[1]}, so it can find a vulnerability when there is one.")

    # --- findings ----------------------------------------------------------
    findings = []
    for coordinate, result in zip(coordinates, results[:-1]):
        ids = vulns_of(result)
        if not ids:
            continue
        details = []
        for identifier in ids:
            detail = get_json(f"{args.endpoint.rstrip('/')}/v1/vulns/{identifier}", args.timeout)
            details.append({
                "id": identifier,
                "aliases": (detail or {}).get("aliases", []),
                "summary": (detail or {}).get("summary", ""),
                "detail_fetched": detail is not None,
            })
        findings.append({**coordinate, "vulnerabilities": details})

    today = dt.date.today()
    used_entries: set[int] = set()
    unresolved, accepted = [], []
    for finding in findings:
        for vuln in finding["vulnerabilities"]:
            names = {vuln["id"], *(vuln["aliases"] or [])}
            match = None
            for index, entry in enumerate(allowlist):
                if entry["id"] in names and entry["package"] == finding["name"]:
                    match = (index, entry)
                    break
            if match is None:
                unresolved.append((finding, vuln, None))
                continue
            index, entry = match
            used_entries.add(index)
            review_by = entry.get("review_by")
            if review_by and dt.date.fromisoformat(str(review_by)) < today:
                unresolved.append((finding, vuln, f"allowlist entry EXPIRED on {review_by}"))
            else:
                accepted.append((finding, vuln, entry))

    stale = [entry for index, entry in enumerate(allowlist) if index not in used_entries]

    report = {
        "endpoint": args.endpoint,
        "scanned_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "coordinates_scanned": len(coordinates),
        "canary": {"coordinate": f"{CANARY[0]}@{CANARY[1]}", "advisories": canary_ids},
        "findings": findings,
        "accepted": [{"package": f["name"], "id": v["id"], "reason": e["reason"], "date": e["date"]}
                     for f, v, e in accepted],
        "unresolved": [{"package": f["name"], "version": f["version"], "id": v["id"],
                        "aliases": v["aliases"], "summary": v["summary"], "note": note}
                       for f, v, note in unresolved],
    }
    out = Path(args.json_out).resolve() if args.json_out else root / "_build" / "dependency-scan" / "report.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"dependency-scan: report written to {out}")

    for finding, vuln, entry in accepted:
        print(f"\ndependency-scan: ACCEPTED  {finding['name']} {finding['version']}  {vuln['id']}")
        print(f"                 reason: {entry['reason']}")
        print(f"                 accepted on {entry['date']}"
              + (f", review by {entry['review_by']}" if entry.get("review_by") else ""))

    if stale:
        lines = ["An allowlist entry must be justified by a finding that exists today.",
                 "These matched nothing in this scan, so they are dead weight that would",
                 "silently accept a future finding nobody reviewed. Remove them:", ""]
        for entry in stale:
            lines.append(f"    {entry['id']}  {entry['package']}  (accepted {entry['date']})")
        banner("ALLOWLIST ENTRY MATCHES NOTHING", lines)
        return EXIT_ALLOWLIST

    if unresolved:
        lines = [f"{len(unresolved)} vulnerability(ies) with no accepted allowlist entry:", ""]
        for finding, vuln, note in unresolved:
            aliases = ", ".join(vuln["aliases"]) if vuln["aliases"] else "(no aliases)"
            lines.append(f"    {finding['name']} {finding['version']}   [{finding['source']}]")
            lines.append(f"        {vuln['id']}   aliases: {aliases}")
            if vuln["summary"]:
                lines.append(f"        {vuln['summary'][:100]}")
            if not vuln["detail_fetched"]:
                lines.append("        (advisory detail could not be fetched; the finding stands)")
            if note:
                lines.append(f"        {note}")
            lines.append("")
        lines.append("Fix: upgrade the dependency. If it genuinely cannot be upgraded, add an")
        lines.append(f"entry to {Path(args.allowlist).name if args.allowlist else 'scripts/ci/security/allowlist.json'}")
        lines.append("with an id, the package, a real reason and today's date.")
        banner("VULNERABLE DEPENDENCIES FOUND", lines)
        return EXIT_VULNERABLE

    print(f"\ndependency-scan: PASSED -- {len(coordinates)} coordinate(s) scanned against OSV, "
          f"no unaccepted vulnerabilities.")
    print("dependency-scan: this is a real answer: the canary proves the endpoint found "
          f"{len(canary_ids)} advisory(ies) when there were some to find.")
    return EXIT_OK


# --------------------------------------------------------------------------
# A stub OSV endpoint, for proving the canary control works
# --------------------------------------------------------------------------

def serve_stub(port: int, mode: str) -> int:
    """A deliberately dishonest OSV. Used only by dependency-scan.sh --self-test.

    mode=empty    HTTP 200 with a well-formed all-clean answer for every query
                  -- the caching proxy / interceptor case
    mode=garbage  HTTP 200 with an HTML error page
    mode=short    HTTP 200 with fewer results than queries
    """
    import http.server

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *args):  # keep the transcript readable
            pass

        def do_POST(self):  # noqa: N802
            length = int(self.headers.get("Content-Length", 0))
            payload = json.loads(self.rfile.read(length) or b"{}")
            count = len(payload.get("queries", []))
            if mode == "garbage":
                body = b"<html><body>502 Bad Gateway (this is not JSON)</body></html>"
                ctype = "text/html"
            elif mode == "short":
                body = json.dumps({"results": [{}] * max(0, count - 1)}).encode()
                ctype = "application/json"
            else:
                body = json.dumps({"results": [{} for _ in range(count)]}).encode()
                ctype = "application/json"
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):  # noqa: N802
            self.send_response(404)
            self.end_headers()

    server = http.server.HTTPServer(("127.0.0.1", port), Handler)
    print(f"stub-osv: listening on http://127.0.0.1:{port} in mode={mode}", flush=True)
    server.serve_forever()
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=str(HERE.parent.parent))
    parser.add_argument("--sbom", default=None)
    parser.add_argument("--allowlist", default=None)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--json-out", default=None)
    parser.add_argument("--serve-stub", type=int, default=None, metavar="PORT")
    parser.add_argument("--stub-mode", default="empty", choices=("empty", "garbage", "short"))
    args = parser.parse_args(argv)

    if args.serve_stub is not None:
        return serve_stub(args.serve_stub, args.stub_mode)

    try:
        return scan(args)
    except ScanFailed as exc:
        if exc.code == EXIT_UNREACHABLE:
            banner(
                "THE DEPENDENCY SCAN DID NOT RUN",
                [
                    str(exc),
                    "",
                    "Nothing has been scanned. This is NOT a clean result, and this step",
                    "must not be treated as passing: a scanner that cannot reach its data",
                    "source has no opinion about your dependencies.",
                    "",
                    "Check network access to the OSV API from this runner, or the proxy",
                    "settings printed above. Do not 'fix' this by ignoring the exit code.",
                ],
            )
        else:
            print(f"\ndependency-scan: {exc}", file=sys.stderr)
        return exc.code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

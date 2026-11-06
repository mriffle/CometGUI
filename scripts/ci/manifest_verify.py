#!/usr/bin/env python3
"""Verify every artefact ``manifests/tools.json`` pins against upstream (R-TEST-08).

Why this job is load-bearing
----------------------------
CometGUI redistributes no tool (``D-008``): every managed binary is fetched by
the user's own copy of CometGUI, from upstream, by pinned URL, size, SHA-256
and MD5. When upstream deletes a release asset, re-tags a release, or rebuilds
an asset in place, **every new installation breaks** and the project holds no
copy to fall back on. This job is the only thing that notices before a user
does. ``R-TEST-08``: it "shall fail loudly when an upstream artefact
disappears, changes checksum, or a new upstream release appears -- the
condition that silently invalidated the PDV 2.6.0 pin".

What it checks
--------------
For every artefact record **and every companion record** of the manifest:

#. its URL is downloaded (each distinct URL once -- several records share one),
   streamed to a temporary directory outside the repository, and never
   executed or extracted to disk;
#. the byte size, SHA-256 and MD5 of the bytes are compared, in full, with the
   record's;
#. where the record pins an archive **member** (``member``/``memberSizeBytes``/
   ``memberSha256``/``memberMd5`` on a record, ``members`` on a companion), that
   member's bytes are read out of the downloaded archive in memory -- with the
   reader the record's ``kind`` names, never one guessed from the URL -- and
   compared too;
#. for every GitHub repository the manifest's URLs name, the releases listing
   (``api.github.com/repos/<owner>/<repo>/releases``) is read and the newest
   non-draft, non-prerelease release must carry a tag the manifest records.

How it fails, and the exit status
---------------------------------
==  ==============================================================================
0   VERIFIED: every record matched upstream and every repository's newest release
    is recorded
1   UPSTREAM CHANGED: at least one red verdict -- (a) a size or digest changed,
    (b) an artefact is gone upstream (HTTP 404/410, or a redirect to nothing),
    (c) a new upstream release the manifest does not record
2   misuse, or an input this job refuses (the manifest, or the not-adopted list)
3   COULD NOT VERIFY: no red verdict, but at least one check could not be
    completed (network down, a persistent transport failure, rate limiting, an
    archive this reader cannot open). **Never green**: an unverified manifest is
    not a verified one
7   ``--self-test`` only: a case did not behave as required
==  ==============================================================================

When there is both a red verdict and an unverifiable check, the exit status is
1 and both are printed: the verdict is the more specific fact.

What is retried, and why that hides nothing
-------------------------------------------
Only **transport** failures are retried: a refused, reset or aborted
connection, a connection closed before the response or before the body's
declared length, a socket timeout, a DNS failure, and HTTP 500/502/503/504.
None of those is a statement about the artefact -- they are statements about
the path to it. A retry starts the transfer again from byte zero, so a verdict
is only ever made over one complete response. Every failed attempt is printed
with its cause, the number of attempts is fixed (``ATTEMPTS``), and when the
last one fails too the job exits 3, which is red. **Never retried:** a 404 or
410, a redirect to nothing, a size or digest mismatch, a missing member, a
rate limit or any other HTTP status -- each of those is either a verdict or a
fact that a second request cannot change, and asking twice for a verdict is
how a flaky upstream gets averaged into a green one.

"Seen, deliberately not adopted"
--------------------------------
A newer upstream release the project has decided not to adopt can be recorded
in ``scripts/ci/manifest/not-adopted.json`` -- one entry per release, each with
its repository, tag, a reason a reader can act on, and the ISO date it was
decided. **Empty is the correct state.** An entry is refused if its reason is
short or a placeholder, its date is malformed or in the future, its tag is
already recorded by the manifest, or it no longer matches an unrecorded newer
release (a stale entry is a place for the next release to hide). It is a
reviewable change to a committed file, never a flag.

Usage
-----
    python3 scripts/ci/manifest_verify.py
    python3 scripts/ci/manifest_verify.py --manifest PATH --not-adopted PATH
    python3 scripts/ci/manifest_verify.py --self-test

Standard library only. Network: the manifest's own download URLs, wherever
GitHub redirects them, and ``api.github.com``'s releases listing of the
repositories those URLs name (``P15-5b``). ``--self-test`` touches none of
them: it serves synthetic artefacts and listings from a loopback server.
"""

from __future__ import annotations

import contextlib
import datetime as _dt
import gzip
import hashlib
import http.client
import http.server
import io
import json
import os
import re
import shutil
import socket
import ssl
import struct
import sys
import tarfile
import tempfile
import threading
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
import zlib
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))
import toolmatrix  # noqa: E402  (scripts/toolmatrix.py -- the one manifest validator)

EXIT_VERIFIED = 0
EXIT_CHANGED = 1
EXIT_MISUSE = 2
EXIT_UNVERIFIED = 3
EXIT_SELFTEST_FAILED = 7

#: Attempts per request, transport failures only (see the module docstring).
ATTEMPTS = 3
#: Seconds to wait before attempt 2 and attempt 3.
RETRY_DELAYS = (10.0, 30.0)
#: Seconds any one socket operation may block.
SOCKET_TIMEOUT = 60.0
#: Seconds one whole transfer attempt may take (the largest artefact is ~100 MB).
TRANSFER_DEADLINE = 1800.0
#: Bytes read from one response at most, whatever the record says: a response
#: that runs past this is reported as a size change, not read forever.
MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024 * 1024
#: Bytes decompressed from one archive at most, against a decompression bomb.
MAX_EXPANDED_BYTES = 4 * 1024 * 1024 * 1024
#: The xar table of contents is inflated to at most this.
MAX_TOC_BYTES = 8 * 1024 * 1024
CHUNK = 1024 * 1024

DEFAULT_MANIFEST = ROOT / "manifests" / "tools.json"
DEFAULT_NOT_ADOPTED = HERE / "manifest" / "not-adopted.json"
MANIFEST_NAME = "manifests/tools.json"
USER_AGENT = "CometGUI-manifest-verify/1 (R-TEST-08; +https://github.com/mriffle/CometGUI)"

RELEASE_URL = re.compile(r"^https://github\.com/([^/]+)/([^/]+)/releases/download/([^/]+)/[^/]+$")
TRANSIENT_STATUS = (500, 502, 503, 504)
GONE_STATUS = (404, 410)
PLACEHOLDER_REASONS = {"todo", "tbd", "n/a", "na", "none", "not adopted", "skip", "later", "-", "?"}
MIN_REASON_CHARS = 40
NOT_ADOPTED_FIELDS = ("repository", "tag", "reason", "date")


# --------------------------------------------------------------------------
# Where requests go.  Production has no knob: the self-test alone builds an
# Endpoints that points at its loopback server, in-process.
# --------------------------------------------------------------------------


@dataclass
class Endpoints:
    download_from: str = "https://github.com/"
    download_to: str = "https://github.com/"
    api_base: str = "https://api.github.com"
    allow_http: bool = False
    attempts: int = ATTEMPTS
    delays: tuple = RETRY_DELAYS
    timeout: float = SOCKET_TIMEOUT
    deadline: float = TRANSFER_DEADLINE
    sleep: object = time.sleep
    today: _dt.date = field(default_factory=lambda: _dt.datetime.now(_dt.timezone.utc).date())

    def download_url(self, url: str) -> str:
        if url.startswith(self.download_from):
            return self.download_to + url[len(self.download_from):]
        return url


class Gone(Exception):
    """The URL answered that the artefact is not there: a verdict (b)."""

    def __init__(self, status: int, detail: str):
        super().__init__(detail)
        self.status = status
        self.detail = detail


class Transient(Exception):
    """A transport failure: says nothing about the artefact; retried."""


class Unverifiable(Exception):
    """A check that could not be completed and that a retry cannot help."""


class InvalidInput(Exception):
    """An input this job refuses: exit 2."""


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------


class _RedirectPolicy(urllib.request.HTTPRedirectHandler):
    """Follow redirects, never off https (outside the loopback self-test)."""

    def __init__(self, allow_http: bool):
        super().__init__()
        self.allow_http = allow_http

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not newurl.startswith("https://") and not self.allow_http:
            raise Unverifiable(
                f"{req.full_url} redirected to {newurl!r}, which is not https; this job "
                "does not fetch a pinned artefact over a downgraded connection"
            )
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _opener(endpoints: Endpoints):
    return urllib.request.build_opener(_RedirectPolicy(endpoints.allow_http))


def _classify_url_error(error: urllib.error.URLError) -> Exception:
    reason = error.reason
    if isinstance(reason, ssl.SSLCertVerificationError):
        return Unverifiable(f"TLS certificate verification failed: {reason}")
    if isinstance(reason, (OSError, socket.timeout, TimeoutError)):
        return Transient(f"{type(reason).__name__}: {reason}")
    return Unverifiable(f"{reason}")


def _one_attempt(url: str, endpoints: Endpoints, accept: str, sink):
    """One request. Returns (final_url, bytes_read); raises Gone/Transient/Unverifiable."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": accept})
    started = time.monotonic()
    try:
        response = _opener(endpoints).open(request, timeout=endpoints.timeout)
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        if status in GONE_STATUS:
            raise Gone(status, f"HTTP {status}") from None
        if 300 <= status < 400:
            raise Gone(status, f"HTTP {status} redirect with no usable Location (a redirect to nothing)") from None
        if status in TRANSIENT_STATUS:
            raise Transient(f"HTTP {status}") from None
        if status in (403, 429):
            raise Unverifiable(
                f"HTTP {status} (refused or rate limited; GitHub allows 60 unauthenticated API "
                "requests an hour per address, and this job holds no credential by design)"
            ) from None
        raise Unverifiable(f"unexpected HTTP {status}") from None
    except urllib.error.URLError as error:
        raise _classify_url_error(error) from None
    except (http.client.HTTPException, ConnectionError, socket.timeout, TimeoutError) as error:
        raise Transient(f"{type(error).__name__}: {error}") from None
    with response:
        final = response.geturl()
        if not final.startswith("https://") and not endpoints.allow_http:
            raise Unverifiable(f"{url} ended at {final!r}, which is not https")
        total = 0
        try:
            while True:
                if time.monotonic() - started > endpoints.deadline:
                    raise Transient(f"the transfer took longer than {endpoints.deadline:.0f} s")
                chunk = response.read(CHUNK)
                if not chunk:
                    break
                total += len(chunk)
                sink(chunk)
                if total > MAX_DOWNLOAD_BYTES:
                    break
        except (http.client.HTTPException, ConnectionError, socket.timeout, TimeoutError, OSError) as error:
            raise Transient(f"{type(error).__name__} after {total} bytes: {error}") from None
        return final, total


def fetch(url: str, endpoints: Endpoints, accept: str, sink_factory, log) -> tuple:
    """Fetch with bounded retries for transport failures only.

    ``sink_factory()`` returns a fresh ``write`` callable for each attempt, so
    a retried transfer starts again from byte zero.
    """
    last = None
    for attempt in range(1, endpoints.attempts + 1):
        try:
            return _one_attempt(url, endpoints, accept, sink_factory())
        except Transient as error:
            last = error
            if attempt < endpoints.attempts:
                delay = endpoints.delays[min(attempt - 1, len(endpoints.delays) - 1)]
                log(f"    transport: attempt {attempt} of {endpoints.attempts} failed ({error}); "
                    f"retrying from byte 0 in {delay:g} s")
                endpoints.sleep(delay)
            else:
                log(f"    transport: attempt {attempt} of {endpoints.attempts} failed ({error}); "
                    "no attempts left")
    raise Unverifiable(
        f"a transport failure persisted through all {endpoints.attempts} attempts; last: {last}"
    )


# --------------------------------------------------------------------------
# The manifest, read as records
# --------------------------------------------------------------------------


@dataclass
class Member:
    path: str
    size: int
    sha256: str
    md5: str


@dataclass
class Record:
    label: str
    tool: str
    url: str
    kind: str
    size: int
    sha256: str
    md5: str
    members: list


def _member_fields(where: str, artefact: dict) -> list:
    names = ("member", "memberSizeBytes", "memberSha256", "memberMd5")
    present = [name for name in names if name in artefact]
    if not present:
        return []
    missing = [name for name in names if name not in artefact]
    if missing:
        raise InvalidInput(
            f"{where} pins an archive member but lacks {', '.join(missing)}; a member is "
            "pinned by path, size, SHA-256 and MD5 together or not at all"
        )
    member = Member(artefact["member"], artefact["memberSizeBytes"],
                    artefact["memberSha256"], artefact["memberMd5"])
    if not re.fullmatch(r"[0-9a-f]{64}", str(member.sha256)) or not re.fullmatch(r"[0-9a-f]{32}", str(member.md5)):
        raise InvalidInput(f"{where} has a member digest that is not lower-case hex of the right length")
    return [member]


def records_of(manifest: dict) -> list:
    records = []
    for index, artefact in enumerate(manifest["artefacts"]):
        base = f"{artefact['tool']} {artefact['version']} {artefact['os']} {artefact['arch']}"
        records.append(Record(base, artefact["tool"], artefact["url"], artefact["kind"],
                              artefact["sizeBytes"], artefact["sha256"], artefact["md5"],
                              _member_fields(f"artefacts[{index}] ({base})", artefact)))
        for companion in artefact["companions"]:
            members = []
            for member in companion["members"]:
                md5 = member.get("md5")
                if not isinstance(md5, str) or not re.fullmatch(r"[0-9a-f]{32}", md5):
                    raise InvalidInput(f"{base} companion {companion['id']}: member {member.get('path')!r} has no valid md5")
                members.append(Member(member["path"], member["sizeBytes"], member["sha256"], md5))
            records.append(Record(f"{base} companion {companion['id']}", artefact["tool"],
                                  companion["url"], companion["kind"], companion["sizeBytes"],
                                  companion["sha256"], companion["md5"], members))
    return records


def repositories_of(manifest: dict) -> dict:
    """``owner/repo`` -> {"tools": set, "tags": set}, from the manifest's URLs and tags."""
    repos: dict = {}
    for index, artefact in enumerate(manifest["artefacts"]):
        urls = [artefact["url"]] + [companion["url"] for companion in artefact["companions"]]
        for url in urls:
            match = RELEASE_URL.match(url)
            if not match:
                raise InvalidInput(
                    f"artefacts[{index}] ({artefact['tool']} {artefact['version']}) has the URL "
                    f"{url}, which is not a GitHub release download; this job derives the "
                    "repository whose releases it watches from that form, and will not guess"
                )
            owner, repo, tag = match.groups()
            entry = repos.setdefault(f"{owner}/{repo}", {"tools": set(), "tags": set()})
            entry["tools"].add(artefact["tool"])
            entry["tags"].add(tag)
        entry["tags"].add(artefact["releaseTag"])
    return repos


def load_not_adopted(path: Path, repos: dict, today: _dt.date) -> list:
    try:
        document = json.loads(Path(path).read_text(encoding="utf-8"))
    except OSError as error:
        raise InvalidInput(f"cannot read the not-adopted list {path}: {error}") from None
    except json.JSONDecodeError as error:
        raise InvalidInput(f"the not-adopted list {path} is not JSON: {error}") from None
    if not isinstance(document, dict) or document.get("schema") != 1 or not isinstance(document.get("entries"), list):
        raise InvalidInput(f"the not-adopted list {path} must be an object with \"schema\": 1 and an \"entries\" array")
    problems, seen, entries = [], set(), []
    for index, entry in enumerate(document["entries"]):
        at = f"entries[{index}]"
        if not isinstance(entry, dict):
            problems.append(f"{at} is not an object")
            continue
        unknown = sorted(set(entry) - set(NOT_ADOPTED_FIELDS))
        missing = [name for name in NOT_ADOPTED_FIELDS if name not in entry]
        if unknown:
            problems.append(f"{at} has unknown field(s) {unknown}")
        if missing:
            problems.append(f"{at} lacks {missing}")
            continue
        repository, tag, reason, date = (entry[name] for name in NOT_ADOPTED_FIELDS)
        at = f"{at} ({repository} {tag})"
        if repository not in repos:
            problems.append(f"{at}: no manifest URL names the repository {repository!r}")
        elif tag in repos[repository]["tags"]:
            problems.append(f"{at}: the manifest records {tag}, so it has been adopted; delete the entry")
        if not isinstance(reason, str) or reason.strip().lower() in PLACEHOLDER_REASONS \
                or len(reason.strip()) < MIN_REASON_CHARS:
            problems.append(f"{at}: the reason {reason!r} is missing, a placeholder or shorter than "
                            f"{MIN_REASON_CHARS} characters; say why, in words a reader can act on")
        try:
            decided = _dt.date.fromisoformat(date) if isinstance(date, str) else None
        except ValueError:
            decided = None
        if decided is None:
            problems.append(f"{at}: the date {date!r} is not an ISO date (YYYY-MM-DD)")
        elif decided > today:
            problems.append(f"{at}: the date {date} is in the future")
        if (repository, tag) in seen:
            problems.append(f"{at}: listed twice")
        seen.add((repository, tag))
        entries.append({"repository": repository, "tag": tag, "reason": reason, "date": date, "used": False})
    if problems:
        raise InvalidInput(f"the not-adopted list {path} is invalid:\n  * " + "\n  * ".join(problems))
    return entries


# --------------------------------------------------------------------------
# Archive members, read without executing or extracting anything to disk
# --------------------------------------------------------------------------


class _Digest:
    def __init__(self):
        self.sha256 = hashlib.sha256()
        self.md5 = hashlib.md5()
        self.size = 0

    def update(self, chunk: bytes):
        self.sha256.update(chunk)
        self.md5.update(chunk)
        self.size += len(chunk)

    def result(self) -> dict:
        return {"size": self.size, "sha256": self.sha256.hexdigest(), "md5": self.md5.hexdigest()}


def _drain(stream, digest: _Digest, budget: list):
    while True:
        chunk = stream.read(CHUNK)
        if not chunk:
            return
        budget[0] -= len(chunk)
        if budget[0] < 0:
            raise Unverifiable(f"the archive expands past {MAX_EXPANDED_BYTES} bytes; refused as a decompression bomb")
        digest.update(chunk)


def _normalise(name: str) -> str:
    while name.startswith("./"):
        name = name[2:]
    return name


def _members_zip(path: Path, wanted: set) -> dict:
    found: dict = {}
    budget = [MAX_EXPANDED_BYTES]
    try:
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if info.filename in wanted:
                    digest = _Digest()
                    with archive.open(info) as stream:
                        _drain(stream, digest, budget)
                    found.setdefault(info.filename, []).append(digest.result())
    except (zipfile.BadZipFile, zlib.error, NotImplementedError, EOFError) as error:
        raise Unverifiable(f"the ZIP reader cannot read it: {error}") from None
    return found


def _members_tar_stream(fileobj, wanted: set, budget: list, found: dict, what: str):
    try:
        with tarfile.open(fileobj=fileobj, mode="r|*") as archive:
            for info in archive:
                name = _normalise(info.name)
                if name in wanted and info.isfile():
                    digest = _Digest()
                    stream = archive.extractfile(info)
                    _drain(stream, digest, budget)
                    found.setdefault(name, []).append(digest.result())
    except (tarfile.TarError, zlib.error, EOFError, OSError) as error:
        raise Unverifiable(f"the {what} reader cannot read it: {error}") from None


def _members_tar(path: Path, wanted: set) -> dict:
    found: dict = {}
    with open(path, "rb") as handle:
        _members_tar_stream(handle, wanted, [MAX_EXPANDED_BYTES], found, "tar")
    return found


class _Slice(io.RawIOBase):
    """A read-only window onto part of a file."""

    def __init__(self, handle, offset: int, length: int):
        super().__init__()
        self.handle, self.position, self.remaining = handle, offset, length

    def readable(self):
        return True

    def readinto(self, buffer):
        if self.remaining <= 0:
            return 0
        self.handle.seek(self.position)
        data = self.handle.read(min(len(buffer), self.remaining))
        buffer[:len(data)] = data
        self.position += len(data)
        self.remaining -= len(data)
        return len(data)


def _members_deb(path: Path, wanted: set) -> dict:
    found: dict = {}
    with open(path, "rb") as handle:
        if handle.read(8) != b"!<arch>\n":
            raise Unverifiable("it does not begin with the ar magic \"!<arch>\", so it is not a .deb")
        while True:
            header = handle.read(60)
            if not header:
                break
            if len(header) != 60 or header[58:60] != b"`\n":
                raise Unverifiable("its ar member header is malformed")
            name = header[0:16].decode("ascii", "replace").strip().rstrip("/")
            try:
                size = int(header[48:58].decode("ascii").strip())
            except ValueError:
                raise Unverifiable("its ar member size is not a number") from None
            start = handle.tell()
            if name.startswith("data.tar"):
                if name.endswith(".zst"):
                    raise Unverifiable(f"its payload is {name}; Python's standard library has no zstd reader")
                window = io.BufferedReader(_Slice(handle, start, size), CHUNK)
                _members_tar_stream(window, wanted, [MAX_EXPANDED_BYTES], found, f".deb {name}")
                return found
            handle.seek(start + size + (size % 2))
    raise Unverifiable("it carries no data.tar.* member, so it installs no files")


def _cpio_odc(stream, wanted: set, budget: list, found: dict):
    while True:
        header = stream.read(76)
        if len(header) != 76:
            raise Unverifiable("its cpio payload ends without a TRAILER!!! entry")
        if header[0:6] != b"070707":
            raise Unverifiable(f"its cpio payload has the magic {header[0:6]!r}, not the odc 070707")
        try:
            namesize = int(header[59:65], 8)
            filesize = int(header[65:76], 8)
        except ValueError:
            raise Unverifiable("its cpio header carries a non-octal field") from None
        name = stream.read(namesize).rstrip(b"\0").decode("utf-8", "replace")
        if name == "TRAILER!!!":
            return
        name = _normalise(name)
        digest = _Digest() if name in wanted else None
        remaining = filesize
        while remaining:
            chunk = stream.read(min(CHUNK, remaining))
            if not chunk:
                raise Unverifiable(f"its cpio payload is truncated inside {name}")
            remaining -= len(chunk)
            budget[0] -= len(chunk)
            if budget[0] < 0:
                raise Unverifiable(f"the archive expands past {MAX_EXPANDED_BYTES} bytes; refused as a decompression bomb")
            if digest:
                digest.update(chunk)
        if digest:
            found.setdefault(name, []).append(digest.result())


class _Inflating(io.RawIOBase):
    def __init__(self, raw):
        super().__init__()
        self.raw, self.inflater, self.pending = raw, zlib.decompressobj(), b""

    def readable(self):
        return True

    def readinto(self, buffer):
        while not self.pending:
            data = self.raw.read(CHUNK)
            if not data:
                self.pending = self.inflater.flush()
                if not self.pending:
                    return 0
                break
            self.pending = self.inflater.decompress(data)
        count = min(len(buffer), len(self.pending))
        buffer[:count] = self.pending[:count]
        self.pending = self.pending[count:]
        return count


def _members_pkg(path: Path, wanted: set) -> dict:
    found: dict = {}
    with open(path, "rb") as handle:
        header = handle.read(28)
        if len(header) != 28 or header[0:4] != b"xar!":
            raise Unverifiable("it does not begin with \"xar!\", so it is not a macOS flat package")
        header_size, _version, toc_compressed, toc_uncompressed, _alg = struct.unpack(">HHQQI", header[4:28])
        if toc_compressed > MAX_TOC_BYTES or toc_uncompressed > MAX_TOC_BYTES:
            raise Unverifiable("its table of contents is larger than this reader inflates")
        handle.seek(header_size)
        try:
            toc = zlib.decompress(handle.read(toc_compressed))
        except zlib.error as error:
            raise Unverifiable(f"its table of contents does not inflate: {error}") from None
        if len(toc) != toc_uncompressed:
            raise Unverifiable(f"its table of contents declares {toc_uncompressed} bytes and inflates to {len(toc)}")
        if b"<!DOCTYPE" in toc or b"<!ENTITY" in toc:
            raise Unverifiable("its table of contents carries a DOCTYPE or ENTITY declaration; refused unparsed")
        try:
            root = ET.fromstring(toc)
        except ET.ParseError as error:
            raise Unverifiable(f"its table of contents is not XML: {error}") from None
        data = None
        for element in root.iter("file"):
            name = element.find("name")
            if name is not None and name.text == "Payload":
                data = element.find("data")
                break
        if data is None:
            raise Unverifiable("its table of contents lists no Payload blob")
        try:
            offset = int(data.findtext("offset"))
            length = int(data.findtext("length"))
        except (TypeError, ValueError):
            raise Unverifiable("its Payload entry has no numeric offset and length") from None
        encoding = data.find("encoding")
        style = encoding.get("style", "") if encoding is not None else ""
        heap = header_size + toc_compressed
        blob = io.BufferedReader(_Slice(handle, heap + offset, length), CHUNK)
        if style in ("", "application/octet-stream"):
            stream = blob
        elif style == "application/x-gzip":
            stream = io.BufferedReader(_Inflating(blob), CHUNK)
        else:
            raise Unverifiable(f"its Payload is encoded as {style!r}; this reader decodes raw and zlib only")
        sniff = stream.peek(6)[:6]
        if sniff[:2] == b"\x1f\x8b":
            stream = gzip.GzipFile(fileobj=stream)
        elif not sniff.startswith(b"0707"):
            raise Unverifiable(f"its Payload begins {sniff!r}, neither gzip nor cpio")
        try:
            _cpio_odc(stream, wanted, [MAX_EXPANDED_BYTES], found)
        except (OSError, EOFError, zlib.error) as error:
            raise Unverifiable(f"its Payload does not decompress: {error}") from None
    return found


MEMBER_READERS = {
    "ZIP": _members_zip,
    "JAR": _members_zip,
    "TAR_GZ": _members_tar,
    "DEB_PAYLOAD": _members_deb,
    "PKG_PAYLOAD": _members_pkg,
}


# --------------------------------------------------------------------------
# The verification
# --------------------------------------------------------------------------


@dataclass
class Report:
    ok: list = field(default_factory=list)
    changed: list = field(default_factory=list)
    gone: list = field(default_factory=list)
    new_releases: list = field(default_factory=list)
    unverified: list = field(default_factory=list)
    not_adopted_seen: list = field(default_factory=list)
    downloads: int = 0
    download_bytes: int = 0
    members: int = 0
    listings: int = 0


def _compare(expected: dict, actual: dict) -> list:
    """Every field that differs, compared in full."""
    return [name for name in ("size", "sha256", "md5") if expected[name] != actual[name]]


def _difference_lines(url: str, expected: dict, actual: dict, differs: list) -> list:
    lines = [f"    url       {url}"]
    for name, unit in (("size", " bytes"), ("sha256", ""), ("md5", "")):
        mark = "   <-- CHANGED" if name in differs else ""
        lines.append(f"    {name:<9} expected {expected[name]}{unit}, actual {actual[name]}{unit}{mark}")
    return lines


def _gone_message(url: str, status: int, detail: str, sha256: str) -> str:
    return (f"    the pinned artefact is no longer available upstream: {detail} for {url}; expected "
            f"sha-256 {sha256}. CometGUI redistributes no tool binary and holds no copy (D-008), "
            "so this is an upstream availability failure, not a corrupt download: the release "
            "asset has been removed or re-tagged, and every new install of this record fails "
            "until the manifest is changed.")


def verify_artefacts(records: list, endpoints: Endpoints, work: Path, report: Report, log):
    by_url: dict = {}
    for record in records:
        by_url.setdefault(record.url, []).append(record)
    for number, (url, sharing) in enumerate(by_url.items(), 1):
        labels = "; ".join(record.label for record in sharing)
        log(f"\n[{number}/{len(by_url)}] {url}")
        log(f"    pinned by {len(sharing)} record(s): {labels}")
        target = work / f"artefact-{number}"
        state = {}

        def sink_factory():
            if "handle" in state:
                state["handle"].close()
            digest = _Digest()
            handle = open(target, "wb")
            state["digest"], state["handle"] = digest, handle

            def write(chunk):
                digest.update(chunk)
                handle.write(chunk)
            return write

        try:
            final, _ = fetch(endpoints.download_url(url), endpoints, "application/octet-stream", sink_factory, log)
        except Gone as gone:
            for record in sharing:
                report.gone.append((record, url, gone))
            log(f"    GONE      {gone.detail}")
            continue
        except Unverifiable as error:
            report.unverified.append((f"download of {url} (pinned by {labels})", str(error)))
            log(f"    UNVERIFIED {error}")
            continue
        finally:
            if "handle" in state:
                state["handle"].close()
        actual = state["digest"].result()
        report.downloads += 1
        report.download_bytes += actual["size"]
        log(f"    fetched   {actual['size']} bytes from {final.split('?')[0]}")
        log(f"              sha256 {actual['sha256']}  md5 {actual['md5']}")
        intact = []
        for record in sharing:
            expected = {"size": record.size, "sha256": record.sha256, "md5": record.md5}
            differs = _compare(expected, actual)
            if differs:
                report.changed.append((record.label, url, expected, actual, differs))
                log(f"    CHANGED   {record.label}: {', '.join(differs)} differ(s)")
            else:
                report.ok.append(record.label)
                log(f"    ok        {record.label}")
                intact.append(record)
        for record in sharing:
            if record not in intact and record.members:
                log(f"    members of {record.label} not checked: the artefact itself changed")
        _verify_members(intact, url, target, actual, report, log)
        with contextlib.suppress(OSError):
            target.unlink()


def _verify_members(records: list, url: str, path: Path, whole: dict, report: Report, log):
    pinned = [(record, member) for record in records for member in record.members]
    if not pinned:
        return
    kind = records[0].kind
    if kind == "BARE_EXECUTABLE":
        found = {member.path: [whole] for _, member in pinned}
    else:
        reader = MEMBER_READERS.get(kind)
        if reader is None:
            report.unverified.append((f"members of {url}", f"no member reader for the kind {kind}"))
            return
        try:
            found = reader(path, {member.path for _, member in pinned})
        except Unverifiable as error:
            report.unverified.append((f"members of {url} ({kind})", str(error)))
            log(f"    UNVERIFIED members: {error}")
            return
    for record, member in pinned:
        label = f"{record.label} member {member.path}"
        expected = {"size": member.size, "sha256": member.sha256, "md5": member.md5}
        hits = found.get(member.path, [])
        report.members += 1
        if len(hits) != 1:
            report.changed.append((label, url, expected, None,
                                   ["absent" if not hits else f"appears {len(hits)} times"]))
            log(f"    CHANGED   {label}: {'absent from' if not hits else 'repeated in'} the {kind} archive")
            continue
        differs = _compare(expected, hits[0])
        if differs:
            report.changed.append((label, url, expected, hits[0], differs))
            log(f"    CHANGED   {label}: {', '.join(differs)} differ(s)")
        else:
            report.ok.append(label)
            log(f"    ok        {label}")


def _release_date(release: dict) -> str:
    return str(release.get("published_at") or release.get("created_at") or "")


def verify_releases(repos: dict, not_adopted: list, endpoints: Endpoints, report: Report, log):
    for name in sorted(repos):
        entry = repos[name]
        url = f"{endpoints.api_base}/repos/{name}/releases?per_page=100"
        log(f"\n[releases] {name} (tools: {', '.join(sorted(entry['tools']))}; "
            f"recorded tags: {', '.join(sorted(entry['tags']))})")
        buffer = {}

        def sink_factory():
            chunks = []
            buffer["chunks"] = chunks
            return chunks.append

        try:
            fetch(url, endpoints, "application/vnd.github+json", sink_factory, log)
            listing = json.loads(b"".join(buffer["chunks"]).decode("utf-8"))
        except (Gone, Unverifiable) as error:
            detail = error.detail if isinstance(error, Gone) else str(error)
            report.unverified.append((f"the releases listing of {name}", detail))
            log(f"    UNVERIFIED {detail}")
            continue
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            report.unverified.append((f"the releases listing of {name}", f"not JSON: {error}"))
            continue
        if not isinstance(listing, list) or not all(isinstance(item, dict) and "tag_name" in item for item in listing):
            report.unverified.append((f"the releases listing of {name}", "not a list of releases"))
            continue
        report.listings += 1
        published = []
        for release in listing:
            if release.get("draft") or release.get("prerelease"):
                log(f"    ignored   {release['tag_name']} ({'draft' if release.get('draft') else 'prerelease'})")
                continue
            published.append(release)
        if not published:
            report.unverified.append((f"the releases listing of {name}", "it lists no published release"))
            continue
        published.sort(key=_release_date, reverse=True)
        newer = []
        for release in published:
            if release["tag_name"] in entry["tags"]:
                log(f"    newest recorded release {release['tag_name']} ({_release_date(release)}); "
                    f"{len(newer)} unrecorded release(s) newer than it")
                break
            newer.append(release)
        else:
            log("    none of the recorded tags appears among the newest 100 published releases")
        for release in newer:
            tag = release["tag_name"]
            waiver = next((item for item in not_adopted if item["repository"] == name and item["tag"] == tag), None)
            if waiver:
                waiver["used"] = True
                report.not_adopted_seen.append((name, tag, _release_date(release), waiver))
                log(f"    seen      {tag}: deliberately not adopted ({waiver['date']}): {waiver['reason']}")
            else:
                report.new_releases.append((name, tag, _release_date(release), entry))
                log(f"    NEW       {tag} ({_release_date(release)})")


def render_summary(report: Report, records: int, log) -> int:
    log("\n===============================================================================")
    log(" R-TEST-08 -- tool manifest verification against upstream")
    log("===============================================================================")
    log(f"  {records} record(s) and {report.members} pinned member(s) checked over "
        f"{report.downloads} download(s) ({report.download_bytes} bytes); "
        f"{report.listings} release listing(s) read")
    for label, url, expected, actual, differs in report.changed:
        log(f"\nRED (a) CHECKSUM OR SIZE CHANGED: {label}")
        if actual is None:
            log(f"    url       {url}")
            log(f"    the pinned member is {differs[0]} in the archive (expected {expected['size']} bytes, "
                f"sha256 {expected['sha256']}, md5 {expected['md5']})")
        else:
            for line in _difference_lines(url, expected, actual, differs):
                log(line)
    for record, url, gone in report.gone:
        log(f"\nRED (b) GONE UPSTREAM -- D-008 upstream availability failure: {record.label}")
        log(_gone_message(url, gone.status, gone.detail, record.sha256))
    for name, tag, date, entry in report.new_releases:
        log(f"\nRED (c) NEW UPSTREAM RELEASE: {name} published {tag} on {date}, which "
            f"{MANIFEST_NAME} does not record")
        log(f"    tools     {', '.join(sorted(entry['tools']))}")
        log(f"    recorded  {', '.join(sorted(entry['tags']))}")
        log("    the newest release of a tool is unpinned: adopt it into the manifest (a reviewed "
            "change), or record it in scripts/ci/manifest/not-adopted.json with a reason and a date")
    for name, tag, date, waiver in report.not_adopted_seen:
        log(f"\nseen, deliberately not adopted: {name} {tag} ({date}) -- {waiver['date']}: {waiver['reason']}")
    for subject, reason in report.unverified:
        log(f"\nCOULD NOT VERIFY: {subject}: {reason}")
    red = len(report.changed) + len(report.gone) + len(report.new_releases)
    log("")
    if red:
        log(f"manifest-verify: UPSTREAM CHANGED -- {len(report.changed)} changed, {len(report.gone)} "
            f"gone upstream, {len(report.new_releases)} new release(s)"
            + (f"; and {len(report.unverified)} check(s) could not be completed" if report.unverified else ""))
        return EXIT_CHANGED
    if report.unverified:
        log(f"manifest-verify: COULD NOT VERIFY -- {len(report.unverified)} check(s) could not be "
            "completed. This is not a pass: nothing above says the manifest still matches upstream.")
        return EXIT_UNVERIFIED
    if not report.ok:
        log("manifest-verify: COULD NOT VERIFY -- nothing was checked")
        return EXIT_UNVERIFIED
    log(f"manifest-verify: VERIFIED -- all {len(report.ok)} record(s) and member(s) match upstream, "
        f"and every repository's newest release is recorded")
    return EXIT_VERIFIED


def run(manifest_path: Path, not_adopted_path: Path, endpoints: Endpoints, log) -> int:
    try:
        manifest = toolmatrix.load(manifest_path)
        records = records_of(manifest)
        repos = repositories_of(manifest)
        not_adopted = load_not_adopted(not_adopted_path, repos, endpoints.today)
    except toolmatrix.ToolMatrixError as error:
        log(f"manifest-verify: the manifest is refused: {error}")
        return EXIT_MISUSE
    except InvalidInput as error:
        log(f"manifest-verify: {error}")
        return EXIT_MISUSE
    log(f"manifest-verify: {manifest_path} -- {len(records)} record(s), "
        f"{len({record.url for record in records})} distinct URL(s), {len(repos)} repository(ies)")
    log(f"manifest-verify: not-adopted list {not_adopted_path} -- {len(not_adopted)} entry(ies)")
    report = Report()
    work = Path(tempfile.mkdtemp(prefix="cometgui-manifest-verify-"))
    try:
        verify_artefacts(records, endpoints, work, report, log)
        verify_releases(repos, not_adopted, endpoints, report, log)
    finally:
        shutil.rmtree(work, ignore_errors=True)
    stale = [item for item in not_adopted if not item["used"]
             and not any(subject.endswith(f"listing of {item['repository']}") for subject, _ in report.unverified)]
    if stale:
        for item in stale:
            log(f"manifest-verify: the not-adopted list is invalid: the entry {item['repository']} "
                f"{item['tag']} matches no unrecorded newer release; a stale entry is a place for the "
                "next release to hide -- delete it")
        render_summary(report, len(records), log)
        return EXIT_MISUSE
    return render_summary(report, len(records), log)


def main(argv=None, endpoints: Endpoints = None, out=None) -> int:
    out = out or sys.stdout

    def log(line=""):
        print(line, file=out, flush=True)

    args = list(sys.argv[1:] if argv is None else argv)
    manifest, not_adopted, self_test = DEFAULT_MANIFEST, DEFAULT_NOT_ADOPTED, False
    while args:
        option = args.pop(0)
        if option in ("-h", "--help"):
            log(__doc__)
            return EXIT_VERIFIED
        if option == "--self-test":
            self_test = True
        elif option in ("--manifest", "--not-adopted") and args:
            value = Path(args.pop(0))
            if option == "--manifest":
                manifest = value
            else:
                not_adopted = value
        else:
            log(f"manifest-verify: unknown or incomplete argument {option!r} (try --help)")
            return EXIT_MISUSE
    if self_test:
        import manifest_verify_selftest
        return manifest_verify_selftest.run_self_test(sys.modules[__name__], log)
    return run(manifest, not_adopted, endpoints or Endpoints(), log)


if __name__ == "__main__":
    sys.modules.setdefault("manifest_verify", sys.modules[__name__])
    sys.exit(main())

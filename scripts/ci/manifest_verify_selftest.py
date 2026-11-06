#!/usr/bin/env python3
"""The self-test of ``manifest_verify.py`` -- R-TEST-08 seen to fail.

Run through ``bash scripts/ci/nightly-manifest-verify.sh --self-test``. It
touches no upstream host: a loopback HTTP server on 127.0.0.1 serves synthetic
artefacts this file builds itself (a bare executable, ZIPs, a ``.deb``, a
``.pkg``) and synthetic GitHub releases listings, and a synthetic manifest pins
them. ``manifests/tools.json`` is read by nothing here.

Every case builds the synthetic world afresh, injects ONE defect into it (the
served bytes, the server's answer, the synthetic manifest's pin, the releases
listing or the not-adopted list), runs the real verifier in-process against the
loopback server, and is graded on three things: the exit status, the exact
lines the verifier must print (a full-line match, or a stated prefix where the
operating system words a transport error), and the number of requests the
server saw for a URL -- which is how "a 404 and a digest mismatch are never
retried" and "a shared URL is downloaded once" are proved rather than claimed.

Two controls grade the grader: a case whose defect is NOT injected, and a case
injected with a different defect than the one whose words it requires. Each must
be reported as a HARNESS FAILURE by the same grading code every case uses;
otherwise a grader that passes everything would be indistinguishable from a
verifier that catches everything.
"""

from __future__ import annotations

import copy
import datetime as _dt
import gzip
import hashlib
import http.server
import io
import json
import socket
import struct
import subprocess
import tarfile
import tempfile
import threading
import zipfile
import zlib
from pathlib import Path

GH = "https://github.com/"
TODAY = _dt.date(2026, 11, 6)


def _digests(data: bytes) -> dict:
    return {"size": len(data), "sha256": hashlib.sha256(data).hexdigest(), "md5": hashlib.md5(data).hexdigest()}


def _pattern(seed: str, size: int) -> bytes:
    out, counter = b"", 0
    while len(out) < size:
        out += hashlib.sha256(f"{seed}:{counter}".encode()).digest()
        counter += 1
    return out[:size]


def _zip(files: dict) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            archive.writestr(info, data, zipfile.ZIP_DEFLATED)
    return buffer.getvalue()


def _tar(files: dict, mode: str) -> bytes:
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode=mode) as archive:
        for name, data in files.items():
            info = tarfile.TarInfo("./" + name)
            info.size, info.mtime = len(data), 1767225600
            archive.addfile(info, io.BytesIO(data))
    return buffer.getvalue()


def _ar_member(name: str, data: bytes) -> bytes:
    header = f"{name + '/':<16}{0:<12}{0:<6}{0:<6}{100644:<8}{len(data):<10}".encode() + b"`\n"
    return header + data + (b"\n" if len(data) % 2 else b"")


def _deb(files: dict) -> bytes:
    return (b"!<arch>\n" + _ar_member("debian-binary", b"2.0\n")
            + _ar_member("control.tar.gz", _tar({"control": b"Package: synthetic\n"}, "w:gz"))
            + _ar_member("data.tar.xz", _tar(files, "w:xz")))


def _cpio_odc(files: dict) -> bytes:
    out = b""
    entries = [("./" + name, data) for name, data in files.items()] + [("TRAILER!!!", b"")]
    for inode, (name, data) in enumerate(entries, 1):
        encoded = name.encode() + b"\0"
        out += (b"070707" + b"%06o" % 0 + b"%06o" % inode + b"%06o" % 0o100644 + b"%06o" % 0
                + b"%06o" % 0 + b"%06o" % 1 + b"%06o" % 0 + b"%011o" % 0
                + b"%06o" % len(encoded) + b"%011o" % len(data) + encoded + data)
    return out


def _pkg(files: dict) -> bytes:
    payload = gzip.compress(_cpio_odc(files), mtime=0)
    toc = (
        '<?xml version="1.0" encoding="UTF-8"?><xar><toc>'
        '<file id="1"><name>synthetic.pkg</name><type>directory</type>'
        '<file id="2"><name>Bom</name><type>file</type></file>'
        f'<file id="3"><name>Payload</name><type>file</type><data><offset>0</offset>'
        f'<length>{len(payload)}</length><size>{len(payload)}</size>'
        '<encoding style="application/octet-stream"/></data></file></file></toc></xar>'
    ).encode()
    compressed = zlib.compress(toc)
    header = b"xar!" + struct.pack(">HHQQI", 28, 1, len(compressed), len(toc), 0)
    return header + compressed + payload


class World:
    """The synthetic upstream: files, server behaviour, listings, manifest, list."""

    def __init__(self):
        self.member_bin = _pattern("percolator-binary", 5000)
        self.member_osx = _pattern("percolator-osx", 4000)
        xsd_a, xsd_b = _pattern("xsd-a", 700), _pattern("xsd-b", 900)
        self.files = {
            "UWPR/Comet/releases/download/v1.0.0/comet.linux.exe": _pattern("comet", 4096),
            "UWPR/Comet/releases/download/v1.0.0/Wrapper.dll": _pattern("dll", 1500),
            "percolator/percolator/releases/download/rel-1-0/perc-linux.zip":
                _zip({"README": b"synthetic\n", "percolator": self.member_bin}),
            "percolator/percolator/releases/download/rel-1-0/perc.deb":
                _deb({"usr/share/xml/a.xsd": xsd_a, "usr/share/xml/b.xsd": xsd_b}),
            "percolator/percolator/releases/download/rel-1-0/perc-osx.zip":
                _zip({"../my_build/src/percolator": self.member_osx}),
            "percolator/percolator/releases/download/rel-1-0/perc.pkg":
                _pkg({"usr/local/share/xml/a.xsd": xsd_a}),
            "wenbostar/PDV/releases/download/v2.0.0/PDV.zip": _zip({"PDV.jar": _pattern("pdv", 8000)}),
        }
        self.behaviour: dict = {}
        self.hits: dict = {}
        self.lock = threading.Lock()
        self.listings = {
            "UWPR/Comet": [_release("v1.0.0", "2026-01-02T00:00:00Z"), _release("v0.9.0", "2025-12-01T00:00:00Z")],
            "percolator/percolator": [_release("rel-1-0", "2025-06-01T00:00:00Z"),
                                      _release("rel-0-9", "2024-06-01T00:00:00Z")],
            "wenbostar/PDV": [_release("v2.0.0", "2026-08-14T00:00:00Z")],
        }
        self.api_behaviour: dict = {}
        self.not_adopted = {"schema": 1, "comment": "synthetic", "entries": []}
        self.manifest = self._manifest(xsd_a, xsd_b)

    def _record(self, tool, version, tag, os_, arch, kind, path, member=None, companions=()):
        record = {
            "tool": tool, "version": version, "releaseTag": tag, "os": os_, "arch": arch, "kind": kind,
            "url": GH + path, "sizeBytes": len(self.files[path]),
            "sha256": _digests(self.files[path])["sha256"], "md5": _digests(self.files[path])["md5"],
            "licence": {"spdx": "Apache-2.0"}, "companions": list(companions), "capabilities": [],
            "advisories": [], "minimumHostRequirements": {"requiredHostLibraries": []},
            "minimumCometGuiVersion": "0.1.0",
        }
        if member:
            name, data = member
            digest = _digests(data)
            record.update({"member": name, "memberSizeBytes": digest["size"],
                           "memberSha256": digest["sha256"], "memberMd5": digest["md5"]})
        return record

    def _companion(self, ident, kind, path, members):
        whole = _digests(self.files[path])
        return {"id": ident, "kind": kind, "url": GH + path, "sizeBytes": whole["size"],
                "sha256": whole["sha256"], "md5": whole["md5"],
                "members": [dict(path=name, installedPath=name, **{key: _digests(data)[key2]
                            for key, key2 in (("sizeBytes", "size"), ("sha256", "sha256"), ("md5", "md5"))})
                            for name, data in members]}

    def _manifest(self, xsd_a, xsd_b):
        dll = "UWPR/Comet/releases/download/v1.0.0/Wrapper.dll"
        deb = "percolator/percolator/releases/download/rel-1-0/perc.deb"
        pkg = "percolator/percolator/releases/download/rel-1-0/perc.pkg"
        pdv = "wenbostar/PDV/releases/download/v2.0.0/PDV.zip"
        return {"schemaVersion": 1, "artefacts": [
            self._record("comet", "1.0.0", "v1.0.0", "linux", "x86-64", "BARE_EXECUTABLE",
                         "UWPR/Comet/releases/download/v1.0.0/comet.linux.exe",
                         companions=[self._companion("comet-dll", "BARE_EXECUTABLE", dll,
                                                     [("Wrapper.dll", self.files[dll])])]),
            self._record("percolator", "1.0", "rel-1-0", "linux", "x86-64", "ZIP",
                         "percolator/percolator/releases/download/rel-1-0/perc-linux.zip",
                         member=("percolator", self.member_bin),
                         companions=[self._companion("perc-deb", "DEB_PAYLOAD", deb,
                                                     [("usr/share/xml/a.xsd", xsd_a),
                                                      ("usr/share/xml/b.xsd", xsd_b)])]),
            self._record("percolator", "1.0", "rel-1-0", "macos", "x86-64", "ZIP",
                         "percolator/percolator/releases/download/rel-1-0/perc-osx.zip",
                         member=("../my_build/src/percolator", self.member_osx),
                         companions=[self._companion("perc-pkg", "PKG_PAYLOAD", pkg,
                                                     [("usr/local/share/xml/a.xsd", xsd_a)])]),
            self._record("pdv", "2.0.0", "v2.0.0", "linux", "x86-64", "ZIP", pdv),
            self._record("pdv", "2.0.0", "v2.0.0", "windows", "x86-64", "ZIP", pdv),
        ]}

    def hit(self, key):
        with self.lock:
            self.hits[key] = self.hits.get(key, 0) + 1
            return self.hits[key]


def _release(tag, date, draft=False, prerelease=False):
    return {"tag_name": tag, "published_at": date, "created_at": date, "draft": draft, "prerelease": prerelease}


class _Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def _send(self, status, body=b"", headers=()):
        self.send_response(status)
        for name, value in headers:
            self.send_header(name, value)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _reset(self):
        self.close_connection = True
        try:
            self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
        except OSError:
            pass
        self.connection.close()

    def do_GET(self):
        world = self.server.world
        path = self.path.split("?")[0]
        if path.startswith("/gh/"):
            key = path[len("/gh/"):]
            count = world.hit(key)
            behaviour = world.behaviour.get(key, "redirect")
            if behaviour == "reset" or (behaviour == "reset-once" and count == 1):
                return self._reset()
            if behaviour in ("404", "410", "503"):
                return self._send(int(behaviour), b"not here")
            if behaviour == "redirect-nothing":
                return self._send(302)
            if key not in world.files:
                return self._send(404)
            return self._send(302, headers=[("Location", f"/assets/{key}?sig=synthetic")])
        if path.startswith("/assets/"):
            key = path[len("/assets/"):]
            return self._send(200, world.files[key], [("Content-Type", "application/octet-stream")])
        if path.startswith("/api/repos/") and path.endswith("/releases"):
            repo = path[len("/api/repos/"):-len("/releases")]
            world.hit("api:" + repo)
            behaviour = world.api_behaviour.get(repo)
            if behaviour == "403":
                return self._send(403, b'{"message": "API rate limit exceeded"}')
            return self._send(200, json.dumps(world.listings.get(repo, [])).encode(),
                              [("Content-Type", "application/json")])
        return self._send(404)


class Loopback:
    def __enter__(self):
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        self.server.daemon_threads = True
        self.server.world = None
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.port = self.server.server_address[1]
        return self

    def __exit__(self, *exc):
        self.server.shutdown()
        self.server.server_close()


def _closed_port() -> int:
    probe = socket.socket()
    probe.bind(("127.0.0.1", 0))
    port = probe.getsockname()[1]
    probe.close()
    return port


# --------------------------------------------------------------------------
# Cases.  Each is (name, inject(world), expected exit, expected lines, hits).
# An expected line is matched against the WHOLE output line; ("prefix", text)
# matches its start, used only where the operating system words the cause.
# --------------------------------------------------------------------------

COMET = "UWPR/Comet/releases/download/v1.0.0/comet.linux.exe"
PDV = "wenbostar/PDV/releases/download/v2.0.0/PDV.zip"
LINUX_ZIP = "percolator/percolator/releases/download/rel-1-0/perc-linux.zip"


def _flip_byte(world):
    data = bytearray(world.files[COMET])
    data[2048] ^= 0x01
    world.files[COMET] = bytes(data)


def _gone_lines(status_text, sha):
    return [
        "RED (b) GONE UPSTREAM -- D-008 upstream availability failure: pdv 2.0.0 linux x86-64",
        "RED (b) GONE UPSTREAM -- D-008 upstream availability failure: pdv 2.0.0 windows x86-64",
        f"    the pinned artefact is no longer available upstream: {status_text} for {GH}{PDV}; expected "
        f"sha-256 {sha}. CometGUI redistributes no tool binary and holds no copy (D-008), so this is an "
        "upstream availability failure, not a corrupt download: the release asset has been removed or "
        "re-tagged, and every new install of this record fails until the manifest is changed.",
    ]


def _cases(world0):
    comet = _digests(world0.files[COMET])
    flipped = bytearray(world0.files[COMET])
    flipped[2048] ^= 0x01
    flipped = _digests(bytes(flipped))
    grown = _digests(world0.files[COMET] + b"\0")
    pdv_sha = _digests(world0.files[PDV])["sha256"]
    comet_sha_last = comet["sha256"][:-1] + ("0" if comet["sha256"][-1] != "0" else "1")
    comet_md5_first = ("0" if comet["md5"][0] != "0" else "1") + comet["md5"][1:]
    member = _digests(world0.member_bin)
    verified = ("manifest-verify: VERIFIED -- all 14 record(s) and member(s) match upstream, "
                "and every repository's newest release is recorded")
    unverified = ("prefix", "manifest-verify: COULD NOT VERIFY -- ")

    def set_(mapping, key, value):
        return lambda world: getattr(world, mapping).__setitem__(key, value)

    def pin(index, name, value, companion=None, member_index=None):
        def inject(world):
            record = world.manifest["artefacts"][index]
            if companion is not None:
                record = record["companions"][companion]
                if member_index is not None:
                    record = record["members"][member_index]
            record[name] = value
        return inject

    def listing(repo, *releases):
        return lambda world: world.listings.__setitem__(repo, list(releases))

    def waive(entry):
        return lambda world: world.not_adopted["entries"].append(entry)

    def both(*injections):
        def inject(world):
            for injection in injections:
                injection(world)
        return inject

    good_reason = "PDV 2.1.0 drops the -k s option CometGUI's spectrum figure needs; revisit at 2.2"
    newer_pdv = listing("wenbostar/PDV", _release("v2.1.0", "2026-10-01T00:00:00Z"),
                        _release("v2.0.0", "2026-08-14T00:00:00Z"))
    new_line = ("RED (c) NEW UPSTREAM RELEASE: wenbostar/PDV published v2.1.0 on 2026-10-01T00:00:00Z, "
                "which manifests/tools.json does not record")
    return [
        ("clean manifest -> verified, one download per distinct URL",
         None, 0, [verified], {PDV: 1, COMET: 1}),
        ("one byte altered -> red (a) naming the record, never retried",
         _flip_byte, 1,
         ["RED (a) CHECKSUM OR SIZE CHANGED: comet 1.0.0 linux x86-64",
          f"    size      expected 4096 bytes, actual 4096 bytes",
          f"    sha256    expected {comet['sha256']}, actual {flipped['sha256']}   <-- CHANGED",
          f"    md5       expected {comet['md5']}, actual {flipped['md5']}   <-- CHANGED",
          "manifest-verify: UPSTREAM CHANGED -- 1 changed, 0 gone upstream, 0 new release(s)"],
         {COMET: 1}),
        ("size changed -> red (a)",
         lambda world: world.files.__setitem__(COMET, world.files[COMET] + b"\0"), 1,
         ["RED (a) CHECKSUM OR SIZE CHANGED: comet 1.0.0 linux x86-64",
          "    size      expected 4096 bytes, actual 4097 bytes   <-- CHANGED",
          f"    sha256    expected {comet['sha256']}, actual {grown['sha256']}   <-- CHANGED"],
         {COMET: 1}),
        ("the pinned sha256 differs only in its last character -> red (a)",
         pin(0, "sha256", comet_sha_last), 1,
         [f"    sha256    expected {comet_sha_last}, actual {comet['sha256']}   <-- CHANGED",
          f"    md5       expected {comet['md5']}, actual {comet['md5']}"], None),
        ("the pinned md5 alone differs -> red (a)",
         pin(0, "md5", comet_md5_first), 1,
         [f"    md5       expected {comet_md5_first}, actual {comet['md5']}   <-- CHANGED",
          f"    sha256    expected {comet['sha256']}, actual {comet['sha256']}"], None),
        ("HTTP 404 -> red (b) in D-008's availability words, never retried",
         set_("behaviour", PDV, "404"), 1, _gone_lines("HTTP 404", pdv_sha)
         + ["manifest-verify: UPSTREAM CHANGED -- 0 changed, 2 gone upstream, 0 new release(s)"], {PDV: 1}),
        ("HTTP 410 -> red (b)",
         set_("behaviour", PDV, "410"), 1, _gone_lines("HTTP 410", pdv_sha), {PDV: 1}),
        ("a redirect to nothing -> red (b)",
         set_("behaviour", PDV, "redirect-nothing"), 1,
         _gone_lines("HTTP 302 redirect with no usable Location (a redirect to nothing)", pdv_sha), {PDV: 1}),
        ("an unknown newer release -> red (c) naming the tag",
         newer_pdv, 1, [new_line, "    tools     pdv", "    recorded  v2.0.0",
                        "manifest-verify: UPSTREAM CHANGED -- 0 changed, 0 gone upstream, 1 new release(s)"],
         None),
        ("only known tags, listed out of date order with an older unrecorded one first -> green",
         listing("percolator/percolator", _release("rel-0-9", "2024-06-01T00:00:00Z"),
                 _release("rel-1-0", "2025-06-01T00:00:00Z"), _release("rel-0-8", "2023-06-01T00:00:00Z")),
         0, [verified], None),
        ("a newer draft and a newer prerelease -> ignored, green",
         listing("wenbostar/PDV", _release("v3.0.0", "2026-11-01T00:00:00Z", draft=True),
                 _release("v3.0.0-rc1", "2026-10-20T00:00:00Z", prerelease=True),
                 _release("v2.0.0", "2026-08-14T00:00:00Z")),
         0, ["    ignored   v3.0.0 (draft)", "    ignored   v3.0.0-rc1 (prerelease)", verified], None),
        ("a connection reset that persists -> could not verify after exactly 3 attempts",
         set_("behaviour", COMET, "reset"), 3,
         [("prefix", f"COULD NOT VERIFY: download of {GH}{COMET} (pinned by comet 1.0.0 linux x86-64): "
                     "a transport failure persisted through all 3 attempts; last: "),
          ("prefix", "    transport: attempt 3 of 3 failed ("), unverified], {COMET: 3}),
        ("one connection reset, then the asset -> verified, the retry printed",
         set_("behaviour", COMET, "reset-once"), 0,
         [("prefix", "    transport: attempt 1 of 3 failed ("), verified], {COMET: 2}),
        ("HTTP 503 that persists -> could not verify, 3 attempts",
         set_("behaviour", COMET, "503"), 3,
         [f"COULD NOT VERIFY: download of {GH}{COMET} (pinned by comet 1.0.0 linux x86-64): a transport "
          "failure persisted through all 3 attempts; last: HTTP 503", unverified], {COMET: 3}),
        ("the releases API refusing (403, rate limit) -> could not verify, not retried",
         set_("api_behaviour", "UWPR/Comet", "403"), 3,
         ["COULD NOT VERIFY: the releases listing of UWPR/Comet: HTTP 403 (refused or rate limited; GitHub "
          "allows 60 unauthenticated API requests an hour per address, and this job holds no credential "
          "by design)", unverified], {"api:UWPR/Comet": 1}),
        ("the network down (every host refusing) -> could not verify",
         "NETWORK-DOWN", 3,
         [("prefix", f"COULD NOT VERIFY: download of {GH}{COMET} (pinned by comet 1.0.0 linux x86-64): "
                     "a transport failure persisted through all 3 attempts; last: "),
          ("prefix", "COULD NOT VERIFY: the releases listing of wenbostar/PDV: a transport failure"),
          ("prefix", "manifest-verify: COULD NOT VERIFY -- 10 check(s) could not be completed.")], None),
        ("a ZIP member's pinned digest differs -> red (a) naming the member",
         pin(1, "memberSha256", "0" * 64), 1,
         ["RED (a) CHECKSUM OR SIZE CHANGED: percolator 1.0 linux x86-64 member percolator",
          f"    sha256    expected {'0' * 64}, actual {member['sha256']}   <-- CHANGED"], {LINUX_ZIP: 1}),
        ("a .deb member's pinned digest differs -> red (a)",
         pin(1, "md5", "f" * 32, companion=0, member_index=1), 1,
         ["RED (a) CHECKSUM OR SIZE CHANGED: percolator 1.0 linux x86-64 companion perc-deb member "
          "usr/share/xml/b.xsd"], None),
        ("a .pkg member's pinned size differs -> red (a)",
         pin(2, "sizeBytes", 701, companion=0, member_index=0), 1,
         ["RED (a) CHECKSUM OR SIZE CHANGED: percolator 1.0 macos x86-64 companion perc-pkg member "
          "usr/local/share/xml/a.xsd", ("prefix", "    size      expected 701 bytes, actual 700 bytes")], None),
        ("a traversal-named ZIP member's pinned digest differs -> red (a)",
         pin(2, "memberMd5", "0" * 32), 1,
         ["RED (a) CHECKSUM OR SIZE CHANGED: percolator 1.0 macos x86-64 member ../my_build/src/percolator"],
         None),
        ("a pinned member absent from the archive -> red (a)",
         pin(1, "member", "bin/percolator"), 1,
         ["RED (a) CHECKSUM OR SIZE CHANGED: percolator 1.0 linux x86-64 member bin/percolator",
          ("prefix", "    the pinned member is absent in the archive (expected 5000 bytes")], None),
        ("a byte altered, an asset gone and a new release at once -> every offender named",
         both(_flip_byte, set_("behaviour", PDV, "404"), newer_pdv), 1,
         ["RED (a) CHECKSUM OR SIZE CHANGED: comet 1.0.0 linux x86-64", _gone_lines("HTTP 404", pdv_sha)[0],
          _gone_lines("HTTP 404", pdv_sha)[1], new_line,
          "manifest-verify: UPSTREAM CHANGED -- 1 changed, 2 gone upstream, 1 new release(s)"], None),
        ("a newer release recorded as deliberately not adopted -> green, the entry printed",
         both(newer_pdv, waive({"repository": "wenbostar/PDV", "tag": "v2.1.0", "reason": good_reason,
                                "date": "2026-10-02"})), 0,
         [f"seen, deliberately not adopted: wenbostar/PDV v2.1.0 (2026-10-01T00:00:00Z) -- 2026-10-02: "
          f"{good_reason}", verified], None),
        ("a not-adopted entry with a placeholder reason -> refused (exit 2)",
         both(newer_pdv, waive({"repository": "wenbostar/PDV", "tag": "v2.1.0", "reason": "TODO",
                                "date": "2026-10-02"})), 2,
         [("prefix", "  * entries[0] (wenbostar/PDV v2.1.0): the reason 'TODO' is missing, a placeholder")], None),
        ("a not-adopted entry dated in the future -> refused (exit 2)",
         both(newer_pdv, waive({"repository": "wenbostar/PDV", "tag": "v2.1.0", "reason": good_reason,
                                "date": "2026-12-25"})), 2,
         ["  * entries[0] (wenbostar/PDV v2.1.0): the date 2026-12-25 is in the future"], None),
        ("a stale not-adopted entry matching no newer release -> refused (exit 2)",
         waive({"repository": "wenbostar/PDV", "tag": "v2.1.0", "reason": good_reason, "date": "2026-10-02"}), 2,
         ["manifest-verify: the not-adopted list is invalid: the entry wenbostar/PDV v2.1.0 matches no "
          "unrecorded newer release; a stale entry is a place for the next release to hide -- delete it"], None),
        ("a manifest record that is not https -> the manifest refused (exit 2)",
         pin(3, "url", "http://github.com/wenbostar/PDV/releases/download/v2.0.0/PDV.zip"), 2,
         [("prefix", "manifest-verify: the manifest is refused: ")], None),
    ]


def _grade(verifier, port, work, name, inject, want_exit, want_lines, want_hits):
    """Run one case and return the list of problems (empty: the case passed)."""
    world = World()
    endpoints = verifier.Endpoints(
        download_from=GH, download_to=f"http://127.0.0.1:{port}/gh/",
        api_base=f"http://127.0.0.1:{port}/api", allow_http=True, attempts=3, delays=(0, 0),
        timeout=5, deadline=30, sleep=lambda seconds: None, today=TODAY)
    if inject == "NETWORK-DOWN":
        dead = _closed_port()
        endpoints.download_to = f"http://127.0.0.1:{dead}/gh/"
        endpoints.api_base = f"http://127.0.0.1:{dead}/api"
    elif inject is not None:
        inject(world)
    _grade.server.world = world
    manifest = work / "tools.json"
    manifest.write_text(json.dumps(world.manifest, indent=1), encoding="utf-8")
    listing = work / "not-adopted.json"
    listing.write_text(json.dumps(world.not_adopted), encoding="utf-8")
    lines = []
    code = verifier.run(manifest, listing, endpoints, lambda line="": lines.extend(str(line).split("\n")))
    problems = []
    if code != want_exit:
        problems.append(f"expected exit {want_exit}, got {code}")
    for want in want_lines:
        if isinstance(want, tuple):
            found = any(line.startswith(want[1]) for line in lines)
            text = want[1] + "..."
        else:
            found = want in lines
            text = want
        if not found:
            problems.append(f"missing the line: {text}")
    if want_exit == 0:
        reds = [line for line in lines if line.startswith(("RED ", "COULD NOT VERIFY"))]
        if reds:
            problems.append(f"a green case printed {reds[0]!r}")
    for key, count in (want_hits or {}).items():
        if world.hits.get(key, 0) != count:
            problems.append(f"expected {count} request(s) for {key}, the server saw {world.hits.get(key, 0)}")
    return problems, lines


def run_self_test(verifier, log) -> int:
    log("manifest-verify self-test: synthetic artefacts and listings on a loopback server; no upstream host")
    work = Path(tempfile.mkdtemp(prefix="cometgui-manifest-selftest-"))
    passed, failed = 0, []
    try:
        with Loopback() as server:
            _grade.server = server.server
            cases = _cases(World())
            log(f"manifest-verify self-test: loopback 127.0.0.1:{server.port}, {len(cases)} case(s) "
                "and 2 harness controls\n")
            for name, inject, want_exit, want_lines, want_hits in cases:
                problems, lines = _grade(verifier, server.port, work, name, inject, want_exit, want_lines, want_hits)
                if problems:
                    failed.append(name)
                    log(f"  FAIL  {name}")
                    for problem in problems:
                        log(f"          {problem}")
                    log("          --- verifier output ---")
                    for line in lines[-40:]:
                        log(f"          | {line}")
                else:
                    passed += 1
                    log(f"  ok    {name} (exit {want_exit})")
            # The two controls on the grader itself.
            byte_case = next(case for case in cases if case[0].startswith("one byte altered"))
            gone_case = next(case for case in cases if case[0].startswith("HTTP 404"))
            controls = [
                ("H1 a case whose defect was not injected", byte_case[0], None, byte_case[2:],
                 "expected exit 1, got 0"),
                ("H2 a red with another defect's words", byte_case[0], gone_case[1], byte_case[2:],
                 "missing the line: RED (a) CHECKSUM OR SIZE CHANGED: comet 1.0.0 linux x86-64"),
            ]
            for label, name, inject, rest, must in controls:
                problems, _ = _grade(verifier, server.port, work, name, inject, *rest)
                if must in problems:
                    passed += 1
                    log(f"  ok    {label}: reported as a HARNESS FAILURE ({must})")
                else:
                    failed.append(label)
                    log(f"  FAIL  {label}: the grader did not report it as a HARNESS FAILURE; it said {problems}")
        # The command line, end to end: misuse is exit 2, through the wrapper.
        wrapper = Path(verifier.__file__).resolve().parent / "nightly-manifest-verify.sh"
        result = subprocess.run(["bash", str(wrapper), "--no-such-option"], capture_output=True, text=True,
                                timeout=60)
        if result.returncode == 2 and "unknown or incomplete argument '--no-such-option'" in result.stdout + result.stderr:
            passed += 1
            log("  ok    the wrapper refuses an unknown option with exit 2")
        else:
            failed.append("wrapper misuse")
            log(f"  FAIL  the wrapper's misuse exit was {result.returncode}: {result.stdout}{result.stderr}")
    finally:
        import shutil
        shutil.rmtree(work, ignore_errors=True)
    total = passed + len(failed)
    log("")
    if failed:
        log(f"manifest-verify self-test FAILED -- {len(failed)} of {total} case(s) did not behave:")
        for name in failed:
            log(f"  * {name}")
        return verifier.EXIT_SELFTEST_FAILED
    log(f"manifest-verify: self-test OK -- {passed}/{total} cases, every injected defect red with its own "
        "words, the clean world green, and the grader seen to fail")
    return 0

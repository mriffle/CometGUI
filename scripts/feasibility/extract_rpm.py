#!/usr/bin/env python3
"""Pure-Python extractor for RPM packages.

Phase 00, work unit 3.  Written because Percolator publishes an ``.rpm``
alongside the ``.deb`` for every release that has Linux artefacts at all, and
because the 3.09 ``.deb`` binary does not load on this host while the ``.rpm``
one does -- so establishing whether the ``.rpm`` is a usable fallback needed an
extractor, and this host has no ``rpm``, ``rpm2cpio``, ``cpio`` or ``7z``.

An RPM is::

    lead                 96 bytes, magic ed ab ee db, otherwise vestigial
    signature header     8e ad e8 header structure, padded to 8 bytes
    header               the same structure, holding NAME/VERSION/ARCH and the
                         payload's format and compressor
    payload              compressed cpio -- gzip, xz or zstd

The cpio reader and the safe unpacker are shared with :mod:`extract_pkg`, which
already implements both flavours of cpio for the macOS ``.pkg`` payload.

Nothing here needs administrative rights and nothing is installed: the payload
is a relative file tree written wherever it is told to go.

Usage::

    python3 extract_rpm.py --list PKG.rpm
    python3 extract_rpm.py --dest DIR PKG.rpm
"""

from __future__ import annotations

import argparse
import bz2
import gzip
import hashlib
import lzma
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_pkg import read_cpio, unpack_cpio  # noqa: E402

RPM_LEAD_MAGIC = b"\xed\xab\xee\xdb"
RPM_HEADER_MAGIC = b"\x8e\xad\xe8"

# The subset of header tags this needs.  Full list: rpmtag.h.
TAG = {1000: "NAME", 1001: "VERSION", 1002: "RELEASE", 1004: "SUMMARY",
       1022: "ARCH", 1044: "SOURCERPM", 1049: "REQUIRENAME",
       1124: "PAYLOADFORMAT", 1125: "PAYLOADCOMPRESSOR",
       1126: "PAYLOADFLAGS"}

TYPE_STRING, TYPE_STRING_ARRAY, TYPE_I18NSTRING = 6, 8, 9


class RpmHeader:
    def __init__(self, blob: bytes, pos: int) -> None:
        if blob[pos:pos + 3] != RPM_HEADER_MAGIC:
            raise ValueError("bad rpm header magic at offset %d: %r"
                             % (pos, blob[pos:pos + 4]))
        nindex, hsize = struct.unpack(">II", blob[pos + 8:pos + 16])
        index_at = pos + 16
        store_at = index_at + nindex * 16
        self.entries = {}
        for i in range(nindex):
            tag, typ, off, cnt = struct.unpack(
                ">IIII", blob[index_at + i * 16:index_at + (i + 1) * 16])
            self.entries[tag] = (typ, off, cnt)
        self.store = blob[store_at:store_at + hsize]
        self.end = store_at + hsize

    def strings(self, tag: int) -> list[str]:
        if tag not in self.entries:
            return []
        typ, off, cnt = self.entries[tag]
        if typ not in (TYPE_STRING, TYPE_STRING_ARRAY, TYPE_I18NSTRING):
            return []
        out, pos = [], off
        for _ in range(cnt if typ != TYPE_STRING else 1):
            end = self.store.index(b"\0", pos)
            out.append(self.store[pos:end].decode("utf-8", "replace"))
            pos = end + 1
        return out

    def string(self, tag: int) -> str | None:
        vals = self.strings(tag)
        return vals[0] if vals else None


def decompress_payload(blob: bytes, declared: str | None) -> tuple[bytes, str]:
    if blob[:2] == b"\x1f\x8b":
        return gzip.decompress(blob), "gzip"
    if blob[:6] == b"\xfd7zXZ\x00":
        return lzma.decompress(blob), "xz"
    if blob[:3] == b"BZh":
        return bz2.decompress(blob), "bzip2"
    if blob[:4] == b"\x28\xb5\x2f\xfd":
        raise ValueError("zstd payload (declared %r): the standard library "
                         "has no zstd decoder on Python 3.11" % declared)
    if blob[:6] in (b"070707", b"070701", b"070702"):
        return blob, "none"
    raise ValueError("unrecognised rpm payload compression (declared %r), "
                     "first bytes %r" % (declared, blob[:8]))


def read_rpm(path: str):
    blob = open(path, "rb").read()
    if blob[:4] != RPM_LEAD_MAGIC:
        raise ValueError("not an rpm: magic is %r" % blob[:4])
    sig = RpmHeader(blob, 96)
    end = sig.end + (-sig.end % 8)          # signature header is 8-byte padded
    hdr = RpmHeader(blob, end)
    meta = {name: hdr.string(tag) for tag, name in TAG.items()
            if tag != 1049}
    meta["REQUIRENAME"] = hdr.strings(1049)
    payload, how = decompress_payload(blob[hdr.end:], meta["PAYLOADCOMPRESSOR"])
    return blob, meta, payload, how


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("rpm")
    ap.add_argument("--dest")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--requires", action="store_true",
                    help="print the package's declared runtime requirements")
    args = ap.parse_args(argv)

    blob, meta, payload, how = read_rpm(args.rpm)
    print("archive : %s" % os.path.abspath(args.rpm))
    print("bytes   : %d" % len(blob))
    print("sha256  : %s" % hashlib.sha256(blob).hexdigest())
    print("package : %s-%s-%s.%s"
          % (meta["NAME"], meta["VERSION"], meta["RELEASE"], meta["ARCH"]))
    print("payload : %s compressed with %s (declared %s) -> %d bytes of cpio %s"
          % (meta["PAYLOADFORMAT"], how, meta["PAYLOADCOMPRESSOR"],
             len(payload), payload[:6].decode("ascii", "replace")))
    if args.requires:
        for r in meta["REQUIRENAME"]:
            print("   requires %s" % r)

    entries = list(read_cpio(payload))
    if args.list or not args.dest:
        for e in entries:
            print("   %-8s %-7s %10d  %s"
                  % (e.kind, oct(e.mode & 0o7777), e.size, e.name))
        print("entries : %d" % len(entries))
        if not args.dest:
            return 0

    os.makedirs(args.dest, exist_ok=True)
    written = unpack_cpio(entries, args.dest)
    print("wrote   : %d files into %s" % (len(written), os.path.abspath(args.dest)))
    for w in written:
        if w["kind"] == "file" and int(w["mode"], 8) & 0o100:
            print("   exec  %s  sha256=%s" % (w["path"], w["sha256"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

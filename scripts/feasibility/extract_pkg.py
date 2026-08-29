#!/usr/bin/env python3
"""Pure-Python extractor for macOS flat installer packages (``.pkg``).

Phase 00, work unit 3.  This host has no ``xar``, no ``cpio``, no ``7z``, no
``bsdtar`` and no ``file``, and nothing may be installed on it.  The shipping
product does this extraction inside the JVM, so a self-contained
implementation of the container formats is the proof that counts.  Only the
decompressors come from the standard library (``zlib``, ``gzip``, ``bz2``,
``lzma``), all of which have JDK equivalents.

A flat ``.pkg`` is three nested containers::

    xar!        an archive with a zlib-compressed XML table of contents and a
                heap of per-file blobs, each blob separately encoded
      Payload   one of those blobs: a gzip stream ...
        cpio    ... wrapping a cpio archive holding the installed file tree

Upstream Percolator's ``.pkg`` uses the old ASCII ``070707`` cpio flavour;
the ``070701``/``070702`` (SVR4 "newc"/"crc") flavours are supported too so
that the product is not surprised by a repackaging.

Nothing here needs administrative rights, and nothing runs the installer: the
payload is a plain relative file tree that is written wherever it is told to
go.  ``installer(8)``, ``pkgutil`` and root are not involved.

Because ``file`` does not exist on this host, this module also parses Mach-O
headers itself, so an extracted macOS binary can be identified (architecture,
file type, minimum macOS version, linked dylibs) on a Linux box that cannot
execute it.

Usage::

    python3 extract_pkg.py --list PKG.pkg
    python3 extract_pkg.py --dest DIR PKG.pkg
    python3 extract_pkg.py --dest DIR --identify PKG.pkg
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
import zlib
import xml.etree.ElementTree as ET

XAR_MAGIC = b"xar!"


# --------------------------------------------------------------------------
# xar
# --------------------------------------------------------------------------

class XarFile:
    """A parsed ``xar!`` archive: header, XML table of contents, heap."""

    def __init__(self, blob: bytes) -> None:
        if blob[:4] != XAR_MAGIC:
            raise ValueError("not a xar archive: magic is %r, expected %r"
                             % (blob[:4], XAR_MAGIC))
        (magic, header_size, version, toc_len_c, toc_len_u,
         cksum_alg) = struct.unpack(">4sHHQQI", blob[:28])
        self.blob = blob
        self.header_size = header_size
        self.version = version
        self.toc_length_compressed = toc_len_c
        self.toc_length_uncompressed = toc_len_u
        self.checksum_alg = {0: "none", 1: "sha1", 2: "md5",
                             3: "sha256", 4: "sha512"}.get(cksum_alg,
                                                           "unknown(%d)" % cksum_alg)
        toc_raw = blob[header_size:header_size + toc_len_c]
        self.toc_xml = zlib.decompress(toc_raw)
        if len(self.toc_xml) != toc_len_u:
            raise ValueError("xar TOC length mismatch: header says %d, "
                             "inflate gave %d" % (toc_len_u, len(self.toc_xml)))
        self.heap_offset = header_size + toc_len_c
        self.root = ET.fromstring(self.toc_xml)

    def entries(self):
        """Yield ``(path, type, data_element)`` for every file in the TOC."""
        toc = self.root.find("toc")
        if toc is None:
            return
        stack = [(el, "") for el in reversed(toc.findall("file"))]
        while stack:
            el, parent = stack.pop()
            name_el = el.find("name")
            name = name_el.text if name_el is not None else "?"
            path = parent + "/" + name if parent else name
            type_el = el.find("type")
            kind = type_el.text if type_el is not None else "file"
            yield path, kind, el.find("data")
            for child in reversed(el.findall("file")):
                stack.append((child, path))

    def read(self, data_el) -> bytes:
        """Return the decoded bytes of one TOC ``<data>`` element."""
        if data_el is None:
            return b""
        def num(tag):
            el = data_el.find(tag)
            return int(el.text) if el is not None and el.text else 0
        offset, length, size = num("offset"), num("length"), num("size")
        enc_el = data_el.find("encoding")
        style = enc_el.get("style") if enc_el is not None else None
        start = self.heap_offset + offset
        raw = self.blob[start:start + length]
        if len(raw) != length:
            raise ValueError("truncated xar heap: wanted %d bytes at %d, "
                             "got %d" % (length, start, len(raw)))
        if style in (None, "application/octet-stream"):
            out = raw
        elif style == "application/x-gzip":
            # xar's "x-gzip" is a raw zlib stream, not an RFC 1952 gzip member.
            out = zlib.decompress(raw)
        elif style == "application/x-bzip2":
            out = bz2.decompress(raw)
        elif style in ("application/x-lzma", "application/x-xz"):
            out = lzma.decompress(raw)
        else:
            raise ValueError("unsupported xar encoding style %r" % style)
        if size and len(out) != size:
            raise ValueError("xar payload size mismatch: TOC says %d, decoded "
                             "%d" % (size, len(out)))
        return out


# --------------------------------------------------------------------------
# cpio
# --------------------------------------------------------------------------

CPIO_ODC = b"070707"        # old ASCII (POSIX portable), octal fields
CPIO_NEWC = b"070701"       # SVR4
CPIO_CRC = b"070702"        # SVR4 with checksum
TRAILER = "TRAILER!!!"

S_IFMT = 0o170000
S_IFREG = 0o100000
S_IFDIR = 0o040000
S_IFLNK = 0o120000


class CpioEntry:
    __slots__ = ("name", "mode", "size", "mtime", "nlink", "data")

    def __init__(self, name, mode, size, mtime, nlink, data):
        self.name = name
        self.mode = mode
        self.size = size
        self.mtime = mtime
        self.nlink = nlink
        self.data = data

    @property
    def kind(self) -> str:
        fmt = self.mode & S_IFMT
        return {S_IFREG: "file", S_IFDIR: "dir", S_IFLNK: "symlink"}.get(
            fmt, "other(%o)" % fmt)


def detect_and_decompress(blob: bytes) -> tuple[bytes, str]:
    """Unwrap whatever compression a cpio payload arrived in."""
    if blob[:2] == b"\x1f\x8b":
        return gzip.decompress(blob), "gzip"
    if blob[:3] == b"BZh":
        return bz2.decompress(blob), "bzip2"
    if blob[:6] == b"\xfd7zXZ\x00":
        return lzma.decompress(blob), "xz"
    if blob[:4] == b"\x28\xb5\x2f\xfd":
        raise ValueError("zstd-compressed payload: the standard library has no "
                         "zstd decoder on Python 3.11")
    if blob[:6] in (CPIO_ODC, CPIO_NEWC, CPIO_CRC):
        return blob, "none"
    if blob[:2] == b"\x78\x9c" or blob[:2] == b"\x78\x01" or blob[:2] == b"\x78\xda":
        return zlib.decompress(blob), "zlib"
    raise ValueError("unrecognised payload compression, first bytes %r"
                     % blob[:8])


def read_cpio(data: bytes):
    """Yield :class:`CpioEntry` for an odc (070707) or newc (070701) archive."""
    pos = 0
    flavour = data[:6]
    while pos + 6 <= len(data):
        magic = data[pos:pos + 6]
        if magic == CPIO_ODC:
            if pos + 76 > len(data):
                break
            h = data[pos:pos + 76]
            def o(a, b):
                return int(h[a:b].decode("ascii"), 8)
            mode, nlink = o(18, 24), o(36, 42)
            mtime, namesize, filesize = o(48, 59), o(59, 65), o(65, 76)
            name = data[pos + 76:pos + 76 + namesize].split(b"\0", 1)[0]
            body_start = pos + 76 + namesize
            body = data[body_start:body_start + filesize]
            pos = body_start + filesize          # odc has no padding
        elif magic in (CPIO_NEWC, CPIO_CRC):
            if pos + 110 > len(data):
                break
            h = data[pos:pos + 110]
            def x(a, b):
                return int(h[a:b].decode("ascii"), 16)
            mode, nlink = x(14, 22), x(38, 46)
            mtime, filesize, namesize = x(46, 54), x(54, 62), x(94, 102)
            name = data[pos + 110:pos + 110 + namesize].split(b"\0", 1)[0]
            body_start = pos + 110 + namesize
            body_start += (-body_start) % 4       # newc pads name to 4 bytes
            body = data[body_start:body_start + filesize]
            pos = body_start + filesize
            pos += (-pos) % 4                     # and pads data to 4 bytes
        else:
            raise ValueError("bad cpio magic %r at offset %d (archive flavour "
                             "was %r)" % (magic, pos, flavour))
        text = name.decode("utf-8", "replace")
        if text == TRAILER:
            break
        yield CpioEntry(text, mode, filesize, mtime, nlink, body)


# --------------------------------------------------------------------------
# Mach-O identification (no `file` on this host)
# --------------------------------------------------------------------------

CPU_TYPES = {
    7: "i386 (x86, 32-bit)",
    0x01000007: "x86_64",
    12: "arm (32-bit)",
    0x0100000C: "arm64",
    0x0200000C: "arm64_32",
    18: "powerpc",
    0x01000012: "powerpc64",
}
FILE_TYPES = {1: "MH_OBJECT", 2: "MH_EXECUTE", 3: "MH_FVMLIB", 4: "MH_CORE",
              5: "MH_PRELOAD", 6: "MH_DYLIB", 7: "MH_DYLINKER", 8: "MH_BUNDLE"}
PLATFORMS = {1: "macOS", 2: "iOS", 3: "tvOS", 4: "watchOS", 6: "macCatalyst"}

LC_LOAD_DYLIB = 0x0C
LC_VERSION_MIN_MACOSX = 0x24
LC_BUILD_VERSION = 0x32


def _version_str(packed: int) -> str:
    return "%d.%d.%d" % (packed >> 16, (packed >> 8) & 0xFF, packed & 0xFF)


def describe_macho(blob: bytes) -> dict | None:
    """Identify a Mach-O image without executing it and without ``file``."""
    if len(blob) < 8:
        return None
    magic = blob[:4]
    if magic in (b"\xca\xfe\xba\xbe", b"\xca\xfe\xba\xbf"):
        nfat = struct.unpack(">I", blob[4:8])[0]
        width = 20 if magic == b"\xca\xfe\xba\xbe" else 32
        slices = []
        for i in range(min(nfat, 32)):
            off = 8 + i * width
            if magic == b"\xca\xfe\xba\xbe":
                cpu, sub, o, size, align = struct.unpack(">iiIII",
                                                         blob[off:off + 20])
            else:
                cpu, sub, o, size, align, _ = struct.unpack(">iiQQII",
                                                            blob[off:off + 32])
            inner = describe_macho(blob[o:o + size])
            slices.append(inner or {"arch": CPU_TYPES.get(cpu & 0xFFFFFFFF,
                                                          "cputype %d" % cpu)})
        return {"format": "Mach-O universal (fat) binary",
                "slice_count": nfat, "slices": slices,
                "architectures": [s.get("arch") for s in slices]}
    if magic == b"\xcf\xfa\xed\xfe":
        endian, bits, hdr = "<", 64, 32
    elif magic == b"\xce\xfa\xed\xfe":
        endian, bits, hdr = "<", 32, 28
    elif magic == b"\xfe\xed\xfa\xcf":
        endian, bits, hdr = ">", 64, 32
    elif magic == b"\xfe\xed\xfa\xce":
        endian, bits, hdr = ">", 32, 28
    else:
        return None
    cpu, sub, ftype, ncmds, sizeofcmds, flags = struct.unpack(
        endian + "iiIIII", blob[4:28])
    info = {
        "format": "Mach-O %d-bit%s" % (bits, "" if endian == "<" else " big-endian"),
        "arch": CPU_TYPES.get(cpu & 0xFFFFFFFFFFFFFFFF if cpu > 0 else cpu,
                              "cputype %d" % cpu),
        "cputype": cpu,
        "cpusubtype": sub,
        "filetype": FILE_TYPES.get(ftype, "type %d" % ftype),
        "ncmds": ncmds,
        "architectures": [CPU_TYPES.get(cpu, "cputype %d" % cpu)],
        "dylibs": [],
    }
    pos = hdr
    for _ in range(ncmds):
        if pos + 8 > len(blob):
            break
        cmd, cmdsize = struct.unpack(endian + "II", blob[pos:pos + 8])
        if cmdsize == 0:
            break
        if cmd == LC_LOAD_DYLIB and pos + 24 <= len(blob):
            off = struct.unpack(endian + "I", blob[pos + 8:pos + 12])[0]
            raw = blob[pos + off:pos + cmdsize].split(b"\0", 1)[0]
            info["dylibs"].append(raw.decode("utf-8", "replace"))
        elif cmd == LC_VERSION_MIN_MACOSX and pos + 16 <= len(blob):
            ver, sdk = struct.unpack(endian + "II", blob[pos + 8:pos + 16])
            info["min_macos"] = _version_str(ver)
            info["sdk"] = _version_str(sdk)
        elif cmd == LC_BUILD_VERSION and pos + 24 <= len(blob):
            plat, minos, sdk, _n = struct.unpack(endian + "IIII",
                                                 blob[pos + 8:pos + 24])
            info["platform"] = PLATFORMS.get(plat, "platform %d" % plat)
            info["min_macos"] = _version_str(minos)
            info["sdk"] = _version_str(sdk)
        pos += cmdsize
    return info


# --------------------------------------------------------------------------
# unpacking
# --------------------------------------------------------------------------

def safe_join(dest: str, name: str) -> str:
    clean = name.lstrip("/")
    while clean.startswith("./"):
        clean = clean[2:]
    target = os.path.normpath(os.path.join(dest, clean))
    root = os.path.normpath(dest)
    if target != root and not target.startswith(root + os.sep):
        raise ValueError("archive member %r escapes the destination" % name)
    return target


def unpack_cpio(entries, dest: str) -> list[dict]:
    written = []
    for e in entries:
        target = safe_join(dest, e.name)
        if e.kind == "dir":
            os.makedirs(target, exist_ok=True)
            continue
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if e.kind == "symlink":
            link = e.data.split(b"\0", 1)[0].decode("utf-8", "replace")
            if os.path.lexists(target):
                os.unlink(target)
            os.symlink(link, target)
            written.append({"path": target, "kind": "symlink", "target": link})
            continue
        if e.kind != "file":
            continue
        with open(target, "wb") as fh:
            fh.write(e.data)
        mode = e.mode & 0o7777
        os.chmod(target, mode if mode else 0o644)
        written.append({"path": target, "kind": "file", "size": e.size,
                        "mode": oct(mode),
                        "sha256": hashlib.sha256(e.data).hexdigest()})
    return written


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pkg")
    ap.add_argument("--dest", help="directory to unpack the payload into")
    ap.add_argument("--list", action="store_true",
                    help="list the payload without writing anything")
    ap.add_argument("--toc", action="store_true", help="print the xar XML TOC")
    ap.add_argument("--identify", action="store_true",
                    help="parse the Mach-O header of every extracted binary")
    args = ap.parse_args(argv)

    blob = open(args.pkg, "rb").read()
    print("archive : %s" % os.path.abspath(args.pkg))
    print("bytes   : %d" % len(blob))
    print("sha256  : %s" % hashlib.sha256(blob).hexdigest())

    xar = XarFile(blob)
    print("xar     : version %d, header %d bytes, checksum %s"
          % (xar.version, xar.header_size, xar.checksum_alg))
    print("toc     : %d bytes compressed -> %d bytes of XML"
          % (xar.toc_length_compressed, xar.toc_length_uncompressed))
    if args.toc:
        print(xar.toc_xml.decode("utf-8", "replace"))

    payloads = []
    print("xar entries:")
    for path, kind, data_el in xar.entries():
        size = ""
        if data_el is not None:
            s = data_el.find("size")
            size = s.text if s is not None and s.text else ""
        print("   %-8s %12s  %s" % (kind, size, path))
        if kind == "file" and os.path.basename(path) == "Payload":
            payloads.append((path, data_el))

    if not payloads:
        print("no Payload member found in the xar TOC", file=sys.stderr)
        return 2

    all_written = []
    for path, data_el in payloads:
        raw = xar.read(data_el)
        plain, how = detect_and_decompress(raw)
        print("\npayload : %s  %d bytes (%s) -> %d bytes of cpio %s"
              % (path, len(raw), how, len(plain),
                 plain[:6].decode("ascii", "replace")))
        entries = list(read_cpio(plain))
        if args.list or not args.dest:
            for e in entries:
                print("   %-8s %-7s %10d  %s"
                      % (e.kind, oct(e.mode & 0o7777), e.size, e.name))
            print("entries : %d" % len(entries))
            if not args.dest:
                continue
        sub = os.path.join(args.dest, os.path.dirname(path)) if len(payloads) > 1 \
            else args.dest
        os.makedirs(sub, exist_ok=True)
        written = unpack_cpio(entries, sub)
        print("wrote   : %d files into %s" % (len(written), os.path.abspath(sub)))
        all_written.extend(written)

    if args.identify:
        print("\nMach-O identification (headers parsed here; nothing executed):")
        for w in all_written:
            if w["kind"] != "file":
                continue
            head = open(w["path"], "rb").read(4096)
            info = describe_macho(head)
            if info is None:
                continue
            print("   %s" % w["path"])
            print("      sha256      : %s" % w["sha256"])
            for key in ("format", "arch", "architectures", "filetype",
                        "platform", "min_macos", "sdk"):
                if key in info:
                    print("      %-11s : %s" % (key, info[key]))
            if info.get("dylibs"):
                for d in info["dylibs"]:
                    print("      dylib       : %s" % d)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

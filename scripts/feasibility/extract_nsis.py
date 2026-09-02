#!/usr/bin/env python3
"""Pure-Python NSIS installer payload extractor.

Written for CometGUI Phase 00 (work unit 4) so that the payload of the
Windows Percolator artefact ``percolator-v3-07.exe`` can be inspected on a
Linux host without installing anything: no 7z, no p7zip, no wine, no cabextract.
Only the Python standard library is used (``zlib``, ``lzma``, ``bz2``).

Format, as implemented here
---------------------------
An NSIS installer is a Win32 PE stub followed by::

    firstheader   28 bytes: flags, 0xDEADBEEF, "NullsoftInst",
                            length_of_header, length_of_all_following_data
    data section  either one solid compressed stream containing
                  [header][file records...], or a separately compressed
                  header block followed by individually compressed file
                  records.

Every block in the non-solid layout is prefixed by an int32 whose high bit
means "this block is compressed"; the remaining 31 bits are the stored length.

The decompressed header starts with an int32 of flags followed by eight
``block_header`` entries ``{int32 offset; int32 num;}`` in the fixed order
pages, sections, entries, strings, langtables, ctlcolors, bgfont, data.
Blocks 0-6 are offsets inside the decompressed header; block 7 (data) is an
offset inside the *data section* of the file (or inside the solid stream).

Files are extracted by walking the entry (instruction) array: opcode 20
``EW_EXTRACTFILE`` carries the destination file name and the offset of the
file's record inside the data block, and opcode 11 ``EW_CREATEDIR`` with its
second parameter set is ``SetOutPath``, which establishes ``$OUTDIR``.

Usage
-----
    python3 extract_nsis.py INSTALLER.exe -o OUTDIR [--list] [--json FILE]
    python3 extract_nsis.py --self-test

Exit status is not evidence: check that the listed files exist with the
listed sizes.

Windows
-------
NSIS member paths are Windows paths: ``$INSTDIR\\bin\\percolator.exe``.  On
Linux a backslash is an ordinary filename character; on Windows it is a
separator, Win32 strips trailing dots and spaces from every component before
the filesystem sees it, and names such as ``NUL`` are devices.  ``--self-test``
drives the path safety net under both platforms' ``os.path`` and requires that
every case land in the same place, inside the output directory, under both.
"""

from __future__ import annotations

import argparse
import bz2
import hashlib
import json
import lzma
import os
import re
import struct
import sys
import zlib

FH_SIG = struct.pack("<I", 0xDEADBEEF) + b"NullsoftInst"
FH_SIZE = 28

# block_header indices, exehead/fileform.h
NB_PAGES, NB_SECTIONS, NB_ENTRIES, NB_STRINGS = 0, 1, 2, 3
NB_LANGTABLES, NB_CTLCOLORS, NB_BGFONT, NB_DATA = 4, 5, 6, 7
BLOCKS_NUM = 8

EW_CREATEDIR = 11
EW_EXTRACTFILE = 20

MAX_ENTRY_OFFSETS = 6
ENTRY_SIZE = 4 * (1 + MAX_ENTRY_OFFSETS)

# $0..$9, $R0..$R9 then the named variables, exehead/fileform.h order.
VAR_NAMES = (
    [str(i) for i in range(10)]
    + ["R%d" % i for i in range(10)]
    + [
        "CMDLINE",
        "INSTDIR",
        "OUTDIR",
        "EXEDIR",
        "LANGUAGE",
        "TEMP",
        "PLUGINSDIR",
        "EXEPATH",
        "EXEFILE",
        "HWNDPARENT",
        "_CLICK",
        "_OUTDIR",
    ]
)

# The handful of CSIDL values an installer of this kind actually uses.
CSIDL_NAMES = {
    0x00: "DESKTOP",
    0x02: "SMPROGRAMS",
    0x05: "DOCUMENTS",
    0x0B: "SMSTARTUP",
    0x10: "DESKTOP",
    0x1A: "APPDATA",
    0x1C: "LOCALAPPDATA",
    0x22: "PROFILE",
    0x23: "WINDIR",
    0x24: "WINDIR",
    0x25: "SYSDIR",
    0x26: "PROGRAMFILES",
    0x2B: "COMMONFILES",
    0x2F: "SMPROGRAMS_COMMON",
    0x35: "COMMONAPPDATA",
    0x2A: "PROGRAMFILES32",
    0x2C: "COMMONFILES32",
}


class NsisError(Exception):
    pass


# --------------------------------------------------------------------------
# decompression
# --------------------------------------------------------------------------


def _lzma_raw(data: bytes, limit: int | None = None) -> bytes:
    """Decode an NSIS LZMA block: 5 props bytes then the stream, no size field."""
    if len(data) < 6:
        raise NsisError("LZMA block too short")
    header = data[:5] + struct.pack("<Q", 0xFFFFFFFFFFFFFFFF)
    dec = lzma.LZMADecompressor(format=lzma.FORMAT_ALONE)
    out = dec.decompress(header + data[5:], max_length=limit or -1)
    return out


def _deflate_raw(data: bytes, limit: int | None = None) -> bytes:
    dec = zlib.decompressobj(-15)
    return dec.decompress(data, limit or 0)


def _bzip2_nsis(data: bytes, limit: int | None = None) -> bytes:
    """NSIS ships a modified bzip2 without the 'BZh' magic; try both forms."""
    try:
        return bz2.BZ2Decompressor().decompress(data, max_length=limit or -1)
    except Exception:
        pass
    for level in b"123456789":
        try:
            payload = b"BZh" + bytes([level]) + data
            return bz2.BZ2Decompressor().decompress(payload, max_length=limit or -1)
        except Exception:
            continue
    raise NsisError("not a bzip2 block this decoder can read")


def _looks_like_lzma_props(b: bytes) -> bool:
    if len(b) < 5:
        return False
    if b[0] >= 9 * 5 * 5:  # lc + lp*9 + pb*45, all in range
        return False
    dic = struct.unpack("<I", b[1:5])[0]
    return 0 < dic <= (1 << 30)


DECODERS = {
    "lzma": _lzma_raw,
    "bzip2": _bzip2_nsis,
    "zlib": _deflate_raw,
}


# --------------------------------------------------------------------------
# string table
# --------------------------------------------------------------------------


class StringTable:
    def __init__(self, blob: bytes, unicode_mode: bool):
        self.blob = blob
        self.unicode = unicode_mode

    def get(self, pos: int) -> str:
        if pos < 0:
            return ""
        if self.unicode:
            off = pos * 2
            if off >= len(self.blob):
                return ""
            end = off
            while end + 1 < len(self.blob) and self.blob[end : end + 2] != b"\x00\x00":
                end += 2
            raw = self.blob[off:end]
            units = [
                struct.unpack("<H", raw[i : i + 2])[0] for i in range(0, len(raw), 2)
            ]
            return self._decode_units(units)
        off = pos
        if off >= len(self.blob):
            return ""
        end = self.blob.find(b"\x00", off)
        if end < 0:
            end = len(self.blob)
        return self._decode_bytes(self.blob[off:end])

    # NSIS escape codes -----------------------------------------------
    # ANSI:    252 skip, 253 var, 254 shell, 255 lang -- parameter in 2 bytes.
    # Unicode: 1 lang, 2 shell, 3 var, 4 skip -- parameter in one UTF-16 unit
    #          holding the same two bytes, each OR'd with 0x80 so the unit is
    #          never NUL.  Confirmed empirically on this installer: the var
    #          indices in the SetOutPath chain decode to 21 ($INSTDIR) and 31
    #          ($_OUTDIR), which is exactly what "File /r" emits.
    UN_LANG, UN_SHELL, UN_VAR, UN_SKIP = 1, 2, 3, 4

    def _decode_units(self, units):
        out = []
        i = 0
        n = len(units)
        while i < n:
            c = units[i]
            i += 1
            if c not in (self.UN_LANG, self.UN_SHELL, self.UN_VAR, self.UN_SKIP):
                out.append(chr(c))
                continue
            if i >= n:
                break
            param = units[i]
            i += 1
            lo, hi = param & 0xFF, (param >> 8) & 0xFF
            if c == self.UN_SKIP:
                out.append(chr(param))
            elif c == self.UN_VAR:
                out.append(self._var((lo & 0x7F) | ((hi & 0x7F) << 7)))
            elif c == self.UN_SHELL:
                out.append(self._shell(lo, hi))
            else:
                out.append("$(LSTR_%d)" % ((lo & 0x7F) | ((hi & 0x7F) << 7)))
        return "".join(out)

    def _decode_bytes(self, raw: bytes):
        out = []
        i = 0
        n = len(raw)
        while i < n:
            c = raw[i]
            i += 1
            if c == 252:  # NS_SKIP_CODE
                if i < n:
                    out.append(chr(raw[i]))
                    i += 1
            elif c == 253:  # NS_VAR_CODE
                if i + 1 < n:
                    idx = (raw[i] & 0x7F) | ((raw[i + 1] & 0x7F) << 7)
                    out.append(self._var(idx))
                    i += 2
            elif c == 254:  # NS_SHELL_CODE
                if i + 1 < n:
                    out.append(self._shell(raw[i], raw[i + 1]))
                    i += 2
            elif c == 255:  # NS_LANG_CODE
                if i + 1 < n:
                    idx = (raw[i] & 0x7F) | ((raw[i + 1] & 0x7F) << 7)
                    out.append("$(LSTR_%d)" % idx)
                    i += 2
            else:
                out.append(chr(c))
        return "".join(out)

    @staticmethod
    def _var(idx: int) -> str:
        if 0 <= idx < len(VAR_NAMES):
            return "$" + VAR_NAMES[idx]
        return "$VAR%d" % idx

    @staticmethod
    def _shell(a: int, b: int) -> str:
        """Render a shell-folder / registry-constant reference.

        NSIS stores a folder id and its all-users fallback.  Ids with 0x80 set
        are compiler-generated registry lookups rather than CSIDLs
        (``$PROGRAMFILES`` is one of those), so no CSIDL name is invented for
        them here.  Payload paths in this archive resolve through
        ``$INSTDIR``/``$_OUTDIR`` and never need a folder id.
        """
        if not (a & 0x80):
            name = CSIDL_NAMES.get(a)
            if name:
                return "$" + name
        return "$SHELL(0x%02x,0x%02x)" % (a, b)


# --------------------------------------------------------------------------


class NsisArchive:
    def __init__(self, path: str):
        self.path = path
        with open(path, "rb") as fh:
            self.raw = fh.read()
        self.sha256 = hashlib.sha256(self.raw).hexdigest()
        self._find_firstheader()
        self._load_header()

    # -- firstheader --------------------------------------------------------
    def _find_firstheader(self):
        idx = self.raw.find(FH_SIG)
        if idx < 4:
            raise NsisError("no NSIS firstheader signature (0xDEADBEEF NullsoftInst)")
        self.fh_offset = idx - 4
        fields = struct.unpack("<7I", self.raw[self.fh_offset : self.fh_offset + FH_SIZE])
        (
            self.fh_flags,
            _sig,
            _n1,
            _n2,
            _n3,
            self.length_of_header,
            self.length_of_all_following_data,
        ) = fields
        self.data_start = self.fh_offset + FH_SIZE
        self.stub_size = self.fh_offset

    # -- header block -------------------------------------------------------
    def _load_header(self):
        d = self.raw
        ds = self.data_start
        first = struct.unpack("<I", d[ds : ds + 4])[0]
        compressed = bool(first & 0x80000000)
        size = first & 0x7FFFFFFF

        self.solid = False
        self.method = None
        header = None

        # Solid archives begin with the raw compressed stream itself.
        if _looks_like_lzma_props(d[ds : ds + 5]):
            blob = _lzma_raw(d[ds:], limit=self.length_of_header)
            if len(blob) == self.length_of_header:
                self.solid, self.method, header = True, "lzma", blob

        if header is None and compressed:
            block = d[ds + 4 : ds + 4 + size]
            for name in ("lzma", "bzip2", "zlib"):
                if name == "lzma" and not _looks_like_lzma_props(block[:5]):
                    continue
                try:
                    blob = DECODERS[name](block, limit=self.length_of_header)
                except Exception:
                    continue
                if len(blob) == self.length_of_header:
                    self.method, header = name, blob
                    break

        if header is None and not compressed:
            blob = d[ds + 4 : ds + 4 + self.length_of_header]
            if len(blob) == self.length_of_header:
                self.method, header = "store", blob

        if header is None:
            # last resort: solid, non-LZMA
            for name in ("zlib", "bzip2"):
                try:
                    blob = DECODERS[name](d[ds:], limit=self.length_of_header)
                except Exception:
                    continue
                if len(blob) == self.length_of_header:
                    self.solid, self.method, header = True, name, blob
                    break

        if header is None:
            raise NsisError("could not decompress the NSIS header block")

        self.header = header
        self.header_block_stored = None if self.solid else 4 + size
        self._parse_header()
        self._set_data_area()

    def _parse_header(self):
        h = self.header
        self.header_flags = struct.unpack("<I", h[0:4])[0]
        self.blocks = []
        for i in range(BLOCKS_NUM):
            off, num = struct.unpack("<II", h[4 + i * 8 : 12 + i * 8])
            self.blocks.append((off, num))

        # string table spans from the strings block to the next block start
        s_off = self.blocks[NB_STRINGS][0]
        s_end = self.blocks[NB_LANGTABLES][0]
        if not (0 < s_off < s_end <= len(h)):
            s_end = len(h)
        strblob = h[s_off:s_end]
        self.unicode = self._detect_unicode(strblob)
        self.strings = StringTable(strblob, self.unicode)

        e_off, e_num = self.blocks[NB_ENTRIES]
        self.entries = []
        for i in range(e_num):
            base = e_off + i * ENTRY_SIZE
            if base + ENTRY_SIZE > len(h):
                break
            vals = struct.unpack("<7I", h[base : base + ENTRY_SIZE])
            self.entries.append(vals)

    @staticmethod
    def _detect_unicode(blob: bytes) -> bool:
        sample = blob[: min(len(blob), 4096)]
        if not sample:
            return False
        odd_nul = sum(1 for i in range(1, len(sample), 2) if sample[i] == 0)
        return odd_nul > len(sample) // 4

    def _set_data_area(self):
        """Locate the base against which EW_EXTRACTFILE record offsets count.

        The exehead measures file records from the first byte after the header
        block (``g_filehdrsize``), not from the start of the data section, and
        ``blocks[NB_DATA].offset`` is 0 in archives built this way.  Honour a
        non-zero NB_DATA offset if one is ever present.
        """
        declared = self.blocks[NB_DATA][0]
        if self.solid:
            computed = self.length_of_header
        else:
            computed = self.header_block_stored
        self.data_area = declared if declared > 0 else computed

    # -- data block ---------------------------------------------------------
    def _data_section(self) -> bytes:
        """Bytes in which NB_DATA offsets are measured."""
        if self.solid:
            if not hasattr(self, "_solid_blob"):
                self._solid_blob = DECODERS[self.method](self.raw[self.data_start :])
            return self._solid_blob
        return self.raw[self.data_start :]

    def read_record(self, rec_offset: int):
        """Return (uncompressed bytes, stored size, was_compressed)."""
        sec = self._data_section()
        base = self.data_area + rec_offset
        if base + 4 > len(sec):
            raise NsisError("file record offset %d out of range" % rec_offset)
        n = struct.unpack("<I", sec[base : base + 4])[0]
        size = n & 0x7FFFFFFF
        compressed = bool(n & 0x80000000) and not self.solid
        blob = sec[base + 4 : base + 4 + size]
        if compressed:
            return DECODERS[self.method](blob), size, True
        return blob, size, False

    # -- instruction walk ---------------------------------------------------
    def files(self):
        """Yield dicts describing every EW_EXTRACTFILE in installer order."""
        outdir = "$INSTDIR"
        base_outdir = "$INSTDIR"
        for which, p0, p1, p2, p3, p4, p5 in self.entries:
            if which == EW_CREATEDIR and p1:
                raw = self.strings.get(p0)
                # "File /r" emits SetOutPath "$_OUTDIR\<subdir>", relative to
                # the outdir that was in force when the recursion started.
                if raw.startswith("$_OUTDIR"):
                    outdir = base_outdir + raw[len("$_OUTDIR"):]
                else:
                    outdir = raw
                    base_outdir = raw
            elif which == EW_EXTRACTFILE:
                name = self.strings.get(p1)
                # A name that already carries a root -- a drive, a leading
                # separator, or a variable such as $PLUGINSDIR (the installer's
                # temp directory) -- is used as-is; anything else is relative
                # to the current $OUTDIR.
                rooted = name.startswith(("$", "\\", "/")) or ":" in name[:3]
                path = name if rooted else (outdir.rstrip("\\/") + "\\" + name)
                yield {
                    "name": name,
                    "outdir": outdir,
                    "install_path": path,
                    "record_offset": p2,
                    "overwrite_flag": p0,
                    "mtime_low": p3,
                    "mtime_high": p4,
                }

    def dirs(self):
        for which, p0, p1, _p2, _p3, _p4, _p5 in self.entries:
            if which == EW_CREATEDIR:
                yield {"path": self.strings.get(p0), "set_outpath": bool(p1)}

    def opcode_histogram(self):
        hist = {}
        for e in self.entries:
            hist[e[0]] = hist.get(e[0], 0) + 1
        return dict(sorted(hist.items()))


# Windows reserves these for devices, in every directory and whatever the
# extension: "bin\NUL.txt" is the null device, not a file.
WIN_RESERVED = frozenset(
    ("CON", "PRN", "AUX", "NUL")
    + tuple("COM%d" % i for i in range(1, 10))
    + tuple("LPT%d" % i for i in range(1, 10))
)


def _safe_segment(seg: str) -> str:
    """Reduce one path component to a form Win32 cannot reinterpret.

    Two things Win32 does and ``os.path`` does not.  It strips trailing dots
    and spaces from *every* component before the filesystem sees it, so the
    component ``".. "`` -- which the ``".."`` filter in ``_safe_join`` lets
    through, and which ``ntpath.normpath`` keeps -- is a parent-directory hop
    on Windows; and it resolves the reserved names above to devices.  Trailing
    dots and spaces are therefore replaced rather than removed, so the result
    can be neither ``".."`` nor a shadow of a legitimate neighbour, and a
    reserved stem is prefixed.  Control characters, which the earlier
    substitution does not cover, are illegal in a Windows filename.

    This runs on every platform: a Linux and a Windows extraction of the same
    installer must produce the same tree, or comparing their checksums proves
    nothing.
    """
    seg = re.sub(r"[\x00-\x1f]", "_", seg)
    seg = re.sub(r"[. ]+$", lambda m: "_" * len(m.group(0)), seg)
    if seg.split(".")[0].upper() in WIN_RESERVED:
        seg = "_" + seg
    return seg


def _safe_join(root: str, install_path: str) -> str:
    """Map a Windows install path onto a relative path under root, safely."""
    p = install_path.replace("\\", "/")
    p = re.sub(r"^\$[A-Za-z_0-9()]+", lambda m: m.group(0).lstrip("$"), p)
    p = re.sub(r"[:*?\"<>|]", "_", p)
    parts = [_safe_segment(seg) for seg in p.split("/") if seg not in ("", ".", "..")]
    dest = os.path.normpath(os.path.join(root, *parts))
    root_abs = os.path.abspath(root)
    if not os.path.abspath(dest).startswith(root_abs + os.sep):
        raise NsisError("refusing to write outside the output directory: %s" % dest)
    return dest


# --------------------------------------------------------------------------
# --self-test
# --------------------------------------------------------------------------


def _win32_canonicalise(path: str) -> str:
    """Model the Win32 path canonicaliser closely enough to catch an escape.

    Win32 strips trailing dots and spaces from every component and only then
    resolves ``".."``.  ``os.path`` never does the first step, so a component
    such as ``".. "`` is inert to Python and a parent-directory hop to the
    filesystem.  That gap is what this models, so the self-test can measure a
    Windows escape from a Linux host.
    """
    out = []
    for seg in path.replace("\\", "/").split("/"):
        if not seg:
            continue
        if set(seg) <= {".", " "}:
            # Win32 strips the trailing dots and spaces, so ".. ", ".. ." and
            # "..." can all end up as the parent hop "..".  Exactly which of
            # them do cannot be settled from here, so assume the worst: one
            # dot is "stay", two or more may be "up".  Over-approximating the
            # attacker can only raise a false alarm, never clear a real
            # escape.
            if seg.count(".") >= 2:
                if out and out[-1] != "..":
                    out.pop()
                else:
                    out.append("..")
            continue
        seg = seg.rstrip(". ")
        if seg:
            out.append(seg)
    return "/".join(out)


# (label, install path, where it must land under the output directory,
#  or None if the extractor must refuse it outright)
SELF_TEST_CASES = [
    ("real payload member", r"$INSTDIR\bin\percolator.exe", "INSTDIR/bin/percolator.exe"),
    ("real plugins member", r"$PLUGINSDIR\System.dll", "PLUGINSDIR/System.dll"),
    ("real nested member",
     r"$INSTDIR\share\xml\percolator\xml-pin-1-3\percolator_in.xsd",
     "INSTDIR/share/xml/percolator/xml-pin-1-3/percolator_in.xsd"),
    ("dot-dot prefix", r"..\..\evil.txt", "evil.txt"),
    ("dot-dot in the middle", r"bin\..\..\evil.txt", "bin/evil.txt"),
    ("deep dot-dot climb", r"$INSTDIR\bin\..\..\..\..\evil.txt", "INSTDIR/bin/evil.txt"),
    ("absolute drive path", r"C:\evil.txt", "C_/evil.txt"),
    ("drive-relative path", r"C:evil.txt", "C_evil.txt"),
    ("UNC share path", "\\\\server\\share\\evil.txt", "server/share/evil.txt"),
    ("leading separator", r"\evil.txt", "evil.txt"),
    ("trailing-space dot-dot", "$INSTDIR\\.. \\.. \\evil.txt", "INSTDIR/___/___/evil.txt"),
    ("trailing space after a name", "$INSTDIR\\bin \\evil.txt", "INSTDIR/bin_/evil.txt"),
    ("trailing-dot dot-dot", r"$INSTDIR\...\...\evil.txt", "INSTDIR/___/___/evil.txt"),
    ("device name CON", r"$INSTDIR\CON", "INSTDIR/_CON"),
    ("device name with suffix", r"$INSTDIR\bin\NUL.txt", "INSTDIR/bin/_NUL.txt"),
    ("device name lowercase", r"$INSTDIR\bin\com1", "INSTDIR/bin/_com1"),
    ("device name LPT9.dll", r"$INSTDIR\bin\LPT9.dll", "INSTDIR/bin/_LPT9.dll"),
    ("non-device lookalike", r"$INSTDIR\bin\CONFIG.sys", "INSTDIR/bin/CONFIG.sys"),
    ("trailing dot shadows a file", r"$INSTDIR\bin\percolator.exe.",
     "INSTDIR/bin/percolator.exe_"),
    ("trailing space shadows a file", "$INSTDIR\\bin\\percolator.exe ",
     "INSTDIR/bin/percolator.exe_"),
    ("control character", "$INSTDIR\\bin\\x\x01y.dll", "INSTDIR/bin/x_y.dll"),
    ("alternate data stream", r"$INSTDIR\bin\percolator.exe:evil", "INSTDIR/bin/percolator.exe_evil"),
    ("nothing left to write", r"..\..", None),
]


def self_test() -> int:
    """Drive the path safety net with both platforms' os.path and report.

    Nothing is written and no installer is needed: ``_safe_join`` is called
    once with POSIX semantics and once with Windows semantics, and each case
    must land in the same place, inside the output directory, under both --
    where "inside" is judged by ``_win32_canonicalise``, not by ``os.path``.
    """
    import io
    import ntpath
    import posixpath

    root = "out"
    saved_path, saved_sep, saved_altsep = os.path, os.sep, os.altsep
    results = []

    def run(pathmod, sep, altsep, install_path):
        os.path, os.sep, os.altsep = pathmod, sep, altsep
        try:
            return _safe_join(root, install_path).replace("\\", "/")
        except NsisError:
            return None

    print("=== extract_nsis --self-test: the path safety net, both platforms ===")
    print("No installer is read and nothing is written.\n")
    try:
        for label, install_path, expected in SELF_TEST_CASES:
            posix = run(posixpath, "/", None, install_path)
            nt = run(ntpath, "\\", "/", install_path)
            os.path, os.sep, os.altsep = saved_path, saved_sep, saved_altsep

            agree = posix == nt
            got = posix
            as_expected = got == (None if expected is None else root + "/" + expected)
            if got is None:
                contained = True
                no_device = True
            else:
                canon = _win32_canonicalise(got)
                contained = canon == root or canon.startswith(root + "/")
                no_device = all(
                    s.rstrip(". ").split(".")[0].upper() not in WIN_RESERVED
                    for s in canon.split("/")
                )
            ok = agree and as_expected and contained and no_device
            results.append(ok)
            why = []
            if not agree:
                why.append("posix %r != nt %r" % (posix, nt))
            if not as_expected:
                why.append("expected %r" % expected)
            if not contained:
                why.append("ESCAPES the output directory on Windows")
            if not no_device:
                why.append("names a Windows device")
            print("  %s %-32s %-44s %s"
                  % ("ok  " if ok else "FAIL", label,
                     "REFUSED" if got is None else got,
                     "; ".join(why)))
    finally:
        os.path, os.sep, os.altsep = saved_path, saved_sep, saved_altsep

    # The two output-encoding fixes.  Linux cannot run them on Windows, but it
    # can reproduce exactly what a redirected Windows stdout and a Windows
    # text-mode write do, and show that the guarded form survives both.
    print()
    name = "bin\\\u0141ukasz.dll"          # not encodable in cp1252
    for label, kwargs, expected_ok in (
        ("stdout cp1252, unguarded", {}, False),
        ("stdout cp1252, guarded", {"errors": "backslashreplace"}, True),
    ):
        try:
            w = io.TextIOWrapper(io.BytesIO(), encoding="cp1252", **kwargs)
            w.write(name)
            w.flush()
            survived = True
        except UnicodeEncodeError:
            survived = False
        ok = survived == expected_ok
        results.append(ok)
        print("  %s %-32s %-44s %s"
              % ("ok  " if ok else "FAIL", label,
                 "survived" if survived else "UnicodeEncodeError",
                 "" if ok else "expected %s" % expected_ok))

    payload = json.dumps({"files": []}, indent=2)
    for label, newline, want_cr in (
        ("manifest newline default", "\r\n", True),   # what Windows would do
        ("manifest newline pinned", "\n", False),
    ):
        buf = io.BytesIO()
        w = io.TextIOWrapper(buf, encoding="utf-8", newline=newline)
        w.write(payload)
        w.flush()
        has_cr = b"\r" in buf.getvalue()
        ok = has_cr == want_cr
        results.append(ok)
        print("  %s %-32s %-44s %s"
              % ("ok  " if ok else "FAIL", label,
                 "CRLF" if has_cr else "LF only", "" if ok else "unexpected"))

    print()
    if all(results):
        print("extract_nsis: self-test OK -- %d checks, every path contained under "
              "both POSIX and Windows semantics." % len(results))
        return 0
    print("extract_nsis: SELF-TEST FAILED -- %d of %d checks did not hold."
          % (results.count(False), len(results)), file=sys.stderr)
    return 1


def main(argv=None):
    ap = argparse.ArgumentParser(description="Extract an NSIS installer payload.")
    ap.add_argument("installer", nargs="?")
    ap.add_argument("-o", "--output", help="directory to write the payload into")
    ap.add_argument("--list", action="store_true", help="list only, extract nothing")
    ap.add_argument("--json", help="write a machine-readable manifest here")
    ap.add_argument("--self-test", action="store_true",
                    help="check the path safety net under Windows semantics and exit")
    args = ap.parse_args(argv)

    if args.self_test:
        return self_test()
    if not args.installer:
        ap.error("the following arguments are required: installer")

    # A payload member name is an arbitrary UTF-16 string.  On Windows a
    # *redirected* stdout -- a CI log, a pipe into tee -- encodes with the ANSI
    # code page, cp1252 in most installs, and a name outside it raises
    # UnicodeEncodeError part-way through the run.  Mangle the printed name
    # rather than lose the extraction.  No effect where every name encodes.
    try:
        sys.stdout.reconfigure(errors="backslashreplace")
    except (AttributeError, ValueError):
        pass

    arc = NsisArchive(args.installer)
    print("installer      : %s" % args.installer)
    print("sha256         : %s" % arc.sha256)
    print("size           : %d bytes" % len(arc.raw))
    print("PE stub size   : %d bytes (firstheader at 0x%x)" % (arc.stub_size, arc.fh_offset))
    print("firstheader    : flags=0x%x header=%d B following-data=%d B"
          % (arc.fh_flags, arc.length_of_header, arc.length_of_all_following_data))
    print("compression    : %s%s" % (arc.method, " (solid)" if arc.solid else " (per-block)"))
    print("charset        : %s" % ("Unicode (UTF-16LE)" if arc.unicode else "ANSI"))
    print("entries        : %d instructions" % len(arc.entries))
    print("file records at: +%d in the data section (NB_DATA offset %d)"
          % (arc.data_area, arc.blocks[NB_DATA][0]))
    print()

    manifest = {
        "installer": os.path.basename(args.installer),
        "sha256": arc.sha256,
        "size": len(arc.raw),
        "stub_size": arc.stub_size,
        "firstheader_offset": arc.fh_offset,
        "length_of_header": arc.length_of_header,
        "length_of_all_following_data": arc.length_of_all_following_data,
        "compression": arc.method,
        "solid": arc.solid,
        "unicode": arc.unicode,
        "data_area": arc.data_area,
        "entry_count": len(arc.entries),
        "opcodes": arc.opcode_histogram(),
        "files": [],
    }

    if args.output and not args.list:
        os.makedirs(args.output, exist_ok=True)

    total = 0
    for idx, f in enumerate(arc.files()):
        blob, stored, comp = arc.read_record(f["record_offset"])
        digest = hashlib.sha256(blob).hexdigest()
        rec = dict(f)
        rec.update(
            {
                "size": len(blob),
                "stored_size": stored,
                "compressed": comp,
                "sha256": digest,
            }
        )
        dest = None
        if args.output and not args.list:
            dest = _safe_join(args.output, f["install_path"])
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "wb") as out:
                out.write(blob)
            rec["extracted_to"] = dest
        manifest["files"].append(rec)
        total += len(blob)
        print("%3d  %10d  %s  %s" % (idx, len(blob), digest[:16], f["install_path"]))

    uniq = {}
    for rec in manifest["files"]:
        uniq.setdefault((rec["install_path"], rec["sha256"]), rec)
    manifest["unique_file_count"] = len(uniq)
    manifest["unique_bytes"] = sum(r["size"] for r in uniq.values())
    print()
    print("%d extract instructions, %d bytes; %d distinct payload files, %d bytes"
          % (len(manifest["files"]), total, len(uniq), manifest["unique_bytes"]))

    if args.json:
        # Explicit encoding and newline: with the defaults this file would be
        # cp1252 with CRLF on Windows and UTF-8 with LF here, so the same
        # installer would produce two different manifests.
        with open(args.json, "w", encoding="utf-8", newline="\n") as jf:
            json.dump(manifest, jf, indent=2)
        print("manifest written to %s" % args.json)

    return 0


if __name__ == "__main__":
    sys.exit(main())

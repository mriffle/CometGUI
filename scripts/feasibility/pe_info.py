#!/usr/bin/env python3
"""Minimal PE/COFF reader, written because this host has no ``file`` command.

Reports the facts a Windows-artefact feasibility check needs: machine type,
subsystem, link timestamp, characteristics, sections, and the import table
(which DLLs the binary needs and, optionally, which symbols it takes from
each).  Standard library only.

    python3 pe_info.py BINARY.exe [--imports] [--json FILE]
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import struct
import sys

MACHINE = {
    0x0000: "UNKNOWN",
    0x014C: "i386 (x86, 32-bit)",
    0x0200: "IA64",
    0x8664: "x86-64 (AMD64, 64-bit)",
    0xAA64: "ARM64",
    0x01C0: "ARM",
    0x01C4: "ARMNT",
}

SUBSYSTEM = {
    0: "UNKNOWN",
    1: "NATIVE",
    2: "WINDOWS_GUI",
    3: "WINDOWS_CUI (console)",
    5: "OS2_CUI",
    7: "POSIX_CUI",
    9: "WINDOWS_CE_GUI",
    10: "EFI_APPLICATION",
}

CHARACTERISTICS = [
    (0x0001, "RELOCS_STRIPPED"),
    (0x0002, "EXECUTABLE_IMAGE"),
    (0x0020, "LARGE_ADDRESS_AWARE"),
    (0x0100, "32BIT_MACHINE"),
    (0x0200, "DEBUG_STRIPPED"),
    (0x2000, "DLL"),
]

DLL_CHARACTERISTICS = [
    (0x0020, "HIGH_ENTROPY_VA"),
    (0x0040, "DYNAMIC_BASE (ASLR)"),
    (0x0080, "FORCE_INTEGRITY"),
    (0x0100, "NX_COMPAT (DEP)"),
    (0x0400, "NO_SEH"),
    (0x8000, "TERMINAL_SERVER_AWARE"),
]


class PeError(Exception):
    pass


class PeFile:
    def __init__(self, path: str):
        with open(path, "rb") as fh:
            self.data = fh.read()
        self.path = path
        self.sha256 = hashlib.sha256(self.data).hexdigest()
        self.size = len(self.data)
        self._parse()

    def _u(self, fmt, off):
        return struct.unpack_from(fmt, self.data, off)

    def _parse(self):
        d = self.data
        if d[:2] != b"MZ":
            raise PeError("not a DOS/PE image (no MZ)")
        (self.e_lfanew,) = self._u("<I", 0x3C)
        if d[self.e_lfanew : self.e_lfanew + 4] != b"PE\0\0":
            raise PeError("no PE signature at e_lfanew")
        coff = self.e_lfanew + 4
        (
            self.machine,
            self.n_sections,
            self.timestamp,
            _sym_ptr,
            _n_syms,
            self.opt_size,
            self.characteristics,
        ) = self._u("<HHIIIHH", coff)

        opt = coff + 20
        (self.opt_magic,) = self._u("<H", opt)
        self.pe32plus = self.opt_magic == 0x20B
        if self.opt_magic not in (0x10B, 0x20B):
            raise PeError("unknown optional header magic 0x%x" % self.opt_magic)

        (self.linker_major, self.linker_minor) = self._u("<BB", opt + 2)
        if self.pe32plus:
            (self.image_base,) = self._u("<Q", opt + 24)
            self.subsystem = self._u("<H", opt + 68)[0]
            self.dll_chars = self._u("<H", opt + 70)[0]
            dd_off = opt + 112
        else:
            (self.image_base,) = self._u("<I", opt + 28)
            self.subsystem = self._u("<H", opt + 68)[0]
            self.dll_chars = self._u("<H", opt + 70)[0]
            dd_off = opt + 96
        (self.n_datadirs,) = self._u("<I", dd_off - 4)
        self.datadirs = [
            self._u("<II", dd_off + 8 * i) for i in range(min(self.n_datadirs, 16))
        ]

        sec = opt + self.opt_size
        self.sections = []
        for i in range(self.n_sections):
            base = sec + 40 * i
            name = d[base : base + 8].rstrip(b"\0").decode("latin-1")
            vsize, vaddr, rawsize, rawptr = self._u("<IIII", base + 8)
            (flags,) = self._u("<I", base + 36)
            self.sections.append(
                {
                    "name": name,
                    "virtual_size": vsize,
                    "virtual_address": vaddr,
                    "raw_size": rawsize,
                    "raw_pointer": rawptr,
                    "flags": flags,
                }
            )

    # -- helpers ------------------------------------------------------------
    def rva_to_off(self, rva: int):
        for s in self.sections:
            if s["virtual_address"] <= rva < s["virtual_address"] + max(
                s["virtual_size"], s["raw_size"]
            ):
                return s["raw_pointer"] + (rva - s["virtual_address"])
        return None

    def _cstr(self, off: int) -> str:
        end = self.data.find(b"\0", off)
        return self.data[off : end if end >= 0 else len(self.data)].decode(
            "latin-1", "replace"
        )

    def imports(self, with_symbols: bool = True):
        """Parse the import directory: [(dll, [symbol, ...]), ...]."""
        if len(self.datadirs) < 2:
            return []
        rva, size = self.datadirs[1]
        if not rva:
            return []
        off = self.rva_to_off(rva)
        if off is None:
            return []
        out = []
        i = 0
        while True:
            base = off + 20 * i
            i += 1
            if base + 20 > len(self.data):
                break
            oft, _ts, _fwd, name_rva, first_thunk = self._u("<IIIII", base)
            if not (oft or name_rva or first_thunk):
                break
            noff = self.rva_to_off(name_rva)
            dll = self._cstr(noff) if noff is not None else "<unreadable>"
            syms = []
            if with_symbols:
                thunk_rva = oft or first_thunk
                toff = self.rva_to_off(thunk_rva)
                if toff is not None:
                    width = 8 if self.pe32plus else 4
                    fmt = "<Q" if self.pe32plus else "<I"
                    ordinal_flag = (1 << 63) if self.pe32plus else (1 << 31)
                    j = 0
                    while j < 8192:
                        (v,) = self._u(fmt, toff + width * j)
                        j += 1
                        if v == 0:
                            break
                        if v & ordinal_flag:
                            syms.append("#%d" % (v & 0xFFFF))
                        else:
                            hoff = self.rva_to_off(v & 0x7FFFFFFF)
                            if hoff is not None:
                                syms.append(self._cstr(hoff + 2))
            out.append((dll, syms))
        return out

    def describe(self) -> dict:
        return {
            "path": self.path,
            "size": self.size,
            "sha256": self.sha256,
            "format": "PE32+" if self.pe32plus else "PE32",
            "machine": "0x%04x %s" % (self.machine, MACHINE.get(self.machine, "?")),
            "subsystem": "%d %s" % (self.subsystem, SUBSYSTEM.get(self.subsystem, "?")),
            "timestamp": self.timestamp,
            "timestamp_utc": _dt.datetime.fromtimestamp(
                self.timestamp, _dt.timezone.utc
            ).strftime("%Y-%m-%d %H:%M:%S UTC"),
            "linker": "%d.%d" % (self.linker_major, self.linker_minor),
            "image_base": "0x%x" % self.image_base,
            "characteristics": [n for m, n in CHARACTERISTICS if self.characteristics & m],
            "dll_characteristics": [n for m, n in DLL_CHARACTERISTICS if self.dll_chars & m],
            "sections": [
                "%-8s vaddr=0x%08x vsize=%-9d rawsize=%d"
                % (s["name"], s["virtual_address"], s["virtual_size"], s["raw_size"])
                for s in self.sections
            ],
        }


def main(argv=None):
    ap = argparse.ArgumentParser(description="Report PE/COFF header facts.")
    ap.add_argument("binaries", nargs="+")
    ap.add_argument("--imports", action="store_true", help="list imported symbols too")
    ap.add_argument("--json", help="write the report here")
    args = ap.parse_args(argv)

    report = []
    for path in args.binaries:
        pe = PeFile(path)
        info = pe.describe()
        imps = pe.imports(with_symbols=args.imports)
        info["imports"] = [{"dll": d, "symbols": s} for d, s in imps]
        report.append(info)

        print("=" * 72)
        print(path)
        print("=" * 72)
        for key in (
            "size",
            "sha256",
            "format",
            "machine",
            "subsystem",
            "timestamp_utc",
            "linker",
            "image_base",
        ):
            print("  %-18s %s" % (key, info[key]))
        print("  %-18s %s" % ("characteristics", ", ".join(info["characteristics"])))
        print("  %-18s %s" % ("dll_characteristics", ", ".join(info["dll_characteristics"])))
        print("  sections (%d):" % len(info["sections"]))
        for s in info["sections"]:
            print("    " + s)
        print("  imported DLLs (%d):" % len(imps))
        for dll, syms in imps:
            print("    %-28s %d symbols" % (dll, len(syms)))
            if args.imports:
                for sym in syms:
                    print("        " + sym)
        print()

    if args.json:
        with open(args.json, "w") as fh:
            json.dump(report, fh, indent=2)
        print("report written to %s" % args.json)
    return 0


if __name__ == "__main__":
    sys.exit(main())

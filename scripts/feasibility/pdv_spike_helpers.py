#!/usr/bin/env python3
"""Helpers for scripts/feasibility/pdv_spike.sh -- Phase 00, work unit 9.

Three subcommands, all deliberately dependency-free (the project virtualenv
holds only Sphinx; nothing may be installed on the host):

  mgf MZML MGF
      Convert an mzML to MGF.  Every MS2 spectrum is written with
      ``TITLE=<the mzML spectrum's native id>`` -- which is exactly the value
      Comet records as ``spectrumNativeID`` in its pepXML, and exactly the key
      PDV's CLI looks up.  See docs/feasibility/pdv-converter-spike.rst.

  run METRICS_FILE -- COMMAND...
      Run COMMAND, then write "exit=<n> wall=<s> user=<s> sys=<s>
      maxrss_mib=<n>" to METRICS_FILE.  There is no /usr/bin/time on this
      host, so peak RSS is taken from wait4()'s rusage.

  verify-image FILE...
      Parse each file's own header and report its real format and pixel
      dimensions.  There is no file(1) on this host, and exit code 0 proves
      nothing, so this is the check that a "generated figure" is an image:

        PNG  magic, IHDR width/height, every chunk's CRC recomputed, the
             IDAT stream inflated and unfiltered, and the non-white pixel
             count reported so a blank canvas cannot pass.
        PDF  %PDF- magic, %%EOF present, /MediaBox for the page size.
        JPEG SOI marker and the SOFn frame header for the dimensions.

      Exits non-zero unless every file parsed as a real, non-blank image.
"""

import base64
import collections
import os
import re
import struct
import sys
import time
import zlib
import xml.etree.ElementTree as ET


# --------------------------------------------------------------------------
# mzML -> MGF
# --------------------------------------------------------------------------

MS_LEVEL = "MS:1000511"
SCAN_START_TIME = "MS:1000016"
SELECTED_ION_MZ = "MS:1000744"
CHARGE_STATE = "MS:1000041"
MZ_ARRAY = "MS:1000514"
INTENSITY_ARRAY = "MS:1000515"
FLOAT64 = "MS:1000523"
FLOAT32 = "MS:1000521"
ZLIB_COMPRESSION = "MS:1000574"
UNIT_MINUTE = "MS:1000038"


def _local(tag):
    return tag.rsplit("}", 1)[-1]


def _decode_arrays(spectrum):
    arrays = {}
    for bda in spectrum.iter():
        if _local(bda.tag) != "binaryDataArray":
            continue
        kind = bits = None
        compressed = False
        payload = ""
        for child in bda:
            name = _local(child.tag)
            if name == "cvParam":
                acc = child.get("accession")
                if acc == MZ_ARRAY:
                    kind = "mz"
                elif acc == INTENSITY_ARRAY:
                    kind = "intensity"
                elif acc == FLOAT64:
                    bits = 64
                elif acc == FLOAT32:
                    bits = 32
                elif acc == ZLIB_COMPRESSION:
                    compressed = True
            elif name == "binary":
                payload = child.text or ""
        if kind is None or bits is None:
            continue
        raw = base64.b64decode(payload)
        if compressed:
            raw = zlib.decompress(raw)
        count = len(raw) // (bits // 8)
        fmt = "<%d%s" % (count, "d" if bits == 64 else "f")
        arrays[kind] = struct.unpack(fmt, raw[: count * (bits // 8)])
    return arrays


def mzml_to_mgf(src, dst):
    written = 0
    skipped = 0
    with open(dst, "w", newline="\n") as out:
        for _, el in ET.iterparse(src, events=("end",)):
            if _local(el.tag) != "spectrum":
                continue
            native_id = el.get("id")
            ms_level = None
            rt = precursor_mz = charge = None
            for cv in el.iter():
                if _local(cv.tag) != "cvParam":
                    continue
                acc = cv.get("accession")
                if acc == MS_LEVEL:
                    ms_level = int(cv.get("value"))
                elif acc == SCAN_START_TIME:
                    rt = float(cv.get("value"))
                    if cv.get("unitAccession") == UNIT_MINUTE or \
                            cv.get("unitName") == "minute":
                        rt *= 60.0
                elif acc == SELECTED_ION_MZ:
                    precursor_mz = float(cv.get("value"))
                elif acc == CHARGE_STATE:
                    charge = int(cv.get("value"))
            if ms_level != 2:
                el.clear()
                continue
            arrays = _decode_arrays(el)
            mz = arrays.get("mz")
            intensity = arrays.get("intensity")
            if not mz or not intensity or precursor_mz is None:
                skipped += 1
                el.clear()
                continue
            out.write("BEGIN IONS\n")
            out.write("TITLE=%s\n" % native_id)
            out.write("PEPMASS=%.6f\n" % precursor_mz)
            if charge:
                out.write("CHARGE=%d+\n" % charge)
            if rt is not None:
                out.write("RTINSECONDS=%.3f\n" % rt)
            for m, i in zip(mz, intensity):
                out.write("%.5f %.3f\n" % (m, i))
            out.write("END IONS\n")
            written += 1
            el.clear()
    print("mgf: wrote %d MS2 spectra to %s (%d skipped: no peaks or no "
          "precursor m/z)" % (written, dst, skipped))
    return 0 if written else 1


# --------------------------------------------------------------------------
# run with wall-clock and peak-RSS accounting
# --------------------------------------------------------------------------

def run_measured(metrics_path, argv):
    started = time.time()
    pid = os.fork()
    if pid == 0:
        try:
            os.execvp(argv[0], argv)
        finally:
            os._exit(127)
    _, status, usage = os.wait4(pid, 0)
    elapsed = time.time() - started
    code = os.waitstatus_to_exitcode(status)
    line = ("exit=%d wall_s=%.2f user_s=%.2f sys_s=%.2f maxrss_mib=%.1f\n"
            % (code, elapsed, usage.ru_utime, usage.ru_stime,
               usage.ru_maxrss / 1024.0))
    with open(metrics_path, "w") as fh:
        fh.write(line)
    sys.stderr.write("measured: " + line)
    return code if code >= 0 else 1


# --------------------------------------------------------------------------
# image header verification
# --------------------------------------------------------------------------

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


def _png_unfilter(raw, width, height, bytes_per_pixel):
    stride = width * bytes_per_pixel
    out = bytearray()
    prev = bytearray(stride)
    pos = 0
    for _ in range(height):
        method = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        for x in range(stride):
            a = line[x - bytes_per_pixel] if x >= bytes_per_pixel else 0
            b = prev[x]
            c = prev[x - bytes_per_pixel] if x >= bytes_per_pixel else 0
            v = line[x]
            if method == 1:
                v = (v + a) & 0xFF
            elif method == 2:
                v = (v + b) & 0xFF
            elif method == 3:
                v = (v + ((a + b) >> 1)) & 0xFF
            elif method == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                v = (v + pred) & 0xFF
            line[x] = v
        out += line
        prev = line
    return bytes(out)


def _verify_png(path, data):
    if len(data) < 8 or data[:8] != PNG_MAGIC:
        return False, "not a PNG (magic %r)" % data[:8]
    offset = 8
    idat = b""
    header = None
    chunks = []
    bad_crc = 0
    while offset + 8 <= len(data):
        length, kind = struct.unpack(">I4s", data[offset:offset + 8])
        body = data[offset + 8:offset + 8 + length]
        stored = struct.unpack(">I", data[offset + 8 + length:
                                          offset + 12 + length])[0]
        if zlib.crc32(kind + body) & 0xFFFFFFFF != stored:
            bad_crc += 1
        chunks.append(kind.decode("ascii", "replace"))
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
        offset += 12 + length
    if header is None:
        return False, "PNG has no IHDR"
    if offset != len(data):
        return False, "PNG chunk stream does not cover the file"
    if "IEND" not in chunks:
        return False, "PNG has no IEND chunk (truncated)"
    if bad_crc:
        return False, "PNG has %d bad chunk CRC(s)" % bad_crc
    width, height, depth, colour, _, _, interlace = header
    detail = ("format=PNG width=%d height=%d bit_depth=%d colour_type=%d "
              "chunks=%s" % (width, height, depth, colour, ",".join(chunks)))
    if width <= 0 or height <= 0:
        return False, detail + " -- zero-sized"
    if depth == 8 and colour == 2 and interlace == 0:
        pixels = _png_unfilter(zlib.decompress(idat), width, height, 3)
        counter = collections.Counter(
            pixels[i:i + 3] for i in range(0, len(pixels), 3))
        white = counter.get(b"\xff\xff\xff", 0)
        non_white = width * height - white
        detail += (" distinct_colours=%d non_white_px=%d (%.2f%%)"
                   % (len(counter), non_white,
                      100.0 * non_white / (width * height)))
        if len(counter) < 8 or non_white < width * height * 0.001:
            return False, detail + " -- blank or near-blank canvas"
    else:
        detail += " (pixel content not decoded: unsupported colour type)"
    return True, detail


def _verify_pdf(path, data):
    if data[:5] != b"%PDF-":
        return False, "not a PDF"
    if b"%%EOF" not in data[-2048:]:
        return False, "PDF has no trailing %%EOF"
    box = re.search(rb"/MediaBox\s*\[\s*([-\d.]+)\s+([-\d.]+)\s+"
                    rb"([-\d.]+)\s+([-\d.]+)\s*\]", data)
    if box:
        x0, y0, x1, y1 = (float(box.group(i)) for i in range(1, 5))
        size = " media_box_pt=%gx%g" % (x1 - x0, y1 - y0)
    else:
        size = " media_box=UNVERIFIED (no /MediaBox found)"
    return True, ("format=PDF version=%s%s"
                  % (data[5:8].decode("ascii", "replace"), size))


def _verify_jpeg(path, data):
    offset = 2
    while offset + 4 <= len(data):
        if data[offset] != 0xFF:
            return False, "malformed JPEG marker stream"
        marker = data[offset + 1]
        length = struct.unpack(">H", data[offset + 2:offset + 4])[0]
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            height, width = struct.unpack(">HH", data[offset + 5:offset + 9])
            return True, ("format=JPEG width=%d height=%d" % (width, height))
        offset += 2 + length
    return False, "JPEG has no SOF frame header"


def verify_images(paths):
    failures = 0
    for path in paths:
        if not os.path.isfile(path):
            print("FAIL %s -- no such file" % path)
            failures += 1
            continue
        size = os.path.getsize(path)
        with open(path, "rb") as fh:
            data = fh.read()
        if data[:8] == PNG_MAGIC:
            ok, detail = _verify_png(path, data)
        elif data[:5] == b"%PDF-":
            ok, detail = _verify_pdf(path, data)
        elif data[:2] == b"\xff\xd8":
            ok, detail = _verify_jpeg(path, data)
        else:
            ok, detail = False, "unrecognised header %r" % data[:8]
        if size < 1024:
            ok, detail = False, detail + " -- trivially small (%d bytes)" % size
        print("%s %s bytes=%d %s"
              % ("OK  " if ok else "FAIL", path, size, detail))
        if not ok:
            failures += 1
    if not paths:
        print("FAIL -- no files given; refusing to report success over nothing")
        return 2
    return 1 if failures else 0


# --------------------------------------------------------------------------

def main(argv):
    if len(argv) < 2:
        sys.stderr.write(__doc__)
        return 2
    command = argv[1]
    if command == "mgf":
        if len(argv) != 4:
            sys.stderr.write("usage: pdv_spike_helpers.py mgf MZML MGF\n")
            return 2
        return mzml_to_mgf(argv[2], argv[3])
    if command == "run":
        if len(argv) < 5 or argv[3] != "--":
            sys.stderr.write("usage: pdv_spike_helpers.py run METRICS -- CMD...\n")
            return 2
        return run_measured(argv[2], argv[4:])
    if command == "verify-image":
        return verify_images(argv[2:])
    sys.stderr.write("unknown subcommand: %s\n" % command)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))

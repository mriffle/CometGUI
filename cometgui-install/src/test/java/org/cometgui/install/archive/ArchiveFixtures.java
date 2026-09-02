/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.cometgui.install.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.cometgui.domain.tools.ArtefactKind;

/**
 * Builds an archive of any container kind from one list of entries, so that an attack can be graded
 * over every kind rather than over the one that was easiest to write.
 *
 * <p>The tenth shape this phase catalogued is a check that is graded on one axis and not on the
 * others: a traversal test written only for zip leaves the same rule switchable off for tar, cpio,
 * {@code ar} and {@code xar} with nothing going red. The point of this class is that the caller
 * describes the attack once and {@link #build} produces it in every kind.
 *
 * <p>Everything is written by hand, at the byte level, including a zip's Unix external attributes
 * -- which {@code java.util.zip} cannot set and which are the only place a zip records that an
 * entry is a symbolic link.
 */
final class ArchiveFixtures {

    private ArchiveFixtures() {}

    /** The kinds that hold more than one entry, and are therefore graded over every attack. */
    static final List<ArtefactKind> MULTI_ENTRY_KINDS =
            List.of(
                    ArtefactKind.ZIP,
                    ArtefactKind.TAR_GZ,
                    ArtefactKind.DEB_PAYLOAD,
                    ArtefactKind.PKG_PAYLOAD);

    /** {@code S_IFMT}: the bits of a Unix mode that say what kind of thing an entry is. */
    static final int FILE_TYPE_MASK = 0170000;

    /** {@code S_IFREG | 0644}. */
    static final int MODE_FILE = 0100644;

    /** {@code S_IFDIR | 0755}. */
    static final int MODE_DIRECTORY = 0040755;

    /** {@code S_IFLNK | 0777}. */
    static final int MODE_SYMLINK = 0120777;

    /** {@code S_IFIFO | 0644}: a named pipe, which no extraction here ever creates. */
    static final int MODE_FIFO = 0010644;

    /**
     * One entry to put into a fixture.
     *
     * @param name the entry's name, exactly as it should appear in the container
     * @param content the bytes, empty for a directory or a link
     * @param mode the Unix mode, whose file-type bits decide what the entry is
     * @param linkTarget a symbolic link's target, or the empty string
     * @param declaredSizeOverride a length to write into the header instead of the real one, or -1
     *     to write the truth -- the only way to build an archive that lies about a size
     */
    record Entry(
            String name, byte[] content, int mode, String linkTarget, long declaredSizeOverride) {

        /** Copies on the way in, as the domain's records do. */
        Entry {
            content = content.clone();
        }

        /** Copies on the way out, so that a fixture cannot be altered by the code under test. */
        @Override
        public byte[] content() {
            return content.clone();
        }

        static Entry file(String name, String text) {
            return file(name, text.getBytes(StandardCharsets.UTF_8));
        }

        static Entry file(String name, byte[] content) {
            return new Entry(name, content, MODE_FILE, "", -1L);
        }

        static Entry directory(String name) {
            return new Entry(name, new byte[0], MODE_DIRECTORY, "", -1L);
        }

        static Entry symlink(String name, String target) {
            return new Entry(name, new byte[0], MODE_SYMLINK, target, -1L);
        }

        static Entry fifo(String name) {
            return new Entry(name, new byte[0], MODE_FIFO, "", -1L);
        }

        Entry declaringSize(long declared) {
            return new Entry(name, content, mode, linkTarget, declared);
        }

        boolean isDirectory() {
            return (mode & FILE_TYPE_MASK) == (MODE_DIRECTORY & FILE_TYPE_MASK);
        }

        boolean isSymlink() {
            return (mode & FILE_TYPE_MASK) == (MODE_SYMLINK & FILE_TYPE_MASK);
        }

        /** The bytes the container carries for this entry: a link's target is its content. */
        byte[] payload() {
            return isSymlink() ? linkTarget.getBytes(StandardCharsets.UTF_8) : content;
        }

        long declaredSize() {
            return declaredSizeOverride >= 0 ? declaredSizeOverride : payload().length;
        }
    }

    /**
     * Writes the same entries as an archive of the given kind.
     *
     * @param kind which container to build
     * @param directory where to write it
     * @param fileName the file to write
     * @param entries what to put in it
     * @return the archive
     * @throws IOException if it cannot be written
     */
    static Path build(ArtefactKind kind, Path directory, String fileName, List<Entry> entries)
            throws IOException {
        Path target = directory.resolve(fileName);
        byte[] bytes =
                switch (kind) {
                    case ZIP, JAR -> zipBytes(entries);
                    case TAR_GZ -> gzip(tarBytes(entries));
                    case DEB_PAYLOAD -> debBytes(entries);
                    case PKG_PAYLOAD -> pkgBytes(entries);
                    case BARE_EXECUTABLE ->
                            throw new IllegalArgumentException(
                                    "BARE_EXECUTABLE is a single file, not a container");
                };
        Files.write(target, bytes);
        return target;
    }

    // ---------------------------------------------------------------- zip --

    /**
     * A zip written by hand, so that Unix external attributes can be set.
     *
     * @param entries the entries
     * @return the archive bytes
     * @throws IOException if deflating fails
     */
    static byte[] zipBytes(List<Entry> entries) throws IOException {
        return zipBytes(entries, true);
    }

    /**
     * A zip written by hand, optionally without the trailing slash a directory usually carries.
     *
     * <p>{@code java.util.zip} decides an entry is a directory from a trailing slash alone, so a
     * zip whose only claim is in its Unix mode is the shape a reader has to get right on its own.
     *
     * @param entries the entries
     * @param directorySlash whether a directory's name ends in {@code /}
     * @return the archive bytes
     * @throws IOException if deflating fails
     */
    static byte[] zipBytes(List<Entry> entries, boolean directorySlash) throws IOException {
        return zipBytes(entries, directorySlash, ENTRY_COMMENT);
    }

    /**
     * A zip whose central-directory records carry the comment the caller chooses.
     *
     * <p>The empty comment exists for one test: a record with no name, no extra field and no
     * comment makes the directory exactly forty-six bytes long, which is the only input that tells
     * "is there room for another record" apart from "is there room for this one".
     *
     * @param entries the entries
     * @param directorySlash whether a directory's name ends in {@code /}
     * @param comment the comment on every record, possibly empty
     * @return the archive bytes
     * @throws IOException if deflating fails
     */
    static byte[] zipBytes(List<Entry> entries, boolean directorySlash, String comment)
            throws IOException {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        List<int[]> offsets = new ArrayList<>();
        List<byte[]> compressed = new ArrayList<>();
        for (Entry entry : entries) {
            byte[] raw = entry.payload();
            byte[] deflated = deflate(raw);
            compressed.add(deflated);
            offsets.add(new int[] {file.size()});
            byte[] name = nameOf(entry, directorySlash);
            file.write(localHeader(name, raw, deflated));
            file.write(name);
            file.write(deflated);
        }
        int directoryOffset = file.size();
        ByteArrayOutputStream directory = new ByteArrayOutputStream();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            byte[] name = nameOf(entry, directorySlash);
            directory.write(
                    centralHeaderWithComment(
                            name,
                            entry.payload(),
                            compressed.get(index),
                            entry,
                            offsets.get(index)[0],
                            comment));
            directory.write(name);
            directory.write(comment.getBytes(StandardCharsets.UTF_8));
        }
        file.write(directory.toByteArray());
        file.write(endRecord(entries.size(), directory.size(), directoryOffset));
        return file.toByteArray();
    }

    private static byte[] nameOf(Entry entry, boolean directorySlash) {
        String name =
                directorySlash && entry.isDirectory() && !entry.name().endsWith("/")
                        ? entry.name() + "/"
                        : entry.name();
        return name.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] localHeader(byte[] name, byte[] raw, byte[] deflated) {
        ByteBuffer header = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0x04034b50);
        header.putShort((short) 20);
        header.putShort((short) 0);
        header.putShort((short) 8);
        header.putShort((short) 0);
        header.putShort((short) 0);
        header.putInt((int) crc32(raw));
        header.putInt(deflated.length);
        header.putInt(raw.length);
        header.putShort((short) name.length);
        header.putShort((short) 0);
        return header.array();
    }

    /**
     * A comment on every central-directory record.
     *
     * <p>Real archivers write these, and until one appeared here the walk from one record to the
     * next could drop the comment length without any test noticing -- every record was 46 bytes
     * plus a name and nothing else, so three of the four numbers being added were zero.
     */
    static final String ENTRY_COMMENT = "written by CometGUI's test fixtures";

    private static byte[] centralHeaderWithComment(
            byte[] name,
            byte[] raw,
            byte[] deflated,
            Entry entry,
            int localOffset,
            String comment) {
        ByteBuffer header = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0x02014b50);
        header.putShort((short) (3 << 8 | 20));
        header.putShort((short) 20);
        header.putShort((short) 0);
        header.putShort((short) 8);
        header.putShort((short) 0);
        header.putShort((short) 0);
        header.putInt((int) crc32(raw));
        header.putInt(deflated.length);
        header.putInt((int) entry.declaredSize());
        header.putShort((short) name.length);
        header.putShort((short) 0);
        header.putShort((short) comment.length());
        header.putShort((short) 0);
        header.putShort((short) 0);
        header.putInt(entry.mode() << 16);
        header.putInt(localOffset);
        return header.array();
    }

    /**
     * A Zip64 archive: the sentinel values in the classic end record, a Zip64 locator and record
     * behind it, and a Zip64 extra field carrying each entry's real uncompressed size.
     *
     * <p>No artefact this product installs is Zip64 today, which is exactly why the fixture exists:
     * a reader that has never met the format it claims to support has not been shown to support it.
     *
     * @param entries the entries
     * @return the archive bytes
     * @throws IOException if deflating fails
     */
    static byte[] zip64Bytes(List<Entry> entries) throws IOException {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        List<byte[]> compressed = new ArrayList<>();
        for (Entry entry : entries) {
            byte[] raw = entry.payload();
            byte[] deflated = deflate(raw);
            compressed.add(deflated);
            offsets.add(file.size());
            byte[] name = nameOf(entry, true);
            file.write(localHeader(name, raw, deflated));
            file.write(name);
            file.write(deflated);
        }
        int directoryOffset = file.size();
        ByteArrayOutputStream directory = new ByteArrayOutputStream();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            byte[] name = nameOf(entry, true);
            byte[] header =
                    centralHeaderWithComment(
                            name,
                            entry.payload(),
                            compressed.get(index),
                            entry,
                            offsets.get(index),
                            ENTRY_COMMENT);
            ByteBuffer patched = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            patched.putInt(24, -1);
            patched.putShort(30, (short) 12);
            directory.write(header);
            /* Name, then extra field, then comment: the order a central-directory record uses. */
            directory.write(name);
            ByteBuffer extra = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            extra.putShort((short) 0x0001);
            extra.putShort((short) 8);
            extra.putLong(entry.declaredSize());
            directory.write(extra.array());
            directory.write(ENTRY_COMMENT.getBytes(StandardCharsets.UTF_8));
        }
        byte[] directoryBytes = directory.toByteArray();
        file.write(directoryBytes);
        int zip64RecordOffset = file.size();
        ByteBuffer record = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        record.putInt(0x06064b50);
        record.putLong(44L);
        record.putShort((short) 45);
        record.putShort((short) 45);
        record.putInt(0);
        record.putInt(0);
        record.putLong(entries.size());
        record.putLong(entries.size());
        record.putLong(directoryBytes.length);
        record.putLong(directoryOffset);
        file.write(record.array());
        ByteBuffer locator = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        locator.putInt(0x07064b50);
        locator.putInt(0);
        locator.putLong(zip64RecordOffset);
        locator.putInt(1);
        file.write(locator.array());
        ByteBuffer end = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        end.putInt(0x06054b50);
        end.putShort((short) 0);
        end.putShort((short) 0);
        end.putShort((short) 0xFFFF);
        end.putShort((short) 0xFFFF);
        end.putInt(-1);
        end.putInt(-1);
        end.putShort((short) 0);
        file.write(end.array());
        return file.toByteArray();
    }

    private static byte[] endRecord(int count, int directorySize, int directoryOffset) {
        ByteBuffer record = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        record.putInt(0x06054b50);
        record.putShort((short) 0);
        record.putShort((short) 0);
        record.putShort((short) count);
        record.putShort((short) count);
        record.putInt(directorySize);
        record.putInt(directoryOffset);
        record.putShort((short) 0);
        return record.array();
    }

    private static long crc32(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static byte[] deflate(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try (DeflaterOutputStream stream = new DeflaterOutputStream(out, deflater)) {
            stream.write(raw);
        } finally {
            deflater.end();
        }
        return out.toByteArray();
    }

    /**
     * A zip written by {@code java.util.zip}, whose central directory records a DOS host and no
     * external attributes -- the ordinary shape, and the one PDV and the Percolator zips have.
     *
     * @param directory where to write it
     * @param fileName the file to write
     * @param contents entry name to content
     * @return the archive
     * @throws IOException if it cannot be written
     */
    static Path plainZip(Path directory, String fileName, Map<String, String> contents)
            throws IOException {
        Path target = directory.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (Map.Entry<String, String> entry : contents.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return target;
    }

    // ---------------------------------------------------------------- tar --

    /**
     * A ustar stream.
     *
     * @param entries the entries
     * @return the archive bytes
     * @throws IOException if it cannot be written
     */
    static byte[] tarBytes(List<Entry> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            byte[] payload = entry.isSymlink() ? new byte[0] : entry.content();
            long declared = entry.isSymlink() ? 0L : entry.declaredSize();
            out.write(tarHeader(entry, declared));
            out.write(payload);
            out.write(new byte[(512 - (payload.length % 512)) % 512]);
        }
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    private static byte[] tarHeader(Entry entry, long declaredSize) {
        byte[] header = new byte[512];
        String name =
                entry.isDirectory() && !entry.name().endsWith("/")
                        ? entry.name() + "/"
                        : entry.name();
        put(header, 0, name, 100);
        put(header, 100, octal(entry.mode() & 07777, 7), 8);
        put(header, 108, octal(0, 7), 8);
        put(header, 116, octal(0, 7), 8);
        put(header, 124, octal(declaredSize, 11), 12);
        put(header, 136, octal(0, 11), 12);
        header[156] = (byte) typeFlag(entry);
        put(header, 157, entry.linkTarget(), 100);
        put(header, 257, "ustar", 6);
        header[263] = '0';
        header[264] = '0';
        for (int index = 148; index < 156; index++) {
            header[index] = ' ';
        }
        long sum = 0;
        for (byte value : header) {
            sum += value & 0xFF;
        }
        put(header, 148, octal(sum, 6), 7);
        header[154] = 0;
        header[155] = ' ';
        return header;
    }

    private static char typeFlag(Entry entry) {
        if (entry.isDirectory()) {
            return '5';
        }
        if (entry.isSymlink()) {
            return '2';
        }
        if ((entry.mode() & FILE_TYPE_MASK) == (MODE_FIFO & FILE_TYPE_MASK)) {
            return '6';
        }
        return '0';
    }

    private static String octal(long value, int width) {
        return String.format(Locale.ROOT, "%0" + width + "o", value);
    }

    private static void put(byte[] target, int offset, String text, int width) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, target, offset, Math.min(bytes.length, width));
    }

    /**
     * A tar stream whose entries carry type flags and names the caller chooses, for the GNU and
     * ustar shapes a fixture built from {@link Entry} cannot express.
     *
     * @param headers each entry as {@code {name, typeFlag, linkName, prefix}}
     * @param bodies each entry's content, in the same order
     * @return the archive bytes
     * @throws IOException if it cannot be written
     */
    static byte[] tarBytesRaw(List<String[]> headers, List<byte[]> bodies) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int index = 0; index < headers.size(); index++) {
            String[] header = headers.get(index);
            byte[] body = bodies.get(index);
            out.write(
                    rawTarHeader(
                            header[0], header[1].charAt(0), header[2], header[3], body.length));
            out.write(body);
            out.write(new byte[(512 - (body.length % 512)) % 512]);
        }
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    private static byte[] rawTarHeader(
            String name, char typeFlag, String linkName, String prefix, long size) {
        byte[] header = new byte[512];
        put(header, 0, name, 100);
        put(header, 100, octal(0644, 7), 8);
        put(header, 124, octal(size, 11), 12);
        put(header, 136, octal(0, 11), 12);
        header[156] = (byte) typeFlag;
        put(header, 157, linkName, 100);
        put(header, 257, "ustar", 6);
        header[263] = '0';
        header[264] = '0';
        put(header, 345, prefix, 155);
        for (int index = 148; index < 156; index++) {
            header[index] = ' ';
        }
        long sum = 0;
        for (byte value : header) {
            sum += value & 0xFF;
        }
        put(header, 148, octal(sum, 6), 7);
        header[154] = 0;
        header[155] = ' ';
        return header;
    }

    // --------------------------------------------------------------- cpio --

    /**
     * An old-ASCII ({@code 070707}) cpio stream, the flavour upstream's {@code .pkg} uses.
     *
     * @param entries the entries
     * @return the archive bytes
     * @throws IOException if it cannot be written
     */
    static byte[] cpioBytes(List<Entry> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            byte[] payload = entry.payload();
            writeCpioHeader(out, entry.name(), entry.mode(), entry.declaredSize());
            out.write(payload);
        }
        writeCpioHeader(out, "TRAILER!!!", 0, 0);
        return out.toByteArray();
    }

    private static void writeCpioHeader(OutputStream out, String name, int mode, long size)
            throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder("070707");
        header.append(octal(0, 6)).append(octal(0, 6)).append(octal(mode, 6));
        header.append(octal(0, 6)).append(octal(0, 6)).append(octal(1, 6));
        header.append(octal(0, 6)).append(octal(0, 11));
        header.append(octal(nameBytes.length + 1L, 6)).append(octal(size, 11));
        out.write(header.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);
        out.write(0);
    }

    /**
     * An SVR4 ({@code 070701}) or SVR4-with-checksum ({@code 070702}) cpio stream, the flavours a
     * repackaging upstream could switch to.
     *
     * @param entries the entries
     * @param magic the six-character magic number to write
     * @return the archive bytes
     * @throws IOException if it cannot be written
     */
    static byte[] cpioNewcBytes(List<Entry> entries, String magic) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            writeNewcEntry(out, magic, entry.name(), entry.mode(), entry.payload());
        }
        writeNewcEntry(out, magic, "TRAILER!!!", 0, new byte[0]);
        return out.toByteArray();
    }

    private static void writeNewcEntry(
            OutputStream out, String magic, String name, int mode, byte[] body) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder(magic);
        header.append(hex(0)).append(hex(mode)).append(hex(0)).append(hex(0));
        header.append(hex(1)).append(hex(0)).append(hex(body.length));
        header.append(hex(0)).append(hex(0)).append(hex(0)).append(hex(0));
        header.append(hex(nameBytes.length + 1)).append(hex(0));
        out.write(header.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);
        out.write(0);
        int namePadding = (int) ((4 - ((110 + nameBytes.length + 1L) % 4)) % 4);
        out.write(new byte[namePadding]);
        out.write(body);
        out.write(new byte[(4 - (body.length % 4)) % 4]);
    }

    private static String hex(long value) {
        return String.format(Locale.ROOT, "%08x", value);
    }

    // ------------------------------------------------------ deb, pkg, gzip --

    /**
     * An {@code ar} archive holding {@code debian-binary}, {@code control.tar.gz} and {@code
     * data.tar.gz}, in that order.
     *
     * @param entries what the payload holds
     * @return the package bytes
     * @throws IOException if it cannot be written
     */
    static byte[] debBytes(List<Entry> entries) throws IOException {
        return debBytesWithPayload("data.tar.gz", gzip(tarBytes(entries)));
    }

    /**
     * An {@code ar} archive whose payload member is named and encoded by the caller.
     *
     * @param payloadName the third member's name
     * @param payload its bytes
     * @return the package bytes
     * @throws IOException if it cannot be written
     */
    static byte[] debBytesWithPayload(String payloadName, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        writeArMember(out, "debian-binary", "2.0\n".getBytes(StandardCharsets.US_ASCII));
        writeArMember(out, "control.tar.gz", gzip(tarBytes(List.of())));
        writeArMember(out, payloadName, payload);
        return out.toByteArray();
    }

    private static void writeArMember(OutputStream out, String name, byte[] body)
            throws IOException {
        StringBuilder header = new StringBuilder();
        header.append(pad(name + "/", 16));
        header.append(pad("0", 12));
        header.append(pad("0", 6));
        header.append(pad("0", 6));
        header.append(pad("100644", 8));
        header.append(pad(String.valueOf(body.length), 10));
        header.append("`\n");
        out.write(header.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        if (body.length % 2 == 1) {
            out.write('\n');
        }
    }

    private static String pad(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }

    /**
     * A {@code xar!} flat package whose one blob is a gzip-wrapped cpio archive.
     *
     * @param entries what the payload holds
     * @return the package bytes
     * @throws IOException if it cannot be written
     */
    static byte[] pkgBytes(List<Entry> entries) throws IOException {
        return pkgBytesWithPayload(gzip(cpioBytes(entries)), "application/octet-stream");
    }

    /**
     * A {@code xar!} package with a table of contents the caller writes.
     *
     * @param toc the table of contents XML
     * @param payload the heap bytes
     * @return the package bytes
     * @throws IOException if it cannot be written
     */
    static byte[] pkgBytesWithToc(String toc, byte[] payload) throws IOException {
        byte[] raw = toc.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = zlib(raw);
        ByteBuffer header = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN);
        header.put("xar!".getBytes(StandardCharsets.US_ASCII));
        header.putShort((short) 28);
        header.putShort((short) 1);
        header.putLong(compressed.length);
        header.putLong(raw.length);
        header.putInt(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.array());
        out.write(compressed);
        out.write(payload);
        return out.toByteArray();
    }

    /**
     * A {@code xar!} flat package whose blob and encoding the caller chooses.
     *
     * @param payload the heap bytes
     * @param encodingStyle the {@code style} attribute the table of contents declares
     * @return the package bytes
     * @throws IOException if it cannot be written
     */
    static byte[] pkgBytesWithPayload(byte[] payload, String encodingStyle) throws IOException {
        String toc =
                "<xar><toc><checksum style=\"none\"/><file id=\"1\"><name>Payload</name>"
                        + "<type>file</type><data><offset>0</offset><length>"
                        + payload.length
                        + "</length><size>"
                        + payload.length
                        + "</size><encoding style=\""
                        + encodingStyle
                        + "\"/></data></file></toc></xar>";
        return pkgBytesWithToc(toc, payload);
    }

    /**
     * Gzips bytes.
     *
     * @param raw the bytes
     * @return the gzip stream
     * @throws IOException if it cannot be written
     */
    static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
        }
        return out.toByteArray();
    }

    /**
     * Zlib-compresses bytes, which is what a {@code xar} table of contents is wrapped in.
     *
     * @param raw the bytes
     * @return the zlib stream
     * @throws IOException if it cannot be written
     */
    static byte[] zlib(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream stream = new DeflaterOutputStream(out)) {
            stream.write(raw);
        }
        return out.toByteArray();
    }

    /**
     * A block of highly compressible bytes, for the decompression-bomb fixtures.
     *
     * @param length how many bytes
     * @return the block
     */
    static byte[] compressible(int length) {
        return new byte[length];
    }
}

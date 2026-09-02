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

package org.cometgui.provenance.hashing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;

/**
 * The {@link HashService} the provenance record is built on: MD5 and SHA-256 computed together, in
 * one pass, over a file of any size.
 *
 * <p><strong>One open, one pass, one buffer.</strong> {@code R-PROV-01} forbids reading a file into
 * memory to hash it and {@code R-PROV-03} requires both digests from a single pass, and those two
 * rules are the whole design. The file is opened <em>once</em>; each chunk is read into one
 * reusable {@code byte[]} of {@link #BUFFER_SIZE} bytes; and <em>both</em> digests are updated from
 * that chunk before the next read overwrites it. Heap use is therefore constant in the size of the
 * file -- a 2 GB spectrum file costs the same {@value #BUFFER_SIZE} bytes as a 2 kB parameter file
 * -- and the disk is read once, not twice. Hashing the same file twice, once per algorithm, would
 * double the I/O on exactly the files where I/O dominates.
 *
 * <p>Both halves of that promise are observable, and both are asserted: {@link FileOpener} makes
 * the <em>open</em> countable and {@link #hash(InputStream)} makes the <em>reads</em> countable.
 * Neither on its own is enough. A version of this class that opened the file twice and threw the
 * first pass away would produce perfectly correct digests and would satisfy every assertion that
 * could be made about a single stream; the count of opens is the only thing that distinguishes it.
 *
 * <p><strong>Safe to share between threads.</strong> The class holds exactly one field, a {@code
 * final} reference to a stateless {@link FileOpener}; the digests and the buffer are locals of the
 * call that created them, so two threads hashing two files through the same instance share nothing
 * mutable. One instance can be created at start-up and handed to every stage. (A {@link
 * MessageDigest} field would be the natural-looking mistake here, and it would be a data race:
 * {@code MessageDigest} is mutable and is not thread-safe. So would a shared buffer.)
 *
 * <p><strong>Both digests, never one.</strong> {@code R-SEC-02} makes SHA-256 the trust mechanism
 * and MD5 a provenance record only. The return type, {@link FileHashes}, cannot represent a file
 * hashed only one way, so nothing downstream has to remember that rule.
 *
 * @see HashService
 * @see FileHashes
 */
public final class StreamingHashService implements HashService {

    /**
     * Bytes read, and digested, per chunk: 256 KiB.
     *
     * <p>Four properties were wanted from this number, and 256 KiB is the smallest that has all
     * four.
     *
     * <ul>
     *   <li><b>It is a multiple of 64.</b> MD5 and SHA-256 both compress in 64-byte blocks, so a
     *       chunk that is a multiple of 64 never leaves a digest holding a partial block between
     *       updates: every {@code update} call consumes whole blocks and returns.
     *   <li><b>Read syscalls are noise.</b> A 2 GB file is 8192 reads. At any plausible per-read
     *       overhead that is far below the cost of digesting the same bytes twice, so a larger
     *       buffer buys nothing measurable.
     *   <li><b>A chunk stays in cache between the read and the digests.</b> The bytes are written
     *       by the read and then read twice, once per algorithm. 256 KiB fits inside the L2 cache
     *       of every CPU this product supports, so the second and third touches are cache hits. A
     *       multi-megabyte buffer would push the chunk back out to main memory and make the digests
     *       slower, not faster.
     *   <li><b>The heap cost is trivial even when every stage hashes at once.</b> One buffer per
     *       in-flight call, 256 KiB each, and nothing retained afterwards.
     * </ul>
     */
    public static final int BUFFER_SIZE = 1 << 18;

    /** Algorithm name of the digest recorded for provenance only ({@code R-SEC-02}). */
    private static final String MD5_ALGORITHM = "MD5";

    /** Algorithm name of the digest verification is allowed to trust ({@code R-SEC-02}). */
    private static final String SHA_256_ALGORITHM = "SHA-256";

    /**
     * Lower-case hexadecimal, with no locale anywhere in it.
     *
     * <p>{@link HexFormat} formats from a fixed ASCII digit alphabet, so its output cannot vary
     * with the default locale. That matters: a checksum rendered through {@code String.format} or
     * through {@code toUpperCase()} on a host running under a Turkish or Arabic-Indic locale can
     * differ from the same checksum rendered in London, and a provenance record whose digest
     * depends on the machine that wrote it is not a provenance record. {@code HexFormat} is
     * immutable and thread-safe, so one shared instance is correct here.
     */
    private static final HexFormat LOWERCASE_HEX = HexFormat.of();

    /**
     * How a path becomes a stream of bytes.
     *
     * <p>This exists for the same reason {@link StreamingHashService#hash(InputStream)} does, and
     * covers the half of the promise that one cannot. {@code R-PROV-01} and {@code R-PROV-03} are
     * claims about <em>how the bytes are fetched</em>, and correct digests cannot distinguish a
     * single pass from two: an implementation that opened the file, hashed it, discarded the result
     * and did it all again would return exactly the right answer, satisfy every assertion that can
     * be made about one stream, and double the I/O on the multi-gigabyte spectrum files this class
     * exists for. The only thing that tells the two apart is the number of times the file is opened
     * -- so opening goes through a seam a test can count, and production supplies {@code
     * Files::newInputStream}.
     *
     * <p>It is package-private, and it is not a configuration point: nothing outside this package
     * may substitute a different way of reading the files whose checksums go into a provenance
     * record.
     */
    @FunctionalInterface
    interface FileOpener {

        /**
         * Opens a regular file for reading.
         *
         * @param path the file to open
         * @return a stream positioned at its first byte, which the caller closes
         * @throws IOException if the file cannot be opened
         */
        InputStream open(Path path) throws IOException;
    }

    /** Opens the file; {@code Files::newInputStream} in production. Stateless, and never null. */
    private final FileOpener opener;

    /**
     * Creates a hasher that reads files from the default file system.
     *
     * <p>Instances hold no mutable state; creating one per call and sharing one across the whole
     * application are equally correct.
     */
    public StreamingHashService() {
        this(Files::newInputStream);
    }

    /**
     * Creates a hasher that opens files through a given opener.
     *
     * <p>For tests, which is why it is package-private: the count of opens is the evidence for the
     * single-pass rule, and evidence that cannot be gathered is not evidence. The opener must be
     * stateless for the thread-safety promise in the class Javadoc to hold; the production one is.
     *
     * @param opener how a path becomes a stream
     * @throws NullPointerException if {@code opener} is {@code null}
     */
    StreamingHashService(FileOpener opener) {
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /**
     * Hashes a regular file, reading it once.
     *
     * @param path the file to hash
     * @return its MD5 and SHA-256 digests, lower-case hexadecimal
     * @throws IOException if the file cannot be read; a path that does not exist surfaces as {@link
     *     java.nio.file.NoSuchFileException}, and a path that is a directory as a plain {@code
     *     IOException} naming the path
     * @throws NullPointerException if {@code path} is {@code null}
     */
    @Override
    public FileHashes hash(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            // Opening a directory does fail on every platform this product supports, but with the
            // operating system's own text -- "Is a directory" on Linux, something else and in
            // another language elsewhere. A caller that hands us a directory has made a programming
            // error and deserves to be told so in words this project controls and can test.
            throw new IOException("Cannot hash a directory, only a regular file: " + path);
        }
        // Exactly one open, and its result goes straight into the one pass.  Anything that
        // opened the file again -- a retry, a "let me just check the size first", a second
        // algorithm -- would be the defect R-PROV-01 forbids, and the opener counts opens.
        return hash(opener.open(path));
    }

    /**
     * Hashes everything remaining in a stream and closes it.
     *
     * <p>This overload is package-private, and it exists to be <em>observed</em>. The single-pass,
     * bounded-buffer promise in {@code R-PROV-01} and {@code R-PROV-03} is a claim about how the
     * bytes are fetched, and no assertion made against a {@link Path} can see that: a test can only
     * check that the digests came out right, which a wasteful two-pass implementation would also
     * manage. Given a stream, a test can wrap one that records every {@code read(byte[], int, int)}
     * -- how many, at what offset, of what length -- and every {@code close}, and then assert the
     * counts. That is the difference between documenting the property and proving it.
     *
     * <p>The stream is closed before this method returns, including when a read throws, because
     * {@link #hash(Path)} has no other opportunity to close the stream it opened.
     *
     * @param stream the bytes to hash; consumed to its end and then closed
     * @return the MD5 and SHA-256 digests of everything the stream yielded
     * @throws IOException if the stream cannot be read or cannot be closed
     * @throws NullPointerException if {@code stream} is {@code null}
     */
    FileHashes hash(InputStream stream) throws IOException {
        Objects.requireNonNull(stream, "stream");
        MessageDigest md5 = newDigest(MD5_ALGORITHM);
        MessageDigest sha256 = newDigest(SHA_256_ALGORITHM);
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream owned = stream) {
            int read;
            while ((read = owned.read(buffer, 0, buffer.length)) != -1) {
                // Both digests, from the same bytes, before the next read overwrites them. The
                // length is the count this read actually delivered, never buffer.length: the last
                // chunk of a file is short, and update(buffer) would digest the stale tail of the
                // previous chunk along with it.
                md5.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
            }
        }
        return new FileHashes(
                LOWERCASE_HEX.formatHex(md5.digest()), LOWERCASE_HEX.formatHex(sha256.digest()));
    }

    /**
     * Creates a digest, turning the impossible checked exception into an unchecked one.
     *
     * <p>Every Java SE implementation is required to provide MD5 and SHA-256, so with the constants
     * this class passes, {@code NoSuchAlgorithmException} cannot happen. Declaring it on {@link
     * #hash(Path)} would push a condition that cannot arise onto every caller in the product; a JVM
     * that really lacked SHA-256 could not verify a download either, and this is the right message
     * to fail with.
     *
     * <p>Package-private and taking the name as an argument rather than reading the constants
     * directly, so that a test can reach the failure branch with an algorithm that genuinely does
     * not exist.
     *
     * @param algorithm the standard algorithm name
     * @return a fresh, unshared digest
     * @throws IllegalStateException if this JVM has no such algorithm
     */
    static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "This JVM provides no " + algorithm + " digest, which Java SE requires", e);
        }
    }
}

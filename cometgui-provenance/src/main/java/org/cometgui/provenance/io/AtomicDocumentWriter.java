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

package org.cometgui.provenance.io;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Writes a file that no reader can ever see half-written: the bytes go to a temporary file beside
 * the target, are forced to the storage device, and only then are renamed over it.
 *
 * <p>{@code R-PROV-05} requires that "finalisation shall be atomic (write-temp-then-rename)", and
 * exit gate item 5 of phase 04 states the consequence: an interrupted finalise never leaves a
 * truncated {@code provenance.json}. This class is where that is true, once, for the manifest, the
 * event log and everything else a run must be able to re-read after a crash. The alternative --
 * every caller opening the target and writing into it -- fails in a way that is invisible until it
 * matters: the file is correct on every successful run, and on the one run that is interrupted it
 * is a valid path to an invalid document, which is worse than no document at all.
 *
 * <p><strong>The sequence, and why every step of it is load-bearing.</strong>
 *
 * <ol>
 *   <li><b>A temporary file in the target's own directory.</b> Not {@code java.io.tmpdir}: a rename
 *       across filesystems cannot be atomic and degrades to copy-then-delete, which is the
 *       half-written state this class exists to prevent. The system temporary directory is very
 *       often a different filesystem -- {@code /tmp} on {@code tmpfs} is the common case -- so a
 *       writer that used it would look correct in every test run on one machine and produce torn
 *       files on another.
 *   <li><b>The content is written, then flushed, then forced.</b> {@link Durability#syncFile} runs
 *       while the temporary file is still open. Data that is only in the page cache is not on the
 *       device, and a power cut between the rename and the eventual writeback leaves the directory
 *       entry pointing at a file of zeroes.
 *   <li><b>The rename happens next, and only next.</b> A sync after the rename protects nothing: by
 *       then the operation it was meant to make durable is already done. This ordering is the
 *       property {@link Durability} exists to make observable, because no inspection of the
 *       resulting file can distinguish a correct order from a wrong one.
 *   <li><b>The directory is forced afterwards, best effort.</b> Forcing the file makes the contents
 *       durable; forcing the directory makes the <em>name</em> durable. This step, alone among the
 *       four, is allowed to fail (see below).
 * </ol>
 *
 * <p><strong>The asymmetry: a failed directory sync is not a failed write.</strong> Opening a
 * directory as a channel is legal on Linux and macOS and illegal on Windows, where it throws. By
 * the time that call is made the document is complete on the device and the rename has already
 * happened, so every byte the caller asked to write is in place under the right name; the only
 * thing at risk is the durability of the rename across a power loss in the next few seconds.
 * Failing the write there would report a failure that did not occur, and would make this class
 * unusable on a platform the product supports. So the failure is swallowed -- and, because
 * swallowing a failure silently is how gates die, the package-private overload {@link #write(Path,
 * ContentWriter, Durability)} <em>returns</em> whether the directory was synced, so a test can
 * assert both outcomes instead of trusting this paragraph. The three earlier steps have no such
 * licence: any failure in them abandons the write.
 *
 * <p><strong>What a failure leaves behind: nothing.</strong> An {@code IOException}, a {@code
 * RuntimeException}, an {@code Error} or a thread interruption anywhere between opening the
 * temporary file and completing the rename removes the temporary file and rethrows. The target is
 * then in exactly the state it was in before the call -- absent if it was absent, byte-identical if
 * it existed -- because nothing has touched it. Interruption in particular is never swallowed: a
 * {@link java.nio.channels.FileChannel} is interruptible, so an interrupt during the write closes
 * the channel and surfaces as {@link java.nio.channels.ClosedByInterruptException} with the
 * thread's interrupt status still set, and this class rethrows it unchanged rather than converting
 * it into a plain failure that the caller cannot recognise as a cancellation.
 *
 * <p><strong>Concurrency.</strong> The class holds no state, and every call generates its own
 * temporary name, so any number of threads may write -- even to the same target -- without
 * interfering. Readers of the target are never blocked and never see an intermediate state: a
 * reader observes the whole old document or the whole new one.
 *
 * @see ContentWriter
 * @see Durability
 */
public final class AtomicDocumentWriter {

    /**
     * Bytes buffered between the {@link ContentWriter} and the file: 64 KiB.
     *
     * <p>The stream handed to a {@link ContentWriter} is backed by a {@link FileChannel}, on which
     * every unbuffered write is a system call. A serialiser writing a JSON manifest field by field
     * would make hundreds of thousands of them. One buffer, comfortably larger than any single
     * field and small enough to be irrelevant to the heap, removes that without the caller having
     * to know it is needed.
     */
    private static final int BUFFER_SIZE = 1 << 16;

    /**
     * What separates the target's name from the random part of a temporary file's name.
     *
     * <p>The temporary name begins with the target's own name so that a file left behind by a
     * crashed process is obviously related to the document it was going to become, and ends with a
     * {@link UUID} so that two threads, or two processes, writing the same target at the same
     * instant cannot choose the same temporary file.
     */
    private static final String TEMPORARY_INFIX = ".tmp-";

    /** The platform's real {@code fsync} and {@code rename}. Stateless, so one instance serves. */
    private static final Durability FILE_SYSTEM = new FileSystemDurability();

    /**
     * Never instantiated: this is a set of static operations over a path, with no state of its own
     * to carry.
     *
     * <p>It throws rather than being an empty private constructor so that the intent is enforced
     * for the one caller that can still reach it -- reflection -- instead of merely being implied.
     */
    private AtomicDocumentWriter() {
        throw new AssertionError(
                "AtomicDocumentWriter is a utility class and is never instantiated");
    }

    /**
     * Writes a document produced on demand, atomically.
     *
     * <p>The stream given to {@code content} is buffered and must not be closed; see {@link
     * ContentWriter}.
     *
     * @param target the file to create or replace
     * @param content produces the document's bytes; called exactly once
     * @throws IOException if the target is an existing directory, if its parent directory does not
     *     exist, if the document cannot be written, or if {@code content} throws -- in every case
     *     the target is left exactly as it was and no temporary file remains
     * @throws NullPointerException if {@code target} or {@code content} is {@code null}
     */
    public static void write(Path target, ContentWriter content) throws IOException {
        write(target, content, FILE_SYSTEM);
    }

    /**
     * Writes a document already held in memory, atomically.
     *
     * <p>The array is written as-is and is never retained.
     *
     * @param target the file to create or replace
     * @param content the exact bytes of the document
     * @throws IOException if the target is an existing directory, if its parent directory does not
     *     exist, or if the document cannot be written -- in every case the target is left exactly
     *     as it was and no temporary file remains
     * @throws NullPointerException if {@code target} or {@code content} is {@code null}
     */
    public static void write(Path target, byte[] content) throws IOException {
        Objects.requireNonNull(content, "content");
        write(target, out -> out.write(content));
    }

    /**
     * Writes text as a document, atomically, encoded as <strong>UTF-8</strong>.
     *
     * <p>The charset is fixed and is never the platform default. A provenance record whose bytes
     * depend on the machine that wrote it is not a provenance record: the same manifest written on
     * a Windows host under {@code windows-1252} and on a Linux host under UTF-8 would hash
     * differently and would render mojibake for every non-ASCII character in a file name.
     *
     * <p>The text is encoded in full before anything is written, so this overload holds the whole
     * document in memory. For a document large enough for that to matter, use {@link #write(Path,
     * ContentWriter)} and encode as you go.
     *
     * @param target the file to create or replace
     * @param content the text of the document
     * @throws IOException if the target is an existing directory, if its parent directory does not
     *     exist, or if the document cannot be written -- in every case the target is left exactly
     *     as it was and no temporary file remains
     * @throws NullPointerException if {@code target} or {@code content} is {@code null}
     */
    public static void write(Path target, CharSequence content) throws IOException {
        Objects.requireNonNull(content, "content");
        write(target, content.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a document atomically through a given {@link Durability}, reporting whether the
     * containing directory could be synced.
     *
     * <p>This is the whole implementation; the public overloads only choose the arguments. It is
     * package-private because the durability seam is not a configuration point (see {@link
     * Durability}), and it returns a value because the best-effort directory sync would otherwise
     * be a swallowed failure that no test could see.
     *
     * @param target the file to create or replace
     * @param content produces the document's bytes; called exactly once
     * @param durability the sync-move-sync mechanism, {@link FileSystemDurability} in production
     * @return {@code true} if the containing directory was synced, so that the rename itself is
     *     durable; {@code false} if the platform refused, which is the expected answer on Windows
     *     and does not mean the document failed to be written
     * @throws IOException if the target is an existing directory, if its parent directory does not
     *     exist, if the document cannot be written, or if {@code content} throws
     * @throws NullPointerException if any argument is {@code null}
     */
    static boolean write(Path target, ContentWriter content, Durability durability)
            throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(durability, "durability");
        if (Files.isDirectory(target)) {
            // Left to itself this surfaces from the rename, as the operating system's own text
            // about a path the caller never chose -- the temporary file. A caller that hands us a
            // directory has made a programming error and deserves to be told so in words this
            // project controls and can test.
            throw new IOException("Cannot write " + target + ": it is an existing directory");
        }
        // Absolute, so that the parent of a bare "provenance.json" is the working directory rather
        // than null, and so that the temporary file is provably beside the target.
        Path absoluteTarget = target.toAbsolutePath();
        // Cannot be null: the only absolute path without a parent is a root, and a root is a
        // directory, which the check above has already rejected.
        Path directory = Objects.requireNonNull(absoluteTarget.getParent(), "parent directory");
        if (!Files.isDirectory(directory)) {
            throw new NoSuchFileException(
                    directory.toString(), null, "the directory to write into does not exist");
        }
        Path temporary =
                directory.resolve(
                        absoluteTarget.getFileName() + TEMPORARY_INFIX + UUID.randomUUID());
        try {
            fill(temporary, content, durability);
            durability.moveIntoPlace(temporary, absoluteTarget);
        } catch (Throwable failure) {
            // Throwable, not IOException: a serialiser that throws IllegalStateException, or a
            // thread that is interrupted, or an OutOfMemoryError, must leave the target as
            // untouched as a failed write does. Precise rethrow keeps the declared throws clause
            // honest -- nothing here converts, wraps or swallows the failure.
            deleteTemporary(temporary, failure);
            throw failure;
        }
        return syncDirectoryIfPossible(directory, durability);
    }

    /**
     * Creates the temporary file, fills it from the content writer, and forces it to the device.
     *
     * <p>{@code CREATE_NEW} rather than {@code CREATE}: the name carries a {@link UUID}, so a name
     * that already exists means something is badly wrong and is worth failing on rather than
     * overwriting.
     *
     * <p>The buffered stream is deliberately flushed but not closed. Closing it would close the
     * channel underneath it, and a closed channel cannot be forced -- the sync has to happen while
     * the file is still open, and it has to happen before the rename.
     *
     * @param temporary the file to create
     * @param content produces the document's bytes
     * @param durability the sync mechanism
     * @throws IOException if the file cannot be created, written or forced
     */
    private static void fill(Path temporary, ContentWriter content, Durability durability)
            throws IOException {
        try (FileChannel channel =
                FileChannel.open(
                        temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            OutputStream out =
                    new BufferedOutputStream(Channels.newOutputStream(channel), BUFFER_SIZE);
            content.writeTo(out);
            out.flush();
            durability.syncFile(channel);
        }
    }

    /**
     * Removes the temporary file after a failed write, without ever replacing the failure that
     * caused it.
     *
     * <p>If the cleanup itself fails it is attached to the original failure as a suppressed
     * exception rather than thrown. The caller needs to know why the write failed; that the debris
     * could not be removed is a second-order fact, and letting it propagate would hide the first.
     *
     * @param temporary the temporary file, which may already be gone
     * @param failure the failure that ended the write, and the carrier for any cleanup failure
     */
    private static void deleteTemporary(Path temporary, Throwable failure) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException | RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * Forces the directory entry, treating a platform that will not allow it as a non-event.
     *
     * <p>See the class Javadoc for why this one step is best effort while the other three are not.
     *
     * @param directory the directory holding the target
     * @param durability the sync mechanism
     * @return {@code true} if the directory was synced, {@code false} if the platform refused
     */
    private static boolean syncDirectoryIfPossible(Path directory, Durability durability) {
        try {
            durability.syncDirectory(directory);
            return true;
        } catch (IOException directorySyncNotSupportedHere) {
            return false;
        }
    }
}

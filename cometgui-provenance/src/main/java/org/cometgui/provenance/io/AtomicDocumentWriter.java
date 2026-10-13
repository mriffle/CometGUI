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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
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
 *       resulting file can distinguish a correct order from a wrong one. A rename the platform
 *       <em>refuses</em> because another process holds the target is retried, briefly and
 *       boundedly; see below.
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
 * <p><strong>A refused rename is retried, briefly and boundedly, and nothing else is.</strong> On
 * Windows, replacing a file that another process has open can be refused -- {@code MoveFileEx}
 * fails with an access-denied or sharing-violation error -- and the processes that do that to a
 * freshly written document are exactly the ones this product does not control: a virus scanner
 * opening the file to scan it, a synchronisation client copying it. They let go within
 * milliseconds. So {@link Durability#moveIntoPlace} is attempted up to {@link #MOVE_ATTEMPTS}
 * times, with an exponentially growing pause between attempts capped at {@link
 * #LONGEST_RETRY_PAUSE_MILLIS}, for a bounded total wait of 2130 ms. Only the rename is retried,
 * and only when it throws {@link java.nio.file.AccessDeniedException} or an {@code IOException}
 * whose class is <em>exactly</em> {@link java.nio.file.FileSystemException}, which is how the JDK
 * reports {@code ERROR_SHARING_VIOLATION}; every other failure -- {@link
 * java.nio.file.AtomicMoveNotSupportedException}, {@link NoSuchFileException}, any other {@code
 * FileSystemException} subclass, any other {@code IOException}, a {@code RuntimeException}, an
 * {@code Error} -- describes something waiting cannot fix and ends the write on the first attempt.
 * The guarantee is unchanged by the retry: every attempt renames the same complete, forced
 * temporary file, and a refused rename leaves the target untouched, so a reader still sees the
 * whole old document or the whole new one. When the bound is exhausted the write fails exactly as
 * any other failed rename does, with the <em>last</em> refusal thrown and the earlier ones attached
 * to it as suppressed exceptions. The same code runs on every platform; on POSIX a refused rename
 * is a permanent permission problem, and it costs at most the bound before the same failure is
 * reported.
 *
 * <p><strong>What a failure leaves behind: nothing.</strong> An {@code IOException}, a {@code
 * RuntimeException}, an {@code Error} or a thread interruption anywhere between opening the
 * temporary file and completing the rename removes the temporary file and rethrows. The target is
 * then in exactly the state it was in before the call -- absent if it was absent, byte-identical if
 * it existed -- because nothing has touched it. Interruption in particular is never swallowed: a
 * {@link java.nio.channels.FileChannel} is interruptible, so an interrupt during the write closes
 * the channel and surfaces as {@link java.nio.channels.ClosedByInterruptException} with the
 * thread's interrupt status still set, and this class rethrows it unchanged rather than converting
 * it into a plain failure that the caller cannot recognise as a cancellation. An interrupt during a
 * pause between two rename attempts stops the retrying at once and surfaces as an {@link
 * java.io.InterruptedIOException} -- the {@code IOException} whose meaning is "an I/O operation was
 * interrupted", and the nearest equivalent of {@code ClosedByInterruptException} for a wait that
 * holds no channel -- again with the interrupt status still set. Its cause is the refusal that was
 * being retried, and the {@link InterruptedException} is attached as suppressed, so the caller
 * learns both that it was cancelled and what it was waiting out.
 *
 * <p><strong>Concurrency.</strong> The class holds no state, and every call generates its own
 * temporary name, so any number of threads may write -- even to the same target -- without
 * interfering. Readers of the target never see an intermediate state: a reader observes the whole
 * old document or the whole new one. On POSIX no reader can obstruct the rename. On Windows a
 * process that holds the target open without delete sharing can make the rename be refused, which
 * is what the bounded retry is for; this product's own readers are not such a process, because they
 * read the document whole and close it before parsing.
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

    /**
     * How many times the rename is attempted before a refusal is reported: 10, which is one attempt
     * and nine retries.
     *
     * <p>The number is chosen together with the two pause constants below so that the total wait is
     * about two seconds -- the pauses are 10, 20, 40, 80, 160, 320, 500, 500 and 500 ms, 2130 ms in
     * all. Two seconds is the bound Go's {@code cmd/go/internal/robustio} uses for exactly this
     * failure (it retries a Windows rename on {@code ERROR_ACCESS_DENIED} and {@code
     * ERROR_SHARING_VIOLATION} for up to two seconds). npm's {@code graceful-fs} retries the same
     * Windows refusals ({@code EACCES}, {@code EPERM}, {@code EBUSY}) far longer, up to a minute;
     * the shorter bound is chosen deliberately. The holders this exists for -- a virus scanner or a
     * synchronisation client looking at a file that was closed a moment ago -- release it in
     * milliseconds, so the early, short pauses absorb the common case and the capped ones absorb a
     * slow one. Longer would only delay the report of a refusal that is not going away: on POSIX a
     * refused rename is a permission problem, and the end of a run must not hang on it.
     */
    static final int MOVE_ATTEMPTS = 10;

    /**
     * The pause before the first retry: 10 ms. Each later pause doubles it, up to {@link
     * #LONGEST_RETRY_PAUSE_MILLIS}.
     *
     * <p>Short, because the typical holder has already let go by then, and a finalise that succeeds
     * on its second attempt should not cost the run a noticeable delay.
     */
    static final long FIRST_RETRY_PAUSE_MILLIS = 10L;

    /**
     * The longest single pause: 500 ms.
     *
     * <p>The cap keeps the doubling from spending the whole budget on one or two long waits, so a
     * holder that lets go at any moment within the bound is noticed within half a second of doing
     * so.
     */
    static final long LONGEST_RETRY_PAUSE_MILLIS = 500L;

    /** The platform's real {@code fsync} and {@code rename}. Stateless, so one instance serves. */
    private static final Durability FILE_SYSTEM = new FileSystemDurability();

    /** The real pause between two rename attempts: the thread sleeps. */
    private static final RetryWaiter SLEEP = Thread::sleep;

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
     * @throws java.io.InterruptedIOException if the thread is interrupted while waiting to retry a
     *     refused rename, with its interrupt status still set; see the class Javadoc
     * @throws IOException if the target is an existing directory, if its parent directory does not
     *     exist, if the document cannot be written, if {@code content} throws, or if replacing the
     *     target was refused on every one of {@code MOVE_ATTEMPTS} attempts -- in every case the
     *     target is left exactly as it was and no temporary file remains
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
        return write(target, content, durability, SLEEP);
    }

    /**
     * Writes a document atomically through a given {@link Durability}, pausing between refused
     * rename attempts through a given {@link RetryWaiter}.
     *
     * <p>Package-private for the same reason as the overload above: the waiter is a seam for tests,
     * so that the retry schedule is an assertion rather than a stopwatch reading, and never a
     * configuration point.
     *
     * @param target the file to create or replace
     * @param content produces the document's bytes; called exactly once
     * @param durability the sync-move-sync mechanism, {@link FileSystemDurability} in production
     * @param waiter how to pause between rename attempts, {@link Thread#sleep(long)} in production
     * @return {@code true} if the containing directory was synced; see {@link #write(Path,
     *     ContentWriter, Durability)}
     * @throws java.io.InterruptedIOException if the thread was interrupted while pausing between
     *     two rename attempts; the interrupt status is then still set
     * @throws IOException if the target is an existing directory, if its parent directory does not
     *     exist, if the document cannot be written, if {@code content} throws, or if the rename
     *     failed in a way that is not retried or was refused {@link #MOVE_ATTEMPTS} times
     * @throws NullPointerException if any argument is {@code null}
     */
    static boolean write(
            Path target, ContentWriter content, Durability durability, RetryWaiter waiter)
            throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(durability, "durability");
        Objects.requireNonNull(waiter, "waiter");
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
            moveIntoPlace(temporary, absoluteTarget, durability, waiter);
        } catch (Throwable failure) {
            // Throwable, not IOException: a serialiser that throws IllegalStateException, or a
            // thread that is interrupted, or an OutOfMemoryError, must leave the target as
            // untouched as a failed write does. Precise rethrow keeps the declared throws clause
            // honest -- nothing here converts, wraps or swallows the failure. (The one conversion
            // in this class, an interrupted retry pause, happens in moveIntoPlace and arrives here
            // already as the InterruptedIOException the caller is given.)
            deleteTemporary(temporary, failure);
            throw failure;
        }
        return syncDirectoryIfPossible(directory, durability);
    }

    /**
     * Renames the temporary file over the target, retrying a refusal that waiting can cure.
     *
     * <p>See the class Javadoc for the policy. Every attempt renames the same complete, forced
     * temporary file, and a refused attempt has not touched the target, so retrying can neither
     * expose a partial document nor lose the old one.
     *
     * @param temporary the fully written, synced and closed temporary file
     * @param target where it must appear
     * @param durability performs each rename attempt
     * @param waiter pauses between attempts
     * @throws java.io.InterruptedIOException if interrupted during a pause; the interrupt status is
     *     set again before it is thrown, its cause is the refusal being retried, and the {@link
     *     InterruptedException} is attached as suppressed
     * @throws IOException the first failure that is not retried, or the last refusal once {@link
     *     #MOVE_ATTEMPTS} attempts have been refused; earlier refusals are attached to it as
     *     suppressed exceptions
     */
    private static void moveIntoPlace(
            Path temporary, Path target, Durability durability, RetryWaiter waiter)
            throws IOException {
        List<IOException> refusals = new ArrayList<>();
        for (int attempt = 1; ; attempt++) {
            try {
                durability.moveIntoPlace(temporary, target);
                return;
            } catch (IOException failure) {
                if (!isTransientRefusal(failure) || attempt >= MOVE_ATTEMPTS) {
                    attachEarlier(failure, refusals);
                    throw failure;
                }
                try {
                    waiter.pause(pauseBeforeRetry(attempt));
                } catch (InterruptedException interrupted) {
                    // Thread.sleep clears the status as it throws. Leaving it cleared would tell
                    // every caller above us that nobody asked this thread to stop.
                    Thread.currentThread().interrupt();
                    attachEarlier(failure, refusals);
                    InterruptedIOException cancelled =
                            new InterruptedIOException(
                                    "Interrupted while waiting to retry replacing "
                                            + target
                                            + " after "
                                            + attempt
                                            + " refused attempt(s)");
                    cancelled.initCause(failure);
                    cancelled.addSuppressed(interrupted);
                    throw cancelled;
                }
                refusals.add(failure);
            }
        }
    }

    /**
     * Whether a failed rename is the kind of refusal that another process holding the file
     * produces, and that therefore may succeed if tried again shortly.
     *
     * <p>{@link AccessDeniedException} is how the JDK reports Windows' {@code ERROR_ACCESS_DENIED};
     * a bare {@link FileSystemException} -- the class itself, not a subclass -- is how it reports
     * {@code ERROR_SHARING_VIOLATION} and the other Windows errors it has no more specific type
     * for. The exact-class test is deliberate: every subclass of {@code FileSystemException} other
     * than {@code AccessDeniedException} names a specific condition -- the source is gone, the
     * target is a non-empty directory, an atomic move is impossible here -- that no amount of
     * waiting changes.
     *
     * @param failure the failure of one rename attempt
     * @return {@code true} if the rename should be attempted again
     */
    private static boolean isTransientRefusal(IOException failure) {
        return failure instanceof AccessDeniedException
                || failure.getClass() == FileSystemException.class;
    }

    /**
     * The pause before attempt {@code attempt + 1}: {@link #FIRST_RETRY_PAUSE_MILLIS}, doubled for
     * every earlier retry, never more than {@link #LONGEST_RETRY_PAUSE_MILLIS}.
     *
     * @param attempt the attempt that has just been refused, from 1
     * @return the pause in milliseconds
     */
    private static long pauseBeforeRetry(int attempt) {
        return Math.min(FIRST_RETRY_PAUSE_MILLIS << (attempt - 1), LONGEST_RETRY_PAUSE_MILLIS);
    }

    /**
     * Attaches the earlier refusals of one rename to the failure that ends it.
     *
     * <p>The caller is given the failure that actually ended the write, and can still see what came
     * before. One exception instance can be thrown more than once by a misbehaving implementation,
     * and {@link Throwable#addSuppressed} refuses to suppress an exception into itself with an
     * {@code IllegalArgumentException} that would replace the real failure, so an instance is never
     * attached to itself.
     *
     * @param failure the failure that ends, or may end, the rename
     * @param refusals the refusals of the earlier attempts, oldest first
     */
    private static void attachEarlier(IOException failure, List<IOException> refusals) {
        for (IOException earlier : refusals) {
            if (earlier != failure) {
                failure.addSuppressed(earlier);
            }
        }
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

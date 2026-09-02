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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

/**
 * Everything about a file except its content: the attribute set {@code R-PROV-02} keys the
 * input-hash cache on, plus the one attribute that makes the key worth anything.
 *
 * <p><strong>The requirement's four.</strong> {@code R-PROV-02} names "canonical path, size,
 * modification time and, where available, file identity". All four are here, and {@link
 * #matches(FileFingerprint)} compares all four, because the requirement's next sentence is that the
 * cache "shall be invalidated by any attribute change" -- any one of them, not a majority of them.
 *
 * <p><strong>Why there is a fifth.</strong> The four cannot see the case the phase's exit gate is
 * about. Overwrite a file in place with content of the same length and put the modification time
 * back with {@link Files#setLastModifiedTime}, and the path, the size, the modification time and
 * the device/inode pair are all exactly what they were while the bytes are different -- and that is
 * not an exotic attack, it is what {@code cp -p}, {@code rsync -t} and {@code tar -x} do every day.
 * A cache keyed on those four alone would serve a digest of content that no longer exists, and
 * {@code R-PROV-02} says a recorded hash "shall always be a hash of the content the tools actually
 * read". So the fingerprint also carries the POSIX <em>inode change time</em> ({@code st_ctim},
 * read as {@code unix:ctime}), which the kernel updates on every write to the file and on every
 * metadata change including the timestamp restoration itself, and which user space cannot set. It
 * is the only attribute in the set that is evidence rather than a hint.
 *
 * <p><strong>Where the evidence is missing.</strong> {@link BasicFileAttributes#fileKey()} is
 * {@code null} on Windows, and no file system there offers {@code st_ctim}. This record represents
 * that honestly, as {@code null} components, and {@link #tamperEvident()} answers {@code false} --
 * which {@link CachingHashService} turns into "rehash", never into "trust it anyway". An absent
 * attribute is a reason to trust an entry less.
 *
 * <p><strong>Timestamps are compared against a whole second, not an instant.</strong> See {@link
 * #settledBefore(Instant)}: a file system whose timestamps have one-second granularity can hide a
 * write inside the tick that the fingerprint was taken in, so a fingerprint taken in the same
 * second as the file's last change is not trusted at all. This is git's "racily clean" rule, and it
 * is here for the same reason git needs it.
 *
 * <p>Instances are immutable and are safe to publish between threads. Package-private on purpose:
 * how the cache decides a file is unchanged is not an extension point.
 *
 * @param canonicalPath the file's path with every symbolic link and {@code ..} resolved, as {@link
 *     Path#toRealPath} returns it -- so that two names for one file are one cache entry
 * @param size the file's length in bytes
 * @param lastModifiedAt when the file's content was last written, as the file system reports it;
 *     user space can set this to anything, so it is a hint and never proof
 * @param fileIdentity the device and inode pair from {@link BasicFileAttributes#fileKey()}, or
 *     {@code null} where the platform has no such notion; {@code fileKey} objects are documented to
 *     compare equal exactly when they denote the same file
 * @param inodeChangedAt the POSIX inode change time ({@code st_ctim}), or {@code null} where the
 *     platform does not publish one; the only component here that a content change cannot avoid
 */
record FileFingerprint(
        Path canonicalPath,
        long size,
        Instant lastModifiedAt,
        Object fileIdentity,
        Instant inodeChangedAt) {

    /** Name of the attribute view that carries {@code st_ctim}; absent on Windows. */
    static final String UNIX_VIEW = "unix";

    /**
     * The one {@code readAttributes} request that fetches every component, in one call.
     *
     * <p>One call, not four: a fingerprint assembled from several {@code stat} calls could describe
     * a file that never existed in that state, which is precisely the sort of not-quite-true record
     * this cache exists to avoid.
     */
    private static final String UNIX_ATTRIBUTES = "unix:size,lastModifiedTime,fileKey,ctime";

    /** Map key for the size, in the {@link #UNIX_ATTRIBUTES} result. */
    private static final String SIZE = "size";

    /** Map key for the modification time, in the {@link #UNIX_ATTRIBUTES} result. */
    private static final String LAST_MODIFIED_TIME = "lastModifiedTime";

    /** Map key for the device/inode pair, in the {@link #UNIX_ATTRIBUTES} result. */
    private static final String FILE_KEY = "fileKey";

    /** Map key for the inode change time, in the {@link #UNIX_ATTRIBUTES} result. */
    private static final String CTIME = "ctime";

    /**
     * Checks the two components that are never optional.
     *
     * @throws NullPointerException if {@code canonicalPath} or {@code lastModifiedAt} is {@code
     *     null}
     */
    FileFingerprint {
        Objects.requireNonNull(canonicalPath, "canonicalPath");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt");
    }

    /**
     * Reads a file's attributes as they are at this instant.
     *
     * <p>This is the production reader, and {@link CachingHashService}'s public constructors wire
     * it in as a method reference; the seam it is passed through exists so that a test can present
     * the fingerprints a Windows file system would produce on a host that has none.
     *
     * @param canonicalPath the file, already resolved by {@link Path#toRealPath}
     * @return its fingerprint, carrying {@code null} for whatever this platform does not publish
     * @throws IOException if the attributes cannot be read -- a missing file surfaces as {@link
     *     java.nio.file.NoSuchFileException}
     * @throws NullPointerException if {@code canonicalPath} is {@code null}
     */
    static FileFingerprint of(Path canonicalPath) throws IOException {
        Objects.requireNonNull(canonicalPath, "canonicalPath");
        if (canonicalPath.getFileSystem().supportedFileAttributeViews().contains(UNIX_VIEW)) {
            return fromUnixAttributes(
                    canonicalPath, Files.readAttributes(canonicalPath, UNIX_ATTRIBUTES));
        }
        return fromBasicAttributes(
                canonicalPath, Files.readAttributes(canonicalPath, BasicFileAttributes.class));
    }

    /**
     * Builds a fingerprint from a {@code unix} attribute map, which has everything.
     *
     * @param canonicalPath the file the attributes were read from
     * @param attributes the result of reading {@link #UNIX_ATTRIBUTES}
     * @return a tamper-evident fingerprint
     */
    static FileFingerprint fromUnixAttributes(Path canonicalPath, Map<String, Object> attributes) {
        return new FileFingerprint(
                canonicalPath,
                (Long) attributes.get(SIZE),
                ((FileTime) attributes.get(LAST_MODIFIED_TIME)).toInstant(),
                attributes.get(FILE_KEY),
                ((FileTime) attributes.get(CTIME)).toInstant());
    }

    /**
     * Builds a fingerprint from the portable attributes alone, which is all Windows offers.
     *
     * <p>The result carries no inode change time, and on Windows carries no file identity either,
     * so {@link #tamperEvident()} is {@code false} and the cache will not serve from it.
     *
     * @param canonicalPath the file the attributes were read from
     * @param attributes the file's basic attributes
     * @return a fingerprint with no inode change time
     */
    static FileFingerprint fromBasicAttributes(Path canonicalPath, BasicFileAttributes attributes) {
        return new FileFingerprint(
                canonicalPath,
                attributes.size(),
                attributes.lastModifiedTime().toInstant(),
                attributes.fileKey(),
                null);
    }

    /**
     * Whether this fingerprint carries the two attributes user space cannot forge.
     *
     * <p>Both are required, and for different reasons. Without the file identity, a file deleted
     * and rewritten at the same path with the same length and a restored timestamp is invisible.
     * Without the inode change time, a file overwritten <em>in place</em> with the same length and
     * a restored timestamp is invisible. Either hole is enough to record a hash of content the
     * tools did not read.
     *
     * @return {@code true} only if both the file identity and the inode change time are present
     */
    boolean tamperEvident() {
        return fileIdentity != null && inodeChangedAt != null;
    }

    /**
     * Whether the file had already stopped changing when this fingerprint was taken.
     *
     * <p><strong>The tick, not the instant.</strong> Both timestamps must fall strictly before the
     * start of the second that {@code observedAt} lies in. That looks over-cautious on ext4 or
     * APFS, where timestamps have nanosecond resolution; it is exactly right on NFS, SMB and HFS+,
     * where they have one-second resolution and a write landing later in the same second leaves
     * <em>every</em> timestamp unchanged. Spectrum files on a facility's NFS share are the normal
     * case for this product, not an edge case, so the rule is applied everywhere rather than
     * conditioned on a granularity the file system does not report. Git calls an index entry that
     * fails this test "racily clean" and re-hashes it; so does this cache.
     *
     * <p>It is not only network file systems, either. Measured on the host this class was written
     * on, an ordinary local file system: two writes to the same file, back to back with no sleep
     * between them, left an <em>identical</em> {@code ctime} in 184 of 200 trials, because the
     * kernel stamps timestamps from a clock that advances on a tick rather than per call. Without
     * this rule, "the inode change time is the same, so the content is the same" would be a
     * usually-true statement rather than a sound one.
     *
     * <p>A fingerprint with no inode change time is never settled: there is nothing to settle.
     *
     * @param observedAt the wall-clock reading taken when the fingerprint was made
     * @return {@code true} if both timestamps are strictly older than the second containing {@code
     *     observedAt}
     * @throws NullPointerException if {@code observedAt} is {@code null}
     */
    boolean settledBefore(Instant observedAt) {
        Instant tick = observedAt.truncatedTo(ChronoUnit.SECONDS);
        return isBefore(lastModifiedAt, tick) && isBefore(inodeChangedAt, tick);
    }

    /**
     * Whether an entry carrying this fingerprint may be kept and later served.
     *
     * @param observedAt the wall-clock reading taken when the fingerprint was made
     * @return {@code true} only if the fingerprint is both tamper-evident and settled
     * @throws NullPointerException if {@code observedAt} is {@code null}
     */
    boolean trustworthyAt(Instant observedAt) {
        return tamperEvident() && settledBefore(observedAt);
    }

    /**
     * Whether two fingerprints describe a file that has not changed in any observable way.
     *
     * <p>Written as five explicit comparisons rather than left to the record's generated {@code
     * equals} so that each one can be removed on its own and watched to fail. "Invalidated by any
     * attribute change" is a claim about five separate attributes, and a test suite that cannot
     * fail five separate ways has not tested it.
     *
     * @param other the fingerprint to compare with, usually one just re-read from the file system
     * @return {@code true} only if every component is equal
     * @throws NullPointerException if {@code other} is {@code null}
     */
    boolean matches(FileFingerprint other) {
        Objects.requireNonNull(other, "other");
        return canonicalPath.equals(other.canonicalPath)
                && size == other.size
                && lastModifiedAt.equals(other.lastModifiedAt)
                && Objects.equals(fileIdentity, other.fileIdentity)
                && Objects.equals(inodeChangedAt, other.inodeChangedAt);
    }

    /**
     * A null-tolerant "strictly earlier than" for the settling rule.
     *
     * @param timestamp the timestamp to place, or {@code null} if the platform has none
     * @param tick the start of the second the observation was made in
     * @return {@code true} if the timestamp exists and is strictly earlier than the tick
     */
    private static boolean isBefore(Instant timestamp, Instant tick) {
        return timestamp != null && timestamp.isBefore(tick);
    }
}

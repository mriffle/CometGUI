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
import java.nio.file.Path;
import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;

/**
 * A {@link HashService} that remembers what it hashed, so that a 2 GB spectrum file is not read
 * again on every run -- and that would rather read it again than record a digest of content nobody
 * read.
 *
 * <p>It decorates another {@code HashService} rather than extending {@link StreamingHashService}:
 * the thing being cached is "whatever this product's hasher says about this file", and the
 * constructor takes the port, not the implementation.
 *
 * <h2>What the cache is keyed on, and what it re-reads</h2>
 *
 * <p>{@code R-PROV-02} names the key -- canonical path, size, modification time and, where
 * available, file identity -- and {@link FileFingerprint} carries all four plus the POSIX inode
 * change time, which is the only one of them a content change cannot avoid. The <em>map</em> is
 * indexed by canonical path alone, because a file has one canonical path at a time; the rest of the
 * key is stored in the entry and compared on every single lookup. There is no path by which a
 * cached digest is returned without the file system having been asked, on this call, what the file
 * looks like now: {@link #hash(Path)} re-reads the attributes before it looks in the map, and a
 * mismatch in any component drops the entry and hashes again.
 *
 * <h2>When an entry is kept at all</h2>
 *
 * <p>Three conditions, all of them "when in doubt, rehash" in a different disguise.
 *
 * <ul>
 *   <li><b>Tamper-evident.</b> The fingerprint must carry both a file identity and an inode change
 *       time. Where either is missing -- Windows publishes neither, and neither does a file inside
 *       a zip -- the cache stores nothing and every call reaches the delegate. That is a correct
 *       cache with a hit rate of zero, which is the right trade when the alternative is a hash of
 *       content the tools did not read.
 *   <li><b>Unchanged across the read.</b> The attributes are read before the delegate hashes and
 *       again afterwards, and the entry is stored only if the two agree. A file rewritten while it
 *       was being hashed is hashed again next time rather than remembered.
 *   <li><b>Settled.</b> See {@link FileFingerprint#settledBefore}. A file whose last change falls
 *       in the same one-second tick as the observation is not cached, because on a file system with
 *       one-second timestamps a later write in that tick would be invisible.
 * </ul>
 *
 * <h2>Bounded</h2>
 *
 * <p>At most {@link #DEFAULT_MAXIMUM_ENTRIES} entries by default, and eviction is strict
 * least-recently-used: the map is a {@link LinkedHashMap} in access order, so a lookup that hits
 * moves that entry to the young end, and the entry evicted when the bound is reached is the one
 * that has gone longest without being asked for. A map keyed on path with no bound is a leak in a
 * long session -- the user browses a directory of 40 000 spectrum files and the process keeps a
 * fingerprint for every one of them for ever.
 *
 * <h2>Bypass</h2>
 *
 * <p>Two ways, as {@code R-PROV-02} requires. {@link #rehash(Path)} hashes one file again whatever
 * the cache holds, and refreshes the entry from the result. {@link #disabled(HashService)} builds
 * an instance that never stores and never serves, for a run that wants no cache at all.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Safe to share. One lock guards the map and the two counters, and it is held only for the map
 * operations themselves -- never while the delegate hashes, which for the files this class exists
 * for takes seconds. Two threads asking for the same uncached file therefore both hash it; they
 * agree, one entry survives, and the duplicated work is preferred to serialising every stage behind
 * one lock. Nothing else is mutable: the delegate is required to be thread-safe (this project's is,
 * and says so), and {@link FileFingerprint} and {@link FileHashes} are immutable records.
 *
 * @see StreamingHashService
 * @see FileFingerprint
 */
public final class CachingHashService implements HashService {

    /**
     * Entries kept before the least recently used one is evicted: 256.
     *
     * <p>A run reads a handful of files per stage, and a session that re-runs a search re-reads the
     * same ones; 256 covers the spectrum files, FASTA and parameter files of any realistic run with
     * room to spare, and an entry is a path, three timestamps-or-numbers and two digest strings --
     * of the order of a quarter of a kilobyte. The bound exists because the cost of an unbounded
     * map here is not the memory of the entries the product needs, it is the memory of the ones it
     * does not: a file browsed once and never read again would otherwise be remembered for the life
     * of the process.
     */
    public static final int DEFAULT_MAXIMUM_ENTRIES = 256;

    /**
     * How a canonical path becomes a fingerprint.
     *
     * <p>Package-private, and not a configuration point, for the same reason {@link
     * StreamingHashService.FileOpener} is: production is wired to {@link FileFingerprint#of} by
     * every public constructor here, and the seam exists so that a test on Linux can present the
     * fingerprints a Windows file system would produce -- no file identity, no inode change time --
     * and prove that the cache then refuses to serve anything at all. That behaviour is
     * unobservable on a host whose file system publishes both.
     */
    @FunctionalInterface
    interface FingerprintReader {

        /**
         * Reads the attributes of a file.
         *
         * @param canonicalPath the file, already resolved by {@link Path#toRealPath}
         * @return its fingerprint as of now
         * @throws IOException if the attributes cannot be read
         */
        FileFingerprint read(Path canonicalPath) throws IOException;
    }

    /**
     * One remembered file: what it looked like, and what it hashed to.
     *
     * @param fingerprint the attributes observed when the digests were computed
     * @param hashes the digests the delegate returned for that content
     */
    private record CacheEntry(FileFingerprint fingerprint, FileHashes hashes) {}

    /** The hasher that does the real work on every miss; never null, and required to be safe. */
    private final HashService delegate;

    /** Entries kept before eviction; at least one. */
    private final int maximumEntries;

    /**
     * False for the instance {@link #disabled(HashService)} builds: store nothing, serve nothing.
     */
    private final boolean enabled;

    /** How attributes are read; {@link FileFingerprint#of} in production. */
    private final FingerprintReader reader;

    /** The wall clock the settling rule is measured against; the system clock in production. */
    private final Clock clock;

    /** Guards {@link #entries}, {@link #hits} and {@link #misses}, and nothing else. */
    private final Object lock = new Object();

    /** Canonical path to entry, in access order so that eviction is least-recently-used. */
    private final Map<Path, CacheEntry> entries;

    /** Lookups that re-read the attributes, found them unchanged and served a stored digest. */
    private long hits;

    /** Times the delegate was asked to hash, including calls that then failed. */
    private long misses;

    /**
     * Creates a cache in front of a hasher, bounded at {@link #DEFAULT_MAXIMUM_ENTRIES} entries.
     *
     * @param delegate the hasher to consult on every miss
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public CachingHashService(HashService delegate) {
        this(delegate, DEFAULT_MAXIMUM_ENTRIES);
    }

    /**
     * Creates a cache in front of a hasher, bounded at a chosen number of entries.
     *
     * @param delegate the hasher to consult on every miss
     * @param maximumEntries how many files to remember; the least recently used is evicted beyond
     *     this
     * @throws NullPointerException if {@code delegate} is {@code null}
     * @throws IllegalArgumentException if {@code maximumEntries} is less than one
     */
    public CachingHashService(HashService delegate, int maximumEntries) {
        this(delegate, maximumEntries, true, FileFingerprint::of, Clock.systemUTC());
    }

    /**
     * Creates a cache that is switched off: every call reaches the delegate, nothing is remembered.
     *
     * <p>This is the "bypassable" half of {@code R-PROV-02} that applies to a whole run. The type
     * stays the same so that wiring does not have to change to turn the cache off.
     *
     * @param delegate the hasher every call is passed to
     * @return a service that behaves exactly like {@code delegate}, and counts the calls
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public static CachingHashService disabled(HashService delegate) {
        return new CachingHashService(
                delegate, DEFAULT_MAXIMUM_ENTRIES, false, FileFingerprint::of, Clock.systemUTC());
    }

    /**
     * The constructor the tests use to stand in for a platform or a clock this host does not have.
     *
     * @param delegate the hasher to consult on every miss
     * @param maximumEntries how many files to remember
     * @param enabled whether the cache stores and serves at all
     * @param reader how attributes are read
     * @param clock the wall clock the settling rule is measured against
     * @throws NullPointerException if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code maximumEntries} is less than one
     */
    CachingHashService(
            HashService delegate,
            int maximumEntries,
            boolean enabled,
            FingerprintReader reader,
            Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maximumEntries < 1) {
            throw new IllegalArgumentException(
                    "maximumEntries must be at least 1, but was: " + maximumEntries);
        }
        this.maximumEntries = maximumEntries;
        this.enabled = enabled;
        this.reader = Objects.requireNonNull(reader, "reader");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    /**
     * Hashes a file, reusing a remembered digest only when the file system says nothing has
     * changed.
     *
     * @param path the file to hash; need not be canonical
     * @return its MD5 and SHA-256 digests -- of the content read now, or of content proved
     *     identical by every attribute the platform publishes
     * @throws IOException if the file cannot be read; the delegate's own failures are passed
     *     through unchanged, so a missing file still surfaces as {@link
     *     java.nio.file.NoSuchFileException}
     * @throws NullPointerException if {@code path} is {@code null}
     */
    @Override
    public FileHashes hash(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return hash(path, true);
    }

    /**
     * Hashes a file again whatever the cache holds, and refreshes the entry from the result.
     *
     * <p>The per-file bypass {@code R-PROV-02} requires: for a caller that has reason to doubt the
     * file system's attributes -- a network mount with a lying clock, a file just produced by
     * another process -- and for the operator who simply wants the bytes read again.
     *
     * @param path the file to hash; need not be canonical
     * @return its MD5 and SHA-256 digests, always computed by the delegate on this call
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public FileHashes rehash(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return hash(path, false);
    }

    /**
     * Forgets one file, if it is remembered.
     *
     * @param path the file to forget; need not be canonical
     * @return {@code true} if an entry was removed
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public boolean invalidate(Path path) {
        Objects.requireNonNull(path, "path");
        return discard(canonicalOrAbsolute(path));
    }

    /** Forgets every file. */
    public void invalidateAll() {
        synchronized (lock) {
            entries.clear();
        }
    }

    /**
     * How many files are remembered right now.
     *
     * @return the number of entries held, never more than {@link #maximumEntries()}
     */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /**
     * The bound this instance was built with.
     *
     * @return the maximum number of entries held at once
     */
    public int maximumEntries() {
        return maximumEntries;
    }

    /**
     * Whether this instance caches at all.
     *
     * @return {@code false} for an instance from {@link #disabled(HashService)}
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * How many calls were answered from a revalidated entry.
     *
     * @return the number of cache hits since this instance was created
     */
    public long hitCount() {
        synchronized (lock) {
            return hits;
        }
    }

    /**
     * How many calls reached the delegate, including calls that failed there.
     *
     * @return the number of cache misses since this instance was created
     */
    public long missCount() {
        synchronized (lock) {
            return misses;
        }
    }

    /**
     * The whole policy, in one place.
     *
     * @param path the file to hash
     * @param mayServeFromCache {@code false} for {@link #rehash(Path)}
     * @return the digests
     * @throws IOException if the file cannot be read
     */
    private FileHashes hash(Path path, boolean mayServeFromCache) throws IOException {
        if (!enabled) {
            return delegateHash(path);
        }
        Path canonical;
        FileFingerprint before;
        try {
            canonical = path.toRealPath();
            before = reader.read(canonical);
        } catch (IOException e) {
            // The file cannot even be named or described. Anything remembered under the name the
            // caller used is now unsupportable, so drop it -- best effort, since without a real
            // path the key can only be guessed -- and let the delegate fail with the caller's own
            // path in the message.
            discard(path.toAbsolutePath().normalize());
            return delegateHash(path);
        }
        if (mayServeFromCache) {
            FileHashes cached = lookup(canonical, before);
            if (cached != null) {
                return cached;
            }
        }
        // Whatever was held for this path has just failed to match, or is being bypassed. Drop it
        // before the delegate runs, so that a hash that throws cannot leave a stale entry behind.
        discard(canonical);
        FileHashes hashes = delegateHash(canonical);
        store(canonical, before, hashes);
        return hashes;
    }

    /**
     * Serves an entry, but only after re-reading the file system and agreeing with it.
     *
     * @param canonical the canonical path, which is the map key
     * @param current the fingerprint just read from the file system
     * @return the remembered digests, or {@code null} if there is nothing trustworthy to serve
     */
    private FileHashes lookup(Path canonical, FileFingerprint current) {
        synchronized (lock) {
            CacheEntry entry = entries.get(canonical);
            if (entry != null && entry.fingerprint().matches(current)) {
                hits++;
                return entry.hashes();
            }
        }
        // Outside the lock deliberately: a `return null` inside a synchronized block compiles to a
        // shape PIT reports as a surviving "replaced return value with null" mutation, which is
        // this very statement and therefore cannot be killed by any test. Nothing about the
        // behaviour changes; the difference is that the mutation report stays free of noise a
        // reviewer would otherwise have to adjudicate by hand.
        return null;
    }

    /**
     * Remembers a file, if everything about the read supports remembering it.
     *
     * @param canonical the canonical path, which is the map key
     * @param before the fingerprint read before the delegate hashed the file
     * @param hashes what the delegate returned
     */
    private void store(Path canonical, FileFingerprint before, FileHashes hashes) {
        FileFingerprint after;
        try {
            after = reader.read(canonical);
        } catch (IOException e) {
            // The file changed out from under the hash badly enough that its attributes can no
            // longer be read. There is nothing here worth remembering.
            return;
        }
        if (!after.matches(before) || !after.trustworthyAt(clock.instant())) {
            return;
        }
        synchronized (lock) {
            entries.put(canonical, new CacheEntry(after, hashes));
            evictWhileOverBound();
        }
    }

    /**
     * Drops least-recently-used entries until the bound is respected.
     *
     * <p>Called with {@link #lock} held.
     */
    private void evictWhileOverBound() {
        Iterator<Path> oldestFirst = entries.keySet().iterator();
        while (entries.size() > maximumEntries) {
            oldestFirst.next();
            oldestFirst.remove();
        }
    }

    /**
     * Hashes with the delegate and counts the call.
     *
     * @param path the path to hand the delegate
     * @return the digests it computed
     * @throws IOException if it cannot read the file
     */
    private FileHashes delegateHash(Path path) throws IOException {
        synchronized (lock) {
            misses++;
        }
        return delegate.hash(path);
    }

    /**
     * Removes one entry.
     *
     * @param key the canonical path to forget
     * @return {@code true} if an entry was there to remove
     */
    private boolean discard(Path key) {
        synchronized (lock) {
            return entries.remove(key) != null;
        }
    }

    /**
     * The key a path would have, resolving it if the file is still there.
     *
     * @param path the caller's path
     * @return its canonical form, or its absolute normalised form if it cannot be resolved
     */
    private static Path canonicalOrAbsolute(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}

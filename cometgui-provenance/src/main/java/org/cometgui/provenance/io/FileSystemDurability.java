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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * The real {@link Durability}: the platform's own {@code fsync} and {@code rename}, and the only
 * implementation the product ships.
 *
 * <p>Stateless and immutable once constructed, so a single instance serves every caller and every
 * thread.
 */
final class FileSystemDurability implements Durability {

    /**
     * How a directory becomes a channel that can be forced.
     *
     * <p>This exists for the same reason {@link Durability} does, one level down. {@link
     * #syncDirectory(Path)} has no observable result: it opens the directory, forces it, and
     * returns nothing, and a version of it that quietly skipped the force would behave identically
     * on every filesystem that can be tested without cutting the power. Routing the open through a
     * seam lets a test hand it a channel that records what was asked of it, so "the directory was
     * forced" becomes an assertion instead of a comment.
     *
     * <p>Package-private and not a configuration point, exactly like the interface it serves.
     */
    @FunctionalInterface
    interface ChannelOpener {

        /**
         * Opens a path for reading.
         *
         * @param path the file or directory to open
         * @return an open channel, which the caller closes
         * @throws IOException if the path cannot be opened
         */
        FileChannel open(Path path) throws IOException;
    }

    /**
     * Opens the directory read-only, which is the portable spelling of "give me a descriptor I can
     * fsync". A directory cannot be opened for writing on any platform this product supports, and
     * {@code force} does not require a writable channel.
     */
    private final ChannelOpener directoryOpener;

    /** Creates the production instance, which opens real directories on the default filesystem. */
    FileSystemDurability() {
        this(path -> FileChannel.open(path, StandardOpenOption.READ));
    }

    /**
     * Creates an instance that opens directories through a given opener.
     *
     * <p>For tests, which is why it is package-private: see {@link ChannelOpener}.
     *
     * @param directoryOpener how a directory becomes a channel
     * @throws NullPointerException if {@code directoryOpener} is {@code null}
     */
    FileSystemDurability(ChannelOpener directoryOpener) {
        this.directoryOpener = Objects.requireNonNull(directoryOpener, "directoryOpener");
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code force(true)} -- data and metadata. The file's length is metadata, and a provenance
     * document whose bytes reached the disk under a stale length is unreadable in precisely the way
     * {@code R-PROV-05} forbids.
     */
    @Override
    public void syncFile(FileChannel channel) throws IOException {
        channel.force(true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code ATOMIC_MOVE} is a demand, not a hint: without it {@code Files.move} is free to fall
     * back to copy-then-delete, which is observable in exactly the half-written state this class
     * exists to prevent. It is honoured for a rename within one directory on every filesystem this
     * product supports, and refused -- loudly, with an {@code AtomicMoveNotSupportedException} --
     * rather than silently downgraded when it is not.
     */
    @Override
    public void moveIntoPlace(Path temporary, Path target) throws IOException {
        Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Opening a directory as a channel is legal on Linux and macOS and illegal on Windows, so
     * this method throws there and {@link AtomicDocumentWriter} expects it to. The exception is
     * deliberately not caught here: whether a failure to sync a directory is fatal is a policy
     * decision, it belongs to the caller that knows the data is already in place, and a policy
     * buried in the mechanism is a policy no test can reach.
     */
    @Override
    public void syncDirectory(Path directory) throws IOException {
        try (FileChannel channel = directoryOpener.open(directory)) {
            syncFile(channel);
        }
    }
}

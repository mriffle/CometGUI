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
import java.nio.file.Path;

/**
 * The three filesystem operations whose <em>order</em> is the atomicity guarantee, gathered behind
 * one seam so that the order can be asserted.
 *
 * <p><strong>Why a seam at all.</strong> {@code R-PROV-05} says finalisation is atomic, and the
 * implementation of that promise is a sequence: fill a temporary file, force its data to the
 * platter, rename it over the target, force the directory entry. Every one of those steps is
 * invisible in the result. A writer that never calls {@code fsync} produces a byte-identical file,
 * passes every round-trip test that can be written, and loses the document on a power cut. A writer
 * that syncs <em>after</em> the rename produces the same byte-identical file and protects nothing,
 * because by then the rename it was supposed to make durable has already happened. Correct output
 * cannot tell any of these apart. The order of the calls can, and only if something records them.
 *
 * <p><strong>Why the move is on this seam and not called directly.</strong> An order is a relation
 * between operations, and a relation can only be asserted where both of its operands are observed.
 * Recording {@code syncFile} and {@code syncDirectory} while leaving {@link
 * java.nio.file.Files#move} to be called directly would let a test prove that two syncs happened
 * and in which order, and leave the one fact that matters most -- that the data sync came before
 * the rename -- unobservable. {@link #moveIntoPlace} is here so that {@code [syncFile, move,
 * syncDirectory]} is a list a test can compare against a literal.
 *
 * <p><strong>It is package-private, and it is not a configuration point.</strong> Nothing outside
 * this package may substitute a different notion of durability for the files a provenance record is
 * made of; the only implementation the product ever uses is {@link FileSystemDurability}.
 *
 * @see AtomicDocumentWriter
 */
interface Durability {

    /**
     * Forces a file's written data, and its metadata, out of the operating system's cache and onto
     * the storage device.
     *
     * <p>Called while the temporary file is still open and before it is renamed. Implementations
     * are {@code FileChannel.force(true)}: {@code true} rather than {@code false} because a file
     * whose data survived but whose length did not is exactly the truncated document this class
     * exists to prevent.
     *
     * @param channel the open channel of the fully written temporary file
     * @throws IOException if the data cannot be forced to the device; the write is then abandoned
     *     and the target left untouched
     */
    void syncFile(FileChannel channel) throws IOException;

    /**
     * Renames the temporary file over the target, atomically, replacing whatever was there.
     *
     * <p>Implementations are {@code Files.move} with {@code ATOMIC_MOVE} and {@code
     * REPLACE_EXISTING}. This is the instant at which the new document becomes visible: before it,
     * every reader sees the old file in full; after it, every reader sees the new file in full; and
     * there is no third state, which is why both paths must be in the same directory and therefore
     * on the same filesystem.
     *
     * @param temporary the fully written, synced and closed temporary file
     * @param target where it must appear
     * @throws IOException if the rename fails; the temporary file is then removed and the target is
     *     left exactly as it was
     */
    void moveIntoPlace(Path temporary, Path target) throws IOException;

    /**
     * Forces a directory's own entries out of the operating system's cache and onto the device, so
     * that the rename itself survives a power loss.
     *
     * <p>Syncing the file makes the <em>contents</em> durable; only syncing the directory makes the
     * <em>name</em> durable. Without it a crash immediately after the rename can leave the file
     * present under its temporary name, or absent altogether, with the data safely on disk and
     * unreachable.
     *
     * @param directory the directory holding the target
     * @throws IOException if the directory cannot be opened or forced -- which is the normal state
     *     of affairs on Windows, and which {@link AtomicDocumentWriter} therefore treats as a
     *     non-fatal outcome rather than a failed write
     */
    void syncDirectory(Path directory) throws IOException;
}

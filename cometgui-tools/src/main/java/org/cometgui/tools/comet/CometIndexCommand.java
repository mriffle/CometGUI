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

package org.cometgui.tools.comet;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.IndexMode;

/**
 * The command that builds a Comet index ({@code R-CMT-07}, design decision P8-8), and the symbolic
 * link it needs.
 *
 * <pre>
 *   &lt;comet&gt; -P&lt;params&gt; -i|-j -D&lt;cache&gt;/&lt;fasta name&gt;
 * </pre>
 *
 * <p>run in the cache directory, with {@link CometSearchCommands#ENVIRONMENT}.
 *
 * <p><strong>Why a link.</strong> Comet writes the index as {@code <database>.idx}, beside the
 * database it was given; with the FASTA in a read-only directory it fails ({@code Error - cannot
 * open index file ... to write}), and with a writable one it writes into the user's data. So {@code
 * -D} names a <strong>symbolic link</strong> to the FASTA inside the cache directory, and the index
 * lands at {@link #indexFile()} there -- measured by the Phase 08 orchestrator and by {@code
 * CometIndexRealBinaryTest}. The search then passes that index with {@code -D} ({@link
 * CometSearchCommands#databaseOverride}).
 *
 * <p><strong>The link is made here</strong>, by {@link #linkDatabase()}, so that the link's name
 * and the {@code -D} argument are written in one place and cannot disagree. The command itself
 * stays pure: {@link #command()} touches nothing.
 *
 * <p><strong>Where symbolic links are unsupported.</strong> Creating one on Windows needs a
 * privilege or Developer Mode, and some file systems have none. {@link #linkDatabase()} then fails
 * with an {@link IOException} saying so; it never falls back to copying the FASTA (a copy would be
 * a second, unhashed database) or to writing beside the original. This has only been run on Linux;
 * on Windows it is unverified, and an index mode there may need a different mechanism, which is a
 * product decision rather than something this class should improvise.
 *
 * @param executable the Comet executable, absolute
 * @param parameterFile the parameter file passed with {@code -P}, absolute
 * @param mode the index to build; never {@link IndexMode#NONE}
 * @param cacheDirectory the cache directory the index is built in, absolute
 * @param fasta the FASTA file the link points to, absolute
 */
public record CometIndexCommand(
        Path executable, Path parameterFile, IndexMode mode, Path cacheDirectory, Path fasta) {

    /** The suffix Comet gives an index: {@code <database>.idx}. */
    public static final String INDEX_SUFFIX = ".idx";

    /**
     * Validates the command.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a path is not absolute, the mode is {@link
     *     IndexMode#NONE}, or the FASTA has no file name
     */
    public CometIndexCommand {
        CometSearchCommands.absolute(executable, "the Comet executable");
        CometSearchCommands.absolute(parameterFile, "the parameter file");
        Objects.requireNonNull(mode, "mode");
        if (mode.buildFlag().isEmpty()) {
            throw new IllegalArgumentException(
                    "index mode " + mode.wireName() + " builds no index, so it has no command");
        }
        CometSearchCommands.absolute(cacheDirectory, "the index cache directory");
        CometSearchCommands.absolute(fasta, "the FASTA file");
        if (fasta.getFileName() == null) {
            throw new IllegalArgumentException("the FASTA path " + fasta + " has no file name");
        }
    }

    /**
     * The symbolic link {@code -D} names: the FASTA's file name, inside the cache directory.
     *
     * @return {@code <cache>/<fasta name>}
     */
    public Path databaseLink() {
        return cacheDirectory.resolve(Objects.requireNonNull(fasta.getFileName()).toString());
    }

    /**
     * Where Comet writes the index.
     *
     * @return {@code <cache>/<fasta name>.idx}
     */
    public Path indexFile() {
        return cacheDirectory.resolve(databaseLink().getFileName() + INDEX_SUFFIX);
    }

    /**
     * The command: the argument array, the cache directory as working directory, and {@link
     * CometSearchCommands#ENVIRONMENT}.
     *
     * @return the command
     */
    public ToolCommand command() {
        return new ToolCommand(
                List.of(
                        executable.toString(),
                        CometSearchCommands.PARAMS_OPTION + parameterFile,
                        mode.buildFlag().orElseThrow(),
                        CometSearchCommands.DATABASE_OPTION + databaseLink()),
                cacheDirectory,
                CometSearchCommands.ENVIRONMENT);
    }

    /**
     * Creates the symbolic link {@link #databaseLink()} pointing at the FASTA.
     *
     * <p>A link already there that points at this FASTA is kept, so a build retried in the same
     * cache directory does not fail on its own link. Anything else at that name -- a file, or a
     * link to another FASTA -- is refused, never replaced: it would be a different database under
     * the same index name.
     *
     * @return the link
     * @throws FileAlreadyExistsException if something other than this link is at the name
     * @throws IOException if the link cannot be created, including where symbolic links are not
     *     supported, with a message naming the link and the FASTA
     */
    public Path linkDatabase() throws IOException {
        Path link = databaseLink();
        if (Files.isSymbolicLink(link)) {
            Path target = Files.readSymbolicLink(link);
            if (target.equals(fasta)) {
                return link;
            }
            throw new FileAlreadyExistsException(
                    link.toString(),
                    fasta.toString(),
                    "the index cache already holds a link of this name to " + target);
        }
        if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(
                    link.toString(),
                    fasta.toString(),
                    "the index cache already holds a file of this name that is not a link");
        }
        try {
            return Files.createSymbolicLink(link, fasta);
        } catch (UnsupportedOperationException unsupported) {
            throw new IOException(
                    "cannot create the symbolic link "
                            + link
                            + " to the FASTA "
                            + fasta
                            + ": this file system does not support symbolic links, and Comet"
                            + " would otherwise write its index beside the FASTA",
                    unsupported);
        }
    }
}

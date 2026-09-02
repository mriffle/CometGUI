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

import java.util.Objects;

/**
 * Why an extraction was refused, and the clause every message for that reason uses.
 *
 * <p>The reason is a value rather than a sentence so that a caller -- the Tool Manager, a
 * provenance record, a test matrix -- can tell two refusals apart without reading English. The
 * clause travels with it so that the wording of a given refusal is written once: a rule whose
 * message is composed afresh at every throw site is a rule whose messages drift apart.
 *
 * <p>Every message built from these also names the entry it rejected. That is not decoration. Phase
 * 05 catalogued an <em>eleventh shape</em> of a check that cannot fail: a guard that fires
 * correctly while its diagnostic misstates what it rejected, sending a reader to the wrong place
 * with the authority of the system behind it. The defence is to pin whole messages in tests, with
 * every computed value hand-typed.
 */
public enum RejectionReason {

    /** A {@code ..} segment: the entry would be written outside the destination directory. */
    ENTRY_NAME_TRAVERSES(
            "its name has a \"..\" segment, which would place it outside the destination"
                    + " directory"),

    /** A leading {@code /}, a leading {@code \\}, or a {@code C:} drive letter. */
    ENTRY_NAME_ABSOLUTE(
            "its name is an absolute path, and an archive does not get to choose the volume or the"
                    + " root a file is written under"),

    /** A backslash used as a separator, which is a second spelling of one path. */
    ENTRY_NAME_BACKSLASH(
            "its name uses a backslash as a separator, and accepting two spellings of one path is"
                    + " how a rejected name gets in under a second name"),

    /** A NUL character: a name that means one thing to Java and another to a C library. */
    ENTRY_NAME_NUL(
            "its name contains a NUL character, which truncates the name for anything that reads it"
                    + " as a C string"),

    /** An empty name, or a name with an empty segment. */
    ENTRY_NAME_EMPTY(
            "its name is empty or has an empty segment, so it does not name a file inside the"
                    + " destination directory"),

    /** Two entries with the same name: one file written and one lost, order deciding which. */
    DUPLICATE_ENTRY_NAME(
            "the archive names it twice, so one of the two would be written and the other lost,"
                    + " with the order of the archive deciding which"),

    /** A symbolic link whose target is absolute, empty, or resolves outside the destination. */
    UNSAFE_SYMLINK(
            "it is a symbolic link whose target does not resolve to a place inside the destination"
                    + " directory, so following it would read or write somewhere this extraction"
                    + " does not own"),

    /**
     * A file, directory or link whose own path passes through a symbolic link.
     *
     * <p>Separate from {@link #UNSAFE_SYMLINK} because the offending entry is a different thing: a
     * link that is perfectly safe on its own, plus a file "inside" it, is how two entries that each
     * pass their own check compose into one escape. Reporting this as an unsafe link would name the
     * wrong entry, which is the eleventh shape -- a guard that fires correctly while its diagnostic
     * misstates what it rejected.
     */
    WRITE_THROUGH_SYMLINK(
            "its own path passes through a symbolic link, and this extractor never writes through"
                    + " one"),

    /** A hard link, device node, socket or anything else that is not a file, directory or link. */
    UNSUPPORTED_ENTRY_TYPE(
            "it is neither a regular file, a directory nor a symbolic link, and this extractor"
                    + " creates nothing else"),

    /** The bytes delivered disagree with the length the archive declared. */
    DECLARED_SIZE_MISMATCH(
            "the bytes delivered disagree with the length the archive declared for it, so the"
                    + " archive's own table cannot be trusted about the rest of it"),

    /** Total uncompressed size past {@link ExtractionLimits#maxTotalUncompressedBytes}. */
    BOMB_TOTAL_UNCOMPRESSED_SIZE(
            "expanding it takes the artefact past the total uncompressed size this extractor will"
                    + " produce"),

    /** More entries than {@link ExtractionLimits#maxEntryCount}. */
    BOMB_ENTRY_COUNT("the artefact holds more entries than this extractor will read"),

    /** Expansion past {@link ExtractionLimits#maxExpansionRatio} times the artefact's own size. */
    BOMB_EXPANSION_RATIO(
            "expanding it takes the artefact past the ratio of uncompressed to compressed bytes"
                    + " this extractor will produce"),

    /** The manifest named a member the artefact does not contain. */
    MEMBER_NOT_FOUND("the artefact does not contain it"),

    /** The whole artefact unpacked without producing the executable the manifest expects. */
    EXPECTED_FILE_MISSING(
            "unpacking the whole artefact did not produce it, so the installed tool would have no"
                    + " executable to run"),

    /** The container's own structure is broken: bad magic, bad checksum, a truncated member. */
    MALFORMED_ARCHIVE("its container structure is not readable"),

    /** A container compressed with something the JDK cannot decode -- xz, zstd, bzip2. */
    UNSUPPORTED_COMPRESSION(
            "it is compressed with a codec the Java runtime cannot decode, and this project adds no"
                    + " dependency to gain one");

    /** The clause every message for this reason is built around. */
    private final String clause;

    RejectionReason(String clause) {
        this.clause = clause;
    }

    /**
     * The clause every message for this reason is built around.
     *
     * @return the clause, which reads on from "was rejected because"
     */
    public String clause() {
        return clause;
    }

    /**
     * Whether this reason is one of the two ways a symbolic link reaches outside a destination.
     *
     * @return {@code true} for the link's own target and for a path that passes through a link
     */
    public boolean isSymbolicLinkAttack() {
        return this == UNSAFE_SYMLINK || this == WRITE_THROUGH_SYMLINK;
    }

    /**
     * Whether this reason is one of the three independent decompression-bomb ceilings.
     *
     * <p>Exists so that a test can assert the three are graded separately rather than collapsed
     * into one, and so that the Tool Manager can say "this artefact is too large to unpack safely"
     * without repeating the list.
     *
     * @return {@code true} for the three bomb reasons
     */
    public boolean isDecompressionBomb() {
        return this == BOMB_TOTAL_UNCOMPRESSED_SIZE
                || this == BOMB_ENTRY_COUNT
                || this == BOMB_EXPANSION_RATIO;
    }

    /**
     * Resolves a reason from its constant name, rejecting an unknown one.
     *
     * @param name the constant name
     * @return the matching reason
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if no reason has that name
     */
    public static RejectionReason fromName(String name) {
        Objects.requireNonNull(name, "name");
        for (RejectionReason reason : values()) {
            if (reason.name().equals(name)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("no rejection reason is named \"" + name + "\"");
    }
}

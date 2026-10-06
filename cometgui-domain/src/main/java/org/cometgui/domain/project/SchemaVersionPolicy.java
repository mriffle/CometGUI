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

package org.cometgui.domain.project;

import java.util.Objects;

/**
 * {@code R-RUN-04}'s policy for opening a {@code project.json} or {@code run.json} written by
 * another version of CometGUI, as a pure function.
 *
 * <p>The requirement asks the application to "define and test its policy for opening older versions
 * (migrate, or refuse with a clear message) and newer ones (refuse without data loss, never
 * partially parse)". This is that definition:
 *
 * <ul>
 *   <li><strong>The current version is read.</strong>
 *   <li><strong>An older version is refused</strong>, with a message naming both versions. There is
 *       nothing to migrate: version 1 is the first published format, so an older one is a corrupt
 *       or invented document. When version 2 exists, its migration from version 1 is registered
 *       here and {@link SchemaVerdict#OLDER} for 1 stops being a refusal.
 *   <li><strong>A newer version is refused</strong>, with a message naming both versions, and it is
 *       refused <em>before any other member is read</em>. A newer writer may have changed what a
 *       member means rather than only added one, so a document read "as far as this build
 *       understands it" is a document silently misread. The reader calls this first, on the one
 *       member every version carries; and because refusal happens while reading, the file is never
 *       written and so is byte-identical afterwards -- "without data loss".
 * </ul>
 *
 * <p>The same three rules {@code ManifestReader} applies to {@code provenance.json}; one policy for
 * every versioned document the product writes.
 */
public final class SchemaVersionPolicy {

    private SchemaVersionPolicy() {
        throw new AssertionError("SchemaVersionPolicy is never instantiated");
    }

    /**
     * Judges a declared version against the version this build reads.
     *
     * @param found the version a document declares
     * @param current the version this build reads and writes
     * @return the verdict
     */
    public static SchemaVerdict judge(long found, int current) {
        if (found > current) {
            return SchemaVerdict.NEWER;
        }
        if (found < current) {
            return SchemaVerdict.OLDER;
        }
        return SchemaVerdict.CURRENT;
    }

    /**
     * Applies the policy, returning normally only for the current version.
     *
     * @param document the document's name or path, for the message
     * @param found the version the document declares
     * @param current the version this build reads and writes
     * @throws UnsupportedSchemaVersionException for an older or a newer version, naming the
     *     document and both versions
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static void requireReadable(String document, long found, int current) {
        Objects.requireNonNull(document, "document");
        SchemaVerdict verdict = judge(found, current);
        if (verdict == SchemaVerdict.NEWER) {
            throw new UnsupportedSchemaVersionException(
                    document,
                    found,
                    current,
                    verdict,
                    document
                            + " declares schema version "
                            + found
                            + ", and this build of CometGUI reads version "
                            + current
                            + ". It was written by a newer CometGUI, which may have changed what a"
                            + " member means, so it is refused before anything else in it is read;"
                            + " the file has not been changed.");
        }
        if (verdict == SchemaVerdict.OLDER) {
            throw new UnsupportedSchemaVersionException(
                    document,
                    found,
                    current,
                    verdict,
                    document
                            + " declares schema version "
                            + found
                            + ", and this build of CometGUI reads version "
                            + current
                            + ". No migration from version "
                            + found
                            + " to version "
                            + current
                            + " exists, so it is refused; the file has not been changed.");
        }
    }
}

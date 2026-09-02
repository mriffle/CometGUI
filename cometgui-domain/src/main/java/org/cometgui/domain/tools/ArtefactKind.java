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

package org.cometgui.domain.tools;

import java.util.Objects;

/**
 * The shape a downloaded tool artefact arrives in, which decides how it is unpacked.
 *
 * <p>{@code R-TOOL-01} requires this to be an explicit field in the manifest and requires the
 * extractor to be chosen from it, <strong>never inferred from the URL suffix</strong>. Percolator
 * 3.09's Windows artefact is a bare {@code percolator.exe} while every other Percolator artefact
 * the product installs is a zip, so a rule of the form "Percolator means ZIP" -- or "{@code .exe}
 * means installer" -- is wrong on a real upstream release.
 *
 * <p><strong>There is deliberately no {@code NSIS_PAYLOAD}, and it must not be added.</strong>
 * {@code D-002} option C, decided by the project owner on 2026-08-29, moved the Percolator binary
 * to the portable {@code noxml} zip on every tier-1 platform and deleted NSIS payload extraction
 * from the product -- "that was the most fragile code the installer was going to contain, and it is
 * now unwritten rather than written and maintained". A later reader who notices that upstream ships
 * an NSIS installer and "completes" this enumeration would be reversing an owner decision. Only a
 * new owner decision can reinstate it.
 *
 * <p>{@link #DEB_PAYLOAD} and {@link #PKG_PAYLOAD} survive that deletion for one narrow purpose: no
 * portable archive ships the two Percolator XSD companion files, so they are fetched from the
 * matching {@code noxml} package as a second small download. Installers are never executed;
 * payloads are read.
 */
public enum ArtefactKind {

    /**
     * The downloaded file is the executable: Comet on every platform, and Percolator 3.09 on
     * Windows.
     */
    BARE_EXECUTABLE("BARE_EXECUTABLE"),

    /** A zip archive. Every managed Percolator binary except 3.09 on Windows arrives this way. */
    ZIP("ZIP"),

    /** A gzip-compressed tar archive. */
    TAR_GZ("TAR_GZ"),

    /** A Java archive, run by the bundled runtime: PDV and the Limelight converter. */
    JAR("JAR"),

    /**
     * The {@code data.tar.*} payload inside a Debian {@code ar} archive, read for the Percolator
     * XSD companions and never installed as a package.
     */
    DEB_PAYLOAD("DEB_PAYLOAD"),

    /**
     * The gzip plus {@code 070707} cpio payload inside a macOS {@code xar!} package, read for the
     * Percolator XSD companions and never installed as a package.
     */
    PKG_PAYLOAD("PKG_PAYLOAD");

    private final String id;

    ArtefactKind(String id) {
        this.id = id;
    }

    /**
     * The stable identifier used in the artefact manifest.
     *
     * <p>It is the token {@code R-TOOL-01} itself uses. It is stored rather than returned from
     * {@code name()} for the reason given on {@link ToolName}: the manifest's vocabulary and the
     * Java constant's spelling are two different things that happen to agree today, and a rename
     * must be a visible change to the file format rather than a silent one.
     *
     * @return the identifier, never {@code null} or blank
     */
    public String id() {
        return id;
    }

    /**
     * Resolves an identifier read from a manifest back to its constant.
     *
     * <p>Exact match: no trimming and no case folding, for the reason given on {@link
     * ToolName#fromId(String)}. {@code NSIS_PAYLOAD} is rejected here like any other unknown value,
     * which is what makes a manifest entry that assumes the deleted extractor fail loudly.
     *
     * @param id the identifier to resolve
     * @return the matching kind
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if no kind has that identifier, with a message naming the
     *     rejected value and listing what is accepted
     */
    public static ArtefactKind fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (ArtefactKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(
                "no artefact kind has the id \""
                        + id
                        + "\"; expected one of [BARE_EXECUTABLE, ZIP, TAR_GZ, JAR, DEB_PAYLOAD,"
                        + " PKG_PAYLOAD]");
    }
}

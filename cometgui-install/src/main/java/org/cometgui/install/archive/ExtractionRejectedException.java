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

import java.io.IOException;
import java.util.Objects;

/**
 * An artefact, or one entry in it, was refused during extraction, and nothing further is unpacked.
 *
 * <p>Thrown rather than returned. {@code R-SEC-05}'s checks exist to stop a file being written
 * somewhere it should not be, and a caller that ignored a returned verdict would already have the
 * file on disk.
 *
 * <p><strong>The message always names the offending entry and states the reason.</strong> The entry
 * name is reproduced exactly as the archive spells it -- including a {@code ../} an attacker put
 * there -- because the person reading the message needs to see what was actually in the file, not a
 * cleaned-up version of it.
 *
 * <p>The destination is left as it was when the refusal happened: files already unpacked are not
 * removed. That is deliberate and is the installer's business, not the extractor's -- an atomic
 * install unpacks into a staging directory it discards, so cleaning up here would be a second
 * answer to a question that already has one.
 */
public final class ExtractionRejectedException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Why it was refused. Enums are serializable, so this class stays so. */
    private final RejectionReason reason;

    /** The entry, member or artefact the refusal is about, exactly as it was spelled. */
    private final String subject;

    private ExtractionRejectedException(String message, RejectionReason reason, String subject) {
        super(message);
        this.reason = reason;
        this.subject = subject;
    }

    /**
     * Refuses one entry of an archive.
     *
     * @param reason why it was refused
     * @param entryName the entry's name, exactly as the archive spells it
     * @param detail what else the reader needs, beginning with a space and ending without a stop,
     *     or the empty string when the reason's own clause says everything
     * @return the exception, for the caller to throw
     * @throws NullPointerException if any argument is {@code null}
     */
    static ExtractionRejectedException entry(
            RejectionReason reason, String entryName, String detail) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(entryName, "entryName");
        Objects.requireNonNull(detail, "detail");
        return new ExtractionRejectedException(
                "the archive entry \""
                        + entryName
                        + "\" was rejected because "
                        + reason.clause()
                        + detail,
                reason,
                entryName);
    }

    /**
     * Refuses the artefact as a whole, or something the manifest asked of it.
     *
     * @param reason why it was refused
     * @param subject what the refusal is about -- the artefact's file name, or the member path the
     *     manifest asked for
     * @param detail what else the reader needs, beginning with a space and ending without a stop,
     *     or the empty string
     * @return the exception, for the caller to throw
     * @throws NullPointerException if any argument is {@code null}
     */
    static ExtractionRejectedException artefact(
            RejectionReason reason, String subject, String detail) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(detail, "detail");
        return new ExtractionRejectedException(
                "the artefact \"" + subject + "\" was rejected because " + reason.clause() + detail,
                reason,
                subject);
    }

    /**
     * Refuses the destination the manifest declared for a member, rather than a name the archive
     * chose.
     *
     * <p>A separate opening because the two faults have different owners: a bad archive name is an
     * attack or an upstream accident, and a bad install path is a mistake in {@code
     * manifests/tools.json}. Reporting them with the same words would send a reader to the wrong
     * file.
     *
     * @param reason why it was refused
     * @param installedPath the destination the manifest declares
     * @param entryName the member the manifest was placing -- an archive entry's name, or the
     *     downloaded file's own name for an artefact kind that is one file
     * @return the exception, for the caller to throw
     * @throws NullPointerException if any argument is {@code null}
     */
    static ExtractionRejectedException destination(
            RejectionReason reason, String installedPath, String entryName) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(installedPath, "installedPath");
        Objects.requireNonNull(entryName, "entryName");
        return new ExtractionRejectedException(
                "the manifest's install path \""
                        + installedPath
                        + "\", for the artefact member \""
                        + entryName
                        + "\", was rejected because "
                        + reason.clause(),
                reason,
                installedPath);
    }

    /**
     * Why the extraction was refused.
     *
     * @return the reason, never {@code null}
     */
    public RejectionReason reason() {
        return reason;
    }

    /**
     * The entry, member or artefact the refusal is about.
     *
     * @return the subject, exactly as it was spelled where it was found
     */
    public String subject() {
        return subject;
    }
}

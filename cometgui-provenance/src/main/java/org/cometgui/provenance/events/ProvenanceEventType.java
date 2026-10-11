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

package org.cometgui.provenance.events;

import java.util.Objects;

/**
 * What a provenance event says happened.
 *
 * <p>The first seven kinds below are the ones a run can emit before any stage exists to emit them,
 * which is the point of phase 04: the model is built first "so that no stage can be built without
 * recording itself". A stage that starts, invokes a tool, hashes a file, warns, and finishes has a
 * constant here for every one of those moments, so a later phase records its work by choosing a
 * constant rather than by inventing a vocabulary.
 *
 * <p><strong>Why the wire name is a field and not {@code name().toLowerCase()}.</strong> The
 * argument is recorded in full on {@link org.cometgui.provenance.manifest.ProvenanceStatus} and it
 * applies here word for word: {@code "RUN_STARTED".toLowerCase()} spells itself with a dotless
 * {@code ı} under a Turkish default locale, and deriving the on-disk token from the Java constant
 * would let an ordinary rename silently change the format of every event log ever written. It would
 * also have to invent a rule for turning {@code RUN_STARTED} into two words, and a rule is one more
 * thing that can differ between the writer and the reader. Here the two names are independent, and
 * the tests pin every wire name as a hand-typed literal.
 *
 * <p>The wire names are dotted, lower case and two-segment -- {@code run.started}, {@code
 * stage.finished} -- matching the shape {@code
 * org.cometgui.provenance.manifest.ProvenanceSchema#SETTINGS_KEY_PATTERN} already fixes for the
 * other open namespace in a provenance document. A reader can therefore group an event log by its
 * first segment without a table.
 */
public enum ProvenanceEventType {

    /** The run began: the moment everything later in the log is relative to. */
    RUN_STARTED("run.started"),

    /** One stage of the workflow began -- the search, the rescoring, a conversion. */
    STAGE_STARTED("stage.started"),

    /**
     * One stage of the workflow ended, however it ended. The outcome belongs in the payload; the
     * type says only that the stage is no longer running.
     */
    STAGE_FINISHED("stage.finished"),

    /**
     * A tool was launched: the executable, its version and the argument array that {@code
     * AC-PRV-03} requires to be recorded exactly.
     */
    TOOL_INVOKED("tool.invoked"),

    /** A file's MD5 and SHA-256 were computed, so that the digests are in the log as they land. */
    FILE_HASHED("file.hashed"),

    /**
     * Something the run survived but a scientist must be told about -- a capability that was not
     * available, an input that was accepted with a caveat.
     */
    WARNING_RAISED("warning.raised"),

    /**
     * The run ended, carrying the terminal {@link
     * org.cometgui.provenance.manifest.ProvenanceStatus} it ended in.
     *
     * <p>An event of this type is required to carry that status; see {@link
     * ProvenanceEvent#STATUS_KEY}. A log whose last line says only "the run finished" cannot answer
     * the question {@code AC-PRV-06} asks of a failed run.
     */
    RUN_FINISHED("run.finished"),

    /**
     * A derived export was written from a run's results after the run: a filtered Percolator table
     * or the learned feature weights, as a new file under the run's {@code exports/} directory.
     *
     * <p>Added by phase 10 ({@code R-RES-01}: the result-view filter values are "written to
     * provenance when used to generate an export"; <em>Application provenance</em>: "result-view q
     * filters when used for a derived export"). The payload names the export file and its
     * checksums, the source table, the filter and category applied and the row counts; the keys are
     * pinned by {@code org.cometgui.results.export}, which writes the event. Unlike the seven above
     * it normally follows {@link #RUN_FINISHED}, because an export is made from a finished run;
     * neither the log nor its reader imposes an order between types.
     */
    EXPORT_WRITTEN("export.written"),

    /**
     * PDV was launched from a run: written <em>before</em> the process is started, so that a launch
     * that then fails -- PDV refuses its input, exits non-zero, never starts at all -- still leaves
     * its record.
     *
     * <p>Added by phase 11 (<em>Managed installation</em>: "Its JAR checksum and reported version
     * shall appear in provenance whenever PDV is launched from a run"; <em>Application
     * provenance</em>: "PDV launch and version when used"). The payload names the JAR, its MD5 and
     * SHA-256, the version its own manifest reports, the kind of launch, the argument array after
     * redaction and what the launch was for; the keys are pinned by {@code
     * org.cometgui.workflow.pdv}, the one path through which every PDV process is started. Like
     * {@link #EXPORT_WRITTEN} it normally follows {@link #RUN_FINISHED}, because the run's sealed
     * record is {@code provenance.json} and what happens to a finished run is appended to its event
     * log.
     */
    PDV_LAUNCHED("pdv.launched"),

    /**
     * The mzTab PDV's {@code denovo-gui} mode is given for a run was written, with the MGFs it
     * references, in the run's PDV directory.
     *
     * <p>Added by phase 11 ({@code R-PDV-02}: "The generated file is a run artefact: it is recorded
     * in provenance with its checksum like any other output"). The payload names the mzTab and each
     * MGF with their MD5 and SHA-256, every source file the export read with its MD5 and SHA-256,
     * and the PSM counts read from the Percolator tables and written to the mzTab; the keys are
     * pinned by {@code org.cometgui.workflow.pdv}, which writes the event. Like {@link
     * #PDV_LAUNCHED} it follows {@link #RUN_FINISHED}: the mzTab is made from a finished run.
     */
    MZTAB_WRITTEN("mztab.written"),

    /**
     * A Limelight conversion of a run is about to start: written <em>before</em> the converter's
     * process is started, so that a conversion that never ends -- the application closed, the
     * machine stopped -- still leaves its record.
     *
     * <p>Added by phase 12 (<em>Application provenance</em>: "Limelight conversion parameters";
     * design decision P12-5). The payload names the converter JAR, its MD5 and SHA-256 and the
     * version it reports, the argument array after redaction, the Limelight q-value cutoff and
     * options, and the MD5 and SHA-256 of every file the converter is given to read; the keys are
     * pinned by {@code org.cometgui.workflow.limelight}, the one path through which a conversion is
     * started. Exactly one of {@link #LIMELIGHT_CONVERTED} and {@link #LIMELIGHT_CONVERSION_FAILED}
     * follows it for the same output path, unless the application stopped first. Like {@link
     * #PDV_LAUNCHED} it follows {@link #RUN_FINISHED}.
     */
    LIMELIGHT_CONVERTING("limelight.converting"),

    /**
     * A Limelight conversion produced a Limelight XML file that passed every check ({@code
     * R-LL-04}) and is now at its final path, read-only.
     *
     * <p>Added by phase 12. The payload repeats {@link #LIMELIGHT_CONVERTING}'s and adds the output
     * file's path, size, MD5 and SHA-256, what it holds, and the converter log's path and
     * checksums.
     */
    LIMELIGHT_CONVERTED("limelight.converted"),

    /**
     * A Limelight conversion that was started ended without a Limelight XML file: the converter
     * failed, or what it wrote was judged not to be a valid Limelight XML file. Nothing is at the
     * output path.
     *
     * <p>Added by phase 12. The payload repeats {@link #LIMELIGHT_CONVERTING}'s and adds the named
     * failure and its reason; it carries no output checksum, because there is no output.
     */
    LIMELIGHT_CONVERSION_FAILED("limelight.unconverted"),

    /**
     * An upload of a run's Limelight XML to a Limelight server is about to start: written
     * <em>before</em> the first byte is sent, after the scientist confirmed the destination it
     * names, so that an upload that never ends still leaves its record.
     *
     * <p>Added by phase 12 ({@code R-SEC-04}; design decisions P12-10 and P12-15). The payload
     * names the server's normalised address, the project id and the project page, and the file's
     * path, size and SHA-256 as re-verified against the run's {@code limelight.converted} record;
     * the keys are pinned by {@code org.cometgui.workflow.limelight}, the one path through which an
     * upload is started. <strong>It never carries the key</strong>, in any form. Exactly one of
     * {@link #LIMELIGHT_UPLOADED} and {@link #LIMELIGHT_UPLOAD_FAILED} follows it, unless the
     * application stopped first. Like {@link #LIMELIGHT_CONVERTED} it follows {@link
     * #RUN_FINISHED}.
     */
    LIMELIGHT_UPLOADING("limelight.uploading"),

    /**
     * The server took the file and queued its import.
     *
     * <p>Added by phase 12. The payload repeats {@link #LIMELIGHT_UPLOADING}'s and adds the
     * server's own identifiers -- its {@code uploadKey} (the import's tracking id) and {@code
     * importerSubDir} -- the program version it reported, the bytes sent, and the upload log's path
     * and checksums.
     */
    LIMELIGHT_UPLOADED("limelight.uploaded"),

    /**
     * An upload that was started ended without the server queuing an import: the server refused a
     * step, or no answer came.
     *
     * <p>Added by phase 12. The payload repeats {@link #LIMELIGHT_UPLOADING}'s and adds the step,
     * the server's own flags and message (after redaction) or the transport failure, and the upload
     * log's path and checksums. The wire name mirrors {@link #LIMELIGHT_CONVERSION_FAILED}'s.
     */
    LIMELIGHT_UPLOAD_FAILED("limelight.unuploaded");

    private final String wireName;

    ProvenanceEventType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The token written to and read from the event log.
     *
     * <p>Stable across renames of the Java constant, and identical on every machine regardless of
     * the JVM default locale; see the class documentation for why both matter.
     *
     * @return the dotted lower-case wire name, never {@code null}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a wire name read from an event log back to its constant.
     *
     * <p>Exact match, deliberately: no trimming, no case folding, no "close enough", for the reason
     * given on {@code ProvenanceStatus.fromWireName}. A line whose type is {@code "Run.Started"}
     * was not written by this application.
     *
     * @param wire the token to resolve
     * @return the matching type
     * @throws NullPointerException if {@code wire} is {@code null}
     * @throws IllegalArgumentException if no type has that wire name, with a message naming the
     *     rejected value and listing what is accepted
     */
    public static ProvenanceEventType fromWireName(String wire) {
        Objects.requireNonNull(wire, "wire");
        for (ProvenanceEventType type : values()) {
            if (type.wireName.equals(wire)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "no provenance event type has the wire name \""
                        + wire
                        + "\"; expected one of [run.started, stage.started, stage.finished,"
                        + " tool.invoked, file.hashed, warning.raised, run.finished,"
                        + " export.written, pdv.launched, mztab.written, limelight.converting,"
                        + " limelight.converted, limelight.unconverted, limelight.uploading,"
                        + " limelight.uploaded, limelight.unuploaded]");
    }
}

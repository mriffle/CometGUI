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

package org.cometgui.provenance.report;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.io.AtomicDocumentWriter;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.provenance.report.RstWriter.Column;

/**
 * Generates {@code provenance.rst} -- the human-readable half of a run's provenance record.
 *
 * <p>The specification is one sentence long about this class and the sentence is the whole design:
 * "the human-readable {@code provenance.rst} report shall be generated from the same
 * machine-readable model, never maintained independently". So this class takes a {@link
 * ProvenanceManifest} and nothing else. It reads no file, probes no environment, holds no clock and
 * asks no other component a question. Every fact in the report is a field of the manifest, which is
 * the same object {@link org.cometgui.provenance.manifest.ManifestWriter} serialises to {@code
 * provenance.json}, and the two documents therefore cannot disagree about a run: there is nothing
 * for them to disagree with.
 *
 * <p><strong>The two writers are deliberately built the same way</strong> -- {@code redactingWith}
 * a rule set, a {@code render} that returns the exact text, a {@code writeTo} that puts it on disk
 * through {@link AtomicDocumentWriter}, timestamps through {@link CanonicalTimestamp}. Where the
 * JSON writer has {@link org.cometgui.provenance.json.JsonWriter}, this has {@link RstWriter}. A
 * later phase adding a field to the manifest has one shape to follow twice, and the structural test
 * beside this class fails until it has followed it twice.
 *
 * <h2>What the report contains</h2>
 *
 * <p>The sections are the specification's Provenance UI, in its order: a summary (run identifier,
 * status, start and end, tool versions, input and output counts), the application and environment
 * record including both locales, the settings, the tools (name, version, managed or local, path,
 * MD5, SHA-256, probed capabilities) with each invocation's argument array, working directory,
 * environment, timing, exit code and archived logs, and finally every input and output file with
 * its role, path, size, timestamp, both digests and its status.
 *
 * <h2>What is written, and the one value that is derived rather than carried</h2>
 *
 * <p>Every field of the report is a record component of {@link ProvenanceManifest} or of a record
 * reachable from it, which is what the test beside this class enumerates reflectively; anything
 * else would be a second source of truth.
 *
 * <p><strong>The duration is the single derived value, and it is derived through the shared {@link
 * CanonicalTimestamp#millisBetween}.</strong> {@code AC-PRV-05} requires a duration to be recorded
 * for every process, and the artefact <em>is</em> the record: a duration that exists only as a
 * method on a model the reader has to reconstruct is not recorded in the file. It is deliberately
 * not a component of any record, so no third number travels with the model for the two instants to
 * contradict; and because both documents derive it from the same truncated instants with the same
 * function, {@code provenance.json} and this report cannot disagree about it by the millisecond the
 * truncation costs.
 *
 * <p>Section headings and field names never carry run data either. A heading is followed by an
 * underline exactly as long as itself, so a tool called {@code comet` } in a heading would be a
 * document that either fails {@code sphinx-build -n -W} or renders as something else; the tool's
 * name is the first field of its section instead, where it is escaped like every other value.
 *
 * <h2>Redaction</h2>
 *
 * <p>Every value in the document has been through the {@link SecretRedactor} inside {@link
 * RstWriter}; there is no writer without one. On top of that, this class redacts the two carriers
 * whose secrets are positional rather than textual -- the argument array, where {@code --password}
 * makes the <em>next</em> element a credential, and the process environment, where a variable named
 * {@code GITHUB_TOKEN} holds one whatever its value looks like. Both are idempotent, so the values
 * pass through the text rules again on their way into the document and lose nothing by it. This is
 * the same arrangement, for the same reason, as the JSON writer's.
 *
 * <p><strong>Immutable and thread-safe.</strong> One instance may render any number of manifests on
 * any number of threads; each call builds its own {@link RstWriter}.
 */
public final class ProvenanceReportWriter {

    /** The name the report always has inside a run directory, beside {@code provenance.json}. */
    public static final String FILE_NAME = "provenance.rst";

    /** The document title, and therefore the length of its overline and underline. */
    private static final String TITLE = "CometGUI provenance record";

    /** The columns of the summary's tool table. */
    private static final List<Column> TOOL_COLUMNS =
            List.of(new Column("Tool", 50), new Column("Version", 50));

    /** The columns of the settings table. */
    private static final List<Column> SETTING_COLUMNS =
            List.of(new Column("Setting", 40), new Column("Value", 60));

    /** The columns of the input and output table, in the order the specification lists them. */
    private static final List<Column> FILE_COLUMNS =
            List.of(
                    new Column("Direction", 8),
                    new Column("Role", 12),
                    new Column("Path", 26),
                    new Column("Size (bytes)", 10),
                    new Column("Modified", 14),
                    new Column("MD5", 14),
                    new Column("SHA-256", 16),
                    new Column("Status", 10));

    /** The one rule set, handed to every {@link RstWriter} this writer builds. */
    private final SecretRedactor redactor;

    /**
     * Use {@link #redactingWith(SecretRedactor)}: a report writer without a redactor is the leak
     * this design exists to prevent, so there is no constructor that permits one.
     *
     * @param redactor the rule set to apply
     */
    private ProvenanceReportWriter(SecretRedactor redactor) {
        this.redactor = redactor;
    }

    /**
     * Creates a writer that redacts with the given rule set.
     *
     * @param redactor the project's secret rule set, loaded with whatever credentials this run
     *     knows about
     * @return a writer
     * @throws NullPointerException if {@code redactor} is {@code null}
     */
    public static ProvenanceReportWriter redactingWith(SecretRedactor redactor) {
        return new ProvenanceReportWriter(Objects.requireNonNull(redactor, "redactor"));
    }

    /**
     * Renders a manifest as the exact text that {@link #writeTo(Path, ProvenanceManifest)} would
     * put on disk.
     *
     * <p>The same string, character for character, including the trailing newline: {@link
     * #writeTo(Path, ProvenanceManifest)} calls this and encodes the result as UTF-8. That is what
     * lets a test pin the document as a literal and know it has pinned the file.
     *
     * @param manifest the manifest to render
     * @return the whole {@code provenance.rst} document, ending in a newline
     * @throws NullPointerException if {@code manifest} is {@code null}
     */
    public String render(ProvenanceManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        RstWriter rst = RstWriter.redactingWith(redactor);
        rst.title(TITLE);
        rst.paragraph(
                "Generated from this run's provenance manifest -- the same model that produces",
                "``provenance.json``, and never maintained independently of it. Every value below",
                "is the manifest's; a fact missing here is missing from the manifest.");
        rst.paragraph(
                "Values are shown as inline literals. A value that reStructuredText cannot carry",
                "in one -- an empty value, or one holding a backtick, a control character or an",
                "edge of whitespace -- is shown instead as a double-quoted string with backslash",
                "escapes, so that ``\\u0060`` is a backtick and ``\\n`` is a line feed;",
                "``provenance.json`` carries the exact characters either way. A field with no",
                "value at all reads " + RstWriter.ABSENT + ".");
        writeSummary(rst, manifest);
        writeApplication(rst, manifest.application());
        writeSettings(rst, manifest.settings());
        writeTools(rst, manifest.tools());
        writeFiles(rst, manifest.files());
        return rst.finish();
    }

    /**
     * Writes {@code provenance.rst} into a run directory, atomically.
     *
     * @param runDirectory the run's own directory, which must already exist
     * @param manifest the manifest to render
     * @return the path that was written
     * @throws IOException if the directory does not exist or the document cannot be written; in
     *     every failure the existing file is left exactly as it was
     * @throws NullPointerException if either argument is {@code null}
     */
    public Path writeInto(Path runDirectory, ProvenanceManifest manifest) throws IOException {
        Objects.requireNonNull(runDirectory, "runDirectory");
        Path target = runDirectory.resolve(FILE_NAME);
        writeTo(target, manifest);
        return target;
    }

    /**
     * Writes a report to an exact path, atomically.
     *
     * <p>The whole document is rendered before anything is opened, and goes to disk through {@link
     * AtomicDocumentWriter}: temporary file beside the target, forced to the device, then renamed
     * over it. A reader of {@code provenance.rst} therefore observes the whole previous document or
     * the whole new one and never a truncated file -- which matters more for a report than it looks
     * like it should, because a half-written reStructuredText document is a document that fails the
     * documentation build rather than one that is obviously incomplete.
     *
     * @param target the file to create or replace
     * @param manifest the manifest to render
     * @throws IOException if the target's directory does not exist, if the target is a directory,
     *     or if the document cannot be written; in every failure the existing file is left exactly
     *     as it was
     * @throws NullPointerException if either argument is {@code null}
     */
    public void writeTo(Path target, ProvenanceManifest manifest) throws IOException {
        Objects.requireNonNull(target, "target");
        AtomicDocumentWriter.write(target, render(manifest));
    }

    /**
     * Describes the writer without naming a secret it might be holding.
     *
     * @return a description safe to put in a log line
     */
    @Override
    public String toString() {
        return "ProvenanceReportWriter[" + redactor + "]";
    }

    /**
     * Writes the summary section: what the specification's Provenance UI puts at the top.
     *
     * @param rst the document being built
     * @param manifest the manifest
     */
    private static void writeSummary(RstWriter rst, ProvenanceManifest manifest) {
        RunRecord run = manifest.run();
        rst.section("Summary");
        rst.fieldValue("Schema version", Integer.toString(manifest.schemaVersion()));
        rst.fieldValue("Run ID", run.runId().value());
        rst.fieldValue("Project", run.projectId());
        rst.fieldValue("Status", run.status().wireName());
        rst.fieldValue("Started", CanonicalTimestamp.utcMillis(run.start()));
        optionalTimestamp(rst, "Ended", run.end());
        optionalDuration(rst, run.start(), run.end());
        rst.fieldValue("Inputs", Long.toString(count(manifest.files(), FileDirection.INPUT)));
        rst.fieldValue("Outputs", Long.toString(count(manifest.files(), FileDirection.OUTPUT)));
        if (manifest.tools().isEmpty()) {
            rst.paragraph("No tool version is recorded for this run.");
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (ToolRecord tool : manifest.tools()) {
            rows.add(List.of(tool.name(), tool.version()));
        }
        rst.listTable(TOOL_COLUMNS, rows);
    }

    /**
     * Writes the application and environment section.
     *
     * <p>Both locales are here because {@code R-PROV-04} is about both: the default locale is what
     * a library reads, and the {@link java.util.Locale.Category#FORMAT} default is what {@code
     * String.format} reads, and a {@code comet.params} file written with commas for decimal points
     * is explained by whichever of the two was set.
     *
     * @param rst the document being built
     * @param application the application record
     */
    private static void writeApplication(RstWriter rst, ApplicationRecord application) {
        rst.section("Application and environment");
        rst.fieldValue("CometGUI version", application.cometGuiVersion());
        rst.fieldValue("Build identifier", application.buildIdentifier());
        rst.fieldValue("Operating system", application.osName());
        rst.fieldValue("Operating system version", application.osVersion());
        rst.fieldValue("Architecture", application.architecture());
        rst.fieldValue("Java runtime", application.jvmVersion());
        rst.fieldValue("Default locale", application.locale().toLanguageTag());
        rst.fieldValue("Format locale", application.formatLocale().toLanguageTag());
        rst.fieldValue("Time zone", application.zoneId().getId());
    }

    /**
     * Writes the settings section.
     *
     * @param rst the document being built
     * @param settings the run's scientific and export settings
     */
    private static void writeSettings(RstWriter rst, Map<String, String> settings) {
        rst.section("Settings");
        if (settings.isEmpty()) {
            rst.paragraph("No scientific or export setting is recorded for this run.");
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            rows.add(List.of(setting.getKey(), setting.getValue()));
        }
        rst.listTable(SETTING_COLUMNS, rows);
    }

    /**
     * Writes the tools section, one subsection per invocation.
     *
     * <p>The subsections are numbered rather than named after the tool, for the reason given on the
     * class: a heading carrying run data is a heading whose underline length and inline markup are
     * decided by the run.
     *
     * @param rst the document being built
     * @param tools the tool records, in the order they ran
     */
    private void writeTools(RstWriter rst, List<ToolRecord> tools) {
        rst.section("Tools");
        if (tools.isEmpty()) {
            rst.paragraph("No tool invocation is recorded for this run.");
            return;
        }
        int number = 0;
        for (ToolRecord tool : tools) {
            number++;
            rst.subsection("Tool " + number);
            writeTool(rst, tool);
        }
    }

    /**
     * Writes one tool: its identity, then what happened when it ran.
     *
     * @param rst the document being built
     * @param tool the tool record
     */
    private void writeTool(RstWriter rst, ToolRecord tool) {
        ExecutionRecord execution = tool.execution();
        ToolCommand command = execution.command();
        rst.fieldValue("Name", tool.name());
        rst.fieldValue("Version", tool.version());
        optionalField(rst, "Release tag", tool.releaseTag());
        rst.fieldValue("Origin", tool.managed() ? "managed" : "local");
        optionalField(rst, "Artefact", tool.artefactIdentity());
        optionalField(rst, "Stage", tool.stageId());
        rst.fieldValue("Executable", tool.executablePath().toString());
        rst.fieldValue("MD5", tool.hashes().md5());
        rst.fieldValue("SHA-256", tool.hashes().sha256());
        rst.fieldValues("Capabilities", tool.capabilities());
        rst.fieldValue("Working directory", command.workingDirectory().toString());
        rst.fieldValue("Started", CanonicalTimestamp.utcMillis(execution.start()));
        rst.fieldValue("Ended", CanonicalTimestamp.utcMillis(execution.end()));
        rst.fieldValue(
                "Duration (ms)",
                Long.toString(
                        CanonicalTimestamp.millisBetween(execution.start(), execution.end())));
        rst.fieldValue("Exit code", Integer.toString(execution.exitCode()));
        rst.fieldValue("Execution status", execution.status().wireName());
        writeLog(rst, "Stdout", execution.stdout());
        writeLog(rst, "Stderr", execution.stderr());
        // Redacted here, before the writer's own text rules see them, because both carry secrets
        // the text rules cannot recognise: the value after --password looks like any other word,
        // and a variable named AWS_SECRET_ACCESS_KEY is a credential whatever its value resembles.
        rst.fieldBullets("Command", redactor.redactArgv(command.argv()));
        rst.fieldMapping("Environment", redactor.redactEnvironment(command.environment()));
        rst.fieldBullets("Warnings", tool.warnings());
    }

    /**
     * Writes one archived log stream: its path and both digests, or three absences.
     *
     * <p>Three fields rather than one, and all three present even when the stream was not captured,
     * so that two tool subsections line up line for line and a reader can diff them.
     *
     * @param rst the document being built
     * @param stream the stream's name, {@code Stdout} or {@code Stderr}
     * @param log the archived log, if there was one
     */
    private static void writeLog(RstWriter rst, String stream, Optional<LogRecord> log) {
        if (log.isEmpty()) {
            rst.fieldAbsent(stream + " log");
            rst.fieldAbsent(stream + " MD5");
            rst.fieldAbsent(stream + " SHA-256");
            return;
        }
        LogRecord present = log.get();
        rst.fieldValue(stream + " log", present.path().toString());
        rst.fieldValue(stream + " MD5", present.hashes().md5());
        rst.fieldValue(stream + " SHA-256", present.hashes().sha256());
    }

    /**
     * Writes the input and output table.
     *
     * @param rst the document being built
     * @param files the file records
     */
    private static void writeFiles(RstWriter rst, List<FileRecord> files) {
        rst.section("Inputs and outputs");
        if (files.isEmpty()) {
            rst.paragraph("No input or output file is recorded for this run.");
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (FileRecord file : files) {
            rows.add(
                    List.of(
                            file.direction().wireName(),
                            file.role(),
                            file.path().toString(),
                            Long.toString(file.sizeBytes()),
                            CanonicalTimestamp.utcMillis(file.modifiedAt()),
                            file.hashes().md5(),
                            file.hashes().sha256(),
                            file.status().wireName()));
        }
        rst.listTable(FILE_COLUMNS, rows);
    }

    /**
     * Writes a present value, or {@code (none)} where there is none.
     *
     * @param rst the document being built
     * @param name the field name
     * @param text the value, if any
     */
    private static void optionalField(RstWriter rst, String name, Optional<String> text) {
        if (text.isEmpty()) {
            rst.fieldAbsent(name);
            return;
        }
        rst.fieldValue(name, text.get());
    }

    /**
     * Writes a present instant, or {@code (none)} where there is none.
     *
     * <p>An absent end is the honest description of a run that is still going, and of one that was
     * interrupted and never wrote its end. {@code R-PROV-05} requires a crash to leave useful
     * history, so a report of a run in progress is a normal document rather than an edge case.
     *
     * @param rst the document being built
     * @param name the field name
     * @param instant the instant, if any
     */
    private static void optionalTimestamp(RstWriter rst, String name, Optional<Instant> instant) {
        if (instant.isEmpty()) {
            rst.fieldAbsent(name);
            return;
        }
        rst.fieldValue(name, CanonicalTimestamp.utcMillis(instant.get()));
    }

    /**
     * Writes the elapsed milliseconds between two instants, or {@code (none)} while the run has not
     * finished.
     *
     * <p><strong>Derived through {@link CanonicalTimestamp#millisBetween}, which is the whole
     * point.</strong> That shared function truncates both instants to milliseconds before
     * subtracting, so the answer is the one a reader can recompute from the timestamps this
     * document actually prints: a run starting at {@code 09:14:00.250999999Z} is printed as
     * starting at {@code .250Z}, and its duration follows from {@code .250Z}. {@code
     * provenance.json} calls the same function, so the pair generated from one model cannot
     * disagree by the millisecond the truncation costs.
     *
     * @param rst the document being built
     * @param start the earlier instant
     * @param end the later instant, absent while the run is still going
     */
    private static void optionalDuration(RstWriter rst, Instant start, Optional<Instant> end) {
        if (end.isEmpty()) {
            rst.fieldAbsent("Duration (ms)");
            return;
        }
        rst.fieldValue(
                "Duration (ms)", Long.toString(CanonicalTimestamp.millisBetween(start, end.get())));
    }

    /**
     * Counts the files on one side of the run.
     *
     * @param files the file records
     * @param direction the side to count
     * @return how many files went that way
     */
    private static long count(List<FileRecord> files, FileDirection direction) {
        return files.stream().filter(file -> file.direction() == direction).count();
    }
}

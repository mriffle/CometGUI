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

package org.cometgui.provenance.manifest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.io.AtomicDocumentWriter;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.provenance.json.JsonWriter;

/**
 * Serialises a {@link ProvenanceManifest} to {@code provenance.json}.
 *
 * <p>This is the half of {@code R-PROV-05} that produces the document; {@link AtomicDocumentWriter}
 * is the half that puts it on disk without a reader ever seeing it half-written, and {@link
 * JsonWriter} is the half that decides what a byte of it looks like. Nothing here re-implements
 * either.
 *
 * <p><strong>The schema version is the first field, before anything else in the document.</strong>
 * A reader has to decide how to read a document before it has read it. With the version first, a
 * streaming reader knows on its first member whether it understands the rest; with the version
 * buried, a reader must parse a document under assumptions it cannot yet justify, and a version-2
 * document would be half-consumed by a version-1 reader before the disagreement surfaced.
 *
 * <h2>The field order, which is part of the format</h2>
 *
 * <p>Object members are written in a fixed order chosen here, not in whatever order a map iterated.
 * Two runs that differ only in their data must produce documents that differ only in that data, or
 * a diff of two provenance records is unreadable. The order is
 *
 * <ol>
 *   <li>{@code schemaVersion}, {@code run}, {@code application}, {@code settings}, {@code tools},
 *       {@code files} at the root -- identity, then environment, then configuration, then what was
 *       done, then what was touched;
 *   <li>within each record, the components in the order the record type declares them, so that the
 *       document and the Java type can be read side by side -- with {@code durationMillis} inserted
 *       directly after {@code end}, being derived from it.
 * </ol>
 *
 * <p>The two open-ended maps -- {@code settings} and a command's {@code environment} -- are written
 * in ascending key order by {@link JsonWriter#sortedObject(Map)}. Arrays keep their order, because
 * an argument array reordered is a different command.
 *
 * <h2>Three conventions a reader may rely on</h2>
 *
 * <dl>
 *   <dt>Absent optional fields are written as {@code null}, never omitted.
 *   <dd>Every document of a given schema version therefore has exactly the same set of keys, which
 *       makes two records line-comparable and makes a missing key mean something quite different
 *       from an empty one: a key that is <em>absent</em> is a schema disagreement worth failing on,
 *       while a key that is {@code null} is this run saying it has no such value. A run still in
 *       progress has {@code "end": null} and that is the honest description of it.
 *   <dt>Timestamps are UTC with exactly three fractional digits.
 *   <dd>See {@link CanonicalTimestamp}, including the fact that the truncation to milliseconds is
 *       real and a round trip is lossy below one.
 *   <dt>A {@link java.util.Locale} is its BCP 47 language tag and a {@link java.time.ZoneId} is its
 *       zone id.
 *   <dd>Both are stable identifiers. A display name is not: it is itself locale-dependent, so a
 *       German JVM would record {@code "Vereinigte Staaten"} where an English one recorded {@code
 *       "United States"}, and the field that exists to explain a locale-dependent difference would
 *       be the one field that had it.
 * </dl>
 *
 * <h2>The duration, which is derived at write time and never carried</h2>
 *
 * <p>{@code AC-PRV-05} requires that "start, end, duration and exit code are recorded for every
 * process", and the provenance record <em>is</em> the artefact: a duration that exists only as a
 * method on a model the reader has to reconstruct is not recorded in the file. So {@code
 * durationMillis} is written, in {@code run} and in every {@code execution}, immediately after
 * {@code end} so that it reads beside the two numbers it comes from.
 *
 * <p><strong>It is computed here, at serialisation, and is never a component of any record
 * type.</strong> That is what removes the risk of a stored duration: there is no third number
 * travelling with the model for the two instants to disagree with, and the value in the document is
 * a pure function of the two timestamps printed next to it. {@link
 * CanonicalTimestamp#millisBetween} derives it from the <em>truncated</em> instants for exactly
 * that reason -- the document shows milliseconds, so the duration must be the one a reader can
 * recompute from what the document shows, not from nanoseconds the document does not contain.
 *
 * <p>A reader should therefore <strong>validate</strong> {@code durationMillis} against {@code
 * start} and {@code end} rather than store it: a document whose duration disagrees with its own
 * timestamps is corrupt. A run still in progress has {@code "end": null} and {@code
 * "durationMillis": null}, because a run that has not finished has not taken a length of time yet.
 *
 * <h2>Redaction</h2>
 *
 * <p>Every string value in the document has been through the {@link SecretRedactor} inside {@link
 * JsonWriter#value(String)}; there is no writer without one. On top of that, this class redacts the
 * two carriers whose secrets are positional rather than textual -- the argument array, where {@code
 * --password} makes the <em>next</em> element a credential, and the process environment, where a
 * variable named {@code GITHUB_TOKEN} holds one whatever its value looks like. Both are idempotent,
 * so the values pass through the text rules again on their way into the document and lose nothing
 * by it.
 *
 * <p><strong>Immutable and thread-safe.</strong> One instance may serialise any number of manifests
 * on any number of threads; each call builds its own {@link JsonWriter}.
 */
public final class ManifestWriter {

    /** The name the manifest always has inside a run directory, from {@code R-PROV-05}. */
    public static final String FILE_NAME = "provenance.json";

    /** The one rule set, handed to every {@link JsonWriter} this writer builds. */
    private final SecretRedactor redactor;

    /**
     * Use {@link #redactingWith(SecretRedactor)}: a manifest writer without a redactor is the leak
     * this design exists to prevent, so there is no constructor that permits one.
     *
     * @param redactor the rule set to apply
     */
    private ManifestWriter(SecretRedactor redactor) {
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
    public static ManifestWriter redactingWith(SecretRedactor redactor) {
        return new ManifestWriter(Objects.requireNonNull(redactor, "redactor"));
    }

    /**
     * Renders a manifest as the exact text that {@link #writeTo(Path, ProvenanceManifest)} would
     * put on disk.
     *
     * <p>The same string, character for character, including the trailing newline: {@link
     * #writeTo(Path, ProvenanceManifest)} calls this and encodes the result as UTF-8. That is what
     * lets a test pin the document as a literal and know it has pinned the file.
     *
     * @param manifest the manifest to serialise
     * @return the whole {@code provenance.json} document, ending in a newline
     * @throws NullPointerException if {@code manifest} is {@code null}
     */
    public String render(ProvenanceManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        JsonWriter json = JsonWriter.redactingWith(redactor);
        json.beginObject();
        json.name("schemaVersion").value(manifest.schemaVersion());
        json.name("run");
        writeRun(json, manifest.run());
        json.name("application");
        writeApplication(json, manifest.application());
        json.name("settings").sortedObject(manifest.settings());
        json.name("tools").beginArray();
        for (ToolRecord tool : manifest.tools()) {
            writeTool(json, tool);
        }
        json.endArray();
        json.name("files").beginArray();
        for (FileRecord file : manifest.files()) {
            writeFile(json, file);
        }
        json.endArray();
        json.endObject();
        return json.finish();
    }

    /**
     * Writes {@code provenance.json} into a run directory, atomically.
     *
     * @param runDirectory the run's own directory, which must already exist
     * @param manifest the manifest to write
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
     * Writes a manifest to an exact path, atomically.
     *
     * <p>The whole document is rendered before anything is opened, and goes to disk through {@link
     * AtomicDocumentWriter}: temporary file beside the target, forced to the device, then renamed
     * over it. A reader of {@code provenance.json} therefore observes the whole previous document
     * or the whole new one and never a truncated file, which is exit gate item 5 of this phase and
     * the reason this class does not open the target itself.
     *
     * @param target the file to create or replace
     * @param manifest the manifest to write
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
        return "ManifestWriter[" + redactor + "]";
    }

    /**
     * Writes the {@code run} object.
     *
     * @param json the document being built
     * @param run the run record
     */
    private static void writeRun(JsonWriter json, RunRecord run) {
        json.beginObject();
        json.name("runId").value(run.runId().value());
        json.name("projectId").value(run.projectId());
        json.name("status").value(run.status().wireName());
        json.name("start").value(CanonicalTimestamp.utcMillis(run.start()));
        json.name("end");
        writeOptionalTimestamp(json, run.end());
        json.name("durationMillis");
        if (run.end().isEmpty()) {
            json.nullValue();
        } else {
            json.value(CanonicalTimestamp.millisBetween(run.start(), run.end().get()));
        }
        json.endObject();
    }

    /**
     * Writes the {@code application} object.
     *
     * @param json the document being built
     * @param application the application record
     */
    private static void writeApplication(JsonWriter json, ApplicationRecord application) {
        json.beginObject();
        json.name("cometGuiVersion").value(application.cometGuiVersion());
        json.name("buildIdentifier").value(application.buildIdentifier());
        json.name("osName").value(application.osName());
        json.name("osVersion").value(application.osVersion());
        json.name("architecture").value(application.architecture());
        json.name("jvmVersion").value(application.jvmVersion());
        json.name("locale").value(application.locale().toLanguageTag());
        json.name("formatLocale").value(application.formatLocale().toLanguageTag());
        json.name("zoneId").value(application.zoneId().getId());
        json.endObject();
    }

    /**
     * Writes one element of the {@code tools} array.
     *
     * @param json the document being built
     * @param tool the tool record
     */
    private void writeTool(JsonWriter json, ToolRecord tool) {
        json.beginObject();
        json.name("name").value(tool.name());
        json.name("version").value(tool.version());
        json.name("releaseTag");
        writeOptionalString(json, tool.releaseTag());
        json.name("executablePath").value(tool.executablePath().toString());
        writeHashes(json, tool.hashes());
        json.name("managed").value(tool.managed());
        json.name("artefactIdentity");
        writeOptionalString(json, tool.artefactIdentity());
        json.name("capabilities").arrayOfStrings(tool.capabilities());
        json.name("stageId");
        writeOptionalString(json, tool.stageId());
        json.name("execution");
        writeExecution(json, tool.execution());
        json.name("warnings").arrayOfStrings(tool.warnings());
        json.endObject();
    }

    /**
     * Writes the {@code execution} object of one tool.
     *
     * <p>The argument array and the environment are redacted <em>here</em>, before they reach the
     * writer, because both carry secrets the text rules cannot see on their own: the value after
     * {@code --password} looks like any other word, and a variable named {@code
     * AWS_SECRET_ACCESS_KEY} is a credential whatever its value resembles. The writer's own text
     * rules then run over the results, which is free because redaction is idempotent.
     *
     * @param json the document being built
     * @param execution the execution record
     */
    private void writeExecution(JsonWriter json, ExecutionRecord execution) {
        ToolCommand command = execution.command();
        json.beginObject();
        json.name("argv").arrayOfStrings(redactor.redactArgv(command.argv()));
        json.name("workingDirectory").value(command.workingDirectory().toString());
        json.name("environment").sortedObject(redactor.redactEnvironment(command.environment()));
        json.name("start").value(CanonicalTimestamp.utcMillis(execution.start()));
        json.name("end").value(CanonicalTimestamp.utcMillis(execution.end()));
        json.name("durationMillis")
                .value(CanonicalTimestamp.millisBetween(execution.start(), execution.end()));
        json.name("exitCode").value(execution.exitCode());
        json.name("stdout");
        writeOptionalLog(json, execution.stdout());
        json.name("stderr");
        writeOptionalLog(json, execution.stderr());
        json.name("status").value(execution.status().wireName());
        json.endObject();
    }

    /**
     * Writes one element of the {@code files} array.
     *
     * @param json the document being built
     * @param file the file record
     */
    private static void writeFile(JsonWriter json, FileRecord file) {
        json.beginObject();
        json.name("direction").value(file.direction().wireName());
        json.name("role").value(file.role());
        json.name("path").value(file.path().toString());
        json.name("sizeBytes").value(file.sizeBytes());
        json.name("modifiedAt").value(CanonicalTimestamp.utcMillis(file.modifiedAt()));
        writeHashes(json, file.hashes());
        json.name("status").value(file.status().wireName());
        json.endObject();
    }

    /**
     * Writes a captured log stream, or {@code null} where there was none.
     *
     * @param json the document being built
     * @param log the captured log, if any
     */
    private static void writeOptionalLog(JsonWriter json, Optional<LogRecord> log) {
        if (log.isEmpty()) {
            json.nullValue();
            return;
        }
        LogRecord present = log.get();
        json.beginObject();
        json.name("path").value(present.path().toString());
        writeHashes(json, present.hashes());
        json.endObject();
    }

    /**
     * Writes the two digests of one file as sibling members.
     *
     * <p>Flat rather than nested under a {@code hashes} object. Wherever a {@link FileHashes}
     * appears there is exactly one file in scope, so a nesting level would carry no information --
     * and it would cost two columns of indentation on the two longest lines in the document, which
     * matters when the format has to be readable and hand-checkable.
     *
     * @param json the document being built
     * @param hashes the digest pair
     */
    private static void writeHashes(JsonWriter json, FileHashes hashes) {
        json.name("md5").value(hashes.md5());
        json.name("sha256").value(hashes.sha256());
    }

    /**
     * Writes a present string, or {@code null} where there is none.
     *
     * @param json the document being built
     * @param value the value, if any
     */
    private static void writeOptionalString(JsonWriter json, Optional<String> value) {
        if (value.isEmpty()) {
            json.nullValue();
            return;
        }
        json.value(value.get());
    }

    /**
     * Writes a present instant, or {@code null} where there is none.
     *
     * @param json the document being built
     * @param instant the instant, if any
     */
    private static void writeOptionalTimestamp(JsonWriter json, Optional<Instant> instant) {
        if (instant.isEmpty()) {
            json.nullValue();
            return;
        }
        json.value(CanonicalTimestamp.utcMillis(instant.get()));
    }
}

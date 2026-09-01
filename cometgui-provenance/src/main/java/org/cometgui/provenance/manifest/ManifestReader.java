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
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.DecimalStyle;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.provenance.json.JsonParseException;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * Rebuilds a {@link ProvenanceManifest} from the {@code provenance.json} {@link ManifestWriter}
 * produced.
 *
 * <p>The inverse of {@link ManifestWriter}, and deliberately not its mirror image. A reader that
 * was written by inverting the writer line by line would agree with the writer about every mistake
 * the writer makes; this one is written against the format -- the key names, the field order, the
 * conventions {@link ManifestWriter} documents -- and its tests parse hand-typed documents and
 * assert hand-typed values, with no writer involved. A round trip through the two proves they
 * agree, which is a weaker statement than either being right.
 *
 * <h2>The schema version decides everything, and it is decided first</h2>
 *
 * <p>{@link ProvenanceSchema#VERSION} is a compatibility statement, and this class enforces the
 * three rules that constant documents. The version is resolved before any other member of the
 * document is looked at, so a document this build cannot interpret is refused for that reason and
 * not for whatever else happens to be wrong with it. (The JSON itself is parsed first, by {@link
 * JsonReader}; that step is generic and decides nothing about meaning.)
 *
 * <ul>
 *   <li><strong>A higher version is refused outright.</strong> Not "read the fields I recognise": a
 *       newer writer may have changed what a field <em>means</em> rather than merely added one, and
 *       a half-understood provenance record is worse than an unreadable one because it is wrong
 *       without saying so. The message says plainly which version the document declares and which
 *       one this build reads.
 *   <li><strong>A lower version is refused until something migrates it.</strong> The policy in
 *       {@link ProvenanceSchema#VERSION} is that an older document "must migrate it explicitly, so
 *       that the fields a later version added have declared values rather than silently absent
 *       ones". There is no migration to run today -- version 1 is the first published format, so
 *       nothing below it was ever written -- and a version below 1 is therefore a corrupt or
 *       invented document. When version 2 exists, the migration goes here, and until it does the
 *       reader refuses rather than guessing at the missing fields.
 *   <li><strong>A member this build does not know is ignored.</strong> That is not leniency, it is
 *       the other half of the same policy: {@link ProvenanceSchema#VERSION} states that "adding an
 *       optional field that an older reader can ignore does not require a bump", so an older reader
 *       has to be able to ignore one. Every field this build <em>does</em> know is required to be
 *       present, so a mistyped key is caught as the missing key it displaced rather than passing as
 *       an unknown extra.
 * </ul>
 *
 * <h2>Absent and null are different, and both are checked</h2>
 *
 * <p>{@link ManifestWriter} writes an absent optional as {@code null} and never omits the key, so
 * every document of a version carries the same key set. A missing key is therefore a disagreement
 * about the schema and is rejected; a {@code null} is this run saying it has no such value, and is
 * accepted wherever the model has an {@link Optional}. A {@code null} where the model has no
 * optional is rejected too.
 *
 * <h2>{@code durationMillis} is verified, never read</h2>
 *
 * <p>The document records a duration beside each pair of instants because {@code AC-PRV-05}
 * requires one, and {@link RunRecord#duration()} and {@link ExecutionRecord#duration()} derive
 * theirs from the instants. This reader does not put the recorded number into the model -- that
 * would create the second source of truth the model exists without -- it <strong>checks</strong>
 * it: the number in the document must equal {@link CanonicalTimestamp#millisBetween} of the two
 * timestamps printed beside it, and a document whose duration contradicts its own instants is
 * refused. A run with no end has no duration, and a number there is refused as well.
 *
 * <h2>Every model invariant still applies</h2>
 *
 * <p>Nothing here re-implements a validation. A digest that is not hexadecimal, a negative size, a
 * relative path, a blank role, an end before a start, a settings key that is not dotted lower-case:
 * each is rejected by the record that owns the rule, at the moment this class tries to construct
 * it, exactly as it would be rejected in a program that built the manifest by hand.
 *
 * <h2>No value from the document ever reaches a message</h2>
 *
 * <p>This is the same rule {@link JsonParseException} states, carried into the semantic layer, and
 * it has two visible consequences.
 *
 * <ul>
 *   <li>A rejection names the <em>member</em> -- {@code "tools[0].execution.exitCode"} -- and the
 *       rule it broke. Member names are part of the schema and are literals in this repository, so
 *       naming one discloses nothing. The rejected value is never printed.
 *   <li>When a value is rejected by something outside this class -- a record constructor, {@link
 *       ProvenanceStatus#fromWireName}, {@link java.time.format.DateTimeFormatter} -- that
 *       exception is <strong>not</strong> attached as a cause, because every one of those messages
 *       quotes the value it rejected. {@code FileHashes} says {@code md5 must be 32 hexadecimal
 *       characters, but was: "..."}, and a stack trace carrying it would put whatever was in that
 *       position of a hostile document straight into a log. The class of the underlying rejection
 *       is named instead, and the reader who wants the value opens the file at the member the
 *       message names.
 * </ul>
 *
 * <p>The one exception is a number this build has to be able to talk about: the schema version and
 * the recorded duration appear in their own messages, because "this document declares version 2 and
 * this build reads version 1" is the entire content of that message and a schema version is not a
 * value a run supplies.
 */
public final class ManifestReader {

    /**
     * The member path of the document root, which is the empty one every other path grows from.
     *
     * <p>Empty rather than {@code "$"} or {@code "root"}: {@link #child(String, String)} turns it
     * into the bare member name, so a root member is reported as {@code "schemaVersion"} and a
     * nested one as {@code "run.status"}, which is how a reader of the message would name them.
     */
    private static final String ROOT = "";

    /**
     * The formatter every timestamp in a document is parsed with.
     *
     * <p>Built from {@link CanonicalTimestamp#PATTERN} so that the reader cannot drift from the
     * writer, and pinned exactly as the writer's is: {@link Locale#ROOT} and {@link
     * DecimalStyle#STANDARD} so that no JVM default locale reaches the digits, and {@link
     * ZoneOffset#UTC} so that a pattern with no zone field still resolves to an {@link Instant}.
     *
     * <p><strong>{@link ResolverStyle#STRICT}, and that is not decoration.</strong> Under the
     * default {@code SMART} style {@code 2026-02-30} does not fail: it resolves to 28 February, so
     * a corrupted date would be read back as a different, plausible one. A provenance timestamp
     * that silently changes value is exactly the failure this whole phase exists to prevent.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern(CanonicalTimestamp.PATTERN, Locale.ROOT)
                    .withZone(ZoneOffset.UTC)
                    .withDecimalStyle(DecimalStyle.STANDARD)
                    .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Never instantiated: reading a manifest is one operation over a document, with no state.
     *
     * <p>It throws rather than being an empty private constructor so that the intent is enforced
     * for the one caller that can still reach it -- reflection -- instead of merely being implied.
     */
    private ManifestReader() {
        throw new AssertionError("ManifestReader is a utility class and is never instantiated");
    }

    /**
     * Rebuilds a manifest from the text of a {@code provenance.json} document.
     *
     * @param document the whole document, as {@link ManifestWriter#render} produced it
     * @return the manifest the document describes
     * @throws NullPointerException if {@code document} is {@code null}
     * @throws InvalidManifestException if the text is not well-formed JSON, if the schema version
     *     is not one this build reads, or if the document is not a manifest this build's model
     *     accepts
     */
    public static ProvenanceManifest parse(String document) {
        JsonValue root;
        try {
            root = JsonReader.parse(document);
        } catch (JsonParseException malformed) {
            throw new InvalidManifestException(
                    "the provenance manifest is not well-formed JSON: " + malformed.getMessage(),
                    malformed);
        }
        return read(object(root, ""));
    }

    /**
     * Reads a manifest from an exact path.
     *
     * <p>UTF-8, and strictly: {@link Files#readString(Path, java.nio.charset.Charset)} reports a
     * malformed byte sequence rather than replacing it with {@code U+FFFD}, so a file that is not
     * the UTF-8 the writer produced fails here instead of arriving as a document with the wrong
     * characters in it.
     *
     * @param file the document to read
     * @return the manifest the file describes
     * @throws IOException if the file cannot be read, or does not hold UTF-8 text
     * @throws NullPointerException if {@code file} is {@code null}
     * @throws InvalidManifestException if the file is not a manifest this build reads
     */
    public static ProvenanceManifest readFrom(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Reads the {@code provenance.json} of a run directory.
     *
     * <p>The inverse of {@link ManifestWriter#writeInto(Path, ProvenanceManifest)}, resolving the
     * same {@link ManifestWriter#FILE_NAME} so that the name lives in one place.
     *
     * @param runDirectory the run's own directory
     * @return the manifest the run recorded
     * @throws IOException if the file is missing or cannot be read
     * @throws NullPointerException if {@code runDirectory} is {@code null}
     * @throws InvalidManifestException if the file is not a manifest this build reads
     */
    public static ProvenanceManifest readIn(Path runDirectory) throws IOException {
        return readFrom(runDirectory.resolve(ManifestWriter.FILE_NAME));
    }

    /**
     * Reads the root object, version first.
     *
     * @param root the document's root object
     * @return the manifest
     */
    private static ProvenanceManifest read(JsonValue.JsonObject root) {
        int schemaVersion =
                integerWithinInt(member(root, ROOT, "schemaVersion"), child(ROOT, "schemaVersion"));
        requireReadableVersion(schemaVersion);

        RunRecord run = readRun(object(member(root, ROOT, "run"), child(ROOT, "run")));
        ApplicationRecord application =
                readApplication(
                        object(member(root, ROOT, "application"), child(ROOT, "application")));
        Map<String, String> settings =
                readStringMap(
                        object(member(root, ROOT, "settings"), child(ROOT, "settings")),
                        child(ROOT, "settings"));
        List<ToolRecord> tools = new ArrayList<>();
        List<JsonValue> toolElements =
                array(member(root, ROOT, "tools"), child(ROOT, "tools")).elements();
        for (int index = 0; index < toolElements.size(); index++) {
            String path = element(child(ROOT, "tools"), index);
            tools.add(readTool(object(toolElements.get(index), path), path));
        }
        List<FileRecord> files = new ArrayList<>();
        List<JsonValue> fileElements =
                array(member(root, ROOT, "files"), child(ROOT, "files")).elements();
        for (int index = 0; index < fileElements.size(); index++) {
            String path = element(child(ROOT, "files"), index);
            files.add(readFile(object(fileElements.get(index), path), path));
        }
        return rebuilt(
                ROOT,
                () ->
                        new ProvenanceManifest(
                                schemaVersion, run, application, settings, tools, files));
    }

    /**
     * Applies the three rules {@link ProvenanceSchema#VERSION} documents.
     *
     * @param schemaVersion the version the document declares
     */
    private static void requireReadableVersion(int schemaVersion) {
        if (schemaVersion > ProvenanceSchema.VERSION) {
            throw new InvalidManifestException(
                    "this provenance manifest declares schema version "
                            + schemaVersion
                            + ", and this build of CometGUI reads version "
                            + ProvenanceSchema.VERSION
                            + "; it was written by a newer CometGUI, which may have changed what a"
                            + " field means rather than only added one, so it is refused rather"
                            + " than half-understood");
        }
        if (schemaVersion < ProvenanceSchema.VERSION) {
            throw new InvalidManifestException(
                    "this provenance manifest declares schema version "
                            + schemaVersion
                            + ", and this build of CometGUI reads version "
                            + ProvenanceSchema.VERSION
                            + "; an older document must be migrated explicitly, and no migration to"
                            + " version "
                            + ProvenanceSchema.VERSION
                            + " is registered");
        }
    }

    /**
     * Reads the {@code run} object.
     *
     * @param run the object
     * @return the run record
     */
    private static RunRecord readRun(JsonValue.JsonObject run) {
        RunId runId =
                rebuilt(
                        child("run", "runId"),
                        () ->
                                new RunId(
                                        string(
                                                member(run, "run", "runId"),
                                                child("run", "runId"))));
        String projectId = string(member(run, "run", "projectId"), child("run", "projectId"));
        ProvenanceStatus status = status(run, "run");
        Instant start = timestamp(member(run, "run", "start"), child("run", "start"));
        Optional<Instant> end = optionalTimestamp(member(run, "run", "end"), child("run", "end"));
        requireRecordedDuration(run, "run", start, end);
        return rebuilt("run", () -> new RunRecord(runId, projectId, status, start, end));
    }

    /**
     * Reads the {@code application} object.
     *
     * @param application the object
     * @return the application record
     */
    private static ApplicationRecord readApplication(JsonValue.JsonObject application) {
        String path = "application";
        String cometGuiVersion = text(application, path, "cometGuiVersion");
        String buildIdentifier = text(application, path, "buildIdentifier");
        String osName = text(application, path, "osName");
        String osVersion = text(application, path, "osVersion");
        String architecture = text(application, path, "architecture");
        String jvmVersion = text(application, path, "jvmVersion");
        Locale locale = locale(application, path, "locale");
        Locale formatLocale = locale(application, path, "formatLocale");
        ZoneId zoneId = zone(application, path, "zoneId");
        return rebuilt(
                path,
                () ->
                        new ApplicationRecord(
                                cometGuiVersion,
                                buildIdentifier,
                                osName,
                                osVersion,
                                architecture,
                                jvmVersion,
                                locale,
                                formatLocale,
                                zoneId));
    }

    /**
     * Reads one element of the {@code tools} array.
     *
     * @param tool the object
     * @param path the member path of this element
     * @return the tool record
     */
    private static ToolRecord readTool(JsonValue.JsonObject tool, String path) {
        String name = text(tool, path, "name");
        String version = text(tool, path, "version");
        Optional<String> releaseTag = optionalText(tool, path, "releaseTag");
        Path executablePath = filePath(tool, path, "executablePath");
        FileHashes hashes = hashes(tool, path);
        boolean managed = flag(member(tool, path, "managed"), child(path, "managed"));
        Optional<String> artefactIdentity = optionalText(tool, path, "artefactIdentity");
        Set<String> capabilities = new LinkedHashSet<>(strings(tool, path, "capabilities"));
        Optional<String> stageId = optionalText(tool, path, "stageId");
        String executionPath = child(path, "execution");
        ExecutionRecord execution =
                readExecution(
                        object(member(tool, path, "execution"), executionPath), executionPath);
        List<String> warnings = strings(tool, path, "warnings");
        return rebuilt(
                path,
                () ->
                        new ToolRecord(
                                name,
                                version,
                                releaseTag,
                                executablePath,
                                hashes,
                                managed,
                                artefactIdentity,
                                capabilities,
                                stageId,
                                execution,
                                warnings));
    }

    /**
     * Reads the {@code execution} object of one tool.
     *
     * @param execution the object
     * @param path the member path of the object
     * @return the execution record
     */
    private static ExecutionRecord readExecution(JsonValue.JsonObject execution, String path) {
        List<String> argv = strings(execution, path, "argv");
        Path workingDirectory = filePath(execution, path, "workingDirectory");
        Map<String, String> environment =
                readStringMap(
                        object(member(execution, path, "environment"), child(path, "environment")),
                        child(path, "environment"));
        ToolCommand command =
                rebuilt(path, () -> new ToolCommand(argv, workingDirectory, environment));
        Instant start = timestamp(member(execution, path, "start"), child(path, "start"));
        Instant end = timestamp(member(execution, path, "end"), child(path, "end"));
        requireRecordedDuration(execution, path, start, Optional.of(end));
        int exitCode =
                integerWithinInt(member(execution, path, "exitCode"), child(path, "exitCode"));
        Optional<LogRecord> stdout = readLog(execution, path, "stdout");
        Optional<LogRecord> stderr = readLog(execution, path, "stderr");
        ProvenanceStatus status = status(execution, path);
        return rebuilt(
                path,
                () -> new ExecutionRecord(command, start, end, exitCode, stdout, stderr, status));
    }

    /**
     * Reads one element of the {@code files} array.
     *
     * @param file the object
     * @param path the member path of this element
     * @return the file record
     */
    private static FileRecord readFile(JsonValue.JsonObject file, String path) {
        FileDirection direction = direction(file, path);
        String role = text(file, path, "role");
        Path filePath = filePath(file, path, "path");
        long sizeBytes = integer(member(file, path, "sizeBytes"), child(path, "sizeBytes"));
        Instant modifiedAt = timestamp(member(file, path, "modifiedAt"), child(path, "modifiedAt"));
        FileHashes hashes = hashes(file, path);
        ProvenanceStatus status = status(file, path);
        return rebuilt(
                path,
                () ->
                        new FileRecord(
                                direction, role, filePath, sizeBytes, modifiedAt, hashes, status));
    }

    /**
     * Reads a captured log, which is an object or {@code null}.
     *
     * @param owner the execution object
     * @param ownerPath the execution's member path
     * @param name {@code stdout} or {@code stderr}
     * @return the log record, or empty where the document says {@code null}
     */
    private static Optional<LogRecord> readLog(
            JsonValue.JsonObject owner, String ownerPath, String name) {
        String path = child(ownerPath, name);
        JsonValue value = member(owner, ownerPath, name);
        if (value instanceof JsonValue.JsonNull) {
            return Optional.empty();
        }
        JsonValue.JsonObject log = object(value, path);
        Path file = filePath(log, path, "path");
        FileHashes hashes = hashes(log, path);
        return Optional.of(rebuilt(path, () -> new LogRecord(file, hashes)));
    }

    /**
     * Reads the two digests written as siblings of the record that owns them.
     *
     * @param owner the object holding {@code md5} and {@code sha256}
     * @param ownerPath the owner's member path
     * @return the digest pair
     */
    private static FileHashes hashes(JsonValue.JsonObject owner, String ownerPath) {
        String md5 = text(owner, ownerPath, "md5");
        String sha256 = text(owner, ownerPath, "sha256");
        return rebuilt(child(ownerPath, "md5"), () -> new FileHashes(md5, sha256));
    }

    /**
     * Checks the recorded duration against the two instants it was derived from.
     *
     * @param owner the object holding {@code durationMillis}
     * @param ownerPath the owner's member path
     * @param start the interval's start, as the document records it
     * @param end the interval's end, absent where the document records none
     */
    private static void requireRecordedDuration(
            JsonValue.JsonObject owner, String ownerPath, Instant start, Optional<Instant> end) {
        String path = child(ownerPath, "durationMillis");
        JsonValue value = member(owner, ownerPath, "durationMillis");
        if (end.isEmpty()) {
            if (!(value instanceof JsonValue.JsonNull)) {
                throw invalid(
                        path,
                        "must be null while the end is null, because an interval that has not"
                                + " ended has no duration");
            }
            return;
        }
        if (value instanceof JsonValue.JsonNull) {
            throw invalid(path, "must not be null once the interval has an end");
        }
        long recorded = integer(value, path);
        long elapsed;
        try {
            elapsed = CanonicalTimestamp.millisBetween(start, end.get());
        } catch (ArithmeticException beyondALong) {
            // The two timestamps are further apart than a long of milliseconds can express --
            // about 292 million years -- which the format's own year field permits and no run
            // produces.  Refused as an invalid manifest rather than allowed to leave this class
            // as an ArithmeticException, because a caller of a strict reader catches
            // InvalidManifestException and nothing else, and every other hostile document is
            // already refused that way.
            throw invalid(
                    path,
                    "cannot be checked, because the start and end recorded beside it are more"
                            + " milliseconds apart than a signed 64-bit integer can hold");
        }
        if (recorded != elapsed) {
            throw invalid(
                    path,
                    "is "
                            + recorded
                            + ", but the start and end recorded beside it are "
                            + elapsed
                            + " milliseconds apart");
        }
    }

    /**
     * Reads an object of strings, such as {@code settings} or an {@code environment}.
     *
     * @param owner the object to read
     * @param path the object's member path
     * @return the members, as text
     */
    private static Map<String, String> readStringMap(JsonValue.JsonObject owner, String path) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : owner.members().entrySet()) {
            entries.put(entry.getKey(), string(entry.getValue(), child(path, entry.getKey())));
        }
        return entries;
    }

    /**
     * Reads an array of strings.
     *
     * @param owner the object holding the array
     * @param ownerPath the owner's member path
     * @param name the array's member name
     * @return the elements, as text
     */
    private static List<String> strings(JsonValue.JsonObject owner, String ownerPath, String name) {
        String path = child(ownerPath, name);
        List<JsonValue> elements = array(member(owner, ownerPath, name), path).elements();
        List<String> values = new ArrayList<>();
        for (int index = 0; index < elements.size(); index++) {
            values.add(string(elements.get(index), element(path, index)));
        }
        return values;
    }

    /**
     * Reads a required string member.
     *
     * @param owner the object holding it
     * @param ownerPath the owner's member path
     * @param name the member name
     * @return the text
     */
    private static String text(JsonValue.JsonObject owner, String ownerPath, String name) {
        return string(member(owner, ownerPath, name), child(ownerPath, name));
    }

    /**
     * Reads a string member that the document may record as {@code null}.
     *
     * @param owner the object holding it
     * @param ownerPath the owner's member path
     * @param name the member name
     * @return the text, or empty where the document says {@code null}
     */
    private static Optional<String> optionalText(
            JsonValue.JsonObject owner, String ownerPath, String name) {
        JsonValue value = member(owner, ownerPath, name);
        if (value instanceof JsonValue.JsonNull) {
            return Optional.empty();
        }
        return Optional.of(string(value, child(ownerPath, name)));
    }

    /**
     * Reads a path member.
     *
     * @param owner the object holding it
     * @param ownerPath the owner's member path
     * @param name the member name
     * @return the path
     */
    private static Path filePath(JsonValue.JsonObject owner, String ownerPath, String name) {
        String path = child(ownerPath, name);
        String value = string(member(owner, ownerPath, name), path);
        return rebuilt(path, () -> Path.of(value));
    }

    /**
     * Reads a language tag member.
     *
     * <p>{@link Locale#forLanguageTag(String)} never fails: handed something that is not a language
     * tag at all it returns {@link Locale#ROOT}, so a corrupted tag would be read back as "no
     * locale" and the field that exists to explain a locale-dependent difference would be the field
     * that quietly lost its value. The tag is therefore re-rendered and required to match, which
     * accepts exactly what {@link Locale#toLanguageTag()} writes and nothing else.
     *
     * @param owner the object holding it
     * @param ownerPath the owner's member path
     * @param name the member name
     * @return the locale
     */
    private static Locale locale(JsonValue.JsonObject owner, String ownerPath, String name) {
        String path = child(ownerPath, name);
        String tag = string(member(owner, ownerPath, name), path);
        Locale parsed = Locale.forLanguageTag(tag);
        if (!parsed.toLanguageTag().equals(tag)) {
            throw invalid(
                    path,
                    "must be a BCP 47 language tag in the form Locale.toLanguageTag() writes");
        }
        return parsed;
    }

    /**
     * Reads a time-zone member.
     *
     * @param owner the object holding it
     * @param ownerPath the owner's member path
     * @param name the member name
     * @return the zone
     */
    private static ZoneId zone(JsonValue.JsonObject owner, String ownerPath, String name) {
        String path = child(ownerPath, name);
        String id = string(member(owner, ownerPath, name), path);
        return rebuilt(path, () -> ZoneId.of(id));
    }

    /**
     * Reads a {@code status} member through {@link ProvenanceStatus#fromWireName}.
     *
     * @param owner the object holding it
     * @param ownerPath the owner's member path
     * @return the status
     */
    private static ProvenanceStatus status(JsonValue.JsonObject owner, String ownerPath) {
        String path = child(ownerPath, "status");
        String wire = string(member(owner, ownerPath, "status"), path);
        try {
            return ProvenanceStatus.fromWireName(wire);
        } catch (IllegalArgumentException notAStatus) {
            throw invalid(
                    path,
                    "must be one of "
                            + Arrays.stream(ProvenanceStatus.values())
                                    .map(ProvenanceStatus::wireName)
                                    .toList());
        }
    }

    /**
     * Reads a {@code direction} member through {@link FileDirection#fromWireName}.
     *
     * @param owner the object holding it
     * @param ownerPath the owner's member path
     * @return the direction
     */
    private static FileDirection direction(JsonValue.JsonObject owner, String ownerPath) {
        String path = child(ownerPath, "direction");
        String wire = string(member(owner, ownerPath, "direction"), path);
        try {
            return FileDirection.fromWireName(wire);
        } catch (IllegalArgumentException notADirection) {
            throw invalid(
                    path,
                    "must be one of "
                            + Arrays.stream(FileDirection.values())
                                    .map(FileDirection::wireName)
                                    .toList());
        }
    }

    /**
     * Reads a timestamp member.
     *
     * @param value the member's value
     * @param path the member's path
     * @return the instant
     */
    private static Instant timestamp(JsonValue value, String path) {
        String text = string(value, path);
        try {
            return TIMESTAMP.parse(text, Instant::from);
        } catch (DateTimeParseException notATimestamp) {
            throw invalid(
                    path, "must be a UTC timestamp of the form " + CanonicalTimestamp.PATTERN);
        }
    }

    /**
     * Reads a timestamp member that the document may record as {@code null}.
     *
     * @param value the member's value
     * @param path the member's path
     * @return the instant, or empty where the document says {@code null}
     */
    private static Optional<Instant> optionalTimestamp(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonNull) {
            return Optional.empty();
        }
        return Optional.of(timestamp(value, path));
    }

    /**
     * Looks up a member that every document of this schema version must carry.
     *
     * @param owner the object to look in
     * @param ownerPath the owner's member path
     * @param name the member name
     * @return the member's value
     */
    private static JsonValue member(JsonValue.JsonObject owner, String ownerPath, String name) {
        return owner.member(name)
                .orElseThrow(() -> invalid(ownerPath, "has no member \"" + name + "\""));
    }

    /**
     * Requires a value to be an object.
     *
     * @param value the value
     * @param path the value's member path
     * @return the object
     */
    private static JsonValue.JsonObject object(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonObject asObject) {
            return asObject;
        }
        throw invalid(path, "must be a JSON object");
    }

    /**
     * Requires a value to be an array.
     *
     * @param value the value
     * @param path the value's member path
     * @return the array
     */
    private static JsonValue.JsonArray array(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonArray asArray) {
            return asArray;
        }
        throw invalid(path, "must be a JSON array");
    }

    /**
     * Requires a value to be a string.
     *
     * @param value the value
     * @param path the value's member path
     * @return the text
     */
    private static String string(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonString asString) {
            return asString.value();
        }
        throw invalid(path, "must be a string");
    }

    /**
     * Requires a value to be a whole number.
     *
     * @param value the value
     * @param path the value's member path
     * @return the number
     */
    private static long integer(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonNumber asNumber) {
            return asNumber.value();
        }
        throw invalid(path, "must be a whole number");
    }

    /**
     * Requires a value to be a whole number that fits in an {@code int}.
     *
     * @param value the value
     * @param path the value's member path
     * @return the number
     */
    private static int integerWithinInt(JsonValue value, String path) {
        long number = integer(value, path);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalid(path, "must fit in a signed 32-bit integer");
        }
        return (int) number;
    }

    /**
     * Requires a value to be a boolean.
     *
     * @param value the value
     * @param path the value's member path
     * @return the flag
     */
    private static boolean flag(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonBoolean asBoolean) {
            return asBoolean.value();
        }
        throw invalid(path, "must be true or false");
    }

    /**
     * Builds part of the model, turning any rejection into one that quotes no value.
     *
     * <p><strong>The supplier must contain nothing but a constructor call over values that were
     * already read.</strong> Reading a member inside it would put this {@code catch} in the way of
     * a rejection that is already precise about which member was wrong, and replace that member's
     * path with the path of whatever record happened to be under construction around it. The tests
     * that assert a precise path -- {@code application.zoneId}, {@code application.formatLocale} --
     * are what fail if anyone ever nests a read in here.
     *
     * @param <T> the type being built
     * @param path the member path the values came from
     * @param construction the constructor call to attempt
     * @return whatever it built
     */
    private static <T> T rebuilt(String path, Supplier<T> construction) {
        try {
            return construction.get();
        } catch (RuntimeException rejected) {
            throw invalid(
                    path,
                    "was rejected by the manifest model ("
                            + rejected.getClass().getSimpleName()
                            + "); the model's own message is not repeated here, because it quotes"
                            + " the value it rejected");
        }
    }

    /**
     * The member path of a member of another member.
     *
     * @param ownerPath the owner's path, empty for the document root
     * @param name the member name
     * @return the child's path
     */
    private static String child(String ownerPath, String name) {
        return ownerPath.isEmpty() ? name : ownerPath + "." + name;
    }

    /**
     * The member path of one element of an array.
     *
     * @param arrayPath the array's path
     * @param index the element's index
     * @return the element's path
     */
    private static String element(String arrayPath, int index) {
        return arrayPath + "[" + index + "]";
    }

    /**
     * A rejection naming the member and the rule, and nothing from the document.
     *
     * @param path the member path, empty for the document root
     * @param problem what that member did wrong
     * @return the exception to throw
     */
    private static InvalidManifestException invalid(String path, String problem) {
        String subject = path.isEmpty() ? "the document" : "\"" + path + "\"";
        return new InvalidManifestException(
                "the provenance manifest is not valid: " + subject + " " + problem);
    }

    /**
     * Thrown when a document is not a provenance manifest this build can read.
     *
     * <p>One type for all three ways that can be true -- the text is not JSON, the schema version
     * is not this one, or the content is not something the model accepts -- because a caller's
     * response to all three is the same: this run's record cannot be shown, and here is which
     * member of which file to look at.
     *
     * <p>See the enclosing class for the rule every message obeys: the member path and the rule,
     * and never a value out of the document.
     */
    public static final class InvalidManifestException extends RuntimeException {

        @Serial private static final long serialVersionUID = 1L;

        /**
         * Creates the exception.
         *
         * @param message which member broke which rule, quoting no value from the document
         */
        InvalidManifestException(String message) {
            super(message);
        }

        /**
         * Creates the exception with the parse failure that caused it.
         *
         * <p>A {@link JsonParseException} is the one cause it is safe to attach, because that type
         * promises its own message quotes no document content. No other rejection is attached; see
         * the enclosing class.
         *
         * @param message which member broke which rule, quoting no value from the document
         * @param cause the parse failure, whose message is a rule and a position
         */
        InvalidManifestException(String message, JsonParseException cause) {
            super(message, cause);
        }
    }
}

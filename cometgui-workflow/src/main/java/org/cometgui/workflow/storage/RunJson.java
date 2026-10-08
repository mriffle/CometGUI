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

package org.cometgui.workflow.storage;

import static org.cometgui.workflow.storage.DocumentFields.ROOT;
import static org.cometgui.workflow.storage.DocumentFields.child;
import static org.cometgui.workflow.storage.DocumentFields.element;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.domain.run.RecordedFingerprint;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.provenance.json.JsonValue;
import org.cometgui.provenance.json.JsonWriter;

/**
 * The {@code run.json} format, schema versions 1 and 2: written by the one {@link JsonWriter}, read
 * by the one {@code JsonReader}.
 *
 * <h2>Two versions</h2>
 *
 * <p>Version 2 is version 1 plus one member, {@code derivedFrom}, between {@code databaseDelivery}
 * and {@code attempts}: the run a derived run reuses the Comet results of ({@link RunDerivation}).
 * A run is written at the lowest version that can express it ({@link
 * RunDescriptor#schemaVersion()}): a run that executes its own search is version 1, byte for byte
 * as Phase 08 wrote it, and a derived run is version 2. This reader reads both. A version-1
 * document with a {@code derivedFrom} member, and a version-2 document without an object there, are
 * refused like any other malformed document; anything below 1 or above 2 is refused by {@code
 * R-RUN-04}'s policy before any other member is read.
 *
 * <p>The identity members come first, in the order of {@link RunIdentity}'s components, then {@code
 * attempts}. Every member is always present -- an absent optional is {@code null}, never omitted --
 * and no other member is accepted, because {@link RunStore#update} rewrites the file and would
 * otherwise drop what it did not understand. {@code docs/reference/project_format.rst} is the
 * format's reference, member by member.
 *
 * <h2>Derived members are verified, not read</h2>
 *
 * <p>Each spectrum entry carries its {@code stageId}, which is a function of its position, so that
 * a reader can map {@code logs/comet-02.log} to its file without code. This reader requires the
 * recorded value to equal the derived one and never uses it otherwise; and each entry's {@code
 * base} must be the one {@link OutputBaseNames} gives the recorded paths in order.
 */
public final class RunJson {

    /** The root members, in the order written. */
    static final List<String> MEMBERS =
            List.of(
                    "schemaVersion",
                    "runId",
                    "projectId",
                    "created",
                    "cometRelease",
                    "spectra",
                    "fasta",
                    "parameters",
                    "indexMode",
                    "databaseDelivery",
                    "attempts");

    /** The root members of a derived run's document (version 2), in the order written. */
    static final List<String> DERIVED_MEMBERS =
            List.of(
                    "schemaVersion",
                    "runId",
                    "projectId",
                    "created",
                    "cometRelease",
                    "spectra",
                    "fasta",
                    "parameters",
                    "indexMode",
                    "databaseDelivery",
                    "derivedFrom",
                    "attempts");

    /** The members of {@code derivedFrom}, in the order written. */
    static final List<String> DERIVATION_MEMBERS =
            List.of("runId", "created", "provenance", "mergedPin");

    /** A spectrum entry's members, in the order written. */
    static final List<String> SPECTRUM_MEMBERS =
            List.of("position", "stageId", "base", "path", "size", "modified", "md5", "sha256");

    /** The FASTA's members, in the order written. */
    static final List<String> INPUT_MEMBERS = List.of("path", "size", "modified", "md5", "sha256");

    /** The parameter file's members, in the order written. */
    static final List<String> ARCHIVED_MEMBERS = List.of("path", "size", "md5", "sha256");

    /** An attempt's members, in the order written. */
    static final List<String> ATTEMPT_MEMBERS =
            List.of("number", "started", "ended", "outcome", "succeededSteps");

    /** A recorded fingerprint's members, in the order written. */
    static final List<String> FINGERPRINT_MEMBERS = List.of("fingerprint", "inputDigests");

    private RunJson() {
        throw new AssertionError("RunJson is never instantiated");
    }

    /**
     * Renders a run's record.
     *
     * @param descriptor the record
     * @return the document
     * @throws NullPointerException if {@code descriptor} is {@code null}
     */
    public static String render(RunDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        RunIdentity identity = descriptor.identity();
        JsonWriter json =
                JsonWriter.redactingWith(SecretRedactor.patternsOnly())
                        .beginObject()
                        .name("schemaVersion")
                        .value(descriptor.schemaVersion())
                        .name("runId")
                        .value(identity.runId().value())
                        .name("projectId")
                        .value(identity.projectId().value())
                        .name("created")
                        .value(CanonicalTimestamp.utcMillis(identity.created()))
                        .name("cometRelease")
                        .value(identity.cometRelease())
                        .name("spectra")
                        .beginArray();
        for (SpectrumInput spectrum : identity.spectra()) {
            json.beginObject()
                    .name("position")
                    .value(spectrum.position())
                    .name("stageId")
                    .value(spectrum.stageId())
                    .name("base")
                    .value(spectrum.base());
            writeRecordedInput(json, spectrum.file());
            json.endObject();
        }
        json.endArray().name("fasta").beginObject();
        writeRecordedInput(json, identity.fasta());
        json.endObject().name("parameters");
        writeArchived(json, identity.parameters());
        json.name("indexMode")
                .value(identity.indexMode().wireName())
                .name("databaseDelivery")
                .value(identity.databaseDelivery().wireName());
        if (identity.derivedFrom().isPresent()) {
            RunDerivation derivation = identity.derivedFrom().get();
            json.name("derivedFrom")
                    .beginObject()
                    .name("runId")
                    .value(derivation.runId().value())
                    .name("created")
                    .value(CanonicalTimestamp.utcMillis(derivation.created()))
                    .name("provenance");
            writeArchived(json, derivation.provenance());
            json.name("mergedPin");
            writeArchived(json, derivation.mergedPin());
            json.endObject();
        }
        json.name("attempts").beginArray();
        for (RunAttempt attempt : descriptor.attempts()) {
            writeAttempt(json, attempt);
        }
        return json.endArray().endObject().finish();
    }

    private static void writeRecordedInput(JsonWriter json, RecordedInput input) {
        json.name("path")
                .value(input.path().toString())
                .name("size")
                .value(input.size())
                .name("modified")
                .value(CanonicalTimestamp.utcMillis(input.modified()));
        writeHashes(json, input.hashes());
    }

    private static void writeArchived(JsonWriter json, ArchivedFile file) {
        json.beginObject().name("path").value(file.path()).name("size").value(file.size());
        writeHashes(json, file.hashes());
        json.endObject();
    }

    private static void writeHashes(JsonWriter json, FileHashes hashes) {
        json.name("md5").value(hashes.md5()).name("sha256").value(hashes.sha256());
    }

    private static void writeAttempt(JsonWriter json, RunAttempt attempt) {
        json.beginObject()
                .name("number")
                .value(attempt.number())
                .name("started")
                .value(CanonicalTimestamp.utcMillis(attempt.started()))
                .name("ended");
        if (attempt.ended().isPresent()) {
            json.value(CanonicalTimestamp.utcMillis(attempt.ended().get()));
        } else {
            json.nullValue();
        }
        json.name("outcome")
                .value(attempt.outcome().wireName())
                .name("succeededSteps")
                .beginObject();
        for (Map.Entry<String, RecordedFingerprint> step : attempt.succeededSteps().entrySet()) {
            json.name(step.getKey())
                    .beginObject()
                    .name("fingerprint")
                    .value(step.getValue().value())
                    .name("inputDigests")
                    .sortedObject(step.getValue().inputDigests())
                    .endObject();
        }
        json.endObject().endObject();
    }

    /**
     * Reads a run's record, its schema version first.
     *
     * @param text the document's text
     * @param document the document's path or label, for messages
     * @return the record
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException if the document is of
     *     another schema version, before any other member is read
     * @throws InvalidDocumentException if the document is not a version-1 or version-2 {@code
     *     run.json}
     */
    public static RunDescriptor parse(String text, String document) {
        Objects.requireNonNull(text, "text");
        DocumentFields fields = new DocumentFields(document);
        JsonValue.JsonObject root = fields.root(text);
        boolean derived =
                fields.requireVersionBetween(
                                root,
                                RunDescriptor.SCHEMA_VERSION,
                                RunDescriptor.DERIVED_SCHEMA_VERSION)
                        == RunDescriptor.DERIVED_SCHEMA_VERSION;
        fields.requireOnly(root, ROOT, derived ? DERIVED_MEMBERS : MEMBERS);

        String runIdText = fields.string(fields.member(root, ROOT, "runId"), "runId");
        RunId runId = fields.rebuilt("runId", () -> new RunId(runIdText));
        String projectIdText = fields.string(fields.member(root, ROOT, "projectId"), "projectId");
        ProjectId projectId = fields.rebuilt("projectId", () -> new ProjectId(projectIdText));
        Instant created = fields.timestamp(fields.member(root, ROOT, "created"), "created");
        String release = fields.string(fields.member(root, ROOT, "cometRelease"), "cometRelease");
        if (release.isBlank() || release.chars().anyMatch(Character::isISOControl)) {
            throw fields.invalid("cometRelease", "must be non-blank and hold no control character");
        }
        List<SpectrumInput> spectra = readSpectra(fields, fields.member(root, ROOT, "spectra"));
        RecordedInput fasta =
                readRecordedInput(
                        fields,
                        fields.object(fields.member(root, ROOT, "fasta"), "fasta"),
                        "fasta",
                        INPUT_MEMBERS);
        ArchivedFile parameters =
                readArchived(
                        fields,
                        fields.object(fields.member(root, ROOT, "parameters"), "parameters"),
                        "parameters");
        if (!parameters.path().startsWith(RunLayout.PARAMETERS_DIRECTORY_NAME + "/")) {
            throw fields.invalid(
                    child("parameters", "path"),
                    "must name a file under " + RunLayout.PARAMETERS_DIRECTORY_NAME + "/");
        }
        IndexMode indexMode =
                wire(
                        fields,
                        "indexMode",
                        fields.string(fields.member(root, ROOT, "indexMode"), "indexMode"),
                        IndexMode.values(),
                        IndexMode::wireName);
        DatabaseDelivery delivery =
                wire(
                        fields,
                        "databaseDelivery",
                        fields.string(
                                fields.member(root, ROOT, "databaseDelivery"), "databaseDelivery"),
                        DatabaseDelivery.values(),
                        DatabaseDelivery::wireName);
        Optional<RunDerivation> derivedFrom =
                derived
                        ? Optional.of(
                                readDerivation(
                                        fields,
                                        fields.object(
                                                fields.member(root, ROOT, "derivedFrom"),
                                                "derivedFrom")))
                        : Optional.empty();
        RunIdentity identity =
                fields.rebuilt(
                        ROOT,
                        () ->
                                new RunIdentity(
                                        runId,
                                        projectId,
                                        created,
                                        release,
                                        spectra,
                                        fasta,
                                        parameters,
                                        indexMode,
                                        delivery,
                                        derivedFrom));

        List<JsonValue> attemptElements =
                fields.array(fields.member(root, ROOT, "attempts"), "attempts");
        List<RunAttempt> attempts = new ArrayList<>(attemptElements.size());
        for (int index = 0; index < attemptElements.size(); index++) {
            String path = element("attempts", index);
            attempts.add(
                    readAttempt(fields, fields.object(attemptElements.get(index), path), path));
        }
        return fields.rebuilt("attempts", () -> new RunDescriptor(identity, attempts));
    }

    private static List<SpectrumInput> readSpectra(DocumentFields fields, JsonValue value) {
        List<JsonValue> elements = fields.array(value, "spectra");
        if (elements.isEmpty()) {
            throw fields.invalid("spectra", "must name at least one spectrum file");
        }
        List<RecordedInput> files = new ArrayList<>(elements.size());
        List<String> bases = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            String path = element("spectra", index);
            // Its member set is checked once, by readRecordedInput, with SPECTRUM_MEMBERS.
            JsonValue.JsonObject entry = fields.object(elements.get(index), path);
            int position =
                    fields.smallInteger(
                            fields.member(entry, path, "position"), child(path, "position"));
            if (position != index + 1) {
                throw fields.invalid(
                        child(path, "position"),
                        "must be " + (index + 1) + ": positions run 1, 2, 3 ... in input order");
            }
            String stageId =
                    fields.string(fields.member(entry, path, "stageId"), child(path, "stageId"));
            if (!stageId.equals(RunLayout.cometStageId(position))) {
                throw fields.invalid(
                        child(path, "stageId"),
                        "must be "
                                + RunLayout.cometStageId(position)
                                + ", derived from the position");
            }
            bases.add(fields.string(fields.member(entry, path, "base"), child(path, "base")));
            files.add(readRecordedInput(fields, entry, path, SPECTRUM_MEMBERS));
        }
        List<Path> paths = files.stream().map(RecordedInput::path).toList();
        List<OutputBase> derived = fields.rebuilt("spectra", () -> OutputBaseNames.derive(paths));
        List<SpectrumInput> spectra = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            if (!bases.get(index).equals(derived.get(index).base())) {
                throw fields.invalid(
                        child(element("spectra", index), "base"),
                        "is not the name the output naming rule gives these inputs");
            }
            spectra.add(new SpectrumInput(index + 1, files.get(index), bases.get(index)));
        }
        return spectra;
    }

    private static RecordedInput readRecordedInput(
            DocumentFields fields, JsonValue.JsonObject owner, String path, List<String> members) {
        fields.requireOnly(owner, path, members);
        String text = fields.string(fields.member(owner, path, "path"), child(path, "path"));
        Path file = fields.rebuilt(child(path, "path"), () -> Path.of(text));
        long size = fields.integer(fields.member(owner, path, "size"), child(path, "size"));
        Instant modified =
                fields.timestamp(fields.member(owner, path, "modified"), child(path, "modified"));
        FileHashes hashes = fields.hashes(owner, path);
        return fields.rebuilt(path, () -> new RecordedInput(file, size, modified, hashes));
    }

    private static RunDerivation readDerivation(DocumentFields fields, JsonValue.JsonObject owner) {
        String path = "derivedFrom";
        fields.requireOnly(owner, path, DERIVATION_MEMBERS);
        String runIdText = fields.string(fields.member(owner, path, "runId"), child(path, "runId"));
        RunId runId = fields.rebuilt(child(path, "runId"), () -> new RunId(runIdText));
        Instant created =
                fields.timestamp(fields.member(owner, path, "created"), child(path, "created"));
        ArchivedFile provenance =
                readArchived(
                        fields,
                        fields.object(
                                fields.member(owner, path, "provenance"),
                                child(path, "provenance")),
                        child(path, "provenance"));
        ArchivedFile mergedPin =
                readArchived(
                        fields,
                        fields.object(
                                fields.member(owner, path, "mergedPin"), child(path, "mergedPin")),
                        child(path, "mergedPin"));
        return fields.rebuilt(path, () -> new RunDerivation(runId, created, provenance, mergedPin));
    }

    private static ArchivedFile readArchived(
            DocumentFields fields, JsonValue.JsonObject owner, String path) {
        fields.requireOnly(owner, path, ARCHIVED_MEMBERS);
        String file = fields.string(fields.member(owner, path, "path"), child(path, "path"));
        long size = fields.integer(fields.member(owner, path, "size"), child(path, "size"));
        FileHashes hashes = fields.hashes(owner, path);
        return fields.rebuilt(path, () -> new ArchivedFile(file, size, hashes));
    }

    private static RunAttempt readAttempt(
            DocumentFields fields, JsonValue.JsonObject attempt, String path) {
        fields.requireOnly(attempt, path, ATTEMPT_MEMBERS);
        int number =
                fields.smallInteger(fields.member(attempt, path, "number"), child(path, "number"));
        Instant started =
                fields.timestamp(fields.member(attempt, path, "started"), child(path, "started"));
        Optional<Instant> ended =
                fields.optionalTimestamp(
                        fields.member(attempt, path, "ended"), child(path, "ended"));
        AttemptOutcome outcome =
                wire(
                        fields,
                        child(path, "outcome"),
                        fields.string(
                                fields.member(attempt, path, "outcome"), child(path, "outcome")),
                        AttemptOutcome.values(),
                        AttemptOutcome::wireName);
        String stepsPath = child(path, "succeededSteps");
        JsonValue.JsonObject steps =
                fields.object(fields.member(attempt, path, "succeededSteps"), stepsPath);
        Map<String, RecordedFingerprint> succeeded = new TreeMap<>();
        for (Map.Entry<String, JsonValue> step : steps.members().entrySet()) {
            String stepId = identifier(fields, step.getKey(), stepsPath, "a step id");
            succeeded.put(
                    stepId, readFingerprint(fields, step.getValue(), child(stepsPath, stepId)));
        }
        return fields.rebuilt(
                path, () -> new RunAttempt(number, started, ended, outcome, succeeded));
    }

    private static RecordedFingerprint readFingerprint(
            DocumentFields fields, JsonValue value, String path) {
        JsonValue.JsonObject recorded = fields.object(value, path);
        fields.requireOnly(recorded, path, FINGERPRINT_MEMBERS);
        String fingerprint =
                fields.string(
                        fields.member(recorded, path, "fingerprint"), child(path, "fingerprint"));
        String digestsPath = child(path, "inputDigests");
        JsonValue.JsonObject digests =
                fields.object(fields.member(recorded, path, "inputDigests"), digestsPath);
        Map<String, String> inputDigests = new TreeMap<>();
        for (Map.Entry<String, JsonValue> digest : digests.members().entrySet()) {
            String kind = identifier(fields, digest.getKey(), digestsPath, "an input kind");
            inputDigests.put(kind, fields.string(digest.getValue(), child(digestsPath, kind)));
        }
        return fields.rebuilt(path, () -> new RecordedFingerprint(fingerprint, inputDigests));
    }

    private static String identifier(
            DocumentFields fields, String key, String ownerPath, String what) {
        if (!RecordedFingerprint.isIdentifier(key)) {
            throw fields.invalid(
                    ownerPath,
                    "has "
                            + what
                            + " that is not lower-case letters and digits in hyphen-separated"
                            + " words");
        }
        return key;
    }

    private static <E> E wire(
            DocumentFields fields, String path, String text, E[] values, Function<E, String> name) {
        for (E value : values) {
            if (name.apply(value).equals(text)) {
                return value;
            }
        }
        throw fields.invalid(path, "must be one of " + Arrays.stream(values).map(name).toList());
    }
}

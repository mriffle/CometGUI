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

package org.cometgui.params.comet.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.cometgui.params.comet.value.VariableModCodec;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * The validation corpus of the Comet 2026.03.0 intake ({@code fixtures/comet-validation/
 * corpus.json}): for each case, the edits made to each release's real {@code comet -q} fixture
 * (CONSTRUCTED input), what each real binary did with the result, and what the validator says for
 * that release. {@link ValidationCorpusTest} holds the validator to the recorded verdicts on every
 * platform; {@link ValidationCorpusRealBinaryTest} holds the real binaries to them on Linux.
 */
final class ValidationCorpus {

    /** Where the corpus is on the test class path. */
    static final String RESOURCE = "/fixtures/comet-validation/corpus.json";

    /** Stands for the database path in edits and in recorded lines. */
    static final String DATABASE = "${DATABASE}";

    /** One parameter set to one value text. */
    record Edit(String name, String value) {

        Edit at(String database) {
            return new Edit(name, value.replace(DATABASE, database));
        }
    }

    /**
     * What one release did with one case, and what the validator says.
     *
     * @param version the release
     * @param exit the binary's exit code
     * @param lines its Warning and Error lines beyond the release's control run
     * @param findings the validator's findings, each "SEVERITY rule.id", sorted
     * @param agreement {@code same} or {@code stricter}
     * @param reason why the validator is stricter; empty when it is not
     * @param builtInCode whether the release's codec refuses an edit, so that the validator's model
     *     holds it only as the editor would build it, in code
     */
    record Verdict(
            ToolVersion version,
            int exit,
            List<String> lines,
            List<String> findings,
            String agreement,
            Optional<String> reason,
            boolean builtInCode) {}

    /** One case. */
    record Case(String id, String topic, List<Edit> edits, List<Verdict> verdicts) {

        Verdict verdict(ToolVersion version) {
            return verdicts.stream()
                    .filter(v -> v.version().equals(version))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(id + " has no verdict for " + version));
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** A release's control run: the lines every case of that release prints anyway. */
    record Control(ToolVersion version, List<String> lines) {}

    /** The pinned local inputs a search reads (D-006: never committed). */
    record Inputs(
            String spectra,
            String spectraSha256,
            String lineFeedSha256,
            String database,
            String databaseSha256,
            int records,
            String subsetSha256) {}

    private final JsonValue.JsonObject root;

    private ValidationCorpus(JsonValue.JsonObject root) {
        this.root = root;
    }

    /**
     * The corpus on the class path.
     *
     * @return the corpus
     */
    static ValidationCorpus load() {
        try (InputStream in = ValidationCorpus.class.getResourceAsStream(RESOURCE)) {
            assertTrue(in != null, "the class path holds no " + RESOURCE);
            return new ValidationCorpus(
                    (JsonValue.JsonObject)
                            JsonReader.parse(
                                    new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static JsonValue member(JsonValue.JsonObject object, String name) {
        return object.member(name)
                .orElseThrow(() -> new AssertionError("the corpus has no \"" + name + "\""));
    }

    private static String text(JsonValue.JsonObject object, String name) {
        return ((JsonValue.JsonString) member(object, name)).value();
    }

    private static List<JsonValue.JsonObject> objects(JsonValue.JsonObject object, String name) {
        List<JsonValue.JsonObject> found = new ArrayList<>();
        for (JsonValue element : ((JsonValue.JsonArray) member(object, name)).elements()) {
            found.add((JsonValue.JsonObject) element);
        }
        return found;
    }

    private static List<String> texts(JsonValue.JsonObject object, String name) {
        List<String> found = new ArrayList<>();
        for (JsonValue element : ((JsonValue.JsonArray) member(object, name)).elements()) {
            found.add(((JsonValue.JsonString) element).value());
        }
        return found;
    }

    private static List<Edit> edits(JsonValue.JsonObject object, String name) {
        return objects(object, name).stream()
                .map(edit -> new Edit(text(edit, "name"), text(edit, "value")))
                .toList();
    }

    /**
     * The edits every case makes first: the database, no spectral library, five scans, the
     * workflow's outputs.
     *
     * @return the edits
     */
    List<Edit> base() {
        return edits(root, "base");
    }

    /**
     * Each release's control lines.
     *
     * @return the controls
     */
    List<Control> controls() {
        return objects(root, "controls").stream()
                .map(
                        control ->
                                new Control(
                                        ToolVersion.parse(text(control, "version")),
                                        texts(control, "lines")))
                .toList();
    }

    /**
     * The pinned inputs.
     *
     * @return the inputs
     */
    Inputs inputs() {
        JsonValue.JsonObject spectra = (JsonValue.JsonObject) member(root, "spectra");
        JsonValue.JsonObject database = (JsonValue.JsonObject) member(root, "database");
        return new Inputs(
                text(spectra, "file"),
                text(spectra, "sha256"),
                text(spectra, "lineFeedSha256"),
                text(database, "file"),
                text(database, "sha256"),
                (int) ((JsonValue.JsonNumber) member(database, "records")).value(),
                text(database, "subsetSha256"));
    }

    /**
     * Every case.
     *
     * @return the cases, in the corpus's order
     */
    List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        for (JsonValue.JsonObject entry : objects(root, "cases")) {
            List<Verdict> verdicts = new ArrayList<>();
            for (JsonValue.JsonObject verdict : objects(entry, "verdicts")) {
                JsonValue reason = member(verdict, "reason");
                verdicts.add(
                        new Verdict(
                                ToolVersion.parse(text(verdict, "version")),
                                (int) ((JsonValue.JsonNumber) member(verdict, "exit")).value(),
                                texts(verdict, "lines"),
                                texts(verdict, "findings"),
                                text(verdict, "agreement"),
                                reason instanceof JsonValue.JsonString words
                                        ? Optional.of(words.value())
                                        : Optional.empty(),
                                ((JsonValue.JsonBoolean) member(verdict, "builtInCode")).value()));
            }
            cases.add(
                    new Case(
                            text(entry, "id"),
                            text(entry, "topic"),
                            edits(entry, "edits"),
                            verdicts));
        }
        return cases;
    }

    /**
     * The text a binary is given: the release's real {@code -q} fixture with the base edits and the
     * case's, each replacing the one line that declares the parameter (its inline comment dropped),
     * exactly as {@code comet -q} would write it otherwise.
     *
     * @param version the release
     * @param edits the edits, with the database path already substituted
     * @return the file text
     * @throws IOException if the fixture cannot be read
     */
    static String file(ToolVersion version, List<Edit> edits) throws IOException {
        String text =
                new String(
                        CometFixtures.bytes(
                                version.text(),
                                CometFixtures.LINUX_X86_64,
                                CometFixtures.Mode.COMPLETE),
                        StandardCharsets.UTF_8);
        for (Edit edit : edits) {
            Pattern line =
                    Pattern.compile(
                            "^" + Pattern.quote(edit.name()) + " = [^\\n]*$", Pattern.MULTILINE);
            Matcher matcher = line.matcher(text);
            assertTrue(matcher.find(), edit.name() + " is not declared by Comet " + version);
            int start = matcher.start();
            int end = matcher.end();
            assertFalse(matcher.find(), edit.name() + " is declared twice");
            text =
                    text.substring(0, start)
                            + edit.name()
                            + " = "
                            + edit.value()
                            + text.substring(end);
        }
        return text;
    }

    /**
     * The model the validator judges: the release's fixture with the base edits, parsed for the
     * release, and then each case edit set as its value text. An edit the release's codec refuses
     * -- a residue character outside its alphabet -- is set as the editor would set it, in code:
     * read by the 2026.03.0 codec and placed on the model as a typed value.
     *
     * @param version the release
     * @param base the base edits, database path substituted
     * @param edits the case's edits, database path substituted
     * @param refusedByCodec collects the names of the edits the release's codec refused
     * @return the model
     * @throws IOException if the fixture cannot be read
     */
    static CometParameters model(
            ToolVersion version, List<Edit> base, List<Edit> edits, List<String> refusedByCodec)
            throws IOException {
        ParseResult parsed =
                new CometParamsParser(Models.METADATA, version).parse(file(version, base));
        assertTrue(parsed.succeeded(), () -> version + ": " + parsed.errors());
        CometParameters model = parsed.model().orElseThrow();
        for (Edit edit : edits) {
            try {
                model = model.withText(edit.name(), edit.value(), ValueOrigin.USER);
            } catch (ValueSyntaxException refused) {
                refusedByCodec.add(edit.name());
                model =
                        model.withValue(
                                edit.name(),
                                new ParameterValue.Tuple(
                                        VariableModCodec.forVersion(
                                                        Models.METADATA, Models.COMET_2026_03_0)
                                                .parse(edit.name(), edit.value())),
                                ValueOrigin.USER);
            }
        }
        return model;
    }

    /** What a binary, or the validator, made of a configuration. */
    enum Verdicts {
        CLEAN,
        WARNING,
        ERROR
    }

    /**
     * The validator's class for a report.
     *
     * @param report the report
     * @return error if it has an error, warning if it has a warning, clean otherwise
     */
    static Verdicts of(ValidationReport report) {
        if (report.hasErrors()) {
            return Verdicts.ERROR;
        }
        return report.findings().isEmpty() ? Verdicts.CLEAN : Verdicts.WARNING;
    }

    /**
     * A binary's class for what it did.
     *
     * @param exit its exit code
     * @param lines its Warning and Error lines beyond the control's
     * @return error if it failed, warning if it warned, clean otherwise
     */
    static Verdicts of(int exit, List<String> lines) {
        if (exit != 0) {
            return Verdicts.ERROR;
        }
        return lines.isEmpty() ? Verdicts.CLEAN : Verdicts.WARNING;
    }

    /**
     * Asserts the agreement criterion for one recorded verdict: the validator's class equals the
     * binary's ({@code same}), or is more severe, with a recorded reason ({@code stricter}); it is
     * never less severe.
     *
     * @param kase the case
     * @param verdict the release's verdict
     */
    static void assertAgreement(Case kase, Verdict verdict) {
        Verdicts binary = of(verdict.exit(), verdict.lines());
        Verdicts validator =
                verdict.findings().stream().anyMatch(f -> f.startsWith("ERROR "))
                        ? Verdicts.ERROR
                        : verdict.findings().isEmpty() ? Verdicts.CLEAN : Verdicts.WARNING;
        String where = kase.id() + ", Comet " + verdict.version().text();
        switch (verdict.agreement()) {
            case "same" -> {
                assertEquals(binary, validator, where + ": recorded as the same verdict");
                assertEquals(Optional.empty(), verdict.reason(), where);
            }
            case "stricter" -> {
                assertTrue(
                        validator.compareTo(binary) > 0,
                        where + ": recorded as stricter, but " + validator + " vs " + binary);
                assertTrue(
                        verdict.reason().filter(r -> !r.isBlank()).isPresent(),
                        where + ": a stricter verdict needs its reason");
            }
            default -> throw new AssertionError(where + ": agreement " + verdict.agreement());
        }
    }
}

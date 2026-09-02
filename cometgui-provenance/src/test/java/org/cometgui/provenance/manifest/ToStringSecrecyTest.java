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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * One assertion, applied to every type in this package: no {@code toString} prints a value that
 * could be a credential.
 *
 * <p>{@code R-SEC-03} requires secrets to be redacted from command display, process environment
 * capture and exported reports, and {@code AC-LL-06} requires that credentials "never appear in
 * provenance, logs or exports". A {@code toString} is the quietest way to break that: it is called
 * by every logging framework, by {@link String#valueOf}, by an assertion message and by an
 * exception whose text nobody reviewed, and a record's compiler-generated one prints every
 * component it has.
 *
 * <p>So the manifest built here is deliberately hostile. A distinctive, hand-typed secret is
 * planted in the two places the specification names as carriers -- an environment variable's value
 * and a settings entry's value -- and the whole graph is rendered. Every type in the package is
 * asserted individually rather than only the root, because a type is used on its own long before it
 * is put in a manifest, and because a per-type assertion says which type leaked when one does.
 *
 * <p>The names and keys are asserted <em>present</em> in the same breath. A {@code toString} that
 * printed nothing would pass a secrecy test and would be useless; the property being proved is that
 * the record identifies itself without disclosing anything.
 */
class ToStringSecrecyTest {

    /**
     * A secret with no other reason to appear in any rendered string. Hand-typed, and shaped like a
     * real access token so that a substring search cannot match it by accident.
     */
    private static final String SECRET = "glpat-Z1x9QeR7sVbN3mK0pLtY";

    private static final String SECRET_PREFIX = "glpat-";

    private static ProvenanceManifest hostileManifest() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("upload.api.key", SECRET);
        settings.put("percolator.seed", "1387");
        return ProvenanceManifest.current(
                ManifestFixtures.completedRun(),
                ManifestFixtures.application(),
                settings,
                List.of(hostileTool()),
                List.of(ManifestFixtures.inputFile("spectra", "spectra.mzML")));
    }

    private static ToolRecord hostileTool() {
        return new ToolRecord(
                "percolator",
                "3.07.1",
                Optional.of("rel-3-07-1"),
                ManifestFixtures.runFile("percolator"),
                ManifestFixtures.ABC_HASHES,
                true,
                Optional.of("percolator-3.07.1-linux-amd64.deb"),
                Set.of("xml"),
                Optional.of("rescore"),
                ManifestFixtures.execution("UPLOAD_TOKEN", SECRET),
                List.of());
    }

    @Test
    @DisplayName("the fixture really does carry the secret, so the test can fail")
    void theFixtureReallyCarriesTheSecret() {
        ProvenanceManifest manifest = hostileManifest();

        assertAll(
                () -> assertEquals(SECRET, manifest.settings().get("upload.api.key")),
                () ->
                        assertEquals(
                                SECRET,
                                manifest.tools()
                                        .get(0)
                                        .execution()
                                        .command()
                                        .environment()
                                        .get("UPLOAD_TOKEN")));
    }

    @Test
    @DisplayName("no type in this package prints the secret")
    void noTypePrintsTheSecret() {
        ProvenanceManifest manifest = hostileManifest();
        ToolRecord tool = manifest.tools().get(0);
        ExecutionRecord execution = tool.execution();
        FileRecord file = manifest.files().get(0);
        LogRecord log =
                new LogRecord(
                        ManifestFixtures.runFile("percolator.stdout.log"),
                        ManifestFixtures.ABC_HASHES);

        Map<String, String> rendered = new LinkedHashMap<>();
        rendered.put("ProvenanceManifest", manifest.toString());
        rendered.put("ToolRecord", tool.toString());
        rendered.put("ExecutionRecord", execution.toString());
        rendered.put("FileRecord", file.toString());
        rendered.put("LogRecord", log.toString());
        rendered.put("RunRecord", manifest.run().toString());
        rendered.put("ApplicationRecord", manifest.application().toString());
        rendered.put("ProvenanceStatus", ProvenanceStatus.COMPLETED.toString());
        rendered.put("FileDirection", FileDirection.INPUT.toString());

        assertAll(
                rendered.entrySet().stream()
                        .<Executable>map(
                                entry ->
                                        () ->
                                                assertFalse(
                                                        entry.getValue().contains(SECRET),
                                                        entry.getKey()
                                                                + " printed the secret: "
                                                                + entry.getValue()))
                        .toList());
    }

    @Test
    @DisplayName("not even a fragment of the secret survives into any rendered string")
    void notEvenAFragmentSurvives() {
        ProvenanceManifest manifest = hostileManifest();

        assertAll(
                () -> assertFalse(manifest.toString().contains(SECRET_PREFIX)),
                () -> assertFalse(manifest.tools().get(0).toString().contains(SECRET_PREFIX)),
                () ->
                        assertFalse(
                                manifest.tools()
                                        .get(0)
                                        .execution()
                                        .toString()
                                        .contains(SECRET_PREFIX)));
    }

    @Test
    @DisplayName("the names and keys are printed, so the records still identify themselves")
    void theNamesAndKeysArePrinted() {
        ProvenanceManifest manifest = hostileManifest();

        assertAll(
                () -> assertTrue(manifest.toString().contains("upload.api.key")),
                () -> assertTrue(manifest.toString().contains("percolator.seed")),
                () -> assertTrue(manifest.toString().contains("percolator")),
                () ->
                        assertTrue(
                                manifest.tools()
                                        .get(0)
                                        .execution()
                                        .toString()
                                        .contains("UPLOAD_TOKEN")));
    }
}

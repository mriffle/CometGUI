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

package org.cometgui.workflow.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.pdv.PdvSpectrumSupport;
import org.cometgui.workflow.steps.RunResultFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 11 exit gate items 6 and 7 through the run-level path, on the mzTab the product actually
 * gives PDV: a finished run laid out from the real K562 {@code _3} + {@code _4} sources (the
 * Percolator tables' {@code PSMId}s re-based on the run's absolute {@code -N} base, as Comet writes
 * it in a CometGUI run), {@link PdvMzTabs#prepare}, and then the fidelity suite of {@code
 * cometgui-tools} -- {@code org.cometgui.tools.pdv.fidelity.FidelityCheck} and its readers,
 * compiled here from that module's test sources rather than copied, so there is one suite and it
 * cannot drift. They depend on the JDK alone, which that module's {@code IndependenceGuardTest}
 * enforces.
 *
 * <p>Reads outside this module: {@code scratch/fixture/*.mzML}, {@code scratch/scientific-path/
 * comet/*.pep.xml}, {@code scratch/scientific-path/percolator-3.07.1/psms.*.txt} (each held to its
 * SHA-256) and {@code cometgui-tools/src/test/java/org/cometgui/tools/pdv/fidelity/}. {@code
 * D-006}: every derived file is under the test's temporary directory.
 */
class PdvMzTabsFidelityTest {

    private static final String BASE_3 = "20100614_Velos1_TaGe_SA_K562_3";
    private static final String BASE_4 = "20100614_Velos1_TaGe_SA_K562_4";

    /** Each K562 input and its SHA-256, as {@code cometgui-tools}' {@code K562Run} pins them. */
    private static final List<List<String>> INPUTS =
            List.of(
                    List.of(
                            "scratch/fixture/" + BASE_3 + ".mzML",
                            "cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7"),
                    List.of(
                            "scratch/fixture/" + BASE_4 + ".mzML",
                            "d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa"),
                    List.of(
                            "scratch/scientific-path/comet/" + BASE_3 + ".pep.xml",
                            "34bf414524e6dedf12a777b44fdf49956c88addcc44c76565a0d9fed316f7dd7"),
                    List.of(
                            "scratch/scientific-path/comet/" + BASE_4 + ".pep.xml",
                            "28f8a6da4ead892322b449c9dba37869cfc75d47433ba2ac457986336889590c"),
                    List.of(
                            "scratch/scientific-path/percolator-3.07.1/psms.target.txt",
                            "458b79b5a423f57406421d31ddd57b946eb8096ab6935447efd1e98e48f7fe9e"),
                    List.of(
                            "scratch/scientific-path/percolator-3.07.1/psms.decoy.txt",
                            "e1245d4f3fd128a5c7efcf89bc0b0ab0991d3f31bff437caa23bec3f437fc4c1"));

    private static final List<String> SUITE =
            List.of(
                    "FidelityCheck.java",
                    "PercolatorTable.java",
                    "PepXmlFile.java",
                    "MzMlFile.java",
                    "MgfEntries.java",
                    "MzTabFile.java",
                    "Peptidoforms.java");

    @TempDir private Path project;

    private static Path repository() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError("no repository root above " + Path.of("").toAbsolutePath());
        }
        return cursor;
    }

    private static Path verified(Path root, List<String> input) throws IOException {
        Path file = root.resolve(input.get(0));
        assertTrue(
                Files.isRegularFile(file),
                file
                        + " is missing: a local, gitignored K562 input (D-006). This test fails"
                        + " rather than skips.");
        assertEquals(
                input.get(1),
                PdvFixtures.digest(file, "SHA-256"),
                file + " is not the file the fidelity suite was written against");
        return file;
    }

    /** Copies a Percolator table with every PSMId's base replaced by the run's absolute base. */
    private static void rebased(Path table, Path copy, List<List<String>> bases)
            throws IOException {
        StringBuilder text = new StringBuilder();
        for (String line : Files.readAllLines(table, StandardCharsets.UTF_8)) {
            String rebased = line;
            for (List<String> base : bases) {
                if (line.startsWith(base.get(0) + "_")) {
                    rebased = base.get(1) + line.substring(base.get(0).length());
                }
            }
            text.append(rebased).append('\n');
        }
        Files.createDirectories(Objects.requireNonNull(copy.getParent(), "a table's directory"));
        Files.writeString(copy, text.toString(), StandardCharsets.UTF_8);
    }

    /** Compiles the fidelity suite's JDK-only sources and returns its {@code verify} method. */
    private Method suite(Path root) throws IOException, ReflectiveOperationException {
        Path sources = root.resolve("cometgui-tools/src/test/java/org/cometgui/tools/pdv/fidelity");
        List<String> arguments =
                new ArrayList<>(List.of("-d", project.resolve("suite").toString()));
        for (String file : SUITE) {
            Path source = sources.resolve(file);
            assertTrue(Files.isRegularFile(source), source + " is the fidelity suite");
            arguments.add(source.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "the test JVM is a JDK");
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
        URLClassLoader loader =
                new URLClassLoader(
                        new URL[] {project.resolve("suite").toUri().toURL()},
                        getClass().getClassLoader());
        Class<?> check = loader.loadClass("org.cometgui.tools.pdv.fidelity.FidelityCheck");
        return check.getMethod("verify", Path.class, Path.class, List.class, Path.class);
    }

    @Test
    void theRunLevelMzTabIsTrueToTheSources() throws IOException, ReflectiveOperationException {
        Path root = repository();
        List<Path> inputs = new ArrayList<>();
        for (List<String> input : INPUTS) {
            inputs.add(verified(root, input));
        }
        RunLayout run = PdvFixtures.run(project, PdvFixtures.RUN_ID);
        RunIdentity identity =
                PdvFixtures.identity(PdvFixtures.RUN_ID, inputs.get(0), inputs.get(1));
        assertEquals(
                List.of(BASE_3, BASE_4),
                identity.spectra().stream().map(spectrum -> spectrum.base()).toList());
        List<List<String>> bases = new ArrayList<>();
        for (String base : List.of(BASE_3, BASE_4)) {
            Files.copy(inputs.get(base.equals(BASE_3) ? 2 : 3), run.pepXmlFile(base));
            bases.add(List.of(base, run.cometOutputBase(base).toString()));
        }
        Path targets = RunResultFiles.table(run, TableKind.TARGET_PSMS);
        Path decoys = RunResultFiles.table(run, TableKind.DECOY_PSMS);
        rebased(inputs.get(4), targets, bases);
        rebased(inputs.get(5), decoys, bases);

        PdvMzTab mzTab =
                new PdvMzTabs(
                                new StreamingHashService(),
                                SecretRedactor.patternsOnly(),
                                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC),
                                Set::of)
                        .prepare(run, identity, run, PdvSpectrumSupport.VERIFIED_VERSION);

        List<List<String>> sources = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            sources.add(
                    List.of(
                            bases.get(index).get(1),
                            inputs.get(index).toString(),
                            run.pepXmlFile(bases.get(index).get(0)).toString(),
                            ""));
        }
        List<String> report;
        try {
            @SuppressWarnings("unchecked")
            List<String> result =
                    (List<String>)
                            suite(root).invoke(null, targets, decoys, sources, mzTab.mzTab());
            report = result;
        } catch (InvocationTargetException failed) {
            throw new AssertionError(failed.getCause().getMessage(), failed.getCause());
        }
        String text = String.join("\n", report);
        assertAll(
                () -> assertEquals("PASSED", report.get(0), text),
                () -> assertEquals(15, report.size(), text),
                () ->
                        assertTrue(
                                text.contains("every PSM exactly once: 13340 compared, 0 failed"),
                                text),
                () ->
                        assertTrue(
                                text.contains("charge as pepXML assumed_charge: 6670 compared"),
                                text),
                () ->
                        assertTrue(
                                mzTab.mzTab()
                                        .startsWith(run.root().resolve("pdv").resolve("mztab")),
                                "the product's location: " + mzTab.mzTab()));
    }
}

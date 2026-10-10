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

package org.cometgui.tools.pdv.fidelity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The mechanical form of design decision P11-9: the fidelity suite reads every source and output
 * with its own readers, so no source file of this package may name a CometGUI type other than the
 * exporter's public entry points and the test support that locates and runs real tools. Every
 * CometGUI name the package's code uses -- imported, written in full, or behind a wildcard -- is
 * checked against an allow list; the readers and the checker may name none at all, and nothing but
 * the JDK, so that {@code cometgui-workflow} can compile them as they are.
 */
class IndependenceGuardTest {

    /** Where this package's sources are, from the module directory the tests run in. */
    static final Path SOURCES = Path.of("src/test/java/org/cometgui/tools/pdv/fidelity");

    private static final String ROOT = "org.cometgui.";

    /** The only CometGUI names the suite may use. */
    static final Set<String> ALLOWED =
            Set.of(
                    // the exporter's public entry points: called, never used to read anything
                    ROOT + "tools.pdv.PdvMzTabExporter",
                    ROOT + "tools.pdv.PdvMzTabRequest",
                    ROOT + "tools.pdv.PdvMzTabSource",
                    ROOT + "tools.pdv.PdvMzTabException",
                    ROOT + "tools.pdv.SpectrumFileFormat",
                    // locating, checksumming and running the real inputs and tools
                    ROOT + "tools.testing.K562Run",
                    ROOT + "tools.testing.RealCometSearch",
                    ROOT + "tools.testing.ProjectXvfb",
                    ROOT + "tools.testing.RealPdv",
                    ROOT + "domain.run.RunLayout",
                    ROOT + "domain.ports.ProcessListener",
                    ROOT + "domain.ports.RunningProcess",
                    ROOT + "domain.ports.ToolCommand",
                    ROOT + "tools.api.JavaRuntime",
                    ROOT + "tools.process.ProcessService");

    /** The files that read and compare, which must depend on the JDK alone. */
    static final Set<String> JDK_ONLY =
            Set.of(
                    "FidelityCheck.java",
                    "PercolatorTable.java",
                    "PepXmlFile.java",
                    "MzMlFile.java",
                    "MgfEntries.java",
                    "MzTabFile.java",
                    "Peptidoforms.java");

    private static final Pattern NAME = Pattern.compile("org\\.cometgui\\.[\\w.*]*");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.*]+);");

    /**
     * Java source with its comments and its string and character literals removed, and the white
     * space around every dot closed up, so that a name broken over lines is still one name and a
     * name merely mentioned in prose or text is not a use.
     *
     * @param text the source
     * @return the code alone
     */
    static String code(String text) {
        StringBuilder code = new StringBuilder();
        int at = 0;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (text.startsWith("//", at)) {
                at = text.indexOf('\n', at);
                at = at < 0 ? text.length() : at;
            } else if (text.startsWith("/*", at)) {
                int end = text.indexOf("*/", at + 2);
                at = end < 0 ? text.length() : end + 2;
                code.append(' ');
            } else if (text.startsWith("\"\"\"", at)) {
                int end = text.indexOf("\"\"\"", at + 3);
                at = end < 0 ? text.length() : end + 3;
                code.append("\"\"");
            } else if (c == '"' || c == '\'') {
                int end = at + 1;
                while (end < text.length() && text.charAt(end) != c) {
                    end += text.charAt(end) == '\\' ? 2 : 1;
                }
                at = end + 1;
                code.append(c).append(c);
            } else {
                code.append(c);
                at++;
            }
        }
        return code.toString().replaceAll("\\s*\\.\\s*", ".");
    }

    /**
     * Every CometGUI name a source file's code uses that is not allowed, and every non-JDK import
     * of a file that must be JDK-only.
     *
     * @param file the file's name
     * @param text its text
     * @return the violations
     */
    static List<String> violations(String file, String text) {
        List<String> found = new ArrayList<>();
        String code = code(text);
        Matcher matcher = NAME.matcher(code);
        while (matcher.find()) {
            String name = matcher.group();
            name = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
            boolean allowed =
                    ALLOWED.contains(name) || name.startsWith(ROOT + "tools.pdv.fidelity");
            for (String type : ALLOWED) {
                // A member access such as K562Run.mzml names an allowed type.
                allowed |= name.startsWith(type + ".");
            }
            if (!allowed) {
                found.add(file + " names " + name);
            }
        }
        if (JDK_ONLY.contains(file)) {
            for (String line : code.split("\n")) {
                Matcher imported = IMPORT.matcher(line.strip());
                if (imported.find()
                        && !imported.group(2).startsWith("java.")
                        && !imported.group(2).startsWith("javax.")) {
                    found.add(file + " imports " + imported.group(2) + ", not the JDK");
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName(
            "no source file of the fidelity suite names a reader, writer or helper the exporter"
                    + " uses")
    void theSuiteIsIndependent() throws IOException {
        assertTrue(Files.isDirectory(SOURCES), SOURCES.toAbsolutePath() + " is the suite");
        List<Path> files;
        try (Stream<Path> listing = Files.list(SOURCES)) {
            files = listing.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        }
        List<String> names = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (Path file : files) {
            String name = String.valueOf(file.getFileName());
            names.add(name);
            found.addAll(violations(name, Files.readString(file, StandardCharsets.UTF_8)));
        }
        assertAll(
                () -> assertEquals(List.of(), found),
                () -> assertTrue(names.containsAll(JDK_ONLY), "the readers are here: " + names),
                () ->
                        assertTrue(
                                names.contains("IndependenceGuardTest.java"),
                                "the guard reads itself"));
    }

    @Test
    @DisplayName("the guard is falsifiable: each forbidden form is caught")
    void theGuardCatchesEachForm() {
        String comet = "org.cometgui." + "tools.comet";
        String results = "org.cometgui." + "results.parser";
        String pdv = "org.cometgui." + "tools.pdv";
        assertAll(
                () ->
                        assertEquals(
                                List.of("A.java names " + comet + ".PepXmlSearchHitReader"),
                                violations(
                                        "A.java", "import " + comet + ".PepXmlSearchHitReader;\n")),
                () ->
                        assertEquals(
                                List.of("B.java names " + results + ".ResultTableReader.read"),
                                violations(
                                        "B.java",
                                        "var rows = "
                                                + results
                                                + ".ResultTableReader.read(file);\n")),
                () ->
                        assertEquals(
                                List.of("C.java names " + pdv + ".*"),
                                violations("C.java", "import " + pdv + ".*;\n")),
                () ->
                        assertEquals(
                                List.of("D.java names " + pdv + ".MzMlSpectrumReader"),
                                violations("D.java", "import " + pdv + ".MzMlSpectrumReader;\n")),
                () ->
                        assertEquals(
                                List.of(
                                        "MzMlFile.java imports org.junit.jupiter.api.Test, not the"
                                                + " JDK"),
                                violations(
                                        "MzMlFile.java", "import org.junit.jupiter.api.Test;\n")),
                () ->
                        assertEquals(
                                List.of(
                                        "FidelityCheck.java imports "
                                                + pdv
                                                + ".PdvMzTabExporter, not the JDK"),
                                violations(
                                        "FidelityCheck.java",
                                        "import " + pdv + ".PdvMzTabExporter;\n")),
                () ->
                        assertEquals(
                                List.of("F.java names " + results + ".QValue.of"),
                                violations(
                                        "F.java",
                                        "/* "
                                                + comet
                                                + " in prose */ var q = \""
                                                + comet
                                                + "\"; var v = org.cometgui\n"
                                                + "  .results\n  .parser.QValue.of(q);\n")),
                () ->
                        assertEquals(
                                List.of(),
                                violations(
                                        "E.java",
                                        "import "
                                                + pdv
                                                + ".PdvMzTabExporter;\n"
                                                + "RealCometSearch.OLDER; org.cometgui.tools"
                                                + ".testing.K562Run.mzml();\n")));
    }
}

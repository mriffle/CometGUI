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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reading parameter names out of the file Comet writes, and reading nothing else. */
class CometParameterDeclarationsTest {

    /** Lines copied verbatim from the {@code comet.params.new} the real binary wrote. */
    private static final List<String> REAL_LINES =
            List.of(
                    "# comet_version 2026.02 rev. 2",
                    "# Comet MS/MS search engine parameters file.",
                    "",
                    "database_name = /some/path/db.fasta",
                    "output_txtfile = 0                     # 0=no, 1=yes, 2=Crux-formatted",
                    "output_pepxmlfile = 1                  # 0=no, 1=yes  write pepXML file",
                    "output_percolatorfile = 0              # 0=no, 1=yes  write Percolator pin",
                    "",
                    "[COMET_ENZYME_INFO]",
                    "0.  Cut_everywhere         0      -           -");

    private static Path write(Path directory, List<String> lines) throws IOException {
        return Files.write(directory.resolve("comet.params.new"), lines, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a declaration is a name at column one before an equals, and nothing else is")
    void whatCountsAsADeclaration(@TempDir Path directory) throws IOException {
        Set<String> declared = CometParameterDeclarations.readFrom(write(directory, REAL_LINES));

        assertEquals(
                Set.of(
                        "database_name",
                        "output_txtfile",
                        "output_pepxmlfile",
                        "output_percolatorfile"),
                declared,
                "the comment lines mention parameters and the enzyme table is indented; neither is"
                        + " a declaration");
    }

    @Test
    @DisplayName("a comment that mentions a parameter name is not a declaration of it")
    void aCommentIsNotADeclaration(@TempDir Path directory) throws IOException {
        Set<String> declared =
                CometParameterDeclarations.readFrom(
                        write(
                                directory,
                                List.of(
                                        "# output_pepxmlfile = 1 is the default",
                                        "  output_percolatorfile = 1",
                                        "\toutput_txtfile = 1")));

        assertEquals(
                Set.of(),
                declared,
                "an indented or commented line is not the binary declaring a parameter, and a"
                        + " probe that read one would grant a capability from a sentence");
    }

    @Test
    @DisplayName("a line with no equals declares nothing")
    void noEquals(@TempDir Path directory) throws IOException {
        assertEquals(
                Set.of(),
                CometParameterDeclarations.readFrom(
                        write(directory, List.of("[COMET_ENZYME_INFO]", "", "just words"))));
    }

    @Test
    @DisplayName("the order of first appearance is kept, and a repeat is one name")
    void orderAndRepeats(@TempDir Path directory) throws IOException {
        Set<String> declared =
                CometParameterDeclarations.readFrom(
                        write(directory, List.of("beta = 1", "alpha = 2", "beta = 3")));

        assertAll(
                () -> assertEquals(List.of("beta", "alpha"), List.copyOf(declared)),
                () -> assertEquals(2, declared.size()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class, () -> declared.add("gamma")));
    }

    @Test
    @DisplayName("an empty file declares nothing, and a missing file is an error not an emptiness")
    void emptyAndMissing(@TempDir Path directory) throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                Set.of(),
                                CometParameterDeclarations.readFrom(write(directory, List.of()))),
                () ->
                        assertThrows(
                                IOException.class,
                                () ->
                                        CometParameterDeclarations.readFrom(
                                                directory.resolve("absent.params"))),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> CometParameterDeclarations.readFrom(null))
                                        .getMessage()));
    }
}

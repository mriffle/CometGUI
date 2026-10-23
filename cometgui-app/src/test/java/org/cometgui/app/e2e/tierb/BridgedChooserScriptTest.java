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

package org.cometgui.app.e2e.tierb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tier B names the dialogs the packaged application opened exactly as Tier A's {@code
 * ScriptedChooser} names its questions, so a scenario's assertion on {@code asked()} reads the same
 * in both tiers.
 */
class BridgedChooserScriptTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "openMultiple\tChoose spectrum files to search\t\tSpectrum files|spectra",
                "open\tChoose the sequence database\t\tFASTA files|database",
                "open\tChoose the file for the OBO file\t\tAll files|file:the OBO file",
                "open\tImport a Comet parameter file\t\tComet parameter files|parameter file",
                "save\tSave the Comet parameter file\tcomet.params\tAll files|save",
                "save\tExport the passing PSMs\tpsms.tsv\tAll files"
                        + "|export:Export the passing PSMs:psms.tsv",
                "open\tChoose spectrum files to search\t\t|unrecognised open dialog: Choose"
                        + " spectrum files to search",
                "openMultiple\tSomething new\t\t|unrecognised openMultiple dialog: Something new",
            })
    @DisplayName("each recorded dialog is named as ScriptedChooser names it")
    void names(String recorded, String name) {
        assertEquals(name, BridgedChooserScript.name(recorded));
    }
}

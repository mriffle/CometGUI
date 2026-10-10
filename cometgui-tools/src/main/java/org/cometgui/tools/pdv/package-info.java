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

/**
 * PDV adapter: opening a selected spectrum for visualisation and driving the CLI figure generation
 * used by the tests.
 *
 * <p><strong>Phase 05 unit 7 landed the identity probe</strong>, which reads the version out of the
 * JAR's own manifest because PDV has no version option and builds a Swing frame before reading its
 * first argument.
 *
 * <p><strong>Phase 11 unit 2</strong> landed what every PDV route stands on: {@link
 * org.cometgui.tools.pdv.PdvSpectrumSupport}, which decides before anything launches whether the
 * selected PDV can be given a run's spectrum source; the source readers ({@link
 * org.cometgui.tools.pdv.SourceSpectra}, {@link org.cometgui.tools.pdv.MzMlSpectrumReader}, {@link
 * org.cometgui.tools.pdv.MgfSpectrumReader}); {@link org.cometgui.tools.pdv.PdvMgfWriter}, the
 * exact MGF re-encoding PDV is given (design decisions P11-11 and P11-12); and {@link
 * org.cometgui.tools.pdv.PdvFigures}, the CLI figure command judged by its parsed output ({@link
 * org.cometgui.tools.pdv.PngFigure}). The {@code denovo-gui} launcher and controller are later
 * units'.
 *
 * <p><strong>Phase 11 unit 5</strong> landed {@link org.cometgui.tools.pdv.PdvMzTabExporter}: the
 * mzTab {@code denovo-gui} is given, and its MGFs, from a run's Percolator PSM tables (through the
 * one table reader in {@code org.cometgui.results.parser}), Comet pepXML and spectrum files --
 * every value transcribed, every PSM joined to exactly one hit and one spectrum or refused by name
 * ({@link org.cometgui.tools.pdv.PdvMzTabException}), written all or nothing.
 */
package org.cometgui.tools.pdv;

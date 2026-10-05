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

package org.cometgui.ui.viewmodel.params;

import java.util.Objects;
import org.cometgui.domain.tools.ToolVersion;

/**
 * A parameter file that names another Comet release than the selected one, waiting for the
 * scientist's choice: migrate it to the selected release with a reviewable report, read it as its
 * own release (when that release is offered), or read it as the selected release with the mismatch
 * warning. Migration is never silent ({@code R-TOOL-09}).
 *
 * @param text the file's text
 * @param source where it came from, as the messages name it
 * @param declared the release its {@code # comet_version} line names
 * @param selected the release the editor is set to
 * @param declaredOffered whether the file's own release is one the editor offers
 */
public record ImportOffer(
        String text,
        String source,
        ToolVersion declared,
        ToolVersion selected,
        boolean declaredOffered) {

    /** Validates presence. */
    public ImportOffer {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(selected, "selected");
    }

    /**
     * The question, in words.
     *
     * @return what the file is for and what can be done with it
     */
    public String question() {
        String choices =
                "Migrate it to Comet "
                        + selected.text()
                        + " with a reviewable report"
                        + (declaredOffered
                                ? ", switch the editor to Comet " + declared.text() + ","
                                : ",")
                        + " or read it as Comet "
                        + selected.text()
                        + " as it is.";
        return source
                + " was written for Comet "
                + declared.text()
                + "; the editor is set to Comet "
                + selected.text()
                + ". "
                + choices;
    }
}

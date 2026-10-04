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

import java.util.List;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;

/**
 * {@link Rule#INDEX_SEARCH_TYPE_IGNORED}: {@code index_search_type} is 0 or 1 while {@code
 * database_name} does not end in {@code .idx}.
 *
 * <p>The value only chooses the type of an index Comet builds when {@code database_name} names an
 * {@code .idx} file that does not exist yet; Comet decides "an index" by the name's last four
 * characters alone ({@code CometSearchManager.cpp} lines 1706-1708 at {@code v2026.03.0}). For any
 * other name the value has no effect, and Comet 2026.03.0 says so ({@code Warning -
 * index_search_type = 1 is ignored: "..." is not an .idx file}, lines 1764-1775); 2026.02.2 says
 * nothing. Whether it is reported is therefore the release's, stated in its version record. A value
 * other than -1, 0 and 1 is the choice rule's; whether an existing {@code .idx} agrees with the
 * value needs the file and is not decided here.
 */
final class IndexSearchTypeRule {

    /** The parameter. */
    static final String INDEX_SEARCH_TYPE = "index_search_type";

    /** The database it depends on. */
    static final String DATABASE = "database_name";

    /** The suffix Comet takes as "an index". */
    static final String INDEX_SUFFIX = ".idx";

    private IndexSearchTypeRule() {}

    static void check(CometParameters model, Findings findings) {
        if (model.entry(INDEX_SEARCH_TYPE).isEmpty()) {
            return;
        }
        int type = ((ParameterValue.Whole) model.value(INDEX_SEARCH_TYPE)).value();
        String database = ((ParameterValue.Text) model.value(DATABASE)).text();
        if ((type == 0 || type == 1) && !database.endsWith(INDEX_SUFFIX)) {
            findings.add(
                    Rule.INDEX_SEARCH_TYPE_IGNORED,
                    List.of(INDEX_SEARCH_TYPE, DATABASE),
                    INDEX_SEARCH_TYPE
                            + " = "
                            + type
                            + " only chooses the type of an index Comet builds when "
                            + DATABASE
                            + " names an "
                            + INDEX_SUFFIX
                            + " file that does not exist yet, and "
                            + DATABASE
                            + " = "
                            + database
                            + " does not, so the value has no effect; set "
                            + INDEX_SEARCH_TYPE
                            + " to -1 (not set), or name the "
                            + INDEX_SUFFIX
                            + " file to build");
        }
    }
}

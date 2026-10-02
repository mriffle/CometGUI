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

package org.cometgui.params.comet.parser;

import java.util.Objects;
import java.util.Optional;

/**
 * One line of {@code comet.params} text, classified by shape and nothing more.
 *
 * <p>Every variant carries its 1-based line {@link #number()} and its exact {@link #text()} -- the
 * line as it appeared, without its {@code \n} terminator but with anything else, a trailing {@code
 * \r} included -- so a later reader that needs the original bytes has them. Nothing here interprets
 * a value: {@link Declaration#value()} is the text between {@code =} and {@code #}, trimmed, and
 * whether it is a number, a path or a tuple is the schema's business.
 */
public sealed interface ParamsLine {

    /**
     * Where the line is.
     *
     * @return the 1-based line number
     */
    int number();

    /**
     * The line exactly as read, without its {@code \n}.
     *
     * @return the raw text
     */
    String text();

    /**
     * The {@code # comet_version ...} line Comet writes first. Kept separate from {@link Comment}
     * because it is the only comment with a meaning; parsing the version out of it is {@code
     * org.cometgui.params.comet.schema.CometVersionMarker}'s job.
     *
     * @param number the 1-based line number
     * @param text the raw line
     */
    record VersionMarker(int number, String text) implements ParamsLine {

        /** Validates the components. */
        public VersionMarker {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A whole-line comment: optional leading blanks, then {@code #}. Comet's continuation comment
     * under {@code sample_enzyme_number}, indented by 39 spaces, is one of these.
     *
     * @param number the 1-based line number
     * @param text the raw line
     */
    record Comment(int number, String text) implements ParamsLine {

        /** Validates the components. */
        public Comment {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A line holding nothing but white space.
     *
     * @param number the 1-based line number
     * @param text the raw line, possibly empty
     */
    record Blank(int number, String text) implements ParamsLine {

        /** Validates the components. */
        public Blank {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * {@code name = value # comment}, before the enzyme table.
     *
     * @param number the 1-based line number
     * @param text the raw line
     * @param name the parameter name, trimmed
     * @param value the text after the first {@code =} and before the first {@code #}, trimmed;
     *     empty for {@code peff_obo =}, which is a value and not an absence
     * @param inlineComment the text after that {@code #}, trimmed, or empty when the line has no
     *     {@code #}; a bare {@code #} gives an empty string, which is not the same thing
     */
    record Declaration(
            int number, String text, String name, String value, Optional<String> inlineComment)
            implements ParamsLine {

        /** Validates the components. */
        public Declaration {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(inlineComment, "inlineComment");
        }
    }

    /**
     * The {@code [COMET_ENZYME_INFO]} line that starts the enzyme table.
     *
     * @param number the 1-based line number
     * @param text the raw line
     */
    record EnzymeHeader(int number, String text) implements ParamsLine {

        /** Validates the components. */
        public EnzymeHeader {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * One row of the enzyme table, {@code number. name sense cut no-cut}, kept verbatim: the fields
     * are the enzyme codec's to interpret.
     *
     * @param number the 1-based line number
     * @param text the raw line
     */
    record EnzymeRow(int number, String text) implements ParamsLine {

        /** Validates the components. */
        public EnzymeRow {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A line that is none of the above, kept with the reason, so that it can be reported where it
     * is rather than dropped.
     *
     * @param number the 1-based line number
     * @param text the raw line
     * @param reason why it is not any accepted shape
     */
    record Malformed(int number, String text, String reason) implements ParamsLine {

        /** Validates the components. */
        public Malformed {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(reason, "reason");
        }
    }
}

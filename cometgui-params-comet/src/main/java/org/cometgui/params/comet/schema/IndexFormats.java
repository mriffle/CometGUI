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

package org.cometgui.params.comet.schema;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Which formats of existing {@code .idx} file one Comet release can search: an entry of its version
 * record, {@code indexFormats}.
 *
 * <p>A versioned Comet index begins {@code Comet index database v<N>.}; Comet 2026.03.0 writes and
 * reads format 5 only and refuses an index of format 4 ({@code is not a v5 unified index file}),
 * and Comet 2026.02.2 writes and reads format 4 only. Which formats a release reads is a release
 * fact, so by decision C-2 of the Comet 2026.03.0 intake it is data in the release's {@link
 * CometVersionRecord}, read by the validator's index rule, never an {@code if (version ...)}. A
 * release that reads none of the versioned formats -- Comet 2024.01.0, whose indexes have no format
 * number -- states an empty list.
 *
 * @param readable the format numbers the release reads, each 1 or more, in the metadata's order
 * @param source the {@code https://} reference to the release's reader, when the metadata states
 *     one; empty only for a record built in code that states nothing, which then reads no index
 */
public record IndexFormats(List<Integer> readable, Optional<String> source) {

    /**
     * Validates the components and takes an immutable copy of the formats.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if a format is below 1 or listed twice, or the source is not
     *     an {@code https://} reference
     */
    public IndexFormats {
        readable = List.copyOf(readable);
        Objects.requireNonNull(source, "source");
        for (int format : readable) {
            if (format < 1) {
                throw new IllegalArgumentException(
                        "an index format number is 1 or more, not " + format);
            }
        }
        if (new HashSet<>(readable).size() != readable.size()) {
            throw new IllegalArgumentException("an index format is listed twice: " + readable);
        }
        if (source.filter(text -> !text.startsWith("https://")).isPresent()) {
            throw new IllegalArgumentException(
                    "the source of the index formats is not an https:// reference: "
                            + source.get());
        }
    }

    /**
     * What a record built in code states when it states nothing: no format, so every existing index
     * is refused rather than assumed readable.
     *
     * @return formats that read nothing
     */
    public static IndexFormats unstated() {
        return new IndexFormats(List.of(), Optional.empty());
    }

    /**
     * The formats, immutable.
     *
     * @return the format numbers
     */
    @Override
    public List<Integer> readable() {
        return List.copyOf(readable);
    }

    /**
     * Whether the release reads an index of one format.
     *
     * @param format the index's format number
     * @return {@code true} if it is one of {@link #readable()}
     */
    public boolean reads(int format) {
        return readable.contains(format);
    }

    /**
     * The formats in words, as the object of "reads".
     *
     * @return for example {@code index format v5}, {@code index formats v4 and v5}, or {@code none
     *     of the versioned index formats}
     */
    public String describe() {
        if (readable.isEmpty()) {
            return "none of the versioned index formats";
        }
        String formats =
                readable.stream().map(format -> "v" + format).collect(Collectors.joining(" and "));
        return (readable.size() == 1 ? "index format " : "index formats ") + formats;
    }
}

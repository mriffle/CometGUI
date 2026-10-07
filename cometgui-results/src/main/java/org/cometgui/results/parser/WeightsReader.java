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

package org.cometgui.results.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads Percolator's learned weights artefact ({@code --weights}).
 *
 * <p>The layout, as 3.06.5, 3.07.1 and 3.09 all write it and as the file's own comments describe
 * it: lines beginning {@code #} are comments; every other line belongs to a cross-validation split
 * of three tab-separated lines -- the feature names (Percolator ends them with {@code m0}, the
 * bias), the normalised weights, then the raw weights. The number of splits is however many such
 * groups the file holds ({@code R-PERC-09}); it is never assumed to be three.
 *
 * <p>Refused, each with its own {@link PercolatorOutputException.Problem} and a message naming the
 * file: a missing file, an empty one, one not UTF-8, a blank line, comments with no split, a split
 * whose header lacks a weight row, a feature name that is empty or repeated, a weight row of the
 * wrong width, a value that is not a finite decimal number, and a split naming different features
 * from the first.
 */
public final class WeightsReader {

    private static final int LINES_PER_SPLIT = 3;

    private WeightsReader() {}

    /**
     * Reads a weights artefact.
     *
     * @param file the artefact
     * @return the learned weights
     * @throws PercolatorOutputException if the file is refused
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static LearnedWeights read(Path file) throws PercolatorOutputException {
        Objects.requireNonNull(file, "file");
        List<String> lines = linesOf(file);
        if (lines.isEmpty()) {
            throw refuse(
                    file,
                    PercolatorOutputException.Problem.EMPTY_FILE,
                    "The Percolator weights file " + file + " is empty");
        }
        List<String> comments = new ArrayList<>();
        List<Integer> dataLines = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String text = lines.get(index);
            if (text.startsWith("#")) {
                comments.add(text);
            } else if (text.isBlank()) {
                throw refuse(
                        file,
                        PercolatorOutputException.Problem.BLANK_LINE,
                        "Line "
                                + (index + 1)
                                + " of the Percolator weights file "
                                + file
                                + " is blank");
            } else {
                dataLines.add(index);
            }
        }
        if (dataLines.isEmpty()) {
            throw refuse(
                    file,
                    PercolatorOutputException.Problem.NO_SPLITS,
                    "The Percolator weights file "
                            + file
                            + " holds no cross-validation split: it has only comment lines");
        }
        List<String> featureNames = null;
        List<WeightsSplit> splits = new ArrayList<>();
        for (int start = 0; start < dataLines.size(); start += LINES_PER_SPLIT) {
            int number = splits.size() + 1;
            if (start + LINES_PER_SPLIT > dataLines.size()) {
                String missing =
                        start + 1 < dataLines.size()
                                ? "its raw-weights row"
                                : "its normalised- and raw-weights rows";
                throw refuse(
                        file,
                        PercolatorOutputException.Problem.HEADER_WITHOUT_ROWS,
                        "Split "
                                + number
                                + " of the Percolator weights file "
                                + file
                                + " (header at line "
                                + (dataLines.get(start) + 1)
                                + ") lacks "
                                + missing
                                + "; every split is a header of feature names followed by a"
                                + " normalised-weights row and a raw-weights row");
            }
            int headerLine = dataLines.get(start);
            List<String> names = featureNames(file, lines.get(headerLine), headerLine + 1, number);
            if (featureNames == null) {
                featureNames = names;
            } else if (!featureNames.equals(names)) {
                throw refuse(
                        file,
                        PercolatorOutputException.Problem.SPLIT_MISMATCH,
                        "Split "
                                + number
                                + " of the Percolator weights file "
                                + file
                                + " (line "
                                + (headerLine + 1)
                                + ") names the features "
                                + names
                                + ", but split 1 names "
                                + featureNames
                                + "; every split must name the same features in the same order");
            }
            int normalisedLine = dataLines.get(start + 1);
            int rawLine = dataLines.get(start + 2);
            splits.add(
                    new WeightsSplit(
                            number,
                            weights(
                                    file,
                                    lines.get(normalisedLine),
                                    normalisedLine + 1,
                                    number,
                                    "normalised",
                                    names),
                            weights(file, lines.get(rawLine), rawLine + 1, number, "raw", names)));
        }
        return new LearnedWeights(file, comments, featureNames, splits);
    }

    private static List<String> featureNames(Path file, String text, int line, int split)
            throws PercolatorOutputException {
        List<String> names = List.of(text.split("\t", -1));
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (name.isBlank() || !seen.add(name)) {
                throw refuse(
                        file,
                        PercolatorOutputException.Problem.BAD_FEATURE_NAME,
                        "Line "
                                + line
                                + " of the Percolator weights file "
                                + file
                                + " (split "
                                + split
                                + "'s feature names) "
                                + (name.isBlank()
                                        ? "has an empty feature name"
                                        : "names the feature '" + name + "' twice")
                                + ": "
                                + names);
            }
        }
        return names;
    }

    private static List<Double> weights(
            Path file, String text, int line, int split, String kind, List<String> names)
            throws PercolatorOutputException {
        String[] fields = text.split("\t", -1);
        if (fields.length != names.size()) {
            throw refuse(
                    file,
                    PercolatorOutputException.Problem.ROW_WIDTH,
                    "Line "
                            + line
                            + " of the Percolator weights file "
                            + file
                            + " (split "
                            + split
                            + ", "
                            + kind
                            + " weights) has "
                            + fields.length
                            + " values, but the split's header names "
                            + names.size()
                            + " features");
        }
        List<Double> values = new ArrayList<>(fields.length);
        for (int index = 0; index < fields.length; index++) {
            double value = DecimalText.parse(fields[index]);
            if (Double.isNaN(value)) {
                throw refuse(
                        file,
                        PercolatorOutputException.Problem.NOT_A_NUMBER,
                        "Line "
                                + line
                                + " of the Percolator weights file "
                                + file
                                + " (split "
                                + split
                                + ", "
                                + kind
                                + " weights) holds '"
                                + fields[index]
                                + "' for the feature '"
                                + names.get(index)
                                + "', which is not a finite decimal number");
            }
            values.add(value);
        }
        return values;
    }

    private static List<String> linesOf(Path file) throws PercolatorOutputException {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            throw new PercolatorOutputException(
                    file,
                    PercolatorOutputException.Problem.MISSING_FILE,
                    "The Percolator weights file " + file + " does not exist",
                    missing);
        } catch (IOException unreadable) {
            throw new PercolatorOutputException(
                    file,
                    PercolatorOutputException.Problem.UNREADABLE,
                    "The Percolator weights file "
                            + file
                            + " could not be read as UTF-8 text: "
                            + unreadable,
                    unreadable);
        }
    }

    private static PercolatorOutputException refuse(
            Path file, PercolatorOutputException.Problem problem, String message) {
        return new PercolatorOutputException(file, problem, message, null);
    }
}

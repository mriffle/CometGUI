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

package org.cometgui.domain.run;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The {@code -N} output base name of every spectrum file in a run: design decision P8-4, as a pure
 * function.
 *
 * <p>Comet is run once per spectrum file, always with {@code -N<run>/outputs/comet/<base>}, and
 * writes {@code <base>.pep.xml} and {@code <base>.pin}. The base names therefore decide whether two
 * inputs' outputs overwrite each other, and whether an output can land outside the run.
 *
 * <h2>The rule</h2>
 *
 * <ol>
 *   <li><strong>Natural base.</strong> The file name (the last path element) minus its spectrum
 *       extension, one of {@link #SPECTRUM_EXTENSIONS}, matched case-insensitively. A name with no
 *       spectrum extension keeps its whole name: whether such a file is acceptable input is the
 *       validator's question, not this one's.
 *   <li><strong>Refusal.</strong> A natural base that is empty, {@code .} or {@code ..}, or that
 *       holds a {@code /}, a {@code \} or a control character, is refused with the file's position
 *       and name -- {@code ..} would put the outputs beside the run directory rather than in it,
 *       and {@code \} is a separator on Windows. {@code ..mzML} is such a name.
 *   <li><strong>Distinct, case-insensitively.</strong> Two bases are the same when they are equal
 *       after {@link Locale#ROOT} lower-casing and Unicode NFC normalisation -- because the default
 *       file systems on macOS and Windows treat {@code A.pin} and {@code a.pin} as one file, and
 *       macOS treats a composed and a decomposed {@code é} as one name.
 *   <li><strong>Collisions, in input order.</strong> First every file's natural base is reserved,
 *       first comer wins. Then each later file whose natural base was already taken gets the
 *       smallest suffix {@code _2}, {@code _3} ... for which {@code <base>_<n>} is neither some
 *       file's natural base nor already assigned. So {@code a.mzML}, {@code A.mzXML}, {@code a.mgf}
 *       become {@code a}, {@code A_2}, {@code a_3}; and {@code a.mzML}, {@code A.mgf}, {@code
 *       a_2.mzML} become {@code a}, {@code A_3}, {@code a_2}, because a file genuinely named {@code
 *       a_2} keeps its own name. The result depends only on the list, so the same inputs always
 *       produce the same names.
 * </ol>
 */
public final class OutputBaseNames {

    /**
     * The spectrum extensions stripped from a file name, in the case Comet's documentation writes
     * them; matched case-insensitively.
     */
    public static final List<String> SPECTRUM_EXTENSIONS =
            List.of(".mzML", ".mzXML", ".mgf", ".ms2", ".cms2", ".bms2", ".raw");

    private OutputBaseNames() {
        throw new AssertionError("OutputBaseNames is never instantiated");
    }

    /**
     * Derives every input's base name, in input order.
     *
     * @param spectrumFiles the run's spectrum files, in the order the user gave them
     * @return one entry per file, position 1 first
     * @throws NullPointerException if the list or an element is {@code null}
     * @throws IllegalArgumentException if the list is empty, a path has no file name, or a file's
     *     name cannot be a base name, naming the file and its position
     */
    public static List<OutputBase> derive(List<Path> spectrumFiles) {
        Objects.requireNonNull(spectrumFiles, "spectrumFiles");
        List<String> names = new ArrayList<>(spectrumFiles.size());
        for (int index = 0; index < spectrumFiles.size(); index++) {
            Path file =
                    Objects.requireNonNull(
                            spectrumFiles.get(index), "spectrumFiles[" + index + "]");
            Path name = file.getFileName();
            if (name == null) {
                throw new IllegalArgumentException(
                        "spectrum file " + (index + 1) + " (" + file + ") has no file name");
            }
            names.add(name.toString());
        }
        List<String> bases = baseNames(names);
        List<OutputBase> derived = new ArrayList<>(bases.size());
        for (int index = 0; index < bases.size(); index++) {
            derived.add(new OutputBase(index + 1, spectrumFiles.get(index), bases.get(index)));
        }
        return List.copyOf(derived);
    }

    /**
     * Derives every base name from the files' names alone, in input order: the whole rule, over
     * strings.
     *
     * <p>{@link #derive(List)} calls this with each path's last element. It exists separately so
     * that the rule can be applied to a name that this JVM cannot turn into a {@link Path} -- a
     * non-ASCII name under a JVM whose file-name encoding is not UTF-8 -- and tested there.
     *
     * @param fileNames the spectrum files' names, without directories, in input order
     * @return the base names, in the same order
     * @throws NullPointerException if the list or an element is {@code null}
     * @throws IllegalArgumentException if the list is empty, or a name cannot be a base name,
     *     naming it and its position
     */
    public static List<String> baseNames(List<String> fileNames) {
        Objects.requireNonNull(fileNames, "fileNames");
        if (fileNames.isEmpty()) {
            throw new IllegalArgumentException("a run needs at least one spectrum file");
        }
        List<String> natural = new ArrayList<>(fileNames.size());
        Set<String> naturalFolded = new HashSet<>();
        for (int index = 0; index < fileNames.size(); index++) {
            String fileName =
                    Objects.requireNonNull(fileNames.get(index), "fileNames[" + index + "]");
            String base = naturalBase(index + 1, fileName);
            natural.add(base);
            naturalFolded.add(fold(base));
        }
        Set<String> assigned = new HashSet<>();
        List<String> bases = new ArrayList<>(natural.size());
        for (String base : natural) {
            String chosen = base;
            if (!assigned.add(fold(base))) {
                int suffix = 2;
                chosen = base + "_" + suffix;
                while (naturalFolded.contains(fold(chosen)) || assigned.contains(fold(chosen))) {
                    suffix++;
                    chosen = base + "_" + suffix;
                }
                assigned.add(fold(chosen));
            }
            bases.add(chosen);
        }
        return List.copyOf(bases);
    }

    /**
     * The natural base of one file: its name minus its spectrum extension.
     *
     * @param position the file's 1-based position, for the message
     * @param text the file's name
     * @return the natural base
     * @throws IllegalArgumentException if the base is unsafe
     */
    static String naturalBase(int position, String text) {
        String base = text;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String extension : SPECTRUM_EXTENSIONS) {
            if (lower.endsWith(extension.toLowerCase(Locale.ROOT))) {
                base = text.substring(0, text.length() - extension.length());
                break;
            }
        }
        String problem = problemWith(base);
        if (problem != null) {
            throw new IllegalArgumentException(
                    "spectrum file "
                            + position
                            + " (\""
                            + text
                            + "\") cannot name a Comet output: its base name "
                            + problem);
        }
        return base;
    }

    /**
     * Requires a base name to be one Comet's output can safely be named after.
     *
     * @param base the base name
     * @return the same base name
     * @throws NullPointerException if {@code base} is {@code null}
     * @throws IllegalArgumentException if the base is empty, {@code .}, {@code ..}, or holds a
     *     separator or a control character
     */
    public static String requireSafe(String base) {
        Objects.requireNonNull(base, "base");
        String problem = problemWith(base);
        if (problem != null) {
            throw new IllegalArgumentException(
                    "\"" + base + "\" cannot be an output base name: " + problem);
        }
        return base;
    }

    /**
     * What is wrong with a base name, or {@code null} if nothing is.
     *
     * @param base the candidate
     * @return the problem in words, or {@code null}
     */
    private static String problemWith(String base) {
        if (base.isEmpty()) {
            return "would be empty";
        }
        if (".".equals(base) || "..".equals(base)) {
            return "would be \""
                    + base
                    + "\", which names a directory, not a file in outputs/comet";
        }
        for (int index = 0; index < base.length(); index++) {
            char character = base.charAt(index);
            if (character == '/' || character == '\\') {
                return "would contain a path separator, and so escape outputs/comet";
            }
            if (Character.isISOControl(character)) {
                return "would contain a control character";
            }
        }
        return null;
    }

    /**
     * The form two bases are compared in: lower case under {@link Locale#ROOT}, then NFC.
     *
     * @param base the base name
     * @return its comparison key
     */
    static String fold(String base) {
        return Normalizer.normalize(base.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }
}

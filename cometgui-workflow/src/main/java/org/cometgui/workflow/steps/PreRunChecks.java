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

package org.cometgui.workflow.steps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.params.CometIndexDescription;
import org.cometgui.domain.params.PreRunFacts;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.schema.ValidatorId;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.tools.comet.CometIndexCommand;
import org.cometgui.tools.comet.CometIndexHeaderReader;
import org.cometgui.tools.comet.FastaDecoyScanner;

/**
 * The pre-run check: everything about a search that needs the file system, gathered into one {@link
 * PreRunReport} together with the one validator's verdict over the model and the facts.
 *
 * <p>Run by {@link CometWorkflow#check} (Run readiness), by {@link CometWorkflow#prepare} before a
 * run directory exists, and again by the run's own {@code validate-configuration} step, so that an
 * attempt -- a retry included -- cannot reach Comet without passing it.
 *
 * <h2>What is checked</h2>
 *
 * <ol>
 *   <li>The selected Comet is the release the parameters are for, its executable exists, can be
 *       executed and still has the SHA-256 it was selected at.
 *   <li>There is at least one spectrum file; each exists, is readable and has a spectrum extension
 *       Comet reads here ({@code .raw} only on Windows); their {@code -N} base names can be
 *       derived.
 *   <li>{@code database_name} is an absolute path to a readable file. A FASTA is scanned for decoys
 *       with the model's own {@code decoy_prefix} ({@code R-DEC-02}). An existing {@code .idx} --
 *       which is searched as it is, so it needs {@link IndexMode#NONE} -- has its header read, and
 *       the FASTA its header names ({@code InputDB:}) is scanned for decoys; an index whose FASTA
 *       cannot be found is refused, because its decoy configuration could not be checked.
 *   <li>With an index mode, the cache entry for the search's key is looked up; if it is complete,
 *       its header is read, so that the validator judges the index the search would reuse.
 *   <li>Every other parameter the release's metadata gives the validator {@code path} -- in the
 *       bundled metadata {@code peff_obo}, {@code compoundmods_file}, {@code spectral_library_name}
 *       and {@code protein_modslist_file} -- is either empty or an absolute path to a readable
 *       file. Measured on Comet 2026.03.0: {@code -q}'s placeholder {@code spectral_library_name =
 *       /some/path/speclib.file}, a missing {@code compoundmods_file} and a missing {@code
 *       protein_modslist_file} each stop Comet with exit 1; a missing {@code peff_obo} is ignored
 *       when {@code peff_format} is 0 and only warned about otherwise, so the search would run
 *       without the modifications the user asked for. The set is read from the model's metadata,
 *       never listed here.
 *   <li>The project's {@code runs/} directory exists and is writable.
 *   <li>The validator judges the model with every fact gathered: the decoy blocks and the index
 *       compatibility rules are its own, so there is no second rule here.
 * </ol>
 */
final class PreRunChecks {

    /** The parameter naming the database. */
    static final String DATABASE = "database_name";

    /** The parameter naming the decoy prefix. */
    static final String DECOY_PREFIX = "decoy_prefix";

    /** The extension of a Thermo RAW file, searched only on Windows. */
    static final String RAW = ".raw";

    private final CachingHashService hashes;

    private final CanonicalParamsWriter writer;

    private final CometValidator validator;

    private final boolean windows;

    PreRunChecks(CachingHashService hashes, CanonicalParamsWriter writer, boolean windows) {
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.validator = CometValidator.standard();
        this.windows = windows;
    }

    /**
     * Whether this JVM runs on Windows, where Comet reads Thermo RAW files.
     *
     * @return {@code true} on Windows
     */
    static boolean onWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * The check's verdict and what it learned on the way.
     *
     * @param report the report
     * @param cacheEntry the index cache entry of the search's key, when an index mode is set and
     *     the key could be computed
     * @param cacheComplete whether that entry was complete when checked
     */
    record Outcome(
            PreRunReport report, Optional<IndexCacheEntry> cacheEntry, boolean cacheComplete) {

        Outcome {
            Objects.requireNonNull(report, "report");
            Objects.requireNonNull(cacheEntry, "cacheEntry");
        }
    }

    /**
     * Checks a search.
     *
     * @param project the project the run would belong to
     * @param model the parameters
     * @param spectra the spectrum files, in order
     * @param comet the selected Comet
     * @param mode the index mode
     * @return the verdict
     */
    Outcome check(
            ProjectLayout project,
            CometParameters model,
            List<Path> spectra,
            CometSelection comet,
            IndexMode mode) {
        List<String> problems = new ArrayList<>();
        checkComet(model, comet, problems);
        checkSpectra(spectra, problems);
        PreRunFacts facts = PreRunFacts.none();
        Optional<IndexCacheEntry> entry = Optional.empty();
        boolean complete = false;
        Optional<Path> database = database(model, problems);
        if (database.isPresent()) {
            Path file = database.get();
            if (isIndex(file)) {
                if (mode != IndexMode.NONE) {
                    problems.add(
                            DATABASE
                                    + " = "
                                    + file
                                    + " is already a Comet index, but the index mode "
                                    + mode.wireName()
                                    + " builds one from a FASTA; choose the FASTA, or the index"
                                    + " mode none to search this index as it is");
                } else {
                    facts = indexFacts(model, file, facts, problems);
                }
            } else {
                facts = census(model, file, facts, problems);
                if (mode != IndexMode.NONE) {
                    try {
                        entry = Optional.of(cacheEntry(project, model, comet, mode, file));
                        Optional<IndexCacheEntry.Completion> done = entry.get().completion();
                        if (done.isPresent()) {
                            complete = true;
                            facts =
                                    facts.withIndex(
                                            CometIndexHeaderReader.read(entry.get().indexFile()));
                        }
                    } catch (IOException | RuntimeException unusable) {
                        problems.add(
                                "the index cache for "
                                        + file
                                        + " cannot be used: "
                                        + unusable.getMessage());
                    }
                }
            }
        }
        checkPathParameters(model, problems);
        if (!Files.isDirectory(project.runsDirectory())
                || !Files.isWritable(project.runsDirectory())) {
            problems.add(
                    "the project's runs directory "
                            + project.runsDirectory()
                            + " does not exist or cannot be written");
        }
        return new Outcome(
                new PreRunReport(problems, validator.validate(model, facts), facts),
                entry,
                complete);
    }

    /**
     * The index cache entry a search with an index mode uses.
     *
     * @throws IOException if the FASTA cannot be hashed
     */
    IndexCacheEntry cacheEntry(
            ProjectLayout project,
            CometParameters model,
            CometSelection comet,
            IndexMode mode,
            Path fasta)
            throws IOException {
        String name = String.valueOf(fasta.getFileName());
        String key =
                IndexCacheKey.of(
                        hashes.hash(fasta).sha256(),
                        name,
                        mode,
                        comet.release(),
                        model,
                        writer.write(model));
        return IndexCacheEntry.of(project, key, name);
    }

    /**
     * Judges an index the run is about to search, with the same rules and the same facts the
     * pre-run check uses: the FASTA's decoy census and the index's own header.
     *
     * @param model the search's parameters
     * @param fasta the FASTA the index was built from
     * @param index the index
     * @return the validator's report
     * @throws IOException if the FASTA cannot be scanned or the index's header cannot be read
     */
    ValidationReport judgeIndex(CometParameters model, Path fasta, Path index) throws IOException {
        return validator.validate(
                model,
                new PreRunFacts(
                        Optional.of(FastaDecoyScanner.scan(fasta, prefixOf(model))),
                        Optional.of(CometIndexHeaderReader.read(index))));
    }

    private void checkComet(CometParameters model, CometSelection comet, List<String> problems) {
        if (!model.version().equals(comet.release())) {
            problems.add(
                    "the parameters are for Comet "
                            + model.version().text()
                            + ", but the selected Comet is "
                            + comet.release().text());
        }
        Path executable = comet.executable();
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            problems.add(
                    "the selected Comet executable "
                            + executable
                            + " does not exist or cannot be executed");
            return;
        }
        try {
            String now = hashes.hash(executable).sha256();
            if (!now.equals(comet.sha256())) {
                problems.add(
                        "the Comet executable "
                                + executable
                                + " has SHA-256 "
                                + now
                                + ", but Comet "
                                + comet.release().text()
                                + " was selected at "
                                + comet.sha256()
                                + "; it has changed since it was selected");
            }
        } catch (IOException unreadable) {
            problems.add(
                    "the selected Comet executable "
                            + executable
                            + " cannot be read: "
                            + unreadable.getMessage());
        }
    }

    private void checkSpectra(List<Path> spectra, List<String> problems) {
        if (spectra.isEmpty()) {
            problems.add("there is no spectrum file to search");
            return;
        }
        for (int index = 0; index < spectra.size(); index++) {
            Path spectrum = spectra.get(index);
            String what = "spectrum file " + (index + 1) + " (" + spectrum + ")";
            if (!Files.isRegularFile(spectrum) || !Files.isReadable(spectrum)) {
                problems.add(what + " does not exist or cannot be read");
            }
            String extension = extensionOf(spectrum);
            if (extension.isEmpty()) {
                problems.add(
                        what
                                + " has none of the spectrum extensions Comet reads: "
                                + String.join(", ", OutputBaseNames.SPECTRUM_EXTENSIONS));
            } else if (RAW.equals(extension.toLowerCase(Locale.ROOT)) && !windows) {
                problems.add(what + " is a Thermo RAW file, which Comet reads only on Windows");
            }
        }
        try {
            OutputBaseNames.derive(spectra);
        } catch (IllegalArgumentException unnamable) {
            problems.add(unnamable.getMessage());
        }
    }

    /**
     * The spectrum extension a file name ends with, as {@link OutputBaseNames} lists it, or empty.
     */
    static String extensionOf(Path file) {
        String name = String.valueOf(file.getFileName()).toLowerCase(Locale.ROOT);
        for (String extension : OutputBaseNames.SPECTRUM_EXTENSIONS) {
            if (name.endsWith(extension.toLowerCase(Locale.ROOT))) {
                return extension;
            }
        }
        return "";
    }

    /** Whether a database is a Comet index rather than a FASTA, as Comet itself decides. */
    static boolean isIndex(Path database) {
        return String.valueOf(database.getFileName()).endsWith(CometIndexCommand.INDEX_SUFFIX);
    }

    private static Optional<Path> database(CometParameters model, List<String> problems) {
        String text = ((ParameterValue.Text) model.value(DATABASE)).text();
        Path file;
        try {
            file = Path.of(text);
        } catch (InvalidPathException unusable) {
            problems.add(DATABASE + " = " + text + " is not a path: " + unusable.getMessage());
            return Optional.empty();
        }
        if (text.isEmpty() || !file.isAbsolute()) {
            problems.add(
                    DATABASE
                            + " = "
                            + text
                            + " must name the database by its absolute path; Comet runs in the"
                            + " run directory, where a relative path would name another file");
            return Optional.empty();
        }
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            problems.add(
                    "the database "
                            + file
                            + " ("
                            + DATABASE
                            + ") does not exist or cannot be read");
            return Optional.empty();
        }
        try {
            // The run records, links and keys the canonical path (R-RUN-03), so this does too.
            return Optional.of(file.toRealPath());
        } catch (IOException unresolvable) {
            problems.add("the database " + file + " cannot be resolved: " + unresolvable);
            return Optional.empty();
        }
    }

    /**
     * Every set parameter the model's metadata gives the validator {@code path}, except {@code
     * database_name}, which {@link #database} checks: it must name a readable file by its absolute
     * path. An empty value -- where the metadata allows one, the validator's {@code path.empty}
     * judges -- means no file and is not checked here.
     */
    static void checkPathParameters(CometParameters model, List<String> problems) {
        for (ParameterEntry entry : model.entries()) {
            String name = entry.name();
            if (DATABASE.equals(name)
                    || !entry.definition().validators().contains(ValidatorId.PATH)) {
                continue;
            }
            String text = ((ParameterValue.Text) entry.value()).text();
            if (!text.isEmpty()) {
                checkPathParameter(name, entry.definition().displayName(), text, problems);
            }
        }
    }

    private static void checkPathParameter(
            String name, String label, String text, List<String> problems) {
        String what = name + " (" + label + ") = " + text;
        Path file;
        try {
            file = Path.of(text);
        } catch (InvalidPathException unusable) {
            problems.add(what + " is not a path: " + unusable.getMessage());
            return;
        }
        if (!file.isAbsolute()) {
            problems.add(
                    what
                            + " must name the file by its absolute path; Comet runs in the run"
                            + " directory, where a relative path would name another file");
        } else if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            problems.add(
                    what
                            + " does not exist or cannot be read; clear it to search without one,"
                            + " or choose the file");
        }
    }

    private static String prefixOf(CometParameters model) {
        return ((ParameterValue.Text) model.value(DECOY_PREFIX)).text();
    }

    private static PreRunFacts census(
            CometParameters model, Path fasta, PreRunFacts facts, List<String> problems) {
        try {
            return facts.withCensus(FastaDecoyScanner.scan(fasta, prefixOf(model)));
        } catch (IOException | IllegalArgumentException unscanned) {
            problems.add(
                    "the decoys in "
                            + fasta
                            + " cannot be counted, so the decoy configuration cannot be checked: "
                            + unscanned.getMessage());
            return facts;
        }
    }

    private static PreRunFacts indexFacts(
            CometParameters model, Path index, PreRunFacts facts, List<String> problems) {
        CometIndexDescription description;
        try {
            description = CometIndexHeaderReader.read(index);
        } catch (IOException unreadable) {
            problems.add(unreadable.getMessage());
            return facts;
        }
        PreRunFacts withIndex = facts.withIndex(description);
        Optional<Path> fasta = indexedFasta(index, description);
        if (fasta.isEmpty()) {
            problems.add(
                    "the index "
                            + index
                            + " names no FASTA that can be read ("
                            + description
                                    .inputDatabase()
                                    .map(name -> "InputDB: " + name)
                                    .orElse("no InputDB: line")
                            + "), so its decoys cannot be counted and the decoy configuration"
                            + " cannot be checked");
            return withIndex;
        }
        return census(model, fasta.get(), withIndex, problems);
    }

    /** The FASTA an index header names, resolved against the index's directory, if readable. */
    static Optional<Path> indexedFasta(Path index, CometIndexDescription description) {
        if (description.inputDatabase().isEmpty()) {
            return Optional.empty();
        }
        Path named;
        try {
            named = Path.of(description.inputDatabase().get());
        } catch (InvalidPathException unusable) {
            return Optional.empty();
        }
        Path directory = index.toAbsolutePath().getParent();
        Path resolved = named.isAbsolute() || directory == null ? named : directory.resolve(named);
        return Files.isRegularFile(resolved) && Files.isReadable(resolved)
                ? Optional.of(resolved)
                : Optional.empty();
    }
}

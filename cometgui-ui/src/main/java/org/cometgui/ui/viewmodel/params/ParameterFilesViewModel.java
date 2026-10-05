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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.migration.MigrationException;
import org.cometgui.params.comet.migration.MigrationResult;
import org.cometgui.params.comet.migration.SchemaMigration;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParamsHighlighting;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.params.comet.writer.ParamsWriteException;
import org.cometgui.params.comet.writer.WrittenParams;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * Saving the configuration as a parameter file, and importing one.
 *
 * <h2>Saving ({@code R-PARAM-12})</h2>
 *
 * <p>The file is written once by {@link CanonicalParamsWriter#writeOnce} and hashed by the {@link
 * HashService} port over the file on disk; the result ({@link WrittenParams}: path, MD5 and
 * SHA-256, size) is what later steps pass on, and the configuration saved is kept as the "last
 * saved" one the Expert diff compares with. A file that already exists is never overwritten.
 *
 * <p><strong>A configuration that would block a run is not saved</strong>: an error in the
 * session's one report (an unresolved migration entry included) or a field holding a refused edit
 * refuses the save, with every reason. A saved file is one Comet can be given; a file that carries
 * a known error, or that does not hold what the screen shows, is not.
 *
 * <h2>Importing</h2>
 *
 * <p>A file is read for the selected release by {@link CometParamsParser} and adopted as {@link
 * Adoption#IMPORTED}; a parse with an error changes nothing and reports every error with its line.
 * A file whose {@code # comet_version} line names another release the metadata describes is not
 * read straight away: the choice is offered ({@link ImportOffer}) -- migrate it to the selected
 * release with {@link SchemaMigration#migrateFile}, its report put under review; read it as its own
 * release when that release is offered; or read it as the selected release, when the parser's
 * mismatch warning names both versions ({@code R-PARAM-06}) and stays in the session's report.
 */
public final class ParameterFilesViewModel {

    private final ParameterSession session;

    private final CanonicalParamsWriter writer;

    private final HashService hashService;

    private final NonNullProperty<Optional<CometParameters>> lastSaved;

    private final NonNullProperty<Optional<WrittenParams>> lastWritten;

    private final NonNullProperty<Optional<ImportOffer>> offer;

    /**
     * Saving and importing for a session.
     *
     * @param session the session
     * @param build the running build, whose version the file's header names
     * @param hashService the hash service that reads the written file
     */
    public ParameterFilesViewModel(
            ParameterSession session, BuildIdentity build, HashService hashService) {
        this.session = Objects.requireNonNull(session, "session");
        this.writer = new CanonicalParamsWriter(Objects.requireNonNull(build, "build"));
        this.hashService = Objects.requireNonNull(hashService, "hashService");
        this.lastSaved = new NonNullProperty<>(this, "lastSaved", Optional.empty());
        this.lastWritten = new NonNullProperty<>(this, "lastWritten", Optional.empty());
        this.offer = new NonNullProperty<>(this, "offer", Optional.empty());
    }

    /**
     * The configuration as it was last saved, for the Expert diff.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<CometParameters>> lastSavedProperty() {
        return lastSaved.getReadOnlyProperty();
    }

    /**
     * The configuration as it was last saved.
     *
     * @return the model saved, or empty before the first save
     */
    public Optional<CometParameters> lastSaved() {
        return lastSaved.get();
    }

    /**
     * The file last written.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<WrittenParams>> lastWrittenProperty() {
        return lastWritten.getReadOnlyProperty();
    }

    /**
     * The file last written: its path, digests and size.
     *
     * @return the file, or empty before the first save
     */
    public Optional<WrittenParams> lastWritten() {
        return lastWritten.get();
    }

    /**
     * Saves the configuration to a new file.
     *
     * @param target where to write; must not exist
     * @return the file written, or every reason it was not
     */
    public SaveOutcome save(Path target) {
        Objects.requireNonNull(target, "target");
        List<String> blocking = new ArrayList<>();
        for (SummaryEntry entry : SummaryEntry.listOf(session.report(), session)) {
            if (entry.kind().blocksRun()) {
                blocking.add(entry.text());
            }
        }
        if (!blocking.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            reasons.add(
                    "The configuration was not saved: a parameter file is saved only when nothing"
                            + " would block a run. Resolve these first:");
            reasons.addAll(blocking);
            return SaveOutcome.refused(reasons);
        }
        CometParameters model = session.model();
        WrittenParams written;
        try {
            written = writer.writeOnce(model, target, hashService);
        } catch (FileAlreadyExistsException exists) {
            return SaveOutcome.refused(
                    List.of(
                            target
                                    + " already exists. CometGUI never overwrites a parameter"
                                    + " file; choose another name."));
        } catch (ParamsWriteException refused) {
            // Not reached while validation and the writer agree: an enzyme number absent from the
            // table is the model's enzyme_in_table error, which blocks the save above. Kept so
            // that a disagreement is a message, not an exception thrown at the view.
            return SaveOutcome.refused(List.of(refused.getMessage()));
        } catch (IOException failed) {
            return SaveOutcome.refused(
                    List.of("Could not write " + target + ": " + failed.getMessage()));
        }
        lastSaved.set(Optional.of(model));
        lastWritten.set(Optional.of(written));
        return SaveOutcome.saved(written);
    }

    /**
     * The import waiting for the scientist's choice.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<ImportOffer>> offerProperty() {
        return offer.getReadOnlyProperty();
    }

    /**
     * The import waiting for the scientist's choice.
     *
     * @return the offer, or empty
     */
    public Optional<ImportOffer> offer() {
        return offer.get();
    }

    /**
     * Imports a parameter file, read as UTF-8.
     *
     * @param file the file
     * @return what happened
     */
    public ImportOutcome importFile(Path file) {
        Objects.requireNonNull(file, "file");
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return refused(
                    List.of(
                            "Could not read "
                                    + file
                                    + " as a UTF-8 text file: "
                                    + unreadable.getMessage()));
        }
        return importText(text, file.toString());
    }

    /**
     * Imports the text of a parameter file.
     *
     * @param text the whole file
     * @param source where it came from, as the messages name it
     * @return what happened; an offer when the file names another release the metadata describes
     */
    public ImportOutcome importText(String text, String source) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(source, "source");
        offer.set(Optional.empty());
        ToolVersion selected = session.release();
        Optional<ToolVersion> declared = ParamsHighlighting.declaredRelease(text);
        if (declared.isPresent()
                && !declared.get().equals(selected)
                && session.metadata().version(declared.get()).isPresent()) {
            ImportOffer waiting =
                    new ImportOffer(
                            text,
                            source,
                            declared.get(),
                            selected,
                            session.offeredReleases().contains(declared.get()));
            offer.set(Optional.of(waiting));
            return new ImportOutcome(ImportOutcome.Kind.OFFERED, List.of(waiting.question()));
        }
        return read(text, source, selected);
    }

    /**
     * Migrates the offered file to the selected release and adopts it, its report under review.
     *
     * @return what happened
     */
    public ImportOutcome migrateOffered() {
        Optional<ImportOffer> waiting = offer.get();
        if (waiting.isEmpty()) {
            return noOffer();
        }
        MigrationResult migrated;
        try {
            migrated =
                    SchemaMigration.migrateFile(
                            session.metadata(), waiting.get().text(), waiting.get().selected());
        } catch (MigrationException refused) {
            return refused(List.of(refused.getMessage()));
        }
        offer.set(Optional.empty());
        session.adoptMigration(migrated);
        String headline = migrated.report().describe().lines().findFirst().orElseThrow();
        return new ImportOutcome(ImportOutcome.Kind.MIGRATED, List.of(headline));
    }

    /**
     * Reads the offered file as its own release and switches the editor to it.
     *
     * @return what happened; refused when that release is not offered
     */
    public ImportOutcome readOfferedAsItsRelease() {
        Optional<ImportOffer> waiting = offer.get();
        if (waiting.isEmpty()) {
            return noOffer();
        }
        if (!waiting.get().declaredOffered()) {
            return refused(
                    List.of(
                            "Comet "
                                    + waiting.get().declared().text()
                                    + " is not a release the editor offers; migrate the file or"
                                    + " read it as Comet "
                                    + waiting.get().selected().text()
                                    + "."));
        }
        return readWaiting(waiting.get(), waiting.get().declared());
    }

    /**
     * Reads the offered file as the selected release, with the parser's mismatch warning.
     *
     * @return what happened
     */
    public ImportOutcome readOfferedAsSelected() {
        Optional<ImportOffer> waiting = offer.get();
        if (waiting.isEmpty()) {
            return noOffer();
        }
        return readWaiting(waiting.get(), waiting.get().selected());
    }

    /** Drops the offer; nothing is imported. */
    public void dismissOffer() {
        offer.set(Optional.empty());
    }

    private ImportOutcome readWaiting(ImportOffer waiting, ToolVersion release) {
        ImportOutcome outcome = read(waiting.text(), waiting.source(), release);
        if (outcome.kind() == ImportOutcome.Kind.IMPORTED) {
            offer.set(Optional.empty());
        }
        return outcome;
    }

    private ImportOutcome read(String text, String source, ToolVersion release) {
        ParseResult parsed = new CometParamsParser(session.metadata(), release).parse(text);
        if (!parsed.succeeded()) {
            List<String> reasons = new ArrayList<>();
            reasons.add(
                    source
                            + " was not imported: it does not read as a Comet "
                            + release.text()
                            + " parameter file.");
            for (Diagnostic error : parsed.errors()) {
                reasons.add(ExpertViewModel.describe(error));
            }
            return refused(reasons);
        }
        session.adopt(parsed.model().orElseThrow(), Adoption.IMPORTED);
        return new ImportOutcome(
                ImportOutcome.Kind.IMPORTED,
                parsed.warnings().stream().map(ExpertViewModel::describe).toList());
    }

    private static ImportOutcome noOffer() {
        return refused(List.of("No import is waiting for a choice."));
    }

    private static ImportOutcome refused(List<String> reasons) {
        return new ImportOutcome(ImportOutcome.Kind.REFUSED, reasons);
    }
}

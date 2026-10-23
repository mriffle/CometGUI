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

package org.cometgui.app.e2e.bridge;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javafx.stage.Window;
import org.cometgui.app.config.FxFileChooser;
import org.cometgui.ui.viewmodel.params.FileChooserPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rewrite of the product's dialogs object, on the product's own compiled class: the three
 * methods, and only they, call {@link BridgeDialogs}; and the product's {@link FxFileChooser} over
 * the rewritten object still sets the titles, filters and suggested names and still turns the
 * answers -- or a cancelled dialog -- into its port's results.
 */
class DialogsTransformerTest {

    @TempDir private Path scratch;

    private static final String DIALOGS_OBJECT = "org/cometgui/app/config/FxFileChooser$1";

    private static final String REWROTE =
            "rewrote org.cometgui.app.config.FxFileChooser$1: [open, openMultiple, save]";

    private static final String STRANGE = "org.cometgui.app.config.FxFileChooser$9";

    private static final ClassDesc DIALOGS =
            ClassDesc.of("org.cometgui.app.config.FxFileChooser$Dialogs");

    @BeforeEach
    @AfterEach
    void forget() {
        BridgeDialogs.reset();
    }

    @Test
    @DisplayName(
            "the product's JAVAFX dialogs object is rewritten -- open, openMultiple and save call"
                    + " BridgeDialogs -- and nothing else is touched")
    void rewritesTheDialogsObjectOnly() throws IOException {
        byte[] original = bytesOf(DIALOGS_OBJECT);
        byte[] rewritten = transform(DIALOGS_OBJECT, original);
        assertAll(
                () -> assertNotNull(rewritten, "the dialogs object is rewritten"),
                () -> assertEquals(List.of(), DialogsTransformer.rewrittenIn(original)),
                () ->
                        assertEquals(
                                List.of("open", "openMultiple", "save"),
                                DialogsTransformer.rewrittenIn(rewritten)),
                () ->
                        assertNull(
                                transform(
                                        "org/cometgui/app/config/FxFileChooser",
                                        bytesOf("org/cometgui/app/config/FxFileChooser")),
                                "FxFileChooser itself is not touched"),
                () ->
                        assertNull(
                                transform(
                                        "org/cometgui/app/config/FxFileChooser$Dialogs",
                                        bytesOf("org/cometgui/app/config/FxFileChooser$Dialogs")),
                                "nor the interface"),
                () ->
                        assertNull(
                                transform("java/lang/String", new byte[] {1}), "nor anything else"),
                () ->
                        assertTrue(
                                DialogsTransformer.report().contains(REWROTE),
                                DialogsTransformer.report().toString()));
    }

    @Test
    @DisplayName(
            "a class that implements Dialogs without the three methods is reported, not rewritten")
    void refusesAStrangeDialogsClass() throws IOException {
        byte[] strange =
                ClassFile.of()
                        .build(
                                ClassDesc.of(STRANGE),
                                builder -> builder.withInterfaceSymbols(DIALOGS));
        assertNull(transform(STRANGE.replace('.', '/'), strange));
        assertTrue(
                DialogsTransformer.report().stream()
                        .anyMatch(
                                line ->
                                        line.startsWith("FAILED to rewrite " + STRANGE)
                                                && line.contains("dialog methods []")),
                DialogsTransformer.report().toString());
    }

    @Test
    @DisplayName(
            "FxFileChooser over the rewritten object: the queued files come back through the port,"
                    + " an unanswered dialog is cancelled, and every dialog's title and filters are"
                    + " the product's")
    void theProductsChooserLogicStillRuns() throws IOException, ReflectiveOperationException {
        FileChooserPort port =
                chooserOver(load(transform(DIALOGS_OBJECT, bytesOf(DIALOGS_OBJECT))));
        // ASCII on purpose: an ordinary test must not depend on the runner's locale. A
        // non-ASCII answer through the bridge is BridgeEndToEndTest's, under a UTF-8 runner.
        Path first = scratch.resolve("sample 1.mzML");
        Path second = scratch.resolve("sample_2.mzML");
        Path export = scratch.resolve("psms.tsv");
        BridgeDialogs.queue(
                "openMultiple",
                Pattern.quote("Choose spectrum files to search"),
                List.of(first.toString(), second.toString()));
        BridgeDialogs.queue(
                "save",
                "(?!" + Pattern.quote("Save the Comet parameter file") + "$).*",
                List.of(export.toString()));

        assertEquals(List.of(first, second), port.chooseSpectrumFiles());
        assertEquals(Optional.empty(), port.chooseDatabase(), "nothing queued: cancelled");
        assertEquals(
                Optional.empty(), port.chooseSaveTarget(), "the export answer does not fit it");
        assertEquals(
                Optional.of(export),
                port.chooseExportTarget("Export the passing PSMs", "psms.tsv"));
        assertEquals(List.of(), port.chooseSpectrumFiles(), "a cancelled spectrum dialog");
        assertEquals(0, BridgeDialogs.unused());
        assertEquals(
                List.of(
                        "openMultiple\tChoose spectrum files to search\t\tSpectrum files (mzML,"
                                + " mzXML, MGF, MS2, Thermo RAW)|All files",
                        "open\tChoose the sequence database\t\tFASTA files|Comet index files|All"
                                + " files",
                        "save\tSave the Comet parameter file\tcomet.params\tComet parameter"
                                + " files|All files",
                        "save\tExport the passing PSMs\tpsms.tsv\tAll files",
                        "openMultiple\tChoose spectrum files to search\t\tSpectrum files (mzML,"
                                + " mzXML, MGF, MS2, Thermo RAW)|All files"),
                BridgeDialogs.asked());
    }

    @Test
    @DisplayName("an answer is refused unless its kind, file count, paths and pattern are sound")
    void answersAreChecked() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> BridgeDialogs.queue("pick", "x", List.of("/a"))),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> BridgeDialogs.queue("open", "x", List.of("/a", "/b"))),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> BridgeDialogs.queue("openMultiple", "x", List.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> BridgeDialogs.queue("open", "x", List.of("relative"))),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> BridgeDialogs.queue("open", "(", List.of("/a"))));
        assertEquals(0, BridgeDialogs.unused());
    }

    private static byte[] transform(String name, byte[] bytes) {
        return new DialogsTransformer()
                .transform(
                        null,
                        DialogsTransformerTest.class.getClassLoader(),
                        name,
                        null,
                        null,
                        bytes);
    }

    private static byte[] bytesOf(String internalName) throws IOException {
        try (InputStream in =
                FxFileChooser.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, internalName + ".class is not on the class path");
            return in.readAllBytes();
        }
    }

    /** Defines the rewritten dialogs object in a loader of its own, beside the original. */
    private static FxFileChooser.Dialogs load(byte[] rewritten)
            throws ReflectiveOperationException {
        Class<?> type =
                new ClassLoader(DialogsTransformerTest.class.getClassLoader()) {
                    Class<?> define() {
                        return defineClass(
                                DIALOGS_OBJECT.replace('/', '.'), rewritten, 0, rewritten.length);
                    }
                }.define();
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (FxFileChooser.Dialogs) constructor.newInstance();
    }

    /** The product's chooser over the given dialogs, through its own package-private seam. */
    private static FileChooserPort chooserOver(FxFileChooser.Dialogs dialogs)
            throws ReflectiveOperationException {
        Constructor<FxFileChooser> constructor =
                FxFileChooser.class.getDeclaredConstructor(
                        Supplier.class, FxFileChooser.Dialogs.class);
        constructor.setAccessible(true);
        Supplier<Window> noOwner = () -> null;
        return constructor.newInstance(noOwner, dialogs);
    }
}

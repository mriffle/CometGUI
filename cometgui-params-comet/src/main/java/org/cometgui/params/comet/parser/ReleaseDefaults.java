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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;

/**
 * The starting parameter set of each Comet release the editor offers: the release's own {@code
 * comet -q} output, bundled with this module and parsed.
 *
 * <p><strong>Why a file and not {@link CometParameters#defaults}.</strong> The curated metadata
 * gives every parameter's default, but it does not curate enzyme rows -- the {@code
 * [COMET_ENZYME_INFO]} table is the file's content, not the schema's -- so a new configuration
 * needs the table the release itself writes. The bundled file is that release's real output, byte
 * for byte the checked-in fixture its {@code SHA256SUMS} pins, and Comet's own Apache-2.0 text.
 *
 * <p>The set it gives is the parse of that file with every value at Comet's default, so every
 * origin is {@link ValueOrigin#COMET_DEFAULT} (the parser marks a declared value {@link
 * ValueOrigin#IMPORTED}; nothing was imported here). A bundled file that does not parse for its own
 * release without a single diagnostic -- a marker naming another release, a parameter the metadata
 * does not model -- is refused: the starting set of a release cannot carry a warning.
 *
 * <p>Only the releases the editor offers are bundled. Comet 2024.01.0 is a migration fixture that
 * is never offered to a user, so it has no starting set and is refused like any other release.
 */
public final class ReleaseDefaults {

    /** Where the bundled files live on the class path: {@code <root><version>/<file>}. */
    static final String RESOURCE_ROOT = "/org/cometgui/params/comet/parser/defaults/";

    /** The name of each bundled file, as the fixtures name Comet's {@code -q} output. */
    static final String FILE_NAME = "comet-q.params";

    private static final List<ToolVersion> BUNDLED =
            List.of(ToolVersion.parse("2026.03.0"), ToolVersion.parse("2026.02.2"));

    private ReleaseDefaults() {}

    /**
     * The releases with a bundled starting set, newest first.
     *
     * @return the releases, immutable
     */
    public static List<ToolVersion> bundledReleases() {
        return BUNDLED;
    }

    /**
     * Whether a release has a bundled starting set.
     *
     * @param release the Comet release
     * @return {@code true} if {@link #load} can give its starting set
     */
    public static boolean isBundled(ToolVersion release) {
        return BUNDLED.contains(Objects.requireNonNull(release, "release"));
    }

    /**
     * The bundled file of a release, exactly as Comet wrote it.
     *
     * @param release the Comet release
     * @return the file's bytes
     * @throws IllegalArgumentException naming the release, if it has no bundled file
     * @throws UncheckedIOException if a listed release's file is missing from the class path or
     *     cannot be read
     */
    public static byte[] bundledFile(ToolVersion release) {
        requireBundled(release);
        String resource = RESOURCE_ROOT + release.text() + "/" + FILE_NAME;
        try (InputStream in = ReleaseDefaults.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UncheckedIOException(
                        new IOException("the class path holds no " + resource));
            }
            return in.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + resource, unreadable);
        }
    }

    /**
     * The starting set of a release: its bundled {@code comet -q} file, parsed for that release,
     * every value at Comet's default.
     *
     * @param metadata the curated metadata
     * @param release the Comet release
     * @return the parameter set, origin {@link ValueOrigin#COMET_DEFAULT} throughout, with no
     *     unknown parameter and no diagnostic
     * @throws IllegalArgumentException naming the release, if it has no bundled file or the
     *     metadata was not curated against it
     * @throws IllegalStateException naming the release and what was found, if the bundled file does
     *     not parse cleanly as that release
     */
    public static CometParameters load(CuratedMetadata metadata, ToolVersion release) {
        Objects.requireNonNull(metadata, "metadata");
        String text = new String(bundledFile(release), StandardCharsets.UTF_8);
        ParseResult parsed = new CometParamsParser(metadata, release).parse(text);
        if (!parsed.diagnostics().isEmpty()) {
            throw new IllegalStateException(
                    "the bundled starting set of Comet "
                            + release.text()
                            + " does not parse cleanly as that release: "
                            + parsed.diagnostics().stream().map(Diagnostic::message).toList());
        }
        // No diagnostic also means no unknown parameter: the parser reports every one it keeps
        // (UNKNOWN_PARAMETER, NOT_IN_VERSION), and a version marker naming another release
        // (VERSION_MISMATCH).
        CometParameters model = parsed.model().orElseThrow();
        for (ParameterEntry entry : model.entries()) {
            model = model.withOrigin(entry.name(), ValueOrigin.COMET_DEFAULT);
        }
        return model;
    }

    private static void requireBundled(ToolVersion release) {
        if (!isBundled(release)) {
            throw new IllegalArgumentException(
                    "Comet "
                            + release.text()
                            + " has no bundled starting set; the releases with one are "
                            + BUNDLED.stream().map(ToolVersion::text).toList());
        }
    }
}

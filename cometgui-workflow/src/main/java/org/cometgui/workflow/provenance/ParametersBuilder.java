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

package org.cometgui.workflow.provenance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValueCodec;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.workflow.steps.CometParameterProvenance;
import org.cometgui.workflow.steps.CometWorkflow;
import org.cometgui.workflow.steps.PercolatorProvenance;
import org.cometgui.workflow.steps.RunResultFiles;

/**
 * Builds the Parameters part: the exact archived files, the Percolator settings, and each Comet
 * parameter with its origin, its release default and the value an applied preset gave it.
 *
 * <p>Nothing here parses {@code comet.params} itself. The archived file is read by {@link
 * CometParamsParser} for the release the run recorded -- the parser every import uses -- and every
 * value is compared as the text {@link ParameterValueCodec} formats it to, which is how {@code
 * ParameterDiff} compares two parameter sets. The origins and presets are read back from the keys
 * {@link CometParameterProvenance} pins ({@code P13-4}, amended), never from a second spelling of
 * them.
 */
final class ParametersBuilder {

    private final RunLayout layout;

    private final Optional<ProvenanceManifest> manifest;

    private final Optional<RunDescriptor> runJson;

    private final Supplier<CuratedMetadata> metadata;

    private final CachingHashService hashes;

    private final DisplayText display;

    private final long textLimit;

    private final List<ProvenanceView.Warning> warnings = new ArrayList<>();

    ParametersBuilder(
            RunLayout layout,
            Optional<ProvenanceManifest> manifest,
            Optional<RunDescriptor> runJson,
            Supplier<CuratedMetadata> metadata,
            CachingHashService hashes,
            DisplayText display,
            long textLimit) {
        this.layout = layout;
        this.manifest = manifest;
        this.runJson = runJson;
        this.metadata = metadata;
        this.hashes = hashes;
        this.display = display;
        this.textLimit = textLimit;
    }

    /** The warnings building found: archived parameter files that are gone or changed. */
    List<ProvenanceView.Warning> warnings() {
        return List.copyOf(warnings);
    }

    ProvenanceView.Parameters build() {
        Map<String, String> settings = manifest.map(ProvenanceManifest::settings).orElse(Map.of());
        List<String> notes = new ArrayList<>();
        Optional<Archived> comet = cometParamsFile().map(this::read);
        Optional<Archived> percolator =
                recorded(RunResultFiles.PERCOLATOR_SETTINGS_ROLE).map(this::read);
        Optional<String> release = release(settings);
        List<ProvenanceView.Parameter> rows = new ArrayList<>();
        if (comet.isEmpty()) {
            notes.add("this run lists no archived comet.params");
        } else if (comet.get().raw().isEmpty()) {
            notes.add("the archived comet.params is not shown, so its parameters are not listed");
        } else if (release.isEmpty()) {
            notes.add(
                    "this run records no Comet release, so its comet.params cannot be read for"
                            + " one");
        } else {
            rows.addAll(parameters(comet.get().raw().get(), release.get(), settings, notes));
        }
        List<ProvenanceView.Setting> percolatorSettings = new ArrayList<>();
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            if (setting.getKey().startsWith(PercolatorProvenance.SETTING_PREFIX)) {
                percolatorSettings.add(
                        new ProvenanceView.Setting(
                                setting.getKey(), display.clean(setting.getValue())));
            }
        }
        return new ProvenanceView.Parameters(
                release.map(display::clean),
                comet.map(Archived::view),
                percolator.map(Archived::view),
                percolatorSettings,
                rows,
                notes,
                presets(settings));
    }

    // ---------------------------------------------------------------- the archived files --

    /** The comet.params the record lists, or failing a record the one {@code run.json} names. */
    private Optional<Source> cometParamsFile() {
        Optional<Source> recorded = recorded(RunResultFiles.COMET_PARAMS_ROLE);
        if (recorded.isPresent() || manifest.isPresent()) {
            return recorded;
        }
        return runJson.map(
                descriptor -> {
                    ArchivedFile archived = descriptor.identity().parameters();
                    return new Source(
                            RunResultFiles.COMET_PARAMS_ROLE,
                            layout.root().resolve(archived.path()).normalize(),
                            archived.hashes());
                });
    }

    private Optional<Source> recorded(String role) {
        if (manifest.isEmpty()) {
            return Optional.empty();
        }
        for (FileRecord file : manifest.get().files()) {
            if (file.role().equals(role)) {
                return Optional.of(new Source(role, file.path(), file.hashes()));
            }
        }
        return Optional.empty();
    }

    private Archived read(Source source) {
        Path path = source.path();
        DiskOutcome check = DiskOutcome.MISSING;
        Optional<String> found = Optional.empty();
        Optional<String> problem = Optional.of("the file is no longer on disk");
        Optional<String> raw = Optional.empty();
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            try {
                FileHashes now = hashes.rehash(path);
                found = Optional.of(now.sha256());
                check = now.equals(source.hashes()) ? DiskOutcome.MATCHES : DiskOutcome.DIFFERS;
                problem = Optional.empty();
            } catch (NoSuchFileException vanished) {
                check = DiskOutcome.MISSING;
            } catch (IOException unreadable) {
                check = DiskOutcome.UNREADABLE;
                problem = Optional.of(unreadable(unreadable));
            }
        }
        if (found.isPresent()) {
            try {
                long size = Files.size(path);
                if (size > textLimit) {
                    problem =
                            Optional.of(
                                    "the file is "
                                            + size
                                            + " bytes, larger than the "
                                            + textLimit
                                            + " bytes this section shows");
                } else {
                    raw = Optional.of(decode(Files.readAllBytes(path)));
                }
            } catch (CharacterCodingException notUtf8) {
                problem = Optional.of("the file is not UTF-8 text");
            } catch (IOException unreadable) {
                problem = Optional.of(unreadable(unreadable));
            }
        }
        ProvenanceView.ArchivedText view =
                new ProvenanceView.ArchivedText(
                        source.role(),
                        display.path(path),
                        path,
                        source.hashes().sha256(),
                        check,
                        found,
                        raw.map(display::clean),
                        problem);
        if (check != DiskOutcome.MATCHES) {
            warnings.add(changed(view, source));
        }
        return new Archived(view, raw);
    }

    private String unreadable(IOException failure) {
        return "the file could not be read: " + display.clean(String.valueOf(failure.getMessage()));
    }

    private ProvenanceView.Warning changed(ProvenanceView.ArchivedText view, Source source) {
        String what = "the archived " + source.role() + " file ";
        String message =
                switch (view.check()) {
                    case MISSING -> what + "is no longer on disk";
                    case DIFFERS ->
                            what
                                    + "no longer has the digests the run recorded:"
                                    + " SHA-256 recorded "
                                    + view.recordedSha256()
                                    + ", found "
                                    + view.foundSha256().orElse("none");
                    default ->
                            what
                                    + "could not be read: "
                                    + view.problem().orElse("no reason was given");
                };
        return new ProvenanceView.Warning(
                WarningSource.PARAMETER_FILE_CHANGED, view.path(), message);
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    // -------------------------------------------------------------------- the parameters --

    private Optional<String> release(Map<String, String> settings) {
        String recorded = settings.get(CometWorkflow.RELEASE_SETTING);
        if (recorded != null) {
            return Optional.of(recorded);
        }
        return runJson.map(descriptor -> descriptor.identity().cometRelease());
    }

    private List<ProvenanceView.Parameter> parameters(
            String text, String release, Map<String, String> settings, List<String> notes) {
        ParseResult parsed;
        try {
            parsed = new CometParamsParser(metadata.get(), ToolVersion.parse(release)).parse(text);
        } catch (IllegalArgumentException notCurated) {
            notes.add(
                    "Comet "
                            + display.clean(release)
                            + " is not a release this build can read parameters for: "
                            + display.clean(String.valueOf(notCurated.getMessage())));
            return List.of();
        }
        for (Diagnostic diagnostic : parsed.diagnostics()) {
            notes.add(display.clean(diagnostic.message()));
        }
        if (parsed.model().isEmpty()) {
            return List.of();
        }
        CometParameters model = parsed.model().get();
        Map<String, String> origins = origins(settings);
        boolean originsRecorded = !origins.isEmpty();
        Map<String, String> presetValues = presetValues(settings);
        ParameterValueCodec codec = model.codec();
        List<ProvenanceView.Parameter> rows = new ArrayList<>();
        for (ParameterEntry entry : model.entries()) {
            String name = entry.name();
            String value = model.text(name);
            Optional<String> origin =
                    originsRecorded
                            ? Optional.of(
                                    origins.getOrDefault(
                                            name,
                                            CometParameterProvenance.wireWord(
                                                    ValueOrigin.COMET_DEFAULT)))
                            : Optional.empty();
            Optional<String> releaseDefault =
                    formatted(codec, entry.definition(), entry.definition().defaultValue());
            Optional<String> preset = Optional.ofNullable(presetValues.get(name));
            boolean differsFromPreset =
                    preset.isPresent()
                            && !formatted(codec, entry.definition(), preset.get())
                                    .equals(Optional.of(value));
            rows.add(
                    new ProvenanceView.Parameter(
                            name,
                            display.clean(value),
                            origin,
                            releaseDefault.map(display::clean),
                            releaseDefault.isPresent() && !releaseDefault.get().equals(value),
                            preset.map(display::clean),
                            differsFromPreset));
        }
        return rows;
    }

    /**
     * A value text as the codec formats it -- the form {@code ParameterDiff} compares -- or empty
     * when the codec cannot read it, which no value the run used can equal.
     */
    private static Optional<String> formatted(
            ParameterValueCodec codec, ParameterDefinition definition, String text) {
        try {
            return Optional.of(codec.format(definition, codec.parse(definition, text)));
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /** Parameter name to recorded origin word, from {@code comet.origin.<word>} = names. */
    private static Map<String, String> origins(Map<String, String> settings) {
        Map<String, String> origins = new HashMap<>();
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            String key = setting.getKey();
            if (key.startsWith(CometParameterProvenance.ORIGIN_PREFIX)) {
                String word = key.substring(CometParameterProvenance.ORIGIN_PREFIX.length());
                for (String name : setting.getValue().split(" ")) {
                    if (!name.isEmpty()) {
                        origins.put(name, word);
                    }
                }
            }
        }
        return origins;
    }

    /** Parameter name to the value the last applied preset that set it gave it. */
    private static Map<String, String> presetValues(Map<String, String> settings) {
        Map<String, String> values = new HashMap<>();
        for (int number = 1;
                settings.containsKey(
                        CometParameterProvenance.presetPrefix(number)
                                + CometParameterProvenance.PRESET_ID);
                number++) {
            for (String applied : applied(settings, number)) {
                String[] nameAndValue =
                        applied.split(Pattern.quote(CometParameterProvenance.APPLIED_SEPARATOR), 2);
                if (nameAndValue.length == 2) {
                    values.put(nameAndValue[0], nameAndValue[1]);
                }
            }
        }
        return values;
    }

    private static List<String> applied(Map<String, String> settings, int number) {
        String prefix =
                CometParameterProvenance.presetPrefix(number)
                        + CometParameterProvenance.PRESET_APPLIED;
        List<String> applied = new ArrayList<>();
        for (int value = 1; settings.containsKey(prefix + twoDigits(value)); value++) {
            applied.add(settings.get(prefix + twoDigits(value)));
        }
        return applied;
    }

    private List<ProvenanceView.Preset> presets(Map<String, String> settings) {
        List<ProvenanceView.Preset> presets = new ArrayList<>();
        for (int number = 1;
                settings.containsKey(
                        CometParameterProvenance.presetPrefix(number)
                                + CometParameterProvenance.PRESET_ID);
                number++) {
            String prefix = CometParameterProvenance.presetPrefix(number);
            List<String> applied = new ArrayList<>();
            for (String value : applied(settings, number)) {
                applied.add(display.clean(value));
            }
            presets.add(
                    new ProvenanceView.Preset(
                            number,
                            field(settings, prefix + CometParameterProvenance.PRESET_ID),
                            field(settings, prefix + CometParameterProvenance.PRESET_NAME),
                            field(settings, prefix + CometParameterProvenance.PRESET_ORIGIN),
                            field(settings, prefix + CometParameterProvenance.PRESET_COMET_VERSION),
                            field(
                                    settings,
                                    prefix + CometParameterProvenance.PRESET_SCHEMA_VERSION),
                            applied));
        }
        return presets;
    }

    private String field(Map<String, String> settings, String key) {
        return display.clean(settings.getOrDefault(key, ""));
    }

    /** The two-digit numbering {@link CometParameterProvenance} writes applied values with. */
    private static String twoDigits(int number) {
        return String.format(Locale.ROOT, "%02d", number);
    }

    /** A file the record or {@code run.json} lists, with the digests it gives. */
    private record Source(String role, Path path, FileHashes hashes) {}

    /** An archived file as shown, and its raw text for the parser when it could be read. */
    private record Archived(ProvenanceView.ArchivedText view, Optional<String> raw) {}
}

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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the Provenance section shows for one run, as plain immutable values.
 *
 * <p>The seven parts the specification's <em>Provenance UI</em> names -- summary, tools, inputs and
 * outputs, parameters, timeline, logs, warnings -- each a record a view-model can bind without
 * logic of its own: every text is already in the words and the form it is shown in, and has already
 * been through the session's {@link org.cometgui.domain.secrets.SecretRedactor}. Built by {@link
 * ProvenanceReader#load}; nothing in it refers back to a file handle or to the reader.
 *
 * <p><strong>Display text and locations are separate on purpose.</strong> A row that names a file
 * carries the path twice: as redacted display text ({@code path}), which is what may be shown or
 * copied, and as a {@link Path} ({@code location}), which is what "Open file location" and "Verify
 * against disk" act on. The location is never a display string; showing it would bypass the
 * redaction every displayed path has been through.
 *
 * @param summary the run at a glance
 * @param tools one row per tool record, in record order
 * @param files one row per input and output file record, in record order
 * @param parameters the archived parameter files, the settings and each Comet parameter
 * @param timeline the executions in time order and the event log in sequence order
 * @param logs each archived process log the record lists, once
 * @param warnings everything the record, the log and the run directory carry that a scientist must
 *     see
 */
public record ProvenanceView(
        Summary summary,
        List<Tool> tools,
        List<File> files,
        Parameters parameters,
        Timeline timeline,
        List<Log> logs,
        List<Warning> warnings) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public ProvenanceView {
        Objects.requireNonNull(summary, "summary");
        tools = List.copyOf(tools);
        files = List.copyOf(files);
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(timeline, "timeline");
        logs = List.copyOf(logs);
        warnings = List.copyOf(warnings);
    }

    /**
     * The tool rows.
     *
     * @return an unmodifiable copy, in record order
     */
    @Override
    public List<Tool> tools() {
        return List.copyOf(tools);
    }

    /**
     * The file rows.
     *
     * @return an unmodifiable copy, in record order
     */
    @Override
    public List<File> files() {
        return List.copyOf(files);
    }

    /**
     * The log rows.
     *
     * @return an unmodifiable copy
     */
    @Override
    public List<Log> logs() {
        return List.copyOf(logs);
    }

    /**
     * The warnings.
     *
     * @return an unmodifiable copy, in the order the sources are listed in {@link WarningSource}
     */
    @Override
    public List<Warning> warnings() {
        return List.copyOf(warnings);
    }

    /**
     * The run at a glance.
     *
     * @param runId the run's identifier
     * @param projectId the project's identifier
     * @param status the run's status: the record's for a sealed run, the latest attempt's otherwise
     * @param recordState whether the run has a sealed record
     * @param start when the run (the recorded attempt) started, canonical UTC
     * @param end when it ended, if it has
     * @param duration how long it took, {@code H:MM:SS.mmm}, if it has ended
     * @param toolVersions each distinct tool and version, {@code name version}, in record order
     * @param inputCount how many input file records there are
     * @param outputCount how many output file records there are
     * @param application the application record, if the run has a sealed one
     */
    public record Summary(
            String runId,
            String projectId,
            String status,
            RecordState recordState,
            String start,
            Optional<String> end,
            Optional<String> duration,
            List<String> toolVersions,
            int inputCount,
            int outputCount,
            Optional<Application> application) {

        /**
         * Validates and copies the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Summary {
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(recordState, "recordState");
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(duration, "duration");
            toolVersions = List.copyOf(toolVersions);
            Objects.requireNonNull(application, "application");
        }

        /**
         * The tool versions.
         *
         * @return an unmodifiable copy
         */
        @Override
        public List<String> toolVersions() {
            return List.copyOf(toolVersions);
        }
    }

    /**
     * The application that ran the run, as its record describes it.
     *
     * @param cometGuiVersion the CometGUI version
     * @param buildIdentifier the build identifier or git commit
     * @param operatingSystem the operating system's name and version
     * @param architecture the architecture
     * @param jvmVersion the Java runtime's version
     * @param locale the JVM default locale during the run, as a language tag
     * @param formatLocale the JVM's format locale during the run, as a language tag
     * @param zone the time zone, as a zone id
     */
    public record Application(
            String cometGuiVersion,
            String buildIdentifier,
            String operatingSystem,
            String architecture,
            String jvmVersion,
            String locale,
            String formatLocale,
            String zone) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Application {
            Objects.requireNonNull(cometGuiVersion, "cometGuiVersion");
            Objects.requireNonNull(buildIdentifier, "buildIdentifier");
            Objects.requireNonNull(operatingSystem, "operatingSystem");
            Objects.requireNonNull(architecture, "architecture");
            Objects.requireNonNull(jvmVersion, "jvmVersion");
            Objects.requireNonNull(locale, "locale");
            Objects.requireNonNull(formatLocale, "formatLocale");
            Objects.requireNonNull(zone, "zone");
        }
    }

    /**
     * One tool record.
     *
     * @param order the record's 1-based position among the run's tool records
     * @param name the tool's logical name
     * @param version the version the tool reported
     * @param releaseTag the release tag or commit, when known
     * @param origin {@code managed} or {@code local}
     * @param path the executable or JAR path, for display
     * @param location the executable's path, to act on; never shown
     * @param md5 the executable's recorded MD5
     * @param sha256 the executable's recorded SHA-256
     * @param artefactIdentity the upstream or managed artefact identity, when known
     * @param capabilities the probed capabilities: the capability badge
     * @param stageId the invocation's stage, such as {@code comet-01}
     * @param warnings the warnings and advisories the record carries for this tool
     * @param command the safely rendered command: what "Copy command" copies, exactly
     */
    public record Tool(
            int order,
            String name,
            String version,
            Optional<String> releaseTag,
            String origin,
            String path,
            Path location,
            String md5,
            String sha256,
            Optional<String> artefactIdentity,
            List<String> capabilities,
            Optional<String> stageId,
            List<String> warnings,
            String command) {

        /**
         * Validates and copies the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Tool {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(releaseTag, "releaseTag");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(md5, "md5");
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(artefactIdentity, "artefactIdentity");
            capabilities = List.copyOf(capabilities);
            Objects.requireNonNull(stageId, "stageId");
            warnings = List.copyOf(warnings);
            Objects.requireNonNull(command, "command");
        }

        /**
         * The capability badge's entries.
         *
         * @return an unmodifiable copy, sorted as the record keeps them
         */
        @Override
        public List<String> capabilities() {
            return List.copyOf(capabilities);
        }

        /**
         * The tool's warnings.
         *
         * @return an unmodifiable copy, in record order
         */
        @Override
        public List<String> warnings() {
            return List.copyOf(warnings);
        }
    }

    /**
     * One input or output file record.
     *
     * @param direction {@code input} or {@code output}
     * @param role the file's role, such as {@code spectrum} or {@code pepxml}
     * @param path the file's path, for display
     * @param location the file's path, to act on; never shown
     * @param sizeBytes the size the record gives, in bytes
     * @param modified the modification time the record gives, canonical UTC
     * @param md5 the recorded MD5
     * @param sha256 the recorded SHA-256
     * @param status the record's status word, such as {@code completed} or {@code partial}
     * @param partial whether the file is marked as anything but complete
     */
    public record File(
            String direction,
            String role,
            String path,
            Path location,
            long sizeBytes,
            String modified,
            String md5,
            String sha256,
            String status,
            boolean partial) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public File {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(modified, "modified");
            Objects.requireNonNull(md5, "md5");
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * The Parameters part.
     *
     * @param cometRelease the Comet release the run's parameters were written for, when known
     * @param cometParams the archived {@code comet.params}, when the run lists one
     * @param percolatorSettingsFile the archived {@code percolator-settings.json}, when the record
     *     lists one
     * @param percolatorSettings every {@code percolator.*} setting the record carries, in key order
     * @param cometParameters each Comet parameter of the archived file with its value, origin,
     *     release default and preset value, in the release's schema order
     * @param notes what could not be shown and why, and what the parameter parser reported
     * @param presets each preset the run records as applied, in order
     */
    public record Parameters(
            Optional<String> cometRelease,
            Optional<ArchivedText> cometParams,
            Optional<ArchivedText> percolatorSettingsFile,
            List<Setting> percolatorSettings,
            List<Parameter> cometParameters,
            List<String> notes,
            List<Preset> presets) {

        /**
         * Validates and copies the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Parameters {
            Objects.requireNonNull(cometRelease, "cometRelease");
            Objects.requireNonNull(cometParams, "cometParams");
            Objects.requireNonNull(percolatorSettingsFile, "percolatorSettingsFile");
            percolatorSettings = List.copyOf(percolatorSettings);
            cometParameters = List.copyOf(cometParameters);
            notes = List.copyOf(notes);
            presets = List.copyOf(presets);
        }

        /**
         * The Percolator settings.
         *
         * @return an unmodifiable copy, in key order
         */
        @Override
        public List<Setting> percolatorSettings() {
            return List.copyOf(percolatorSettings);
        }

        /**
         * The Comet parameter rows.
         *
         * @return an unmodifiable copy, in schema order
         */
        @Override
        public List<Parameter> cometParameters() {
            return List.copyOf(cometParameters);
        }

        /**
         * The notes.
         *
         * @return an unmodifiable copy
         */
        @Override
        public List<String> notes() {
            return List.copyOf(notes);
        }

        /**
         * The applied presets.
         *
         * @return an unmodifiable copy, in the order they were applied
         */
        @Override
        public List<Preset> presets() {
            return List.copyOf(presets);
        }
    }

    /**
     * An archived parameter file: its exact text, and whether it is still the file the run
     * recorded.
     *
     * @param role the file's provenance role
     * @param path the file's path, for display
     * @param location the file's path, to act on; never shown
     * @param recordedSha256 the SHA-256 the run recorded for it
     * @param check whether the file on disk still has the recorded digests
     * @param foundSha256 the SHA-256 computed now, when the file could be read
     * @param text the file's text, redacted, when it is no larger than {@link
     *     ProvenanceReader#PARAMETER_TEXT_LIMIT} and is UTF-8
     * @param problem why the text is not shown, when it is not
     */
    public record ArchivedText(
            String role,
            String path,
            Path location,
            String recordedSha256,
            DiskOutcome check,
            Optional<String> foundSha256,
            Optional<String> text,
            Optional<String> problem) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public ArchivedText {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(recordedSha256, "recordedSha256");
            Objects.requireNonNull(check, "check");
            Objects.requireNonNull(foundSha256, "foundSha256");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(problem, "problem");
        }
    }

    /**
     * One recorded setting.
     *
     * @param key the settings key
     * @param value its value, redacted
     */
    public record Setting(String key, String value) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if either component is {@code null}
         */
        public Setting {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * One Comet parameter of the archived file.
     *
     * @param name the parameter name, as Comet spells it
     * @param value the value the run used, in {@code comet.params} syntax
     * @param origin where the value came from, as recorded ({@code comet-default}, {@code
     *     cometgui-default}, {@code preset}, {@code user}, {@code imported}, {@code
     *     workflow-enforced}), or empty when the run recorded no origins at all
     * @param releaseDefault the release's own default, when the release is curated in this build
     * @param differsFromDefault whether the value is not the release default
     * @param presetValue the value the last applied preset that set this parameter gave it
     * @param differsFromPreset whether the value is not the preset's value; {@code false} when no
     *     preset set the parameter
     */
    public record Parameter(
            String name,
            String value,
            Optional<String> origin,
            Optional<String> releaseDefault,
            boolean differsFromDefault,
            Optional<String> presetValue,
            boolean differsFromPreset) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(releaseDefault, "releaseDefault");
            Objects.requireNonNull(presetValue, "presetValue");
        }
    }

    /**
     * One preset the run records as applied.
     *
     * @param number its 1-based number, in the order presets were applied
     * @param id the preset's identifier
     * @param name the preset's display name
     * @param origin {@code built-in} or {@code user}
     * @param cometVersion the Comet version the preset was written for
     * @param schemaVersion the preset format's schema version
     * @param applied each value the preset set, {@code name = value}, in order
     */
    public record Preset(
            int number,
            String id,
            String name,
            String origin,
            String cometVersion,
            String schemaVersion,
            List<String> applied) {

        /**
         * Validates and copies the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Preset {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(cometVersion, "cometVersion");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            applied = List.copyOf(applied);
        }

        /**
         * The applied values.
         *
         * @return an unmodifiable copy, in order
         */
        @Override
        public List<String> applied() {
            return List.copyOf(applied);
        }
    }

    /**
     * The Timeline part.
     *
     * @param executions every recorded process execution, in the order they started
     * @param events every recovered event of {@code events.log}, in sequence order
     */
    public record Timeline(List<Execution> executions, List<Event> events) {

        /**
         * Copies the components.
         *
         * @throws NullPointerException if either component is {@code null}
         */
        public Timeline {
            executions = List.copyOf(executions);
            events = List.copyOf(events);
        }

        /**
         * The executions.
         *
         * @return an unmodifiable copy, in start order
         */
        @Override
        public List<Execution> executions() {
            return List.copyOf(executions);
        }

        /**
         * The events.
         *
         * @return an unmodifiable copy, in sequence order
         */
        @Override
        public List<Event> events() {
            return List.copyOf(events);
        }
    }

    /**
     * One process execution.
     *
     * @param tool the tool's name
     * @param version the tool's version
     * @param stageId the invocation's stage, such as {@code comet-02}
     * @param command the safely rendered command
     * @param workingDirectory the working directory, for display
     * @param environment the variables the execution added or overrode, {@code NAME=value} one per
     *     line, redacted
     * @param start when it started, canonical UTC
     * @param end when it ended, canonical UTC
     * @param duration how long it ran, {@code H:MM:SS.mmm}
     * @param durationMillis how long it ran, in milliseconds
     * @param exitCode its exit code
     * @param status the execution's status word
     */
    public record Execution(
            String tool,
            String version,
            Optional<String> stageId,
            String command,
            String workingDirectory,
            String environment,
            String start,
            String end,
            String duration,
            long durationMillis,
            int exitCode,
            String status) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Execution {
            Objects.requireNonNull(tool, "tool");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(stageId, "stageId");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * One event of {@code events.log}.
     *
     * @param sequence its sequence number
     * @param timestamp when it was recorded, canonical UTC
     * @param type its type's wire name, such as {@code stage.finished} or {@code pdv.launched}
     * @param payload its payload, one {@code key = value} line per entry in key order, redacted
     */
    public record Event(long sequence, String timestamp, String type, List<String> payload) {

        /**
         * Validates and copies the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Event {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(type, "type");
            payload = List.copyOf(payload);
        }

        /**
         * The payload lines.
         *
         * @return an unmodifiable copy, in key order
         */
        @Override
        public List<String> payload() {
            return List.copyOf(payload);
        }
    }

    /**
     * One archived process log.
     *
     * @param tool the tool whose execution wrote it
     * @param stageId the invocation's stage
     * @param streams which streams it holds: {@code stdout and stderr}, {@code stdout} or {@code
     *     stderr}
     * @param path the log's path, for display
     * @param location the log's path, to act on; never shown
     * @param md5 the recorded MD5
     * @param sha256 the recorded SHA-256
     */
    public record Log(
            String tool,
            Optional<String> stageId,
            String streams,
            String path,
            Path location,
            String md5,
            String sha256) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Log {
            Objects.requireNonNull(tool, "tool");
            Objects.requireNonNull(stageId, "stageId");
            Objects.requireNonNull(streams, "streams");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(md5, "md5");
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    /**
     * One warning.
     *
     * @param source where it came from
     * @param subject what it is about: a tool, a file, a setting key, a log line
     * @param message what the scientist must be told, redacted
     */
    public record Warning(WarningSource source, String subject, String message) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Warning {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(message, "message");
        }
    }
}

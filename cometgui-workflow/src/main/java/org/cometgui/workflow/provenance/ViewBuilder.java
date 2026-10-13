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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.events.EventLogDefect;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.events.RecoveredEventLog;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.limelight.LimelightConversions;
import org.cometgui.workflow.limelight.LimelightUploads;
import org.cometgui.workflow.steps.PercolatorProvenance;
import org.cometgui.workflow.steps.ToolWarnings;

/**
 * Builds every part of a {@link ProvenanceView} but the parameters, from what a load found.
 *
 * <p>A sealed record is the authority for everything it holds. Without one ({@code P13-5}) the
 * summary comes from {@code run.json}, the timeline from the event log, and the warnings say which
 * of the two is all there is.
 */
final class ViewBuilder {

    /** What the summary says when nothing the run left names a value. */
    static final String UNKNOWN = "unknown";

    private static final String STAGE_FINISHED = ProvenanceEventType.STAGE_FINISHED.wireName();

    private final RunLayout layout;

    private final RecordState state;

    private final Optional<ProvenanceManifest> manifest;

    private final Optional<String> recordProblem;

    private final Optional<RunDescriptor> runJson;

    private final Optional<String> runJsonProblem;

    private final RecoveredEventLog events;

    private final Optional<String> eventLogProblem;

    private final DisplayText display;

    ViewBuilder(Loaded loaded, DisplayText display) {
        this.layout = loaded.layout();
        this.state = loaded.state();
        this.manifest = loaded.manifest();
        this.recordProblem = loaded.recordProblem();
        this.runJson = loaded.runJson();
        this.runJsonProblem = loaded.runJsonProblem();
        this.events = loaded.events();
        this.eventLogProblem = loaded.eventLogProblem();
        this.display = display;
    }

    /** What a load found, before any of it is turned into text. */
    record Loaded(
            RunLayout layout,
            RecordState state,
            Optional<ProvenanceManifest> manifest,
            Optional<String> recordProblem,
            Optional<RunDescriptor> runJson,
            Optional<String> runJsonProblem,
            RecoveredEventLog events,
            Optional<String> eventLogProblem) {}

    ProvenanceView build(ProvenanceView.Parameters parameters, List<ProvenanceView.Warning> more) {
        List<ProvenanceView.Warning> warnings = warnings();
        warnings.addAll(more);
        // A run without a sealed record has no tool, file, execution or log records: those parts
        // are empty, and the warnings say why.
        return new ProvenanceView(
                summary(),
                manifest.map(this::tools).orElse(List.of()),
                manifest.map(this::files).orElse(List.of()),
                parameters,
                new ProvenanceView.Timeline(
                        manifest.map(this::executions).orElse(List.of()), eventRows()),
                manifest.map(this::logs).orElse(List.of()),
                warnings);
    }

    // ------------------------------------------------------------------------- summary --

    private ProvenanceView.Summary summary() {
        if (manifest.isPresent()) {
            return sealedSummary(manifest.get());
        }
        Optional<RunAttempt> latest = latestAttempt();
        String runId =
                runJson.map(descriptor -> descriptor.identity().runId().value())
                        .or(this::runIdFromEvents)
                        .orElse(UNKNOWN);
        String projectId =
                runJson.map(descriptor -> descriptor.identity().projectId().value())
                        .orElse(UNKNOWN);
        Optional<Instant> start =
                latest.map(RunAttempt::started)
                        .or(() -> runJson.map(descriptor -> descriptor.identity().created()));
        Optional<Instant> end = latest.flatMap(RunAttempt::ended);
        String status;
        if (runJson.isEmpty()) {
            status = UNKNOWN;
        } else if (latest.isEmpty()) {
            status = RunCondition.NOT_STARTED.word();
        } else {
            status = statusWord(latest.get().outcome());
        }
        return new ProvenanceView.Summary(
                display.clean(runId),
                display.clean(projectId),
                status,
                state,
                start.map(DisplayText::timestamp).orElse(UNKNOWN),
                end.map(DisplayText::timestamp),
                end.map(
                        ended ->
                                DisplayText.duration(
                                        DisplayText.millisBetween(start.orElseThrow(), ended))),
                List.of(),
                0,
                0,
                Optional.empty());
    }

    private Optional<RunAttempt> latestAttempt() {
        if (runJson.isEmpty() || runJson.get().attempts().isEmpty()) {
            return Optional.empty();
        }
        List<RunAttempt> attempts = runJson.get().attempts();
        return Optional.of(attempts.get(attempts.size() - 1));
    }

    private Optional<String> runIdFromEvents() {
        for (ProvenanceEvent event : events.events()) {
            String id = event.payload().get(ProvenanceEvent.RUN_ID_KEY);
            if (id != null) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    /** An attempt's outcome in the words a sealed record uses for the same thing. */
    static String statusWord(AttemptOutcome outcome) {
        return switch (outcome) {
            case RUNNING -> ProvenanceStatus.RUNNING.wireName();
            case SUCCEEDED -> ProvenanceStatus.COMPLETED.wireName();
            case FAILED -> ProvenanceStatus.FAILED.wireName();
            case CANCELLED -> ProvenanceStatus.CANCELLED.wireName();
        };
    }

    private ProvenanceView.Summary sealedSummary(ProvenanceManifest record) {
        RunRecord run = record.run();
        Set<String> versions = new LinkedHashSet<>();
        for (ToolRecord tool : record.tools()) {
            versions.add(display.clean(tool.name() + " " + tool.version()));
        }
        ApplicationRecord application = record.application();
        return new ProvenanceView.Summary(
                display.clean(run.runId().value()),
                display.clean(run.projectId()),
                run.status().wireName(),
                state,
                DisplayText.timestamp(run.start()),
                run.end().map(DisplayText::timestamp),
                run.end()
                        .map(
                                ended ->
                                        DisplayText.duration(
                                                DisplayText.millisBetween(run.start(), ended))),
                List.copyOf(versions),
                count(record, FileDirection.INPUT),
                count(record, FileDirection.OUTPUT),
                Optional.of(
                        new ProvenanceView.Application(
                                display.clean(application.cometGuiVersion()),
                                display.clean(application.buildIdentifier()),
                                display.clean(application.osName() + " " + application.osVersion()),
                                display.clean(application.architecture()),
                                display.clean(application.jvmVersion()),
                                application.locale().toLanguageTag(),
                                application.formatLocale().toLanguageTag(),
                                application.zoneId().getId())));
    }

    private static int count(ProvenanceManifest record, FileDirection direction) {
        int count = 0;
        for (FileRecord file : record.files()) {
            if (file.direction() == direction) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------- tools and files --

    private List<ProvenanceView.Tool> tools(ProvenanceManifest record) {
        List<ProvenanceView.Tool> rows = new ArrayList<>();
        int order = 0;
        for (ToolRecord tool : record.tools()) {
            order++;
            rows.add(
                    new ProvenanceView.Tool(
                            order,
                            display.clean(tool.name()),
                            display.clean(tool.version()),
                            tool.releaseTag().map(display::clean),
                            tool.managed() ? "managed" : "local",
                            display.path(tool.executablePath()),
                            tool.executablePath(),
                            tool.hashes().md5(),
                            tool.hashes().sha256(),
                            tool.artefactIdentity().map(display::clean),
                            cleaned(new ArrayList<>(tool.capabilities())),
                            tool.stageId().map(display::clean),
                            cleaned(tool.warnings()),
                            display.command(tool.execution().command())));
        }
        return rows;
    }

    private List<String> cleaned(List<String> texts) {
        List<String> cleaned = new ArrayList<>(texts.size());
        for (String text : texts) {
            cleaned.add(display.clean(text));
        }
        return cleaned;
    }

    private List<ProvenanceView.File> files(ProvenanceManifest record) {
        List<ProvenanceView.File> rows = new ArrayList<>();
        for (FileRecord file : record.files()) {
            rows.add(
                    new ProvenanceView.File(
                            file.direction().wireName(),
                            display.clean(file.role()),
                            display.path(file.path()),
                            file.path(),
                            file.sizeBytes(),
                            DisplayText.timestamp(file.modifiedAt()),
                            file.hashes().md5(),
                            file.hashes().sha256(),
                            file.status().wireName(),
                            file.status() != ProvenanceStatus.COMPLETED));
        }
        return rows;
    }

    // ------------------------------------------------------------------------ timeline --

    private List<ProvenanceView.Execution> executions(ProvenanceManifest record) {
        List<ProvenanceView.Execution> rows = new ArrayList<>();
        List<ToolRecord> started = new ArrayList<>(record.tools());
        // Stable: two executions that started in the same millisecond keep their record order.
        started.sort(Comparator.comparing(tool -> tool.execution().start()));
        for (ToolRecord tool : started) {
            ExecutionRecord execution = tool.execution();
            long millis = DisplayText.millisBetween(execution.start(), execution.end());
            rows.add(
                    new ProvenanceView.Execution(
                            display.clean(tool.name()),
                            display.clean(tool.version()),
                            tool.stageId().map(display::clean),
                            display.command(execution.command()),
                            display.path(execution.command().workingDirectory()),
                            display.environment(execution.command().environment()),
                            DisplayText.timestamp(execution.start()),
                            DisplayText.timestamp(execution.end()),
                            DisplayText.duration(millis),
                            millis,
                            execution.exitCode(),
                            execution.status().wireName()));
        }
        return rows;
    }

    private List<ProvenanceView.Event> eventRows() {
        List<ProvenanceView.Event> rows = new ArrayList<>();
        for (ProvenanceEvent event : events.events()) {
            rows.add(
                    new ProvenanceView.Event(
                            event.sequence(),
                            DisplayText.timestamp(event.timestamp()),
                            event.type().wireName(),
                            display.payload(event.payload())));
        }
        return rows;
    }

    // ---------------------------------------------------------------------------- logs --

    private List<ProvenanceView.Log> logs(ProvenanceManifest record) {
        List<ProvenanceView.Log> rows = new ArrayList<>();
        for (ToolRecord tool : record.tools()) {
            Optional<LogRecord> stdout = tool.execution().stdout();
            Optional<LogRecord> stderr = tool.execution().stderr();
            if (stdout.isPresent() && stdout.equals(stderr)) {
                rows.add(log(tool, "stdout and stderr", stdout.get()));
            } else {
                stdout.ifPresent(log -> rows.add(log(tool, "stdout", log)));
                stderr.ifPresent(log -> rows.add(log(tool, "stderr", log)));
            }
        }
        return rows;
    }

    private ProvenanceView.Log log(ToolRecord tool, String streams, LogRecord log) {
        return new ProvenanceView.Log(
                display.clean(tool.name()),
                tool.stageId().map(display::clean),
                streams,
                display.path(log.path()),
                log.path(),
                log.hashes().md5(),
                log.hashes().sha256());
    }

    // ------------------------------------------------------------------------ warnings --

    private List<ProvenanceView.Warning> warnings() {
        List<ProvenanceView.Warning> warnings = new ArrayList<>();
        String record = display.path(layout.provenanceJsonFile());
        if (state == RecordState.NO_SEALED_RECORD) {
            warnings.add(
                    warning(
                            WarningSource.NO_SEALED_RECORD,
                            record,
                            "this run has no sealed provenance record: it crashed before"
                                    + " finalising, or is still running, so it is shown from"
                                    + " run.json and events.log alone"));
        } else if (state == RecordState.RECORD_UNREADABLE) {
            warnings.add(
                    warning(
                            WarningSource.RECORD_UNREADABLE,
                            record,
                            "the provenance record could not be read: "
                                    + recordProblem.orElse("no reason was given")));
        }
        runJsonProblem.ifPresent(
                problem ->
                        warnings.add(
                                warning(
                                        WarningSource.RUN_JSON_UNREADABLE,
                                        display.path(layout.runFile()),
                                        problem)));
        eventLogProblem.ifPresent(
                problem ->
                        warnings.add(
                                warning(
                                        WarningSource.EVENT_LOG_UNREADABLE,
                                        display.path(layout.eventLogFile()),
                                        problem)));
        for (EventLogDefect defect : events.defects()) {
            warnings.add(
                    warning(
                            WarningSource.EVENT_LOG_DEFECT,
                            "events.log line " + defect.lineNumber(),
                            defectWord(defect)
                                    + " at line "
                                    + defect.lineNumber()
                                    + ", byte "
                                    + defect.byteOffset()
                                    + ": "
                                    + defect.detail()));
        }
        outcome().ifPresent(warnings::add);
        stepFailures(warnings);
        manifest.ifPresent(present -> recordWarnings(present, warnings));
        eventWarnings(warnings);
        return warnings;
    }

    private static String defectWord(EventLogDefect defect) {
        return switch (defect.kind()) {
            case TORN_FINAL_LINE -> "a torn final line";
            case MALFORMED_LINE -> "a malformed line";
            case SEQUENCE_GAP -> "a gap in the sequence numbers";
        };
    }

    private Optional<ProvenanceView.Warning> outcome() {
        String status =
                manifest.map(present -> present.run().status().wireName())
                        .or(() -> latestAttempt().map(attempt -> statusWord(attempt.outcome())))
                        .orElse(UNKNOWN);
        if (status.equals(ProvenanceStatus.FAILED.wireName())
                || status.equals(ProvenanceStatus.CANCELLED.wireName())) {
            return Optional.of(
                    warning(
                            WarningSource.RUN_OUTCOME,
                            "run",
                            "the run "
                                    + (status.equals(ProvenanceStatus.FAILED.wireName())
                                            ? "failed"
                                            : "was cancelled")
                                    + "; outputs it left are marked partial"));
        }
        return Optional.empty();
    }

    private void stepFailures(List<ProvenanceView.Warning> warnings) {
        for (ProvenanceEvent event : events.events()) {
            String message = event.payload().get(ProvenanceEvent.MESSAGE_KEY);
            if (event.type().wireName().equals(STAGE_FINISHED) && message != null) {
                warnings.add(
                        warning(
                                WarningSource.STEP_FAILURE,
                                event.payload().getOrDefault(ProvenanceEvent.STAGE_KEY, "stage"),
                                message));
            }
        }
    }

    private void recordWarnings(ProvenanceManifest record, List<ProvenanceView.Warning> warnings) {
        for (FileRecord file : record.files()) {
            if (file.status() != ProvenanceStatus.COMPLETED) {
                warnings.add(
                        warning(
                                WarningSource.PARTIAL_FILE,
                                file.path().toString(),
                                "the "
                                        + file.direction().wireName()
                                        + " file "
                                        + file.path()
                                        + " ("
                                        + file.role()
                                        + ") is recorded as "
                                        + file.status().wireName()));
            }
        }
        Map<String, String> settings = record.settings();
        Set<String> said = new LinkedHashSet<>();
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            String key = setting.getKey();
            for (String prefix :
                    List.of(
                            PercolatorProvenance.ADVISORY_PREFIX,
                            ToolWarnings.COMET_ADVISORY_PREFIX)) {
                if (key.startsWith(prefix)) {
                    said.add(setting.getValue());
                    warnings.add(
                            warning(
                                    WarningSource.VERSION_ADVISORY,
                                    key.substring(prefix.length()),
                                    setting.getValue()));
                }
            }
            for (String prefix :
                    List.of(
                            ToolWarnings.COMET_DISCREPANCY_PREFIX,
                            ToolWarnings.PERCOLATOR_DISCREPANCY_PREFIX)) {
                if (key.startsWith(prefix)) {
                    said.add(setting.getValue());
                    warnings.add(
                            warning(
                                    WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                    key,
                                    setting.getValue()));
                }
            }
        }
        for (int number = 1; settings.containsKey(skipped(number, "version")); number++) {
            warnings.add(
                    warning(
                            WarningSource.SKIPPED_VERSION,
                            "Percolator " + settings.get(skipped(number, "version")),
                            "Percolator "
                                    + settings.get(skipped(number, "version"))
                                    + " was skipped: "
                                    + settings.getOrDefault(skipped(number, "reason"), "")
                                    + " (missing: "
                                    + settings.getOrDefault(skipped(number, "missing"), "")
                                    + ")"));
        }
        for (int number = 1; settings.containsKey(notEmitted(number, "option")); number++) {
            String option = settings.get(notEmitted(number, "option"));
            warnings.add(
                    warning(
                            WarningSource.OPTION_NOT_EMITTED,
                            option,
                            "the option "
                                    + option
                                    + " was not passed to Percolator: "
                                    + settings.getOrDefault(notEmitted(number, "reason"), "")));
        }
        String weights = settings.get(PercolatorProvenance.WEIGHTS_WARNING);
        if (weights != null) {
            said.add(weights);
            warnings.add(
                    warning(
                            WarningSource.WEIGHTS_FALLBACK,
                            PercolatorProvenance.WEIGHTS_WARNING,
                            weights));
        }
        for (ToolRecord tool : record.tools()) {
            for (String text : tool.warnings()) {
                // A Percolator advisory and the weights warning are recorded twice -- in the
                // settings and on the tool -- and are shown once, under the more specific source.
                if (!said.contains(text)) {
                    warnings.add(
                            warning(
                                    WarningSource.TOOL_WARNING,
                                    tool.name() + tool.stageId().map(id -> " " + id).orElse(""),
                                    text));
                }
            }
        }
    }

    private static String skipped(int number, String field) {
        return PercolatorProvenance.SKIPPED_PREFIX + twoDigits(number) + "." + field;
    }

    private static String notEmitted(int number, String field) {
        return PercolatorProvenance.NOT_EMITTED_PREFIX + twoDigits(number) + "." + field;
    }

    /** The two-digit numbering {@link PercolatorProvenance} writes its lists with. */
    private static String twoDigits(int number) {
        return String.format(Locale.ROOT, "%02d", number);
    }

    private void eventWarnings(List<ProvenanceView.Warning> warnings) {
        for (ProvenanceEvent event : events.events()) {
            Map<String, String> payload = event.payload();
            switch (event.type()) {
                case WARNING_RAISED ->
                        warnings.add(
                                warning(
                                        WarningSource.WARNING_EVENT,
                                        subject(event),
                                        payload.getOrDefault(
                                                ProvenanceEvent.MESSAGE_KEY,
                                                String.join("; ", display.payload(payload)))));
                case LIMELIGHT_CONVERSION_FAILED, LIMELIGHT_UPLOAD_FAILED ->
                        warnings.add(
                                warning(
                                        WarningSource.LIMELIGHT_FAILURE,
                                        subject(event),
                                        limelightFailure(event)));
                default -> {
                    // Not a warning: shown in the timeline only.
                }
            }
        }
    }

    private static String subject(ProvenanceEvent event) {
        return event.type().wireName() + " #" + event.sequence();
    }

    private static String limelightFailure(ProvenanceEvent event) {
        Map<String, String> payload = event.payload();
        boolean conversion = event.type() == ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED;
        String failure =
                payload.getOrDefault(
                        conversion ? LimelightConversions.FAILURE : LimelightUploads.FAILURE,
                        "failed");
        String reason =
                Optional.ofNullable(payload.get(ProvenanceEvent.MESSAGE_KEY))
                        .or(() -> Optional.ofNullable(payload.get(LimelightUploads.SERVER_MESSAGE)))
                        .or(
                                () ->
                                        Optional.ofNullable(
                                                        payload.get(LimelightUploads.HTTP_STATUS))
                                                .map(status -> "HTTP status " + status))
                        .orElse("no reason was recorded");
        return (conversion ? "the Limelight conversion" : "the Limelight upload")
                + " did not succeed ("
                + failure
                + "): "
                + reason;
    }

    private ProvenanceView.Warning warning(WarningSource source, String subject, String message) {
        return new ProvenanceView.Warning(source, display.clean(subject), display.clean(message));
    }
}

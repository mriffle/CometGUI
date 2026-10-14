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

package org.cometgui.ui.viewmodel.provenance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.cometgui.workflow.provenance.DiskOutcome;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.RunCondition;
import org.cometgui.workflow.provenance.RunListing;
import org.cometgui.workflow.provenance.WarningSource;

/**
 * The words the Provenance section shows, laid out from the read model's values.
 *
 * <p>Layout only. Every value placed here is already display text the read model produced -- each
 * path, command, setting and warning already through the session's redactor -- and nothing is
 * parsed, computed or judged: a status word, a digest, a duration is placed beside its caption
 * exactly as it was given. The one number formatted here, a file's size, is formatted with {@link
 * Locale#ROOT}.
 */
final class ProvenanceTexts {

    /** The words for a record that was read. */
    static final String SEALED =
            "Sealed record: this run's provenance.json was read whole and parsed.";

    /** The words for a run without a record. */
    static final String NO_SEALED_RECORD =
            "No sealed record: this run has no provenance.json -- it is still running, or the"
                    + " application or the computer stopped before the record was written -- so"
                    + " it is shown from its run.json and events.log alone.";

    /** The words for a record that could not be read, before the reader's message. */
    static final String RECORD_UNREADABLE =
            "Record unreadable: this run's provenance.json exists but could not be read, so the"
                    + " run is shown from its run.json and events.log alone. The reader said: ";

    private ProvenanceTexts() {}

    /**
     * One run in the run list.
     *
     * @param run the listing
     * @return its identifier, when it was created, what became of it and whether it has a sealed
     *     record; or its directory and why its {@code run.json} could not be read
     */
    static String runLabel(RunListing run) {
        if (run.runId().isEmpty()) {
            return run.directoryName()
                    + " -- "
                    + RunCondition.UNREADABLE.word()
                    + ": "
                    + run.problem().orElse("no reason was given");
        }
        return run.runId().get()
                + " -- created "
                + run.created().orElse("at an unrecorded time")
                + ", "
                + run.condition().word()
                + ", "
                + (run.sealedRecord() ? "sealed record" : "no sealed record");
    }

    /**
     * Whether a run has a record, in words.
     *
     * @param state the state the load found
     * @param problem the reader's message, for an unreadable record
     * @return the statement
     */
    static String recordText(RecordState state, Optional<String> problem) {
        return switch (state) {
            case SEALED -> SEALED;
            case NO_SEALED_RECORD -> NO_SEALED_RECORD;
            case RECORD_UNREADABLE -> RECORD_UNREADABLE + problem.orElse("no reason was given");
        };
    }

    /**
     * The Summary part.
     *
     * @param summary the run at a glance
     * @return one line per fact
     */
    static String summary(ProvenanceView.Summary summary) {
        List<String> lines = new ArrayList<>();
        lines.add("Run " + summary.runId() + " of project " + summary.projectId());
        lines.add("Status: " + summary.status());
        lines.add("Record: " + summary.recordState().word());
        lines.add("Started: " + summary.start());
        lines.add("Ended: " + summary.end().orElse("not recorded"));
        lines.add("Duration: " + summary.duration().orElse("not recorded"));
        lines.add(
                "Tool versions: "
                        + (summary.toolVersions().isEmpty()
                                ? "none recorded"
                                : String.join(", ", summary.toolVersions())));
        lines.add(
                "Input files: "
                        + summary.inputCount()
                        + "; output files: "
                        + summary.outputCount());
        lines.add(
                summary.application()
                        .map(
                                application ->
                                        "Application: CometGUI "
                                                + application.cometGuiVersion()
                                                + " (build "
                                                + application.buildIdentifier()
                                                + "), "
                                                + application.operatingSystem()
                                                + ", "
                                                + application.architecture()
                                                + ", Java "
                                                + application.jvmVersion()
                                                + ", locale "
                                                + application.locale()
                                                + ", format locale "
                                                + application.formatLocale()
                                                + ", time zone "
                                                + application.zone())
                        .orElse("Application: not recorded, because the run has no sealed record"));
        return String.join("\n", lines);
    }

    /**
     * One row of the Tools part.
     *
     * @param tool the tool record
     * @return its name, version, origin, path, both digests and its capability badge
     */
    static String tool(ProvenanceView.Tool tool) {
        List<String> lines = new ArrayList<>();
        StringBuilder heading =
                new StringBuilder()
                        .append(tool.order())
                        .append(". ")
                        .append(tool.name())
                        .append(' ')
                        .append(tool.version())
                        .append(" -- ")
                        .append(tool.origin());
        tool.releaseTag().ifPresent(tag -> heading.append(", release tag ").append(tag));
        tool.stageId().ifPresent(stage -> heading.append(", stage ").append(stage));
        lines.add(heading.toString());
        lines.add("Path: " + tool.path());
        lines.add("MD5: " + tool.md5());
        lines.add("SHA-256: " + tool.sha256());
        lines.add(
                "Capabilities: "
                        + (tool.capabilities().isEmpty()
                                ? "none recorded"
                                : String.join(", ", tool.capabilities())));
        tool.artefactIdentity().ifPresent(identity -> lines.add("Artefact: " + identity));
        if (!tool.warnings().isEmpty()) {
            lines.add("Warnings: " + String.join("; ", tool.warnings()));
        }
        return String.join("\n", lines);
    }

    /**
     * One row of the Inputs/outputs part. A file that is not complete says so first, in capitals
     * and in words, so that it is never told apart by colour alone.
     *
     * @param file the file record
     * @return its role, status, path, size and both digests
     */
    static String file(ProvenanceView.File file) {
        List<String> lines = new ArrayList<>();
        lines.add((file.partial() ? "PARTIAL -- " : "") + file.direction() + " " + file.role());
        lines.add(
                "Status: "
                        + file.status()
                        + (file.partial()
                                ? " (incomplete: the stage that wrote it did not complete)"
                                : ""));
        lines.add("Path: " + file.path());
        lines.add(
                "Size: "
                        + String.format(Locale.ROOT, "%d", file.sizeBytes())
                        + " bytes; modified "
                        + file.modified());
        lines.add("MD5: " + file.md5());
        lines.add("SHA-256: " + file.sha256());
        return String.join("\n", lines);
    }

    /**
     * One row of the Parameters part's per-parameter list.
     *
     * @param parameter the parameter
     * @return its value, origin, release default and preset value, each difference named
     */
    static String parameter(ProvenanceView.Parameter parameter) {
        StringBuilder row =
                new StringBuilder()
                        .append(parameter.name())
                        .append(" = ")
                        .append(parameter.value())
                        .append("; origin: ")
                        .append(parameter.origin().orElse("not recorded"))
                        .append("; release default: ")
                        .append(parameter.releaseDefault().orElse("not known"));
        if (parameter.differsFromDefault()) {
            row.append(" (differs)");
        }
        parameter
                .presetValue()
                .ifPresent(
                        value -> {
                            row.append("; preset value: ").append(value);
                            if (parameter.differsFromPreset()) {
                                row.append(" (differs)");
                            }
                        });
        return row.toString();
    }

    /**
     * The applied presets.
     *
     * @param presets each preset the run records as applied
     * @return one paragraph per preset, or a sentence saying there were none
     */
    static String presets(List<ProvenanceView.Preset> presets) {
        if (presets.isEmpty()) {
            return "No preset is recorded as applied to this run's parameters.";
        }
        List<String> lines = new ArrayList<>();
        for (ProvenanceView.Preset preset : presets) {
            lines.add(
                    "Preset "
                            + preset.number()
                            + ": "
                            + preset.name()
                            + " ("
                            + preset.id()
                            + ", "
                            + preset.origin()
                            + ", written for Comet "
                            + preset.cometVersion()
                            + ", preset schema "
                            + preset.schemaVersion()
                            + ")");
            for (String applied : preset.applied()) {
                lines.add("  set " + applied);
            }
        }
        return String.join("\n", lines);
    }

    /**
     * The Percolator settings the record carries.
     *
     * @param settings the settings, in key order
     * @return one {@code key = value} line each, or a sentence saying there are none
     */
    static String settings(List<ProvenanceView.Setting> settings) {
        if (settings.isEmpty()) {
            return "No Percolator setting is recorded for this run.";
        }
        List<String> lines = new ArrayList<>();
        for (ProvenanceView.Setting setting : settings) {
            lines.add(setting.key() + " = " + setting.value());
        }
        return String.join("\n", lines);
    }

    /**
     * Whether an archived parameter file is still the one the run recorded.
     *
     * @param what what the file is, for the sentence
     * @param archived the file, if the run lists one
     * @return the file's path and what was found on disk, or a sentence saying there is none
     */
    static String archivedStatus(String what, Optional<ProvenanceView.ArchivedText> archived) {
        if (archived.isEmpty()) {
            return "This run lists no archived " + what + ".";
        }
        ProvenanceView.ArchivedText file = archived.get();
        StringBuilder status =
                new StringBuilder()
                        .append("The archived ")
                        .append(what)
                        .append(", ")
                        .append(file.path())
                        .append(": recorded SHA-256 ")
                        .append(file.recordedSha256())
                        .append("; on disk now it ")
                        .append(file.check().word());
        file.foundSha256()
                .filter(found -> !found.equals(file.recordedSha256()))
                .ifPresent(found -> status.append(" (SHA-256 ").append(found).append(')'));
        file.problem().ifPresent(problem -> status.append(". ").append(problem));
        return status.append('.').toString();
    }

    /**
     * An archived file's exact text, as it is shown.
     *
     * @param archived the file, if the run lists one
     * @return its text, or empty when there is none to show -- {@link #archivedStatus} says why
     */
    static String archivedText(Optional<ProvenanceView.ArchivedText> archived) {
        return archived.flatMap(ProvenanceView.ArchivedText::text).orElse("");
    }

    /**
     * One row of the Timeline part's executions.
     *
     * @param execution the execution
     * @return its stage, outcome, exit status, times, command, directory and environment
     */
    static String execution(ProvenanceView.Execution execution) {
        List<String> lines = new ArrayList<>();
        lines.add(
                execution.stageId().orElse(execution.tool())
                        + ": "
                        + execution.tool()
                        + " "
                        + execution.version()
                        + " -- "
                        + execution.status()
                        + ", exit status "
                        + execution.exitCode());
        lines.add(
                "Started "
                        + execution.start()
                        + ", ended "
                        + execution.end()
                        + ", took "
                        + execution.duration());
        lines.add("Command: " + execution.command());
        lines.add("Working directory: " + execution.workingDirectory());
        lines.add(
                "Environment: "
                        + (execution.environment().isEmpty()
                                ? "nothing added or overridden"
                                : execution.environment().replace("\n", "; ")));
        return String.join("\n", lines);
    }

    /**
     * One row of the Timeline part's event log.
     *
     * @param event the event
     * @return its sequence number, time, type and payload
     */
    static String event(ProvenanceView.Event event) {
        StringBuilder row =
                new StringBuilder()
                        .append(event.sequence())
                        .append(' ')
                        .append(event.timestamp())
                        .append(' ')
                        .append(event.type());
        if (!event.payload().isEmpty()) {
            row.append(": ").append(String.join("; ", event.payload()));
        }
        return row.toString();
    }

    /**
     * One row of the Logs part.
     *
     * @param log the archived log
     * @return the tool and stage that wrote it, its streams, its path and both digests
     */
    static String log(ProvenanceView.Log log) {
        return log.tool()
                + log.stageId().map(stage -> ", stage " + stage).orElse("")
                + " ("
                + log.streams()
                + ")\nPath: "
                + log.path()
                + "\nMD5: "
                + log.md5()
                + "\nSHA-256: "
                + log.sha256();
    }

    /**
     * The Warnings part, grouped by where each warning came from, in the read model's order.
     *
     * @param warnings the warnings
     * @return one entry per source: its name and count, then each warning's subject and message
     */
    static List<String> warningGroups(List<ProvenanceView.Warning> warnings) {
        Map<WarningSource, List<String>> groups = new LinkedHashMap<>();
        for (ProvenanceView.Warning warning : warnings) {
            groups.computeIfAbsent(warning.source(), source -> new ArrayList<>())
                    .add("- " + warning.subject() + ": " + warning.message());
        }
        List<String> shown = new ArrayList<>();
        for (Map.Entry<WarningSource, List<String>> group : groups.entrySet()) {
            String heading =
                    capitalised(group.getKey().word()) + " (" + group.getValue().size() + ")";
            List<String> lines = new ArrayList<>();
            lines.add(heading);
            lines.addAll(group.getValue());
            shown.add(String.join("\n", lines));
        }
        return List.copyOf(shown);
    }

    /**
     * One file of a verification against disk.
     *
     * @param check what was found
     * @return the outcome first, then what the file is, and both digests where they differ
     */
    static String check(DiskVerification.Check check) {
        StringBuilder row =
                new StringBuilder()
                        .append(check.outcome().word().toUpperCase(Locale.ROOT))
                        .append(" -- ")
                        .append(check.kind())
                        .append(' ')
                        .append(check.role())
                        .append(": ")
                        .append(check.path());
        if (check.outcome() == DiskOutcome.DIFFERS) {
            row.append("\nRecorded MD5 ")
                    .append(check.recordedMd5())
                    .append(", found ")
                    .append(check.foundMd5().orElse("nothing"))
                    .append("\nRecorded SHA-256 ")
                    .append(check.recordedSha256())
                    .append(", found ")
                    .append(check.foundSha256().orElse("nothing"));
        }
        check.problem().ifPresent(problem -> row.append("\n").append(problem));
        return row.toString();
    }

    /**
     * What a verification found, in one sentence.
     *
     * @param verification the verification
     * @return the sentence
     */
    static String verificationSummary(DiskVerification verification) {
        String checked = files(verification.checks().size());
        if (verification.allMatch()) {
            return "Every file the record lists matches its record on disk: "
                    + checked
                    + " checked.";
        }
        String counts =
                verification.count(DiskOutcome.MATCHES)
                        + " match, "
                        + verification.count(DiskOutcome.DIFFERS)
                        + " differ, "
                        + verification.count(DiskOutcome.MISSING)
                        + " missing, "
                        + verification.count(DiskOutcome.UNREADABLE)
                        + " unreadable";
        if (!verification.complete()) {
            return "The verification was cancelled after checking "
                    + checked
                    + ", so not every file was checked: "
                    + counts
                    + ".";
        }
        return "Not every file matches its record: of " + checked + ", " + counts + ".";
    }

    private static String files(int count) {
        return count + (count == 1 ? " file" : " files");
    }

    private static String capitalised(String words) {
        return words.isEmpty()
                ? words
                : words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }
}

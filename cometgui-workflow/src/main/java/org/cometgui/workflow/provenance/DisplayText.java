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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.tools.process.ProcessRedactor;

/**
 * The one place a value out of a record, a log or a file becomes text the Provenance section shows.
 *
 * <p>Every method that takes text from a run passes it through the session's {@link
 * SecretRedactor}, so no display string the read model produces can carry a secret the session
 * knows -- whatever the run's own redactor knew when it wrote the record. Nothing here is a second
 * rule set: the text rules, the argument-array rule and the environment rule are all {@link
 * SecretRedactor}'s, and a command is rendered by the process service's own {@link
 * ProcessRedactor#redactedDisplayCommand}, the rendering every stage log already uses.
 */
final class DisplayText {

    private static final long MILLIS_PER_SECOND = 1000L;

    private static final long SECONDS_PER_MINUTE = 60L;

    private static final long MINUTES_PER_HOUR = 60L;

    private final SecretRedactor redactor;

    private final ProcessRedactor commands;

    DisplayText(SecretRedactor redactor) {
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.commands = new ProcessRedactor(redactor);
    }

    /** Text out of a run, redacted. */
    String clean(String text) {
        return redactor.redactText(text);
    }

    /** A path out of a run, as redacted text. */
    String path(Path path) {
        return clean(path.toString());
    }

    /**
     * A command as "Copy command" copies it: each argument redacted, then escaped and quoted by
     * {@link ToolCommand#displayString()} -- in that order, which is the process service's.
     */
    String command(ToolCommand command) {
        return commands.redactedDisplayCommand(command);
    }

    /**
     * An environment, {@code NAME=value} one per line in name order, redacted by name and value.
     */
    String environment(Map<String, String> environment) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> variable :
                new TreeMap<>(redactor.redactEnvironment(environment)).entrySet()) {
            lines.add(clean(variable.getKey() + "=" + variable.getValue()));
        }
        return String.join("\n", lines);
    }

    /** A payload, one {@code key = value} line per entry in key order, redacted. */
    List<String> payload(Map<String, String> payload) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(payload).entrySet()) {
            lines.add(clean(entry.getKey() + " = " + entry.getValue()));
        }
        return List.copyOf(lines);
    }

    /** An instant, as the record writes one: canonical UTC with milliseconds. */
    static String timestamp(Instant instant) {
        return CanonicalTimestamp.utcMillis(instant);
    }

    /** The milliseconds between two instants, as the record's writer computes them. */
    static long millisBetween(Instant start, Instant end) {
        return CanonicalTimestamp.millisBetween(start, end);
    }

    /**
     * A duration in milliseconds as {@code H:MM:SS.mmm}, with no locale anywhere near the digits.
     *
     * @param millis a duration, never negative (every record refuses an end before its start)
     */
    static String duration(long millis) {
        long seconds = millis / MILLIS_PER_SECOND;
        long minutes = seconds / SECONDS_PER_MINUTE;
        long hours = minutes / MINUTES_PER_HOUR;
        return String.format(
                Locale.ROOT,
                "%d:%02d:%02d.%03d",
                hours,
                minutes % MINUTES_PER_HOUR,
                seconds % SECONDS_PER_MINUTE,
                millis % MILLIS_PER_SECOND);
    }
}

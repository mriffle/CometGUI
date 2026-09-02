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

package org.cometgui.domain.ports;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything needed to launch one external tool: the argument array, the working directory and the
 * environment.
 *
 * <p>An argument <em>array</em>, never a command string. {@code R-PROC-02} requires it and an
 * ArchUnit rule enforces the other half, but the reason is concrete: a FASTA file named {@code my
 * proteins;rm -rf.fasta} is an ordinary filename and an execution vulnerability the moment anything
 * joins these arguments with spaces and hands the result to a shell. This type is the boundary at
 * which that becomes impossible, so it validates rather than trusts, and it never offers a "command
 * line" that could be pasted into a terminal -- see {@link #displayString()}.
 *
 * <p>Both collections are copied on construction and exposed as immutable copies, so a caller that
 * keeps and mutates the list it passed in cannot change a command that has already been validated,
 * or one that has already been written to the provenance record. The argument array keeps its
 * order, because that is the command; the environment does not, because {@link Map#copyOf} makes no
 * promise about iteration order -- so anything that writes the environment to a log or a provenance
 * record sorts it by name, as {@link #toString()} does, rather than relying on the order it was
 * given in.
 *
 * @param argv the executable followed by its arguments; at least one element, none null or blank
 * @param workingDirectory the absolute directory to run in, required by {@code R-PROC-04}
 * @param environment the environment variables to set, which may be empty but not {@code null}
 */
public record ToolCommand(
        List<String> argv, Path workingDirectory, Map<String, String> environment) {

    /**
     * Validates the command and takes defensive, unmodifiable copies of both collections.
     *
     * @throws NullPointerException if {@code argv}, {@code workingDirectory} or {@code environment}
     *     is {@code null}
     * @throws IllegalArgumentException if {@code argv} is empty, if any argument is null or blank,
     *     if the working directory is relative, or if an environment name is blank or contains
     *     {@code =} or has a null value -- with a message naming the offending element
     */
    public ToolCommand {
        argv = checkedArgv(argv);
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        if (!workingDirectory.isAbsolute()) {
            throw new IllegalArgumentException(
                    "workingDirectory must be absolute, but was: " + workingDirectory);
        }
        environment = checkedEnvironment(environment);
    }

    private static List<String> checkedArgv(List<String> argv) {
        List<String> copy = new ArrayList<>(Objects.requireNonNull(argv, "argv"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(
                    "argv must contain at least one element: the executable to run");
        }
        for (int index = 0; index < copy.size(); index++) {
            String argument = copy.get(index);
            if (argument == null) {
                throw new IllegalArgumentException("argv[" + index + "] must not be null");
            }
            if (argument.isBlank()) {
                throw new IllegalArgumentException(
                        "argv[" + index + "] must not be blank, but was: \"" + argument + "\"");
            }
        }
        return List.copyOf(copy);
    }

    private static Map<String, String> checkedEnvironment(Map<String, String> environment) {
        Map<String, String> copy =
                new LinkedHashMap<>(Objects.requireNonNull(environment, "environment"));
        for (Map.Entry<String, String> variable : copy.entrySet()) {
            String name = variable.getKey();
            if (name == null) {
                throw new IllegalArgumentException("an environment variable name must not be null");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException(
                        "an environment variable name must not be blank, but was: \""
                                + name
                                + "\"");
            }
            if (name.indexOf('=') >= 0) {
                throw new IllegalArgumentException(
                        "an environment variable name must not contain '=', but was: \""
                                + name
                                + "\"");
            }
            if (variable.getValue() == null) {
                throw new IllegalArgumentException(
                        "the environment variable \"" + name + "\" must not have a null value");
            }
        }
        return Map.copyOf(copy);
    }

    /**
     * The executable and its arguments, in order.
     *
     * <p>The returned list is immutable, and the copy is free: the component was already replaced
     * by an immutable copy in the constructor, and {@link List#copyOf} returns such a list
     * unchanged. Stating the copy here rather than relying on that is what makes the guarantee
     * visible to a reader -- and to SpotBugs, which reports a record accessor handing out a
     * collection field as {@code EI_EXPOSE_REP} and is right to, since nothing at the call site
     * shows which kind of list it received.
     *
     * @return the argument array, immutable
     */
    public List<String> argv() {
        return List.copyOf(argv);
    }

    /**
     * The environment variables to set for the process.
     *
     * <p>Immutable, and copied for the reason given on {@link #argv()}. Iteration order is not
     * specified; sort by name before writing these to a log or a provenance record.
     *
     * @return the environment, immutable
     */
    public Map<String, String> environment() {
        return Map.copyOf(environment);
    }

    /**
     * Renders the command for a log line, a console pane or a provenance report.
     *
     * <p><strong>The result is not a shell command and must never be treated as one.</strong> It is
     * the argument array written the way a Java or JSON array is written -- {@code ["/opt/comet",
     * "-P", "comet.params"]} -- with every element quoted and with backslashes, quotes, tabs,
     * newlines and other control characters escaped. Two properties follow, and both are what
     * {@code R-PROC-02} is protecting:
     *
     * <ul>
     *   <li>the boundaries between arguments survive the rendering, so an argument containing a
     *       space is visibly one argument rather than two;
     *   <li>nothing a shell reacts to -- {@code ;}, {@code $(..)}, a backtick, a newline -- can
     *       escape the quotes it is printed inside, so copying this text into a terminal cannot run
     *       something the application did not run.
     * </ul>
     *
     * @return the escaped argument array, never {@code null}
     */
    public String displayString() {
        StringBuilder rendered = new StringBuilder(64).append('[');
        for (int index = 0; index < argv.size(); index++) {
            if (index > 0) {
                rendered.append(", ");
            }
            appendQuoted(rendered, argv.get(index));
        }
        return rendered.append(']').toString();
    }

    private static void appendQuoted(StringBuilder target, String argument) {
        target.append('"');
        for (int position = 0; position < argument.length(); position++) {
            char character = argument.charAt(position);
            if (character == '\\') {
                target.append("\\\\");
            } else if (character == '"') {
                target.append("\\\"");
            } else if (character == '\n') {
                target.append("\\n");
            } else if (character == '\r') {
                target.append("\\r");
            } else if (character == '\t') {
                target.append("\\t");
            } else if (character < ' ') {
                target.append(String.format("\\u%04x", (int) character));
            } else {
                target.append(character);
            }
        }
        target.append('"');
    }

    /**
     * Describes the command without disclosing any environment value.
     *
     * <p>The record's generated {@code toString} would print every environment value, and an
     * environment is one of the places a token, a password or an upload key lives. This one prints
     * the variable <em>names</em> only, sorted so that the same command always describes itself the
     * same way. The values are still available through {@link #environment()}, which is the
     * deliberate, redactable path the provenance recorder uses.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "ToolCommand[argv="
                + displayString()
                + ", workingDirectory="
                + workingDirectory
                + ", environmentNames="
                + environment.keySet().stream().sorted().toList()
                + "]";
    }
}

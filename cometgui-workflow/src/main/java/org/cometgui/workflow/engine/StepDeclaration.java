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

package org.cometgui.workflow.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Everything a step will touch, stated before it runs: the files it reads and writes, and the
 * identifiers of the tool invocations it makes.
 *
 * <p>The invocation identifiers are the process service's stage identifiers ({@code comet-01},
 * {@code comet-02} ...), which are also the {@code stageId} of each invocation's provenance tool
 * record. Declaring them is what lets a retry that reuses this step carry its recorded invocations
 * into the new provenance record, and refuse the reuse when one of them is not recorded as
 * completed. An invocation whose identifier the step did not declare is refused when it is
 * attempted.
 *
 * @param files the declared files, in the order they are recorded
 * @param invocationIds the declared invocation identifiers, each matching {@code
 *     [A-Za-z0-9_-]{1,64}}, with no duplicates
 */
public record StepDeclaration(List<DeclaredFile> files, List<String> invocationIds) {

    private static final Pattern STAGE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** A step that touches no file and runs no tool. */
    public static final StepDeclaration NOTHING = new StepDeclaration(List.of(), List.of());

    /**
     * Validates and copies.
     *
     * @throws NullPointerException if a list or an element is {@code null}
     * @throws IllegalArgumentException if a file is declared twice in the same direction, or an
     *     invocation identifier is malformed or repeated, quoting it
     */
    public StepDeclaration {
        files = List.copyOf(files);
        invocationIds = List.copyOf(invocationIds);
        Set<String> seenFiles = new HashSet<>();
        for (DeclaredFile file : files) {
            if (!seenFiles.add(file.direction().wireName() + " " + file.path())) {
                throw new IllegalArgumentException(
                        "the "
                                + file.direction().wireName()
                                + " "
                                + file.path()
                                + " is declared twice");
            }
        }
        Set<String> seenIds = new HashSet<>();
        for (String id : invocationIds) {
            if (!STAGE_ID.matcher(id).matches()) {
                throw new IllegalArgumentException(
                        "an invocation identifier must match [A-Za-z0-9_-]{1,64}, but was: \""
                                + id
                                + "\"");
            }
            if (!seenIds.add(id)) {
                throw new IllegalArgumentException("the invocation " + id + " is declared twice");
            }
        }
    }

    @Override
    public List<DeclaredFile> files() {
        return List.copyOf(files);
    }

    @Override
    public List<String> invocationIds() {
        return List.copyOf(invocationIds);
    }

    /**
     * Whether an invocation identifier is declared.
     *
     * @param id an identifier
     * @return {@code true} if this declaration lists it
     */
    boolean declares(String id) {
        return invocationIds.contains(Objects.requireNonNull(id, "id"));
    }
}

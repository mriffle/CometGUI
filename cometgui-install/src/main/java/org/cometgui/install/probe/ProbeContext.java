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

package org.cometgui.install.probe;

import java.util.List;
import java.util.Objects;
import org.cometgui.domain.tools.LoaderDiagnostic;

/**
 * The three things a loader failure has to be described with that the loader's own output does not
 * contain.
 *
 * <p>Kept together because they travel together into every {@link LoaderDiagnostic} this package
 * builds, and because a method taking four unrelated strings invites a call site to swap two of
 * them.
 *
 * @param subject what to name when the loader named nothing -- the executable's own file name, so
 *     that "percolator is not executable on this host" reads as a sentence about the thing the user
 *     asked for
 * @param declaredHostLibraries the libraries the manifest says the host must already provide, which
 *     is the only place the name of a missing Visual C++ runtime DLL exists: Windows reports the
 *     failure as "the specified module could not be found" and does not say which module
 * @param alternatives what the user can do instead, in the order they should be offered; empty is
 *     allowed and is rendered as such, because "there is nothing else you can do" is information
 */
public record ProbeContext(
        String subject, List<String> declaredHostLibraries, List<String> alternatives) {

    /**
     * Validates the context and takes immutable copies of both lists.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if {@code subject} is blank
     */
    public ProbeContext {
        Objects.requireNonNull(subject, "subject");
        if (subject.isBlank()) {
            throw new IllegalArgumentException(
                    "subject must not be blank: a diagnostic has to name something the user can"
                            + " recognise");
        }
        declaredHostLibraries =
                List.copyOf(Objects.requireNonNull(declaredHostLibraries, "declaredHostLibraries"));
        alternatives = List.copyOf(Objects.requireNonNull(alternatives, "alternatives"));
    }

    /**
     * The libraries the manifest declared, immutable.
     *
     * @return the libraries, possibly empty
     */
    @Override
    public List<String> declaredHostLibraries() {
        return List.copyOf(declaredHostLibraries);
    }

    /**
     * The alternatives to offer, immutable and in order.
     *
     * @return the alternatives, possibly empty
     */
    @Override
    public List<String> alternatives() {
        return List.copyOf(alternatives);
    }
}

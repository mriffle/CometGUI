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

package org.cometgui.install.cache;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * The macOS quarantine step could not show every installed file free of {@code
 * com.apple.quarantine}.
 *
 * <p>An {@link IOException}, so the install pipeline fails the install exactly as it fails one
 * whose attribute change was refused: an installed tool Gatekeeper refuses on first use is worse
 * than an install that says why it stopped. The message names every such file and the reason; the
 * {@linkplain #report() report} says, in addition, what <em>was</em> cleared and made executable
 * before the step gave up, so a caller -- the macOS verification job above all -- can compare the
 * product's account with what {@code /usr/bin/xattr} sees.
 *
 * <p>The report does not survive serialization -- a {@link FixupReport} is not serializable, and
 * nothing in this product serializes an exception -- but the reasons and the message do.
 */
public final class QuarantineNotClearedException extends IOException {

    private static final long serialVersionUID = 1L;

    /** The whole report, including the files that were not cleared. */
    private final transient FixupReport report;

    /** One reason per file that was not cleared, each starting with that file's path. */
    private final String[] reasons;

    /**
     * Creates the exception.
     *
     * @param report what the step did, with at least one file it could not clear
     * @param reasons one line per such file, each beginning with its relative path
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the report names no file that was not cleared, or the
     *     number of reasons differs from the number of such files
     */
    public QuarantineNotClearedException(FixupReport report, List<String> reasons) {
        super(message(report, reasons));
        this.report = report;
        this.reasons = reasons.toArray(new String[0]);
    }

    private static String message(FixupReport report, List<String> reasons) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(reasons, "reasons");
        int failed = report.quarantineNotCleared().size();
        if (failed == 0) {
            throw new IllegalArgumentException(
                    "a report that cleared every file is not a failure to clear one");
        }
        if (reasons.size() != failed) {
            throw new IllegalArgumentException(
                    "every file that was not cleared needs exactly one reason: "
                            + failed
                            + " file(s), "
                            + reasons.size()
                            + " reason(s)");
        }
        return PlatformFixups.QUARANTINE_ATTRIBUTE
                + " could not be shown removed from "
                + failed
                + " installed file(s), so Gatekeeper may refuse to run them (R-PLAT-04): "
                + String.join("; ", reasons);
    }

    /**
     * What the step did before and while failing.
     *
     * @return the report, whose {@link FixupReport#quarantineNotCleared()} is not empty
     */
    public FixupReport report() {
        return report;
    }

    /**
     * Why each file was not cleared, in the order of {@link FixupReport#quarantineNotCleared()}.
     *
     * @return one line per file, each beginning with its relative path; immutable
     */
    public List<String> reasons() {
        return List.of(reasons);
    }
}

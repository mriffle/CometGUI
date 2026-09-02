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

package org.cometgui.ui.viewmodel;

import java.util.Objects;
import org.cometgui.domain.platform.HostBaselineOutcome;
import org.cometgui.domain.platform.HostBaselineReport;

/**
 * The startup host-baseline banner, as a view-model: whether to show one, how severe it is, and
 * what it says.
 *
 * <p>{@code R-PLAT-01} wants an unsupported host reported to the user at startup rather than
 * discovered by a crash three stages into a run. The domain's {@code HostBaselineVerifier} produces
 * a {@link HostBaselineReport}; this class turns it into the three answers a banner needs, so that
 * the mapping from five outcomes to "no banner / warning / blocking" is written down once and
 * tested, instead of being a chain of conditionals inside a controller.
 *
 * <h2>Why there are no observable properties here</h2>
 *
 * <p>The baseline is checked once, synchronously, before the shell is built: it reads system
 * properties and, on Linux, one native symbol. The report therefore never changes while the
 * application is running, and a property that can never fire would be a promise this class cannot
 * keep. If a later phase makes the check asynchronous -- a slow probe moved off the startup path,
 * say -- this is the class to give a property to, and the accessors below become its reads.
 *
 * <h2>The severity is in the text, not only in the colour</h2>
 *
 * <p>The specification's <em>Accessibility</em> principle requires that a validation error be
 * conveyed in text rather than by colour alone. {@link BannerLevel#heading()} is what makes that
 * true of this banner: a screen reader that never sees the red border still reads "Cannot
 * continue", and {@link #bannerText()} is the whole sentence including it.
 */
public final class HostBaselineViewModel {

    private final HostBaselineReport report;

    private final BannerLevel level;

    /**
     * A view-model presenting one baseline report.
     *
     * @param report the report the host baseline check produced
     * @throws NullPointerException if {@code report} is {@code null}
     */
    public HostBaselineViewModel(HostBaselineReport report) {
        this.report = Objects.requireNonNull(report, "report");
        this.level = levelFor(report.outcome());
    }

    /**
     * The banner level an outcome calls for.
     *
     * <p>Three cases, in this order: a supported host gets no banner; any outcome the domain marks
     * {@link HostBaselineOutcome#blocking()} gets a blocking banner; everything else -- the two
     * "could not be established" outcomes -- gets a warning. The blocking test is delegated to the
     * outcome rather than listed here, so adding an outcome to the domain cannot leave this class
     * quietly classifying it as a warning.
     *
     * @param outcome what the baseline check found
     * @return the level of banner to show
     */
    private static BannerLevel levelFor(HostBaselineOutcome outcome) {
        if (outcome == HostBaselineOutcome.SUPPORTED) {
            return BannerLevel.NONE;
        }
        return outcome.blocking() ? BannerLevel.BLOCKING : BannerLevel.WARNING;
    }

    /**
     * The report this view-model presents.
     *
     * @return the report, never {@code null}
     */
    public HostBaselineReport report() {
        return report;
    }

    /**
     * How severe the banner is.
     *
     * @return {@link BannerLevel#NONE}, {@link BannerLevel#WARNING} or {@link BannerLevel#BLOCKING}
     */
    public BannerLevel level() {
        return level;
    }

    /**
     * Whether a banner should be shown at all.
     *
     * @return {@code true} unless the host meets the baseline
     */
    public boolean bannerVisible() {
        return level.visible();
    }

    /**
     * Whether the banner is a blocking one rather than a warning.
     *
     * @return {@code true} when no run can proceed on this host
     */
    public boolean blocking() {
        return level.blocking();
    }

    /**
     * The diagnostic the domain produced, naming the host's value and the requirement.
     *
     * @return the message, never blank
     */
    public String message() {
        return report.message();
    }

    /**
     * The whole banner sentence: the level's heading, then the diagnostic.
     *
     * <p>Always composed, even when {@link #bannerVisible()} is {@code false} -- a view calls this
     * only when it is showing a banner, and a method that threw would make the class awkward to
     * test and to log. For a supported host the sentence is the heading {@link BannerLevel#NONE}
     * carries followed by the report's own confirmation message.
     *
     * @return heading, colon, space, message
     */
    public String bannerText() {
        return level.heading() + ": " + report.message();
    }

    /**
     * How loudly the banner speaks.
     *
     * <p>Three levels rather than the domain's five outcomes, because a banner has three shapes and
     * not five: absent, advisory, and the one that stops the workflow. The outcomes stay in the
     * domain, where the distinction between "no 64-bit build exists" and "glibc could not be read"
     * belongs; what reaches the view is this.
     */
    public enum BannerLevel {

        /** The host meets the baseline: nothing is shown. */
        NONE(false, false, "Host baseline satisfied"),

        /** Something could not be established. The run may proceed. */
        WARNING(true, false, "Warning"),

        /** The host cannot run the managed tools. Nothing may proceed. */
        BLOCKING(true, true, "Cannot continue");

        private final boolean visible;

        private final boolean blocking;

        private final String heading;

        BannerLevel(boolean visible, boolean blocking, String heading) {
            this.visible = visible;
            this.blocking = blocking;
            this.heading = heading;
        }

        /**
         * Whether a banner is shown at this level.
         *
         * @return {@code false} only for {@link #NONE}
         */
        public boolean visible() {
            return visible;
        }

        /**
         * Whether this level stops the workflow.
         *
         * @return {@code true} only for {@link #BLOCKING}
         */
        public boolean blocking() {
            return blocking;
        }

        /**
         * The short text that states the severity in words, for a heading and for a screen reader.
         *
         * @return the heading, never blank
         */
        public String heading() {
            return heading;
        }
    }
}

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

package org.cometgui.app.config;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.cometgui.domain.ports.RunIdSource;
import org.cometgui.domain.run.RunId;

/**
 * The real {@link RunIdSource}: a UTC timestamp from an injected {@link Clock}, then a sequence
 * number.
 *
 * <p>An identifier looks like {@code run-20260830T142530123Z-1}. It is built from the character set
 * {@link RunId} permits -- a letter or digit first, then letters, digits, {@code .}, {@code -} and
 * {@code _} -- and is 25 characters long, well inside {@link RunId#MAX_LENGTH}. {@link RunId}'s own
 * constructor validates it, so a change here that produced something unacceptable fails at the
 * point of production rather than when a directory is created.
 *
 * <h2>Why there is a sequence number as well as a clock</h2>
 *
 * <p>{@link RunIdSource} requires that no identifier is ever handed out twice, because the
 * identifier names a run directory and a collision overwrites an earlier run's evidence. {@code
 * R-PROC-01} requires the clock to be injectable so that tests are deterministic -- and a test
 * clock is usually a fixed one. Those two requirements are in direct tension: an identifier derived
 * from a fixed clock alone would repeat on the second call.
 *
 * <p><strong>The decision, and it is deliberate: two identifiers from the same fixed clock
 * differ.</strong> The timestamp is the human-readable part and the sequence is what makes the
 * value unique, so uniqueness survives a fixed clock, a coarse clock, and two runs started in the
 * same millisecond. The cost is that this source is not a pure function of its clock, which is why
 * the sequence is separately visible and starts at 1 for a fresh instance: a test asserts the exact
 * strings {@code run-...-1} and {@code run-...-2} rather than "they are different".
 *
 * <p>The counter is per instance rather than static, so that two tests cannot interfere; the
 * composition root builds exactly one for the life of the application. Across a restart the counter
 * starts again at 1, and it is the millisecond timestamp that separates the runs -- an installation
 * would have to be restarted and start a run inside the same millisecond as a previous run to
 * collide.
 *
 * <p>The class is safe to use from several threads: the counter is an {@link AtomicLong} and {@link
 * Clock} is documented as thread-safe.
 */
public final class ClockRunIdSource implements RunIdSource {

    /** The prefix every identifier this source produces begins with. */
    public static final String PREFIX = "run-";

    /**
     * The timestamp format: {@code uuuuMMdd'T'HHmmssSSS'Z'}, always UTC.
     *
     * <p>Compact rather than ISO-8601-with-punctuation because {@code :} and {@code +} are not in
     * {@link RunId}'s permitted set and are not legal in a Windows path segment. The trailing
     * {@code Z} is a literal that records the offset the formatter is fixed to, so that a directory
     * name cannot be misread as local time.
     */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

    private final Clock clock;

    private final AtomicLong sequence = new AtomicLong();

    /**
     * Creates a source over the clock it timestamps identifiers from.
     *
     * @param clock the clock seam of {@code R-PROC-01}; the composition root passes {@link
     *     Clock#systemUTC()} and a test passes {@link Clock#fixed}
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    public ClockRunIdSource(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * {@inheritDoc}
     *
     * <p>The instant is read from the injected clock and rendered in UTC; the sequence number is
     * this instance's next, counting from 1.
     */
    @Override
    public RunId newRunId() {
        return new RunId(PREFIX + STAMP.format(clock.instant()) + "-" + sequence.incrementAndGet());
    }

    /**
     * How many identifiers this instance has produced.
     *
     * <p>Exposed because it is the half of an identifier that is not a function of the clock, and a
     * caller reasoning about uniqueness -- or a test asserting the exact next value -- should not
     * have to parse it back out of a string.
     *
     * @return the number of calls to {@link #newRunId()} so far, {@code 0} for a fresh instance
     */
    public long issuedCount() {
        return sequence.get();
    }
}

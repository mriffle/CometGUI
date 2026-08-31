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

package org.cometgui.provenance.json;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/**
 * The one way this project writes an instant down: ISO-8601, UTC, exactly three fractional digits.
 *
 * <p><strong>Fixed width, and that is the requirement rather than a preference.</strong> {@link
 * DateTimeFormatter#ISO_INSTANT} renders {@code 2026-08-31T09:48:00Z} when the nanoseconds happen
 * to be zero, {@code ...:00.250Z} when they are a whole millisecond and {@code ...:00.250999999Z}
 * otherwise -- so the <em>shape</em> of a provenance record would depend on what the clock did
 * during the run. Two runs of the same workflow would then produce documents that differ in
 * structure, a reader would have to accept three grammars, and a hand-typed expected document could
 * not be written at all. This renders {@code .000} for an instant with no nanoseconds and {@code
 * .250} for one with 250 999 999 of them, and a reader may rely on both.
 *
 * <p><strong>Milliseconds, and the truncation is real.</strong> Sub-millisecond precision is not a
 * fact about a proteomics run: the stages are seconds to hours long, the file modification times
 * come from a filesystem that often stores less, and nothing downstream can act on a microsecond.
 * Three digits is what a scientist reads without counting zeros. The consequence is stated here
 * rather than discovered later: <strong>an {@link Instant} written by this class and read back is
 * truncated towards the past, not rounded</strong>, so {@code ...:00.250999999Z} is recorded as
 * {@code ...:00.250Z} and a round trip is lossy below a millisecond. Anything that compares a
 * parsed instant against an original must compare at millisecond precision.
 *
 * <p><strong>UTC, always, and separately from the recorded zone.</strong> The instant is an
 * absolute moment; rendering it in the machine's default zone would mean two records of the same
 * event disagreed, and would silently change meaning when a laptop crossed a border or a
 * daylight-saving boundary. The zone the run actually happened in is not lost: it is recorded once,
 * as {@code ApplicationRecord.zoneId}, which is where a reader that wants local wall-clock time
 * gets it.
 *
 * <p><strong>No locale reaches this.</strong> The formatter is built with {@link Locale#ROOT} and
 * {@link DecimalStyle#STANDARD}, so its digits are ASCII whatever {@link Locale#getDefault()} is --
 * {@code th-TH-u-nu-thai} and {@code ar-EG-u-nu-arab} both have their own digit sets, and a
 * provenance timestamp written in Thai digits is a timestamp no tool will parse. The pattern uses
 * {@code uuuu} rather than {@code yyyy} because {@code yyyy} is year-of-era and needs an era to be
 * unambiguous; {@code uuuu} is the proleptic year and is what ISO-8601 means.
 *
 * <p>{@link #millisBetween} belongs here for the same reason: a duration written into a document
 * has to be the one a reader can recompute from the two timestamps printed beside it, which are
 * truncated. Deriving it anywhere else would eventually derive it from the untruncated instants.
 *
 * <p><strong>Shared, so that the JSON and the report cannot drift.</strong> {@code provenance.json}
 * and {@code provenance.rst} are generated from one model, as {@code R-PROV-05} requires, and two
 * documents that render the same instant differently would break that promise in the one field a
 * reader is most likely to compare across them. Both call this.
 */
public final class CanonicalTimestamp {

    /**
     * The pattern every timestamp in a provenance artefact is written with.
     *
     * <p>Published so that the schema documentation and any future reader quote one string rather
     * than three copies of it. A reader building a formatter from it <strong>must</strong> supply
     * {@link Locale#ROOT}: {@link DateTimeFormatter#ofPattern(String)} without a locale reads the
     * JVM default, which is exactly the dependency this class exists to remove.
     */
    public static final String PATTERN = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * The formatter itself: immutable, thread-safe, and pinned to UTC, {@link Locale#ROOT} and
     * {@link DecimalStyle#STANDARD} at construction so that no caller can supply a different one.
     */
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern(PATTERN, Locale.ROOT)
                    .withZone(ZoneOffset.UTC)
                    .withDecimalStyle(DecimalStyle.STANDARD);

    /**
     * Never instantiated: this is one operation over an instant, with no state to carry.
     *
     * <p>It throws rather than being an empty private constructor so that the intent is enforced
     * for the one caller that can still reach it -- reflection -- instead of merely being implied.
     */
    private CanonicalTimestamp() {
        throw new AssertionError("CanonicalTimestamp is a utility class and is never instantiated");
    }

    /**
     * Renders an instant as {@code uuuu-MM-ddTHH:mm:ss.SSSZ} in UTC.
     *
     * @param instant the moment to render
     * @return the instant in UTC with exactly three fractional digits, truncated towards the past
     * @throws NullPointerException if {@code instant} is {@code null}
     */
    public static String utcMillis(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return UTC_MILLIS.format(instant);
    }

    /**
     * The elapsed milliseconds between two instants <strong>as a reader of the document can
     * recompute them</strong>.
     *
     * <p><strong>Both instants are truncated to milliseconds first, and that is the whole point of
     * this method existing.</strong> A provenance document carries {@link #utcMillis} renderings,
     * so a reader validating a recorded duration against the two timestamps beside it has only the
     * truncated values to work from. Deriving the duration from the raw nanoseconds would produce a
     * number the document contradicts itself about: for the pinned fixture, a start of {@code
     * 09:14:00.250999999Z} and an end of {@code 09:48:00.000Z} give 2 039 749 ms from the raw
     * instants and 2 039 750 ms from what the document actually says. The second is the honest
     * answer, because the first describes a start the document does not contain.
     *
     * <p>Negative results are possible and are not rejected here: the record types already refuse
     * an end before a start, and a serialiser is the wrong place to re-litigate a model invariant.
     *
     * @param start the earlier instant
     * @param end the later instant
     * @return the elapsed milliseconds between the two, each truncated to milliseconds first
     * @throws NullPointerException if either argument is {@code null}
     * @throws ArithmeticException if the elapsed time overflows a {@code long} of milliseconds,
     *     which needs instants nearly 300 million years apart
     */
    public static long millisBetween(Instant start, Instant end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        return Duration.between(
                        start.truncatedTo(ChronoUnit.MILLIS), end.truncatedTo(ChronoUnit.MILLIS))
                .toMillis();
    }
}

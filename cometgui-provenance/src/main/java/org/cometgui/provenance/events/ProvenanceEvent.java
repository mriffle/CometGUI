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

package org.cometgui.provenance.events;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.cometgui.provenance.manifest.ProvenanceStatus;

/**
 * One line of the provenance event log: what happened, when, in what order, and the details.
 *
 * <p>{@code R-PROV-05} allows provenance to be written "incrementally as appendable events", and an
 * event is only useful for that if it carries its own position in the stream. The four components
 * are the minimum that makes a torn log readable: the {@link #sequence()} says whether anything is
 * missing, the {@link #timestamp()} says when, the {@link #type()} says what kind of thing
 * happened, and the {@link #payload()} says which stage, tool or file it happened to.
 *
 * <p><strong>Why the payload is an open map and not fields on seven record types.</strong> The
 * alternative -- a sealed interface with a record per event type, each with its own components --
 * reads better at a call site and fails at exactly the point this package must not fail. Redaction
 * would then be per-type: every new event type would have to remember to send its own new field
 * through {@link org.cometgui.domain.secrets.SecretRedactor}, and the first one that forgot would
 * open a leak path that the six existing types had already closed. With one generic payload there
 * is one place where values are cleaned -- {@link ProvenanceEventLog#append} -- and adding an event
 * type cannot add a field that bypasses it. The type is still explicit: {@link ProvenanceEventType}
 * is the tag, and it is pinned on the wire.
 *
 * <p><strong>Sorted, because a log is diffed.</strong> The payload is iterated in ascending key
 * order, always, for the reason {@code ProvenanceManifest} gives about its settings map: two runs
 * that differ only in the order a {@link java.util.HashMap} happened to hash its keys must produce
 * byte-identical lines, or two event logs cannot be compared and a line's own checksum is not
 * reproducible. {@link String#compareTo} is used, which does not consult the default locale.
 *
 * <p><strong>The timestamp is truncated to milliseconds on the way in, not on the way out.</strong>
 * The wire form carries exactly three fractional digits, so an event holding microseconds would not
 * equal the event read back from the line it produced. That inequality would be invisible in
 * ordinary use and would surface as an unreproducible test on whichever machine has a
 * microsecond-resolution clock, so the truncation happens here, once, and the in-memory event is
 * always exactly what the file says.
 *
 * @param sequence this event's position in the log, counting from 1 with no gaps
 * @param timestamp when the event happened, in UTC, truncated to milliseconds
 * @param type what kind of thing happened
 * @param payload the details, iterated in ascending key order; may be empty
 */
public record ProvenanceEvent(
        long sequence, Instant timestamp, ProvenanceEventType type, Map<String, String> payload) {

    /**
     * The sequence number of the first event in a log: 1.
     *
     * <p>Counting from one rather than zero so that "the highest sequence seen" and "how many
     * events are in this log" are the same number for an intact log, which is the check a reader
     * makes first.
     */
    public static final long FIRST_SEQUENCE = 1L;

    /**
     * The payload key under which a {@link ProvenanceEventType#RUN_FINISHED} event carries the
     * terminal status the run ended in: {@code status}.
     *
     * <p>Pinned here for the reason {@code ProvenanceSchema.PERCOLATOR_SEED_SETTING} is pinned: a
     * key written as a literal at two call sites is a key that eventually differs at two call
     * sites, and a reader looking for {@code "status"} while the writer wrote {@code "run_status"}
     * would report every finished run as unfinished. The value is a {@link
     * ProvenanceStatus#wireName()}.
     */
    public static final String STATUS_KEY = "status";

    /** The earliest instant the fixed-width wire timestamp can represent. */
    private static final Instant EARLIEST = Instant.parse("0000-01-01T00:00:00Z");

    /** The latest instant the fixed-width wire timestamp can represent. */
    private static final Instant LATEST = Instant.parse("9999-12-31T23:59:59.999Z");

    /**
     * Validates the event, truncates its timestamp and takes an immutable sorted copy of the
     * payload.
     *
     * @throws NullPointerException if {@code timestamp}, {@code type} or {@code payload} is {@code
     *     null}, or if any payload key or value is {@code null}
     * @throws IllegalArgumentException if {@code sequence} is below {@link #FIRST_SEQUENCE}, if the
     *     timestamp falls outside the four-digit-year range the wire format can represent, if a
     *     payload key is blank, or if a {@link ProvenanceEventType#RUN_FINISHED} event does not
     *     carry a terminal status under {@link #STATUS_KEY}
     */
    public ProvenanceEvent {
        if (sequence < FIRST_SEQUENCE) {
            throw new IllegalArgumentException(
                    "an event sequence number starts at "
                            + FIRST_SEQUENCE
                            + ", but was: "
                            + sequence);
        }
        Objects.requireNonNull(timestamp, "timestamp");
        // Truncated before the range is checked, not after, so that an instant which truncates
        // into the representable range is accepted rather than rejected for digits the wire form
        // was never going to carry.
        timestamp = timestamp.truncatedTo(ChronoUnit.MILLIS);
        if (timestamp.isBefore(EARLIEST) || timestamp.isAfter(LATEST)) {
            throw new IllegalArgumentException(
                    "an event timestamp must lie between 0000-01-01T00:00:00Z and"
                            + " 9999-12-31T23:59:59.999Z, which is what the fixed-width wire form"
                            + " can represent");
        }
        Objects.requireNonNull(type, "type");
        payload = sortedPayload(payload);
        requireTerminalStatusOnRunFinished(type, payload);
    }

    /**
     * The details of the event.
     *
     * <p>Immutable, and <strong>iterated in ascending key order</strong> for the reason given on
     * the class. The copy is what makes the immutability visible at the call site -- and to
     * SpotBugs, which reports a record accessor handing out a collection field as {@code
     * EI_EXPOSE_REP}.
     *
     * @return the payload, immutable and sorted by key
     */
    public Map<String, String> payload() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(payload));
    }

    /**
     * Describes the event without disclosing a single payload value.
     *
     * <p>The generated {@code toString} would print every value, and the payload is an open
     * namespace that later phases fill with whatever a stage needs to record -- an upload target, a
     * command line, eventually something that should never have been put there. {@code R-SEC-03}
     * and {@code AC-LL-06} make that a leak, and a {@code toString} is the quietest way to have
     * one: it is called by every logging framework, by {@link String#valueOf}, and by an exception
     * message nobody reviewed.
     *
     * <p>So it prints the payload <em>keys</em>, already sorted, which is what identifies an event
     * in a log line, and never the values. The values are still available through {@link
     * #payload()}, which is the deliberate, redactable path {@link ProvenanceEventLog} uses.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "ProvenanceEvent[sequence="
                + sequence
                + ", timestamp="
                + EventLineFormat.formatTimestamp(timestamp)
                + ", type="
                + type.wireName()
                + ", payloadKeys="
                + payload.keySet()
                + "]";
    }

    /**
     * Copies the payload into ascending key order, rejecting anything that cannot be written.
     *
     * @param payload the caller's map
     * @return an immutable sorted copy
     */
    private static Map<String, String> sortedPayload(Map<String, String> payload) {
        Objects.requireNonNull(payload, "payload");
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "a payload key");
            if (key.isBlank()) {
                throw new IllegalArgumentException(
                        "a payload key must not be blank, and one of them is");
            }
            String value = entry.getValue();
            Objects.requireNonNull(value, () -> "the payload value of \"" + key + "\"");
            sorted.put(key, value);
        }
        return Collections.unmodifiableMap(sorted);
    }

    /**
     * Enforces the one rule a type places on its payload: a finished run names how it finished.
     *
     * <p><strong>No rejected value is ever quoted in these messages.</strong> The reader builds
     * events out of lines it found in a file, and a damaged or foreign file's bytes are not
     * necessarily ones this application wrote and redacted. A message that echoed the offending
     * value would put unredacted file content into an exception, a log line and a UI, which is the
     * leak the whole package exists to prevent. The messages therefore name the field and the rule,
     * never the content.
     *
     * @param type the event type
     * @param payload the already-sorted payload
     */
    private static void requireTerminalStatusOnRunFinished(
            ProvenanceEventType type, Map<String, String> payload) {
        if (type != ProvenanceEventType.RUN_FINISHED) {
            return;
        }
        String status = payload.get(STATUS_KEY);
        if (status == null) {
            throw new IllegalArgumentException(
                    "a run.finished event must carry its terminal status under the \""
                            + STATUS_KEY
                            + "\" payload key, and this one carries no such key");
        }
        ProvenanceStatus resolved;
        try {
            resolved = ProvenanceStatus.fromWireName(status);
        } catch (IllegalArgumentException notAStatusWireName) {
            throw new IllegalArgumentException(
                    "the \""
                            + STATUS_KEY
                            + "\" payload key of a run.finished event must hold a provenance status"
                            + " wire name, and this one does not");
        }
        if (resolved == ProvenanceStatus.RUNNING) {
            throw new IllegalArgumentException(
                    "a run.finished event must carry a terminal status, and \"running\" is not"
                            + " one");
        }
    }
}

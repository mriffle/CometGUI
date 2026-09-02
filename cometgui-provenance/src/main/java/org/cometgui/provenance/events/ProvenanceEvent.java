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
import java.util.regex.Pattern;
import org.cometgui.provenance.manifest.ProvenanceSchema;
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

    /** The run this event belongs to, on a {@link ProvenanceEventType#RUN_STARTED} event. */
    public static final String RUN_ID_KEY = "run.id";

    /** Which stage started or finished, on a {@code stage.started} or {@code stage.finished}. */
    public static final String STAGE_KEY = "stage";

    /** The name of the tool that was launched, on a {@code tool.invoked} event. */
    public static final String TOOL_KEY = "tool";

    /**
     * The version of that tool, on a {@code tool.invoked} event.
     *
     * <p>Namespaced under {@code tool} rather than a bare {@code version}, because a provenance
     * document already carries several versions -- the schema's, the application's -- and a reader
     * scanning a log line should not have to know which one a bare word meant.
     */
    public static final String TOOL_VERSION_KEY = "tool.version";

    /** The absolute path of the file that was hashed, on a {@code file.hashed} event. */
    public static final String FILE_PATH_KEY = "file.path";

    /** That file's MD5, on a {@code file.hashed} event. */
    public static final String FILE_MD5_KEY = "file.md5";

    /** That file's SHA-256, on a {@code file.hashed} event. */
    public static final String FILE_SHA256_KEY = "file.sha256";

    /** What the run must tell the scientist, on a {@code warning.raised} event. */
    public static final String MESSAGE_KEY = "message";

    /**
     * The shape every payload key must have: {@value}.
     *
     * <p><strong>Why a shape rather than a closed list, and why this shape.</strong> The payload is
     * the second open namespace in the provenance format, and {@code
     * ProvenanceSchema#SETTINGS_KEY_PATTERN} already answers the question for the first one: this
     * phase does not own the semantics later phases will record, so it pins the <em>shape</em> of a
     * key instead of guessing at its name. Without that rule phases 05, 08 and 09 would each write
     * {@code runId}, {@code run_id} and {@code run.id}, all three would be accepted, and a reader
     * looking for one of them would silently miss the other two. With it, a phase either matches
     * the convention or fails a test.
     *
     * <p>The dotted half of this pattern <em>is</em> {@code ProvenanceSchema#SETTINGS_KEY_PATTERN},
     * referenced rather than copied, so that the two namespaces cannot drift apart: every settings
     * key is a legal payload key by construction.
     *
     * <p><strong>The one relaxation: a single bare segment is also legal.</strong> A settings key
     * must have at least two segments because the settings map is one flat dictionary shared by
     * every phase in a run -- Percolator's seed, the Limelight conversion parameters, the result
     * view's filters all live in it, so a bare {@code seed} there would be ambiguous about which
     * tool it belonged to, and the first segment is what disambiguates it. A payload is not that.
     * It is scoped to one event, and that event already carries its {@link ProvenanceEventType}: a
     * {@code stage} key inside a {@code stage.started} event cannot collide with anything, and
     * spelling it {@code stage.name} would only restate the type. So the namespacing requirement is
     * dropped and the anti-drift rule -- lower-case ASCII, digits, dots and hyphens, no underscore,
     * no camel case, no spaces, no empty segment -- is kept in full, which is the half that was
     * doing the work.
     *
     * <p><strong>Each phase pins its own keys as constants beside {@link #STATUS_KEY}</strong>, the
     * way {@code ProvenanceSchema} asks for settings keys, and asserts them against this pattern in
     * its own tests. The nine above are the ones the seven event types imply and are this phase's
     * to fix; a stage's own vocabulary is not.
     */
    public static final String PAYLOAD_KEY_PATTERN =
            "(?:" + ProvenanceSchema.SETTINGS_KEY_PATTERN + ")|[a-z0-9]+";

    /** The compiled form of {@link #PAYLOAD_KEY_PATTERN}, so the rule has one source of truth. */
    private static final Pattern PAYLOAD_KEY = Pattern.compile(PAYLOAD_KEY_PATTERN);

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
     *     payload key does not match {@link #PAYLOAD_KEY_PATTERN}, or if a {@link
     *     ProvenanceEventType#RUN_FINISHED} event does not carry a terminal status under {@link
     *     #STATUS_KEY}
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
            if (!PAYLOAD_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(
                        "a payload key must be lower-case ASCII, either one segment or dotted"
                                + " segments, matching "
                                + PAYLOAD_KEY_PATTERN
                                + ", but was: \""
                                + key
                                + "\"");
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
     * <p><strong>No rejected <em>value</em> is ever quoted in these messages.</strong> The reader
     * builds events out of lines it found in a file, and a damaged or foreign file's bytes are not
     * necessarily ones this application wrote and redacted. A message that echoed the offending
     * value would put unredacted file content into an exception, a log line and a UI, which is the
     * leak the whole package exists to prevent. The messages here therefore name the field and the
     * rule, never the content.
     *
     * <p>A rejected payload <em>key</em> is quoted, and the distinction is the redaction rule set's
     * own: {@code SecretRedactor.redactEnvironment} never redacts a name and always redacts a
     * value, because "a variable whose name I will not tell you was set to a value I will not tell
     * you" records nothing. {@code ProvenanceManifest} quotes a rejected settings key for the same
     * reason. The quoted text is what makes the message useful to the phase author who wrote {@code
     * runId} at a call site, which is where nearly every one of these rejections happens.
     *
     * <p>The other place a rejection can happen is {@link ProvenanceEventLogReader}, reading a file
     * this application did not necessarily write, where the same quotation would be a way for a
     * credential in key position to reach a log or a UI. That is handled where the untrusted text
     * enters rather than by weakening the message here: the reader cleans every quoted fragment
     * through the shared redactor and bounds its length. See that class for the ordering argument.
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

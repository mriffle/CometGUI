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

package org.cometgui.domain.build;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Immutable identity of the running build: application version, source commit and build timestamp.
 *
 * <p><strong>Build-skeleton scaffolding created by phase 01.</strong> It exists so that the
 * coverage, mutation and architecture gates wired up in this phase measure real branching code
 * instead of an empty reactor, and so that later phases have a value to put in the provenance
 * manifest. The properties it parses are the ones Maven resource filtering will produce.
 */
public final class BuildIdentity {

    /** Value recorded for {@code cometgui.commit} when the source commit is not known. */
    public static final String UNKNOWN_COMMIT = "unknown";

    /** Properties key holding the application version, for example {@code 0.1.0-SNAPSHOT}. */
    public static final String VERSION_KEY = "cometgui.version";

    /** Properties key holding the full 40-character git commit id, or {@value #UNKNOWN_COMMIT}. */
    public static final String COMMIT_KEY = "cometgui.commit";

    /** Properties key holding the build timestamp as an ISO-8601 instant. */
    public static final String TIMESTAMP_KEY = "cometgui.buildTimestamp";

    private static final Pattern FULL_COMMIT_ID = Pattern.compile("[0-9a-f]{40}");

    private final String version;
    private final String commitId;
    private final Instant buildTimestamp;

    private BuildIdentity(String version, String commitId, Instant buildTimestamp) {
        this.version = version;
        this.commitId = commitId;
        this.buildTimestamp = buildTimestamp;
    }

    /**
     * Creates a validated build identity.
     *
     * @param version non-blank application version
     * @param commitId exactly 40 lowercase hex characters, or {@value #UNKNOWN_COMMIT}
     * @param buildTimestamp the moment the build ran
     * @return the validated identity
     * @throws IllegalArgumentException if the version is blank or the commit id is neither form
     * @throws NullPointerException if the timestamp is {@code null}
     */
    public static BuildIdentity of(String version, String commitId, Instant buildTimestamp) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("build version must not be blank");
        }
        if (commitId == null
                || !(UNKNOWN_COMMIT.equals(commitId)
                        || FULL_COMMIT_ID.matcher(commitId).matches())) {
            throw new IllegalArgumentException(
                    "build commit id must be 40 lowercase hex characters or \""
                            + UNKNOWN_COMMIT
                            + "\", but was: "
                            + commitId);
        }
        Objects.requireNonNull(buildTimestamp, "buildTimestamp");
        return new BuildIdentity(version.strip(), commitId, buildTimestamp);
    }

    /**
     * Reads a build identity from the properties file produced by the build.
     *
     * @param properties the loaded properties
     * @return the validated identity
     * @throws IllegalArgumentException if a required key is missing or a value is malformed
     * @throws NullPointerException if {@code properties} is {@code null}
     */
    public static BuildIdentity fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        String rawTimestamp = properties.getProperty(TIMESTAMP_KEY);
        if (rawTimestamp == null || rawTimestamp.isBlank()) {
            throw new IllegalArgumentException("missing build property: " + TIMESTAMP_KEY);
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(rawTimestamp.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "build property "
                            + TIMESTAMP_KEY
                            + " is not an ISO-8601 instant: "
                            + rawTimestamp,
                    e);
        }
        return of(
                properties.getProperty(VERSION_KEY), properties.getProperty(COMMIT_KEY), timestamp);
    }

    /**
     * @return the application version, never blank
     */
    public String version() {
        return version;
    }

    /**
     * @return the 40-character commit id, or {@value #UNKNOWN_COMMIT}
     */
    public String commitId() {
        return commitId;
    }

    /**
     * @return the build timestamp
     */
    public Instant buildTimestamp() {
        return buildTimestamp;
    }

    /**
     * @return {@code true} unless the commit id is {@value #UNKNOWN_COMMIT}
     */
    public boolean isCommitKnown() {
        return !UNKNOWN_COMMIT.equals(commitId);
    }

    /*
     * No `this == other` short circuit: it is unobservable, and an unobservable branch is a
     * mutation that no honest test can kill.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BuildIdentity that)) {
            return false;
        }
        return version.equals(that.version)
                && commitId.equals(that.commitId)
                && buildTimestamp.equals(that.buildTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, commitId, buildTimestamp);
    }

    @Override
    public String toString() {
        return "BuildIdentity[version="
                + version
                + ", commit="
                + commitId
                + ", built="
                + buildTimestamp
                + "]";
    }
}

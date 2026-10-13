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

package org.cometgui.workflow.provenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;

/**
 * Hashes every file a sealed record lists again and compares it with the record ({@code P13-7}, the
 * product half).
 *
 * <p>Always through {@link CachingHashService#rehash}, which reads the file whatever the cache
 * holds: a verification that could be answered from a remembered digest would compare the record
 * with the cache, not with the disk.
 */
final class DiskVerifier {

    /** Hashes files again, never from the cache. */
    private final CachingHashService hashes;

    private final DisplayText display;

    DiskVerifier(CachingHashService hashes, DisplayText display) {
        this.hashes = hashes;
        this.display = display;
    }

    /**
     * Verifies every input and output record, every distinct archived log and every distinct tool
     * executable, in that order, stopping between files when asked to.
     */
    DiskVerification verify(ProvenanceManifest manifest, BooleanSupplier cancelled) {
        List<Target> targets = new ArrayList<>();
        for (FileRecord file : manifest.files()) {
            targets.add(
                    new Target(
                            file.direction().wireName(), file.role(), file.path(), file.hashes()));
        }
        Set<Target> logs = new LinkedHashSet<>();
        Set<Target> tools = new LinkedHashSet<>();
        for (ToolRecord tool : manifest.tools()) {
            ExecutionRecord execution = tool.execution();
            String stage = tool.stageId().orElse(tool.name());
            addLog(logs, stage, execution.stdout());
            addLog(logs, stage, execution.stderr());
            tools.add(new Target("tool", tool.name(), tool.executablePath(), tool.hashes()));
        }
        targets.addAll(logs);
        targets.addAll(tools);
        List<DiskVerification.Check> checks = new ArrayList<>();
        for (Target target : targets) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return new DiskVerification(checks, false);
            }
            checks.add(check(target));
        }
        return new DiskVerification(checks, true);
    }

    private static void addLog(Set<Target> logs, String stage, Optional<LogRecord> log) {
        log.ifPresent(
                present -> logs.add(new Target("log", stage, present.path(), present.hashes())));
    }

    private DiskVerification.Check check(Target target) {
        Path path = target.path();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return result(target, DiskOutcome.MISSING, Optional.empty(), Optional.empty());
        }
        FileHashes found;
        try {
            if (!Files.isRegularFile(path)) {
                throw new IOException(path + " is not a regular file that can be read");
            }
            found = hashes.rehash(path);
        } catch (NoSuchFileException vanished) {
            return result(target, DiskOutcome.MISSING, Optional.empty(), Optional.empty());
        } catch (IOException unreadable) {
            return result(
                    target,
                    DiskOutcome.UNREADABLE,
                    Optional.empty(),
                    Optional.of(display.clean(String.valueOf(unreadable.getMessage()))));
        }
        boolean same =
                found.md5().equals(target.hashes().md5())
                        && found.sha256().equals(target.hashes().sha256());
        return result(
                target,
                same ? DiskOutcome.MATCHES : DiskOutcome.DIFFERS,
                Optional.of(found),
                Optional.empty());
    }

    private DiskVerification.Check result(
            Target target,
            DiskOutcome outcome,
            Optional<FileHashes> found,
            Optional<String> problem) {
        return new DiskVerification.Check(
                target.kind(),
                display.clean(target.role()),
                display.path(target.path()),
                target.path(),
                outcome,
                target.hashes().md5(),
                target.hashes().sha256(),
                found.map(FileHashes::md5),
                found.map(FileHashes::sha256),
                problem);
    }

    /** One file to hash again: a value, so that a log or tool listed twice is checked once. */
    private record Target(String kind, String role, Path path, FileHashes hashes) {}
}

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

package org.cometgui.workflow.limelight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.SessionCredentialStore;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Refused;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Submitted;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.tools.limelight.fake.LoopbackGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 12 gate item 7, the model half: "A seeded credential appears in no log, no provenance
 * record and no export; the test greps every artefact the run produced" ({@code R-SEC-03}, {@code
 * AC-LL-06}).
 *
 * <p>A distinctive credential enters through {@link SessionSecrets#admit} and is kept in the
 * session store; a run is converted through the one conversion path; it is uploaded to the fake and
 * submitted; it is uploaded again with the key refused. Then <strong>every file under the test's
 * root</strong> -- the project with its run (logs, events, provenance, outputs, results), the
 * inputs, the converter's cache, and the test's own log and configuration directories -- is found
 * by walking the tree, not from a list, and each file's bytes are searched for the credential in
 * five forms: as UTF-8, Base64 (standard and URL-safe, with and without padding), URL-encoded,
 * UTF-16LE and UTF-16BE. None may match. A control plants the credential, in each form, in one file
 * and requires the search to find exactly that file -- so a search that cannot find anything cannot
 * pass this test.
 */
@EnabledOnOs(value = OS.LINUX, disabledReason = "the run fixtures' paths and modes are POSIX")
class LimelightUploadLeakTest {

    /** The seeded credential: long, distinctive, and found nowhere else on earth. */
    static final String SEEDED = "LLseeded-gate7-Qz8vK2mW4xN6pR0tY3uB5cD7eF9gH1j";

    @TempDir private Path root;

    @AfterEach
    void nothingLeftTheMachine() {
        assertEquals(List.of(), LoopbackGuard.takeCaught());
    }

    @Test
    @DisplayName(
            "gate 7 (model): after convert, upload and a refused upload, no file holds the key")
    void theSeededCredentialIsInNoFile() throws IOException {
        Path logs = Files.createDirectories(root.resolve("application/logs"));
        Path config = Files.createDirectories(root.resolve("application/config"));
        SessionSecrets secrets = new SessionSecrets();
        SessionCredentialStore store = new SessionCredentialStore();
        LimelightUploadsTest.Converted world = LimelightUploadsTest.converted(root);
        try (FakeLimelightServer fake = FakeLimelightServer.start(SEEDED, 7)) {
            store.save(fake.baseUrl(), secrets.admit(SEEDED));
            Credential key = store.find(fake.baseUrl()).orElseThrow();
            LimelightUploads uploads = LimelightUploadsTest.uploads(secrets);

            LimelightUploadAttempt submitted =
                    uploads.upload(
                            uploads.destination(
                                            world.layout(),
                                            world.run().identity(),
                                            world.file(),
                                            fake.baseUrl(),
                                            "7")
                                    .confirm(),
                            key,
                            line -> {});
            fake.refuseKey(true);
            LimelightUploadAttempt refused =
                    uploads.upload(
                            uploads.destination(
                                            world.layout(),
                                            world.run().identity(),
                                            world.file(),
                                            fake.baseUrl(),
                                            "7")
                                    .confirm(),
                            key,
                            line -> {});

            assertInstanceOf(Submitted.class, submitted.outcome());
            assertInstanceOf(Refused.class, refused.outcome());
            assertTrue(fake.rawTraffic().length > 12_000_000, "the key did travel to the fake");
        }

        List<Path> files = everyFile(root);
        Map<Path, String> found = search(files, SEEDED);

        assertEquals(Map.of(), found, "the seeded credential was written to a file");
        List<Path> expected =
                List.of(
                        world.layout().eventLogFile(),
                        world.layout().logsDirectory().resolve("limelight-upload.log"),
                        world.layout().logsDirectory().resolve("limelight-upload.1.log"),
                        world.layout().logsDirectory().resolve("limelight-converter.log"),
                        world.file(),
                        world.layout().provenanceJsonFile());
        for (Path file : expected) {
            assertTrue(files.contains(file), "the walk did not reach " + file);
        }
        assertTrue(files.size() >= 12, "the walk found only " + files.size() + " files");
        assertTrue(Files.isDirectory(logs) && Files.isDirectory(config));
    }

    @Test
    @DisplayName("the search's control: a credential planted in any form is found, in that file")
    void theSearchFindsAPlantedCredential() throws IOException {
        Files.writeString(root.resolve("clean.txt"), "nothing to see");
        for (Map.Entry<String, byte[]> form : forms(SEEDED).entrySet()) {
            Path planted = root.resolve("planted-" + form.getKey() + ".bin");
            byte[] around = "before|".getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[around.length * 2 + form.getValue().length];
            System.arraycopy(around, 0, bytes, 0, around.length);
            System.arraycopy(form.getValue(), 0, bytes, around.length, form.getValue().length);
            System.arraycopy(
                    around, 0, bytes, around.length + form.getValue().length, around.length);
            Files.write(planted, bytes);

            Map<Path, String> found = search(everyFile(root), SEEDED);

            assertEquals(Set.of(planted), found.keySet(), "the " + form.getKey() + " form");
            Files.delete(planted);
        }
        assertEquals(
                List.of(
                        "utf-8",
                        "base64",
                        "base64-unpadded",
                        "base64url",
                        "base64url-unpadded",
                        "url-encoded",
                        "utf-16le",
                        "utf-16be"),
                new ArrayList<>(forms(SEEDED).keySet()));
    }

    /** Every regular file under a directory, found by walking it. */
    static List<Path> everyFile(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /** The files holding the secret in any form, with the first form found in each. */
    static Map<Path, String> search(List<Path> files, String secret) throws IOException {
        Map<String, byte[]> forms = forms(secret);
        Map<Path, String> found = new LinkedHashMap<>();
        for (Path file : files) {
            byte[] bytes = Files.readAllBytes(file);
            for (Map.Entry<String, byte[]> form : forms.entrySet()) {
                if (indexOf(bytes, form.getValue()) >= 0) {
                    found.put(file, form.getKey());
                    break;
                }
            }
        }
        return found;
    }

    /** The secret in each form it could be written in. */
    static Map<String, byte[]> forms(String secret) {
        byte[] utf8 = secret.getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> forms = new LinkedHashMap<>();
        forms.put("utf-8", utf8);
        forms.put("base64", Base64.getEncoder().encode(utf8));
        forms.put("base64-unpadded", Base64.getEncoder().withoutPadding().encode(utf8));
        forms.put("base64url", Base64.getUrlEncoder().encode(utf8));
        forms.put("base64url-unpadded", Base64.getUrlEncoder().withoutPadding().encode(utf8));
        forms.put(
                "url-encoded",
                URLEncoder.encode(secret, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        forms.put("utf-16le", secret.getBytes(StandardCharsets.UTF_16LE));
        forms.put("utf-16be", secret.getBytes(StandardCharsets.UTF_16BE));
        return forms;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start <= haystack.length - needle.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }
}

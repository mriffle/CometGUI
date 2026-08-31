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

package org.cometgui.tools.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The property that matters: a registered secret appears <em>nowhere</em> in anything the process
 * service will show or record.
 *
 * <h2>What is set up, and why each part of it is there</h2>
 *
 * <p>One command carrying the same credential in the three places it can realistically appear:
 *
 * <ul>
 *   <li>as an argument on the command line, which is what {@code --api-token} looks like;
 *   <li>as the value of a secret-named environment variable, which the name rule catches without
 *       knowing the value;
 *   <li>as part of the value of an <em>innocuously</em>-named environment variable, which only the
 *       registered value can catch. This is the case that fails if either half of the design is
 *       dropped.
 * </ul>
 *
 * <h2>Absence of the whole thing is not absence of the thing</h2>
 *
 * <p>The assertions here are not {@code assertFalse(output.contains(secret))}. Phase 04 shipped a
 * sweep that asserted exactly that and passed while 99% of a private key was still in the file,
 * because one character inside it had been rewritten and the literal no longer matched. So every
 * rendering is additionally scanned by {@link SecretScan}, which slides a four-character window
 * over the secret and looks for every window; the window length and the reasoning behind it are
 * documented there.
 *
 * <p><strong>And the scanner is itself proved to fire before it is trusted.</strong> {@link
 * #theScannerSeesALeakThatTheNaiveAssertionMisses()} builds the phase 04 failure on purpose -- the
 * secret with one character rewritten, sitting in the output -- and requires that {@code contains}
 * says everything is fine while the scan reports 36 surviving fragments. A detector nobody has
 * watched detect anything is the vacuous gate this project keeps finding.
 *
 * <h2>Why the secret looks like that</h2>
 *
 * <p>{@link #SECRET} alternates digits and lower-case letters, so every four-character window of it
 * contains two digits and cannot occur inside the paths, flag names and placeholders that make up
 * the rest of the rendered output. A hit is therefore a leak and never a coincidence. That is a
 * property of the test data, chosen on purpose.
 */
class SecretRedactionPropertyTest {

    /** Forty characters of alternating digit and lower-case letter. See the class documentation. */
    private static final String SECRET = "7a3f9c2e8b4d6a1f0c5e7b9d3a2f8c4e6b1d0a5f";

    private static final ToolCommand UPLOAD =
            new ToolCommand(
                    List.of(
                            "/opt/limelight/uploader",
                            "--api-token",
                            SECRET,
                            "--project",
                            "PXD000001"),
                    Path.of("run").toAbsolutePath(),
                    environment());

    private static final ProcessRedactor REDACTOR = ProcessRedactor.with(SecretRegistry.of(SECRET));

    private static Map<String, String> environment() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("LIMELIGHT_API_TOKEN", SECRET);
        variables.put("UPLOAD_TARGET", "https://limelight.example/upload?key=" + SECRET);
        variables.put("PATH", "/opt/tools/bin");
        return variables;
    }

    @Test
    @DisplayName(
            "the rendered command is exactly this, and no fragment of the secret survives in"
                    + " it")
    void theDisplayCommandLeaksNothing() {
        String rendered = REDACTOR.redactedDisplayCommand(UPLOAD);

        assertEquals(
                "[\"/opt/limelight/uploader\", \"--api-token\", \"[REDACTED]\","
                        + " \"--project\", \"PXD000001\"]",
                rendered);
        assertFalse(rendered.contains(SECRET));
        SecretScan.assertNothingOfTheSecretSurvives(SECRET, rendered, "the display command");
    }

    @Test
    @DisplayName(
            "the captured environment is exactly this, and no fragment of the secret survives"
                    + " in any key or value")
    void theEnvironmentLeaksNothing() {
        Map<String, String> redacted = REDACTOR.redactedEnvironment(UPLOAD.environment());

        assertEquals(
                Map.of(
                        "LIMELIGHT_API_TOKEN",
                        "[REDACTED]",
                        "UPLOAD_TARGET",
                        "https://limelight.example/upload?key=[REDACTED]",
                        "PATH",
                        "/opt/tools/bin"),
                redacted);

        for (Map.Entry<String, String> variable : redacted.entrySet()) {
            SecretScan.assertNothingOfTheSecretSurvives(
                    SECRET, variable.getKey(), "the environment variable name");
            SecretScan.assertNothingOfTheSecretSurvives(
                    SECRET,
                    variable.getValue(),
                    "the value of the environment variable " + variable.getKey());
        }
    }

    @Test
    @DisplayName(
            "the two halves of the design are both load-bearing: the name rule alone would"
                    + " leave the secret in UPLOAD_TARGET")
    void bothHalvesAreNeeded() {
        Map<String, String> withoutTheRegistry =
                ProcessRedactor.with(SecretRegistry.empty())
                        .redactedEnvironment(UPLOAD.environment());

        assertEquals("[REDACTED]", withoutTheRegistry.get("LIMELIGHT_API_TOKEN"));
        assertEquals(
                "https://limelight.example/upload?key=" + SECRET,
                withoutTheRegistry.get("UPLOAD_TARGET"),
                "with no registered value the innocuously-named variable keeps the token, which"
                        + " is exactly why phase 12 must register it");
        assertEquals(
                "[\"/opt/limelight/uploader\", \"--api-token\", \""
                        + SECRET
                        + "\", \"--project\", \"PXD000001\"]",
                ProcessRedactor.with(SecretRegistry.empty()).redactedDisplayCommand(UPLOAD),
                "and a bare token on the command line behind a flag the shared list does not"
                        + " know is kept too: the registry is the half that catches it");
    }

    @Test
    @DisplayName(
            "the scanner sees a leak that assertFalse(contains(secret)) misses -- the phase"
                    + " 04 failure, reproduced")
    void theScannerSeesALeakThatTheNaiveAssertionMisses() {
        String rewrittenLastCharacter = SECRET.substring(0, 39) + "g";
        String leaky =
                "[\"/opt/limelight/uploader\", \"--api-token\", \""
                        + rewrittenLastCharacter
                        + "\"]";

        assertFalse(
                leaky.contains(SECRET),
                "39 of the 40 characters are present, yet the naive assertion is satisfied; this"
                        + " is how a private key leaked past a green test suite");
        assertEquals(
                36,
                SecretScan.survivingFragments(SECRET, leaky).size(),
                "rewriting the last character destroys exactly the one window that ends on it");
        assertEquals("7a3f", SecretScan.survivingFragments(SECRET, leaky).get(0));
        assertEquals("0a5f", SECRET.substring(36));
    }

    @Test
    @DisplayName("a character rewritten in the middle destroys four windows and no more")
    void aRewriteInTheMiddleIsAlsoSeen() {
        String rewrittenMiddle = SECRET.substring(0, 20) + "z" + SECRET.substring(21);
        String leaky = "PATH=/opt/" + rewrittenMiddle + "/bin";

        assertFalse(leaky.contains(SECRET));
        assertEquals(33, SecretScan.survivingFragments(SECRET, leaky).size());
    }

    @Test
    @DisplayName("the scan really examines 37 windows, so a clean result is not a vacuous one")
    void theScanIsNotVacuous() {
        assertEquals(40, SECRET.length());
        assertEquals(4, SecretScan.FRAGMENT_LENGTH);
        assertEquals(37, SecretScan.survivingFragments(SECRET, SECRET).size());
        assertTrue(
                SecretScan.survivingFragments(SECRET, "nothing here").isEmpty(),
                "and it finds nothing when there is nothing to find");
    }
}

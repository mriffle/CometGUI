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

import java.util.ArrayList;
import java.util.List;

/**
 * Looks for <em>any part</em> of a secret in a piece of output, not just the whole of it.
 *
 * <h2>Why this exists</h2>
 *
 * <p><strong>Absence of the whole thing is not absence of the thing.</strong> Phase 04 shipped a
 * secret sweep asserting {@code assertFalse(output.contains(secret))}, and it passed while 99% of a
 * private key was still in the file: one character inside the key had been rewritten by an earlier
 * rule, so the literal no longer matched and the assertion saw nothing. An assertion that can only
 * see a byte-perfect copy of a credential is not a test that the credential was removed.
 *
 * <p>So this slides a window over the secret and looks for every window. A leak of even a short run
 * of consecutive characters is found, whatever happened to the rest of the value.
 *
 * <h2>Why the window is four characters</h2>
 *
 * <p>{@link #FRAGMENT_LENGTH} is 4, and the number is a trade-off between the two ways this check
 * can be useless.
 *
 * <ul>
 *   <li><strong>Too long and it stops seeing leaks.</strong> A 20-character window would have
 *       missed nothing in the phase 04 case, but it cannot see a leak of a short prefix, and a
 *       prefix is what a truncating or off-by-one redaction leaves behind.
 *   <li><strong>Too short and it fires on coincidence.</strong> A one- or two-character window
 *       matches ordinary text constantly, and a check that always fails is deleted rather than
 *       fixed.
 * </ul>
 *
 * <p>Four is short enough to catch the smallest leak worth calling a leak, and long enough that
 * coincidence is not a practical concern for the credentials these tests use: every test secret
 * here is built from alternating digits and lower-case letters, so every window of length four
 * contains two digits and cannot occur inside the alphabetic paths, flag names and placeholders
 * that make up the rest of the rendered output. That property is a deliberate choice about the test
 * data, not luck, and it is what lets a hit be read as a leak rather than as noise.
 *
 * <p>A scan that examines no windows would pass everything, which is the vacuous-gate shape this
 * project keeps finding. {@link #survivingFragments(String, String)} therefore refuses a secret too
 * short to have a window at all, and {@code SecretRedactionPropertyTest} proves the scanner
 * actually fires on a secret with one character rewritten before trusting it to prove anything.
 */
final class SecretScan {

    /** The length of the window slid over the secret. See the class documentation. */
    static final int FRAGMENT_LENGTH = 4;

    private SecretScan() {}

    /**
     * Every {@link #FRAGMENT_LENGTH}-character window of {@code secret} that occurs in {@code
     * haystack}, in the order the windows are taken.
     *
     * @param secret the credential that must not have survived
     * @param haystack the output to search
     * @return the surviving fragments; empty when nothing of the secret is present
     * @throws IllegalArgumentException if the secret is too short to have a single window, which
     *     would make every scan of it vacuously clean
     */
    static List<String> survivingFragments(String secret, String haystack) {
        if (secret.length() < FRAGMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "a secret of "
                            + secret.length()
                            + " characters has no window of "
                            + FRAGMENT_LENGTH
                            + ", so scanning it would prove nothing");
        }
        List<String> surviving = new ArrayList<>();
        for (int start = 0; start + FRAGMENT_LENGTH <= secret.length(); start++) {
            String fragment = secret.substring(start, start + FRAGMENT_LENGTH);
            if (haystack.contains(fragment)) {
                surviving.add(fragment);
            }
        }
        return List.copyOf(surviving);
    }

    /**
     * Fails unless no window of the secret occurs anywhere in the output.
     *
     * @param secret the credential that must not have survived
     * @param haystack the output to search
     * @param what names the output, so a failure says which of several renderings leaked
     */
    static void assertNothingOfTheSecretSurvives(String secret, String haystack, String what) {
        assertEquals(
                List.of(),
                survivingFragments(secret, haystack),
                () ->
                        what
                                + " still contains fragments of the secret, so it leaked even"
                                + " though the whole value is not present; the output was: "
                                + haystack);
    }
}

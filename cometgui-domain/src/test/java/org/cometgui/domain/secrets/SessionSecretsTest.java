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

package org.cometgui.domain.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SessionSecrets}, {@link Credential} and {@link SessionCredentialStore}: a
 * credential cannot exist without being registered, and nothing prints it. Every expected string is
 * typed by hand.
 */
class SessionSecretsTest {

    /** A seeded submit key, 34 characters. */
    private static final String KEY = "LLseeded-0123456789abcdefABCDEF-zz";

    @Test
    @DisplayName("an admitted credential is redacted by the session's redactor from then on")
    void admittingRegisters() {
        SessionSecrets secrets = new SessionSecrets();
        SecretRedactor before = secrets.redactor();

        Credential credential = secrets.admit(KEY);

        assertEquals(KEY, credential.reveal());
        assertEquals(1, secrets.size());
        assertEquals(
                "the key is [REDACTED] here",
                secrets.redactor().redactText("the key is " + KEY + " here"));
        assertEquals(
                "the key is " + KEY + " here",
                before.redactText("the key is " + KEY + " here"),
                "a redactor taken before the admission is unchanged");
    }

    @Test
    @DisplayName("admitting the same value twice holds it once")
    void duplicatesCollapse() {
        SessionSecrets secrets = new SessionSecrets();
        Credential first = secrets.admit(KEY);
        Credential second = secrets.admit(KEY);

        assertEquals(1, secrets.size());
        assertNotSame(first, second);
        assertFalse(first.equals(second), "equality is identity, never the value");
    }

    @Test
    @DisplayName(
            "too short, blank or holding a control character: refused, and no message names"
                    + " the value")
    void refusals() {
        SessionSecrets secrets = new SessionSecrets();

        SecretTooShortException shortOne =
                assertThrows(SecretTooShortException.class, () -> secrets.admit("abc1234"));
        assertFalse(shortOne.getMessage().contains("abc1234"));
        IllegalArgumentException blank =
                assertThrows(IllegalArgumentException.class, () -> secrets.admit("          "));
        assertEquals("a registered secret must not consist only of whitespace", blank.getMessage());
        IllegalArgumentException newline =
                assertThrows(
                        IllegalArgumentException.class, () -> secrets.admit("abcdefgh\nijklmn"));
        assertEquals(
                "a credential must not contain a control character (one was found at position 8"
                        + " of 15)",
                newline.getMessage());
        assertThrows(IllegalArgumentException.class, () -> secrets.admit("\u0000bcdefghijk"));
        assertThrows(IllegalArgumentException.class, () -> secrets.admit("abcdefghij\u007f"));
        assertThrows(NullPointerException.class, () -> secrets.admit(Nulls.of(String.class)));
        assertEquals(0, secrets.size(), "nothing refused was registered");
    }

    @Test
    @DisplayName(
            "neither the credential, the session nor the store prints a character of the value")
    void nothingPrintsTheValue() {
        SessionSecrets secrets = new SessionSecrets();
        Credential credential = secrets.admit(KEY);
        SessionCredentialStore store = new SessionCredentialStore();
        store.save("https://limelight.example/limelight", credential);

        assertEquals("Credential[redacted]", credential.toString());
        assertEquals("SessionSecrets[secretCount=1]", secrets.toString());
        assertEquals("SessionCredentialStore[accounts=1]", store.toString());
    }

    @Test
    @DisplayName("the session store keeps one credential per account, in memory only")
    void theSessionStore() {
        SessionSecrets secrets = new SessionSecrets();
        SessionCredentialStore store = new SessionCredentialStore();
        Credential one = secrets.admit(KEY);
        Credential two = secrets.admit("another-seeded-key-0001");

        assertFalse(store.persistent());
        assertEquals(
                "this session only (held in memory, forgotten when CometGUI closes)",
                store.description());
        assertEquals(Optional.empty(), store.find("https://a.example/limelight"));
        store.save("https://a.example/limelight", one);
        store.save("https://b.example/limelight", two);
        assertSame(one, store.find("https://a.example/limelight").orElseThrow());
        assertSame(two, store.find("https://b.example/limelight").orElseThrow());
        store.save("https://a.example/limelight", two);
        assertSame(two, store.find("https://a.example/limelight").orElseThrow());
        store.forget("https://a.example/limelight");
        assertEquals(Optional.empty(), store.find("https://a.example/limelight"));
        assertTrue(store.find("https://b.example/limelight").isPresent());

        assertThrows(IllegalArgumentException.class, () -> store.find(" "));
        assertThrows(NullPointerException.class, () -> store.save("x", Nulls.of(Credential.class)));
        assertThrows(NullPointerException.class, () -> store.forget(Nulls.of(String.class)));
    }
}

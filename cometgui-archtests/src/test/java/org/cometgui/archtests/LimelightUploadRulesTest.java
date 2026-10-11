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

package org.cometgui.archtests;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.util.Properties;
import org.cometgui.archtests.fixtures.OwnProxySelector;
import org.cometgui.archtests.fixtures.SetsAProxy;
import org.cometgui.archtests.fixtures.StartsAnUploadFromTheEngine;
import org.cometgui.archtests.fixtures.UploadsOutsideTheService;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.tools.limelight.LimelightUploadClient;
import org.cometgui.tools.limelight.LimelightUploadListener;
import org.cometgui.tools.limelight.LimelightUploadRequest;
import org.cometgui.workflow.limelight.LimelightUploadConfirmation;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;
import org.cometgui.workflow.limelight.LimelightUploads;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Upload is the only outward-facing action in the product ({@code R-SEC-04}), and the test guard
 * against a real server is a proxy every test JVM routes through (phase 12, design decisions
 * P12-10, P12-11, P12-14 and P12-16). Four rules, each graded against the product and against a
 * committed fixture that must be rejected, with the same rule object:
 *
 * <ol>
 *   <li>only {@code org.cometgui.workflow.limelight} -- the upload service, which shows the
 *       destination first and records every upload -- calls the upload client;
 *   <li>only the service itself and the interface ({@code org.cometgui.ui}, {@code
 *       org.cometgui.app}) start an upload: nothing in the run engine can;
 *   <li>no product class sets, replaces or bypasses a proxy, so the guard cannot be routed around;
 *   <li>the one product class that passes a proxy selector to an HTTP client is the upload client,
 *       and it passes the JVM's default.
 * </ol>
 */
class LimelightUploadRulesTest {

    /** Rule 1. */
    static final ArchRule ONLY_THE_SERVICE_USES_THE_CLIENT =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("org.cometgui.workflow.limelight..")
                    .should()
                    .callMethod(
                            LimelightUploadClient.class,
                            "upload",
                            LimelightUploadRequest.class,
                            Credential.class,
                            LimelightUploadListener.class)
                    .because(
                            "R-SEC-04 and P12-10: an upload is sent only after its destination was"
                                    + " shown and confirmed, and is always logged and recorded;"
                                    + " org.cometgui.workflow.limelight's LimelightUploads does"
                                    + " both");

    /** Rule 2. */
    static final ArchRule ONLY_THE_INTERFACE_STARTS_AN_UPLOAD =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "org.cometgui.workflow.limelight..",
                            "org.cometgui.ui..",
                            "org.cometgui.app..")
                    .should()
                    .callMethod(
                            LimelightUploads.class,
                            "upload",
                            LimelightUploadConfirmation.class,
                            Credential.class,
                            LimelightUploadWatcher.class)
                    .because(
                            "R-SEC-04: an upload requires an explicit user action every time and"
                                    + " is never triggered automatically as part of a run");

    /** Rule 3. */
    static final ArchRule NO_PRODUCT_CLASS_CHOOSES_A_PROXY =
            noClasses()
                    .should()
                    .callMethod(System.class, "setProperty", String.class, String.class)
                    .orShould()
                    .callMethod(System.class, "setProperties", Properties.class)
                    .orShould()
                    .callMethod(System.class, "clearProperty", String.class)
                    .orShould()
                    .callMethod(ProxySelector.class, "setDefault", ProxySelector.class)
                    .orShould()
                    .callMethod(ProxySelector.class, "of", InetSocketAddress.class)
                    .orShould()
                    .accessField(HttpClient.Builder.class, "NO_PROXY")
                    .orShould()
                    .accessField(Proxy.class, "NO_PROXY")
                    .orShould()
                    .callConstructor(Proxy.class, Proxy.Type.class, java.net.SocketAddress.class)
                    .orShould()
                    .beAssignableTo(ProxySelector.class)
                    .because(
                            "P12-16: every test JVM that can upload routes HTTP(S) through a"
                                    + " loopback sinkhole proxy set in its system properties;"
                                    + " product code that set a property or chose its own proxy"
                                    + " could reach a real Limelight server from a test");

    /** Rule 4, its first half: nothing else hands an HTTP client a proxy selector. */
    static final ArchRule ONLY_THE_UPLOAD_CLIENT_SETS_A_SELECTOR =
            noClasses()
                    .that()
                    .doNotHaveFullyQualifiedName(LimelightUploadClient.class.getName())
                    .should()
                    .callMethod(HttpClient.Builder.class, "proxy", ProxySelector.class)
                    .because("P12-16: the upload client's selector is the JVM's own, and no other");

    /** Rule 4, its second half: the upload client takes the JVM's default. */
    static final ArchRule THE_UPLOAD_CLIENT_TAKES_THE_DEFAULT_SELECTOR =
            classes()
                    .that()
                    .haveFullyQualifiedName(LimelightUploadClient.class.getName())
                    .should()
                    .callMethod(ProxySelector.class, "getDefault")
                    .andShould()
                    .callMethod(HttpClient.Builder.class, "proxy", ProxySelector.class)
                    .because(
                            "P12-14: the client honours the JVM's default proxy selector,"
                                    + " explicitly, so that the test guard catches it");

    @Test
    @DisplayName("the product keeps all four rules")
    void theProductKeepsTheRules() {
        JavaClasses product = ProductClasses.all();
        ONLY_THE_SERVICE_USES_THE_CLIENT.check(product);
        ONLY_THE_INTERFACE_STARTS_AN_UPLOAD.check(product);
        NO_PRODUCT_CLASS_CHOOSES_A_PROXY.check(product);
        ONLY_THE_UPLOAD_CLIENT_SETS_A_SELECTOR.check(product);
        THE_UPLOAD_CLIENT_TAKES_THE_DEFAULT_SELECTOR.check(product);
    }

    @Test
    @DisplayName("rule 1 rejects a direct use of the upload client, naming it")
    void rule1RejectsAShortcut() {
        String message = rejected(ONLY_THE_SERVICE_USES_THE_CLIENT, UploadsOutsideTheService.class);

        assertTrue(message.contains("R-SEC-04 and P12-10"), message);
        assertTrue(
                message.contains(
                        "org.cometgui.archtests.fixtures.UploadsOutsideTheService.shortcut("),
                message);
        assertTrue(message.contains("LimelightUploadClient.upload("), message);
    }

    @Test
    @DisplayName("rule 2 rejects an upload started outside the interface and the service")
    void rule2RejectsAnAutomaticUpload() {
        String message =
                rejected(ONLY_THE_INTERFACE_STARTS_AN_UPLOAD, StartsAnUploadFromTheEngine.class);

        assertTrue(message.contains("never triggered automatically"), message);
        assertTrue(message.contains("StartsAnUploadFromTheEngine.automatic("), message);
    }

    @Test
    @DisplayName("rule 3 rejects every way of choosing a proxy, each by name")
    void rule3RejectsEveryProxyShortcut() {
        String message =
                rejected(
                        NO_PRODUCT_CLASS_CHOOSES_A_PROXY, SetsAProxy.class, OwnProxySelector.class);

        assertTrue(message.contains("P12-16"), message);
        assertTrue(message.contains("SetsAProxy.property()"), message);
        assertTrue(message.contains("System.setProperty("), message);
        assertTrue(message.contains("ProxySelector.setDefault("), message);
        assertTrue(message.contains("ProxySelector.of("), message);
        assertTrue(message.contains("HttpClient$Builder.NO_PROXY"), message);
        assertTrue(message.contains("java.net.Proxy.NO_PROXY"), message);
        assertTrue(message.contains("java.net.Proxy.<init>("), message);
        assertTrue(message.contains("OwnProxySelector"), message);
    }

    @Test
    @DisplayName("rule 4 rejects a selector given anywhere else, and a client without one")
    void rule4RejectsAnotherSelector() {
        String message = rejected(ONLY_THE_UPLOAD_CLIENT_SETS_A_SELECTOR, SetsAProxy.class);
        assertTrue(message.contains("SetsAProxy.noProxy()"), message);

        JavaClasses imported = new ClassFileImporter().importClasses(SetsAProxy.class);
        assertEquals(1, imported.size());
        THE_UPLOAD_CLIENT_TAKES_THE_DEFAULT_SELECTOR.allowEmptyShould(true).check(imported);
        assertThrows(
                AssertionError.class,
                () ->
                        classes()
                                .that()
                                .haveFullyQualifiedName(SetsAProxy.class.getName())
                                .should()
                                .callMethod(ProxySelector.class, "getDefault")
                                .check(imported));
    }

    private static String rejected(ArchRule rule, Class<?>... fixtures) {
        JavaClasses imported = new ClassFileImporter().importClasses(fixtures);
        assertEquals(fixtures.length, imported.size());
        return assertThrows(AssertionError.class, () -> rule.check(imported)).getMessage();
    }
}

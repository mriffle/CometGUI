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

package org.cometgui.tools.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * The main attributes of a JAR's own {@code META-INF/MANIFEST.MF}.
 *
 * <p>What the build stamped into the artefact, read from the artefact. This is not metadata about
 * the download and it is not a manifest claim: it is a field inside the bytes whose SHA-256 the
 * installer verified at install step 2, four steps before anything is probed.
 *
 * <p><strong>Why a JAR tool's identity is read here rather than asked of the program.</strong> It
 * was executed on this project's Debian 12 host on 2026-09-03: {@code java -jar PDV-2.7.0.jar} with
 * {@code -h}, {@code -v}, {@code -V} and {@code --version} each exits <strong>1</strong> with
 * {@code java.awt.HeadlessException} thrown from {@code PDVCLI.PDVCLIMainClass.<init>} line 203,
 * because PDV constructs a {@code JFrame} before it reads its first argument -- and its usage text,
 * quoted in {@code docs/feasibility/pdv-converter-spike.rst}, lists no version option at all. So
 * there is no argument, on any host, that makes PDV print its version, and a probe that insisted on
 * one would report a working PDV as unidentifiable. Its manifest carries {@code
 * Implementation-Version: 2.7.0}.
 */
public final class JarAttributes {

    private final Map<String, String> attributes;

    private JarAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * Reads the main attributes of a JAR.
     *
     * @param jar the JAR file
     * @return its main attributes, in the order the manifest lists them
     * @throws IOException if the file is not a readable JAR, or carries no manifest at all -- both
     *     of which are refusals rather than an empty attribute set, because "this is not a JAR" and
     *     "this JAR says nothing about itself" are different facts and only the second is evidence
     * @throws NullPointerException if {@code jar} is {@code null}
     */
    public static JarAttributes of(Path jar) throws IOException {
        Objects.requireNonNull(jar, "jar");
        if (!Files.isRegularFile(jar)) {
            throw new IOException(jar + " is not a regular file, so it cannot be read as a JAR");
        }
        try (JarFile archive = open(jar)) {
            Manifest manifest = archive.getManifest();
            if (manifest == null) {
                throw new IOException(
                        jar
                                + " is a ZIP container with no META-INF/MANIFEST.MF, so it is not a"
                                + " JAR this product can identify");
            }
            return new JarAttributes(mainAttributesOf(manifest));
        }
    }

    /*
     * The JDK's own refusal names nothing: a file that is not a ZIP comes back as "zip END header
     * not found", which tells a reader what went wrong and not what it went wrong ON.  A probe is
     * pointed at a file the user chose, so the path is the whole point of the message.
     */
    private static JarFile open(Path jar) throws IOException {
        try {
            return new JarFile(jar.toFile());
        } catch (IOException notAnArchive) {
            throw new IOException(
                    jar
                            + " cannot be read as a JAR: "
                            + notAnArchive.getMessage()
                            + " ("
                            + notAnArchive.getClass().getName()
                            + ")",
                    notAnArchive);
        }
    }

    private static Map<String, String> mainAttributesOf(Manifest manifest) {
        Map<String, String> read = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : manifest.getMainAttributes().entrySet()) {
            read.put(
                    ((Attributes.Name) entry.getKey()).toString(),
                    String.valueOf(entry.getValue()));
        }
        return Map.copyOf(read);
    }

    /**
     * One attribute.
     *
     * @param name the attribute name, matched exactly -- {@code Implementation-Version}, not {@code
     *     implementation-version}: guessing what a manifest meant is how a reader and a writer
     *     drift apart
     * @return its value, or empty if the manifest does not carry it or carries it blank
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public Optional<String> value(String name) {
        Objects.requireNonNull(name, "name");
        String found = attributes.get(name);
        if (found == null || found.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(found.strip());
    }

    /**
     * Every main attribute, by name.
     *
     * @return the attributes, immutable
     */
    public Map<String, String> all() {
        return attributes;
    }
}

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

package org.cometgui.install.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-typed {@code tools.json} documents for the reader's tests, and the one knob each test turns.
 *
 * <p>Every document these tests read is written here as literal JSON rather than produced by
 * anything in {@code org.cometgui.install.registry}. A fixture rendered by the code under test
 * cannot fail: it would agree with the reader about any mistake the reader makes, which is the
 * whole class of defect these tests exist to find.
 *
 * <p>The builder exists so that one rejection can be graded over the axes the rule it tests does
 * <em>not</em> depend on. A record is built for a named tool, version, platform and kind, and the
 * test then breaks exactly one member of it -- so "a missing {@code sha256} is rejected" can be
 * asserted for a Comet bare executable on Windows and for a Percolator zip on Linux, not only for
 * whichever one happened to be typed first. Phase 05's unit 1 was sent back for exactly that gap.
 */
final class ManifestDocuments {

    /** The real SHA-256 of the Percolator 3.07.1 Linux portable zip. */
    static final String ARTEFACT_SHA256 =
            "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    /** The real MD5 of the same file. */
    static final String ARTEFACT_MD5 = "9c86de1c45d2d93dae1ab43216b5864c";

    /** The real SHA-256 of the {@code percolator} member inside it. */
    static final String MEMBER_SHA256 =
            "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c";

    /** The real MD5 of that member. */
    static final String MEMBER_MD5 = "0b77b68fd859639d7421f1c5e006ade5";

    private ManifestDocuments() {}

    /**
     * Wraps records in a schema-version-1 document.
     *
     * @param records the rendered artefact records
     * @return the whole document
     */
    static String document(String... records) {
        return "{\"schemaVersion\": 1, \"artefacts\": [" + String.join(", ", records) + "]}";
    }

    /**
     * A valid named-member record: the manifest names one member of an archive and where it goes.
     *
     * @param tool the tool identifier
     * @param version the version text
     * @param releaseTag the upstream release tag
     * @param os the operating-system identifier
     * @param arch the architecture identifier
     * @param kind the artefact-kind identifier
     * @return the builder, ready to be broken in one place
     */
    static Json namedMember(
            String tool, String version, String releaseTag, String os, String arch, String kind) {
        return common(tool, version, releaseTag, os, arch, kind)
                .str("member", "percolator")
                .num("memberSizeBytes", 2538632)
                .str("memberSha256", MEMBER_SHA256)
                .str("memberMd5", MEMBER_MD5)
                .str("installedPath", "bin/percolator");
    }

    /**
     * A valid whole-artefact record: the whole download is unpacked and the executable is expected
     * at a declared path.
     *
     * @param tool the tool identifier
     * @param version the version text
     * @param releaseTag the upstream release tag
     * @param os the operating-system identifier
     * @param arch the architecture identifier
     * @param kind the artefact-kind identifier
     * @return the builder, ready to be broken in one place
     */
    static Json wholeArtefact(
            String tool, String version, String releaseTag, String os, String arch, String kind) {
        return common(tool, version, releaseTag, os, arch, kind)
                .str("expectedExecutablePath", "bin/tool");
    }

    private static Json common(
            String tool, String version, String releaseTag, String os, String arch, String kind) {
        return new Json()
                .str("tool", tool)
                .str("version", version)
                .str("releaseTag", releaseTag)
                .str("os", os)
                .str("arch", arch)
                .str("kind", kind)
                .str("url", "https://github.com/example/example/releases/download/t/artefact")
                .num("sizeBytes", 946303)
                .str("sha256", ARTEFACT_SHA256)
                .str("md5", ARTEFACT_MD5)
                .bool("executable", true)
                .raw("licence", licence())
                .raw("companions", "[]")
                .raw("capabilities", "[]")
                .raw("advisories", "[]")
                .raw("minimumHostRequirements", requirements())
                .str("minimumCometGuiVersion", "0.1.0");
    }

    /**
     * A valid licence object.
     *
     * @return the rendered object
     */
    static String licence() {
        return new Json()
                .str("spdx", "Apache-2.0")
                .str("url", "https://raw.githubusercontent.com/example/example/t/LICENSE")
                .str("note", "upstream LICENSE at tag t is the Apache License 2.0")
                .render();
    }

    /**
     * A host-requirements object that declares nothing.
     *
     * @return the rendered object
     */
    static String requirements() {
        return new Json()
                .raw("glibc", "null")
                .raw("glibcxx", "null")
                .raw("macos", "null")
                .raw("requiredHostLibraries", "[]")
                .render();
    }

    /**
     * A declared capability.
     *
     * @param capability the capability identifier
     * @param evidence the evidence identifier
     * @return the builder, ready to be broken in one place
     */
    static Json capability(String capability, String evidence) {
        return new Json()
                .str("capability", capability)
                .str("evidence", evidence)
                .str("note", "run on linux-x86-64 by phase 00");
    }

    /**
     * A companion that takes named files out of a package payload.
     *
     * @param id the companion identifier
     * @param kind the artefact-kind identifier
     * @return the builder, ready to be broken in one place
     */
    static Json payloadCompanion(String id, String kind) {
        return new Json()
                .str("id", id)
                .str("kind", kind)
                .str("url", "https://github.com/example/example/releases/download/t/payload")
                .num("sizeBytes", 1852660)
                .str("sha256", ARTEFACT_SHA256)
                .str("md5", ARTEFACT_MD5)
                .bool("runtimePrerequisite", false)
                .raw("gatesCapability", "null")
                .str("note", "the two schemas no portable archive ships")
                .raw(
                        "members",
                        array(
                                companionMember(
                                                "usr/share/xml/percolator/percolator_out.xsd",
                                                "share/percolator_out.xsd")
                                        .render()));
    }

    /**
     * One member of a companion.
     *
     * @param path the member's name inside the payload
     * @param installedPath where it is installed
     * @return the builder, ready to be broken in one place
     */
    static Json companionMember(String path, String installedPath) {
        return new Json()
                .str("path", path)
                .num("sizeBytes", 10388)
                .str("sha256", MEMBER_SHA256)
                .str("md5", MEMBER_MD5)
                .str("installedPath", installedPath);
    }

    /**
     * Renders a JSON array of already-rendered elements.
     *
     * @param elements the rendered elements
     * @return the rendered array
     */
    static String array(String... elements) {
        return "[" + String.join(", ", elements) + "]";
    }

    /** An ordered JSON object built member by member, so a test can break exactly one. */
    static final class Json {

        private final Map<String, String> members = new LinkedHashMap<>();

        /**
         * Sets a member to a raw JSON fragment.
         *
         * @param name the member name
         * @param rawJson the fragment
         * @return this builder
         */
        Json raw(String name, String rawJson) {
            members.put(name, rawJson);
            return this;
        }

        /**
         * Sets a string member.
         *
         * @param name the member name
         * @param value the text, escaped here
         * @return this builder
         */
        Json str(String name, String value) {
            return raw(name, quote(value));
        }

        /**
         * Sets a number member.
         *
         * @param name the member name
         * @param value the number
         * @return this builder
         */
        Json num(String name, long value) {
            return raw(name, Long.toString(value));
        }

        /**
         * Sets a boolean member.
         *
         * @param name the member name
         * @param value the flag
         * @return this builder
         */
        Json bool(String name, boolean value) {
            return raw(name, Boolean.toString(value));
        }

        /**
         * Removes a member, so that the reader sees it missing.
         *
         * @param name the member name
         * @return this builder
         */
        Json without(String name) {
            if (members.remove(name) == null) {
                throw new IllegalStateException(
                        "the fixture has no member \""
                                + name
                                + "\" to remove; a test that removes a member that was never there"
                                + " proves nothing");
            }
            return this;
        }

        /**
         * Renames a member, so that the reader sees one missing and one unknown.
         *
         * @param from the existing member name
         * @param to the misspelling
         * @return this builder
         */
        Json renamed(String from, String to) {
            Map<String, String> copy = new LinkedHashMap<>(members);
            members.clear();
            for (Map.Entry<String, String> entry : copy.entrySet()) {
                members.put(entry.getKey().equals(from) ? to : entry.getKey(), entry.getValue());
            }
            if (!members.containsKey(to)) {
                throw new IllegalStateException(
                        "the fixture has no member \"" + from + "\" to rename");
            }
            return this;
        }

        /**
         * Renders the object.
         *
         * @return the JSON text
         */
        String render() {
            List<String> rendered = new ArrayList<>(members.size());
            for (Map.Entry<String, String> entry : members.entrySet()) {
                rendered.add(quote(entry.getKey()) + ": " + entry.getValue());
            }
            return "{" + String.join(", ", rendered) + "}";
        }

        @Override
        public String toString() {
            return render();
        }
    }

    /**
     * Quotes and escapes a JSON string.
     *
     * @param value the text
     * @return the quoted literal
     */
    static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}

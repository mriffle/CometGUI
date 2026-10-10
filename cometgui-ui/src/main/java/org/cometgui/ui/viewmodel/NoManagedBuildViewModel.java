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

package org.cometgui.ui.viewmodel;

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolName;

/**
 * One tool of which CometGUI can install no build on this computer, said plainly, with the
 * registration of the user's own binary offered where the product supports it ({@code D-011}).
 *
 * <p><strong>Every word comes from the port's {@link NoManagedBuild} fact</strong> -- the tool, the
 * platform and whether a local binary can be registered -- and the fact comes from the absence of a
 * manifest row. Nothing here names a release or tests for a particular platform: an Intel Mac reads
 * "No CometGUI-managed Comet exists for an Intel Mac" because the manifest has no Comet row an
 * Intel Mac can run, and would read nothing at all the day it has one.
 *
 * <p>The registration itself is {@link ToolManagerViewModel#register}'s; this class holds what the
 * Tool Manager shows about it.
 */
public final class NoManagedBuildViewModel {

    /** What the registration status says before anything was attempted. */
    public static final String NOTHING_REGISTERED_YET = "";

    private final NoManagedBuild fact;

    private final ReadOnlyStringWrapper status =
            new ReadOnlyStringWrapper(this, "status", NOTHING_REGISTERED_YET);

    private final ReadOnlyBooleanWrapper registering =
            new ReadOnlyBooleanWrapper(this, "registering", false);

    /**
     * Shows one fact.
     *
     * @param fact what the port reported
     * @throws NullPointerException if {@code fact} is {@code null}
     */
    NoManagedBuildViewModel(NoManagedBuild fact) {
        this.fact = Objects.requireNonNull(fact, "fact");
    }

    /**
     * The fact this shows.
     *
     * @return the fact
     */
    public NoManagedBuild fact() {
        return fact;
    }

    /**
     * The tool with no managed build here.
     *
     * @return the tool
     */
    public ToolName tool() {
        return fact.tool();
    }

    /**
     * The explanation, in plain words, for a scientist rather than an engineer.
     *
     * @return for example {@code "No CometGUI-managed Comet exists for an Intel Mac (macos-x86-64):
     *     ..."}
     */
    public String explanationText() {
        String tool = toolWords(fact.tool());
        String said =
                "No CometGUI-managed "
                        + tool
                        + " exists for "
                        + platformWords(fact.host())
                        + " ("
                        + fact.host().id()
                        + "): the "
                        + tool
                        + " developers publish no build of it for this kind of computer, so there"
                        + " is nothing for CometGUI to download and install.";
        if (fact.localRegistration()) {
            return said
                    + " You can register a "
                    + tool
                    + " you have built or obtained yourself: CometGUI runs it to read its version"
                    + " and check what it can do, and then lists it below as your own binary.";
        }
        return said
                + " CometGUI cannot register a "
                + tool
                + " of your own either, so "
                + tool
                + " cannot be used on this computer.";
    }

    /**
     * What the registration button says.
     *
     * @return for example {@code "Register your own Comet..."}
     */
    public String registerActionText() {
        return "Register your own " + toolWords(fact.tool()) + "...";
    }

    /**
     * What the file chooser's title names.
     *
     * @return for example {@code "a Comet executable to register"}
     */
    public String chooserText() {
        return "a " + toolWords(fact.tool()) + " executable to register";
    }

    /**
     * Whether a registration can be started now: the product supports one for this tool, and none
     * is already running.
     *
     * @return {@code true} if the register action should be enabled
     */
    public boolean canRegister() {
        return fact.localRegistration() && !registering.get();
    }

    /**
     * Whether a registration is running.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty registeringProperty() {
        return registering.getReadOnlyProperty();
    }

    /**
     * What happened to the last registration, in a sentence.
     *
     * @return the read-only property, empty until a registration was attempted
     */
    public ReadOnlyStringProperty statusProperty() {
        return status.getReadOnlyProperty();
    }

    /**
     * The registration status's current value.
     *
     * @return the sentence, empty until a registration was attempted
     */
    public String status() {
        return status.get();
    }

    void started(String said) {
        registering.set(true);
        status.set(Objects.requireNonNull(said, "said"));
    }

    void finished(String said) {
        registering.set(false);
        status.set(Objects.requireNonNull(said, "said"));
    }

    void said(String said) {
        status.set(Objects.requireNonNull(said, "said"));
    }

    /**
     * A tool's name as a scientist reads it.
     *
     * @param tool the tool
     * @return the words
     */
    public static String toolWords(ToolName tool) {
        Objects.requireNonNull(tool, "tool");
        return switch (tool) {
            case COMET -> "Comet";
            case PERCOLATOR -> "Percolator";
            case PDV -> "PDV";
            case LIMELIGHT_CONVERTER -> "Limelight converter";
        };
    }

    /**
     * A platform as a scientist would describe their computer.
     *
     * @param host the platform
     * @return for example {@code "an Intel Mac"}
     */
    public static String platformWords(HostPlatform host) {
        Objects.requireNonNull(host, "host");
        return switch (host.operatingSystem()) {
            case MACOS ->
                    switch (host.architecture()) {
                        case X86_64 -> "an Intel Mac";
                        case AARCH64 -> "a Mac with Apple silicon";
                    };
            case LINUX ->
                    switch (host.architecture()) {
                        case X86_64 -> "Linux on a 64-bit Intel or AMD processor";
                        case AARCH64 -> "Linux on a 64-bit ARM processor";
                    };
            case WINDOWS ->
                    switch (host.architecture()) {
                        case X86_64 -> "Windows on a 64-bit Intel or AMD processor";
                        case AARCH64 -> "Windows on a 64-bit ARM processor";
                    };
        };
    }

    @Override
    public String toString() {
        return "NoManagedBuildViewModel[" + fact.tool().id() + " on " + fact.host().id() + "]";
    }
}

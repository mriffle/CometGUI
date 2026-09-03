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

package org.cometgui.install.manager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;

/**
 * A local-binary registrar a test can script, standing in for {@code
 * org.cometgui.tools.percolator.LocalPercolatorRegistration}.
 *
 * <p>The real one is exercised against the real 3.07.1 binary and a 3.04 stub from {@code
 * cometgui-app}, which is the only module that can see it. What this class is for is what the Tool
 * Manager does around it: that the offer joins the list, that registering the same file twice
 * replaces rather than duplicates, and that a registrar answering with the wrong tool or the wrong
 * origin is refused rather than filed.
 */
final class FakeRegistrar implements LocalBinaryRegistrar {

    /** Every registration asked for, as {@code tool path}. */
    private final List<String> asked = new ArrayList<>();

    private ToolName answersWithTool;
    private ToolOrigin answersWithOrigin = ToolOrigin.LOCAL;
    private ToolVersion answersWithVersion = ToolVersion.parse("3.07.1");
    private String refusesWith;
    private boolean answersWithNull;

    FakeRegistrar refusing(String message) {
        this.refusesWith = message;
        return this;
    }

    FakeRegistrar answeringWithTool(ToolName tool) {
        this.answersWithTool = tool;
        return this;
    }

    FakeRegistrar answeringWithOrigin(ToolOrigin origin) {
        this.answersWithOrigin = origin;
        return this;
    }

    FakeRegistrar answeringWithVersion(String version) {
        this.answersWithVersion = ToolVersion.parse(version);
        return this;
    }

    FakeRegistrar answeringWithNull() {
        this.answersWithNull = true;
        return this;
    }

    List<String> asked() {
        return List.copyOf(asked);
    }

    @Override
    public ToolOffer register(ToolName tool, Path executable) throws ToolRegistrationException {
        asked.add(tool.id() + " " + executable);
        if (refusesWith != null) {
            throw new ToolRegistrationException(refusesWith);
        }
        if (answersWithNull) {
            return null;
        }
        ToolName answered = answersWithTool == null ? tool : answersWithTool;
        return new ToolOffer(
                answered,
                answersWithVersion,
                answersWithOrigin,
                ToolInstallState.INSTALLED,
                answersWithOrigin == ToolOrigin.LOCAL
                        ? OptionalLong.empty()
                        : OptionalLong.of(946303L),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.of(executable));
    }
}

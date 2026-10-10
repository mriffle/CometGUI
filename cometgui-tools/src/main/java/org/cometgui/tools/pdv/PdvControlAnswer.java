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

package org.cometgui.tools.pdv;

import java.util.Objects;

/**
 * What PDV's loopback control server answered one request: its HTTP status and its body, as sent.
 *
 * <p>PDV 2.7.0's {@code PdvControlServer} answers every request with a short text body: {@code 200
 * ready} or {@code 503 loading} for {@code /ready}; {@code 200 selected}, {@code 404 unknown ref},
 * {@code 400 missing ref} or {@code 503 not ready} for {@code /select}; {@code 200 bye} for {@code
 * /shutdown}; {@code 500 error: ...} for anything that went wrong inside it.
 *
 * @param status the HTTP status
 * @param body the body, as sent
 */
public record PdvControlAnswer(int status, String body) {

    /**
     * Requires a body.
     *
     * @throws NullPointerException if {@code body} is {@code null}
     */
    public PdvControlAnswer {
        Objects.requireNonNull(body, "body");
    }

    /**
     * The answer in words, as a message shows it: the status, then the body without surrounding
     * white space.
     *
     * @return for example {@code 200 selected}
     */
    public String describe() {
        return status + " " + body.strip();
    }
}

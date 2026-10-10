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

package org.cometgui.tools.comet;

import java.util.List;
import java.util.Objects;

/**
 * A PEFF sequence variant Comet reports inside a hit's {@code modification_info}: an {@code
 * aminoacid_substitution} or a {@code sequence_substitution} element, carried verbatim -- its name
 * and every attribute, in document order.
 *
 * <p>Comet writes one only when it searched a PEFF database with variants: the hit's {@code
 * peptide} is then the <em>variant</em> sequence, and this element says which protein residues it
 * replaced. The reader does not interpret it; a consumer that cannot represent a variant faithfully
 * refuses the hit that carries one.
 *
 * @param element the element's local name
 * @param attributes its attributes, in document order
 */
public record PepXmlSequenceVariant(String element, List<PepXmlNameValue> attributes) {

    /**
     * Validates the variant.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PepXmlSequenceVariant {
        Objects.requireNonNull(element, "element");
        attributes = List.copyOf(attributes);
    }

    @Override
    public List<PepXmlNameValue> attributes() {
        return List.copyOf(attributes);
    }
}

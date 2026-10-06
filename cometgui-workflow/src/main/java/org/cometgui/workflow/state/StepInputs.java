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

package org.cometgui.workflow.state;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The current value of each input kind a run supplies: the configuration and the input files'
 * hashes, as the fingerprints see them.
 *
 * <p>Immutable. A caller may supply values that no planned step reads -- the display filters,
 * Percolator's settings in a run that does not run Percolator -- and they are simply not looked at;
 * that is what lets a caller hand over the whole configuration. What a caller may not do is supply
 * a value of the wrong type for its kind, which is refused here rather than turning up later as a
 * fingerprint nobody can reproduce.
 */
public final class StepInputs {

    private final Map<InputKind, InputValue> values;

    private StepInputs(Map<InputKind, InputValue> values) {
        this.values = values;
    }

    /**
     * Inputs from a map of values.
     *
     * @param values each kind's value
     * @return the inputs
     * @throws NullPointerException if the map, a key or a value is {@code null}
     * @throws IllegalArgumentException if a value's type is not its kind's, naming the kind and
     *     both types
     */
    public static StepInputs of(Map<InputKind, ? extends InputValue> values) {
        Objects.requireNonNull(values, "values");
        Map<InputKind, InputValue> copy = new EnumMap<>(InputKind.class);
        for (Map.Entry<InputKind, ? extends InputValue> entry : values.entrySet()) {
            InputKind kind = Objects.requireNonNull(entry.getKey(), "values contains a null kind");
            InputValue value =
                    Objects.requireNonNull(entry.getValue(), "no value for " + kind.id());
            if (value.type() != kind.valueType()) {
                throw new IllegalArgumentException(
                        "input "
                                + kind.id()
                                + " takes a "
                                + kind.valueType()
                                + " value, but was given a "
                                + value.type()
                                + " value");
            }
            copy.put(kind, value);
        }
        return new StepInputs(Collections.unmodifiableMap(copy));
    }

    /**
     * These inputs with one value replaced or added.
     *
     * @param kind the kind
     * @param value its new value
     * @return new inputs; this object is unchanged
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the value's type is not the kind's
     */
    public StepInputs with(InputKind kind, InputValue value) {
        Map<InputKind, InputValue> changed = new EnumMap<>(InputKind.class);
        changed.putAll(values);
        changed.put(Objects.requireNonNull(kind, "kind"), value);
        return of(changed);
    }

    /**
     * One kind's value.
     *
     * @param kind the kind
     * @return the value, or empty if none was supplied
     */
    public Optional<InputValue> get(InputKind kind) {
        return Optional.ofNullable(values.get(Objects.requireNonNull(kind, "kind")));
    }

    /**
     * Every supplied value.
     *
     * @return an immutable map, iterating in {@link InputKind} order
     */
    public Map<InputKind, InputValue> values() {
        return Collections.unmodifiableMap(values);
    }
}

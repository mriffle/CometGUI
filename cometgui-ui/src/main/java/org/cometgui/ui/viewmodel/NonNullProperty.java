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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;

/**
 * A JavaFX object property that can never hold {@code null}, is never handed out writable, and can
 * never be bound.
 *
 * <p>Every observable value a view-model in this package publishes has a meaning at every instant:
 * "no section is selected" and "no minimum severity" are not states this application has. A plain
 * writable property would let a view push {@code null} in from a selection model that had just been
 * cleared, and the failure would surface much later, in whatever code read the property, with
 * nothing left to say where the {@code null} came from. Rejecting it at the setter puts the
 * exception on the line that caused it.
 *
 * <h2>Read-only outwards</h2>
 *
 * <p>It extends {@link ReadOnlyObjectWrapper}, so a view-model keeps the writable object private
 * and publishes {@link ReadOnlyObjectWrapper#getReadOnlyProperty()}. A view therefore observes and
 * binds <em>to</em> the value but changes it only by calling a named method on the view-model --
 * {@code select}, {@code showOnlyStage}, {@code setMinimumSeverity} -- which is the MVVM boundary
 * doing its job: state transitions belong to the model, and a view-model whose state could be
 * assigned from outside has no invariants left to keep.
 *
 * <h2>Why {@link #bind(ObservableValue)} is refused</h2>
 *
 * <p>A bound JavaFX property does not go through {@code set}: {@code ObjectPropertyBase.get()}
 * reads the bound observable directly, so a binding would walk straight past the null check this
 * class exists for, and the property would quietly start returning {@code null} again. The writable
 * object never leaves its view-model, so this cannot happen from a view; refusing the operation
 * keeps it from happening inside one either.
 *
 * <p>Package-private: this is a mechanism the view-models in this package share, not part of the
 * interface they present to a view.
 *
 * @param <T> the value type
 */
final class NonNullProperty<T> extends ReadOnlyObjectWrapper<T> {

    /**
     * A property holding {@code initialValue}.
     *
     * @param bean the object holding the property, used in diagnostics
     * @param name the property name, used in every rejection message
     * @param initialValue the value the property starts with
     * @throws NullPointerException if {@code initialValue} is {@code null}, naming the property
     */
    NonNullProperty(Object bean, String name, T initialValue) {
        super(bean, name, Objects.requireNonNull(initialValue, name));
    }

    /**
     * Sets the value, rejecting {@code null}.
     *
     * <p>{@code setValue} delegates here, so both writes are guarded. Only the owning view-model
     * can reach this: what it publishes is {@link ReadOnlyObjectProperty}.
     *
     * @param newValue the new value
     * @throws NullPointerException if {@code newValue} is {@code null}, naming the property
     */
    @Override
    public void set(T newValue) {
        super.set(Objects.requireNonNull(newValue, getName()));
    }

    /**
     * Refuses to bind. See the note on this class.
     *
     * @param newObservable ignored
     * @throws UnsupportedOperationException always, naming the property and saying what to do
     *     instead
     */
    @Override
    public void bind(ObservableValue<? extends T> newObservable) {
        throw new UnsupportedOperationException(
                "the view-model property '"
                        + getName()
                        + "' cannot be bound: a bound property is read from its binding rather than"
                        + " through set, which would bypass the null check that makes this property"
                        + " safe to read. Set it instead.");
    }
}

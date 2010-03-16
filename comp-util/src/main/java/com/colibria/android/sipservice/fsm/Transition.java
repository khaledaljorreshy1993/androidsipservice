/*
 *
 * Copyright (C) 2010 Colibria AS
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.colibria.android.sipservice.fsm;

/**
 * @author Sebastian Dehne
 */
public class Transition<S, M extends Machine<S>> {

    private final Condition<S, M> condition;
    private final State<S, M> targetState;
    private final State<S, M> exceptionState;

    public Transition(Condition<S, M> condition, State<S, M> targetState) {
        this(condition, targetState, null);
    }

    public Transition(Condition<S, M> condition, State<S, M> targetState, State<S, M> exceptionState) {
        this.condition = condition;
        this.targetState = targetState;
        this.exceptionState = exceptionState;
    }

    /**
     * Get condition associated with this transition
     *
     * @return the associated condition
     */
    public final Condition<S, M> getCondition() {
        return condition;
    }

    /**
     * Get target state for this transtion
     *
     * @return the target state
     */
    public final State<S, M> getTargetState() {
        return targetState;
    }

    public final State<S, M> getExceptionState() {
        return exceptionState;
    }

    /**
     * The activity to be performed upon doing the transition
     *
     * @param machine the machine for which this transtion is performed
     * @param signal  the signal that causes the transition
     * @throws TransitionActivityException
     */
    public void activity(M machine, S signal) throws TransitionActivityException {

    }
}

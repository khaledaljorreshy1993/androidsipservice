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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Sebastian Dehne
 */
public class State<S, M extends Machine<S>> {

    private final List<Transition> transitions;
    private final String name;
    private final boolean blocks;

    /**
     * Constract a state with given name and final indicator
     *
     * @param name   name of state
     * @param blocks define wether the machine should block the current thread
     *               if it cannot find a matching condition for the received signal
     */
    public State(String name, boolean blocks) {
        if (name == null) {
            this.name = getClass().getName();
        } else {
            this.name = name;
        }
        transitions = new CopyOnWriteArrayList<Transition>();
        this.blocks = blocks;
    }

    /**
     * Constract a non final named state
     *
     * @param name name of state
     */
    public State(String name) {
        this(name, false);
    }

    /**
     * Construct non final unnamed state
     */
    public State() {
        this(null);
    }

    /**
     * Get state name
     *
     * @return the state name
     */
    public String getName() {
        return name;
    }

    /**
     * Add transation to state,
     * note that transitions will be evaluated in the order they are added
     *
     * @param transition the transation to add
     */
    public void addTransition(Transition transition) {
        transitions.add(transition);
    }


    /**
     * Find Transition for given Signal
     *
     * @param signal the Signal to find correct Transtion for
     * @param owner  the owning State
     * @return the selected Transition
     */
    public Transition<S, M> input(S signal, M owner) {
        //noinspection unchecked
        for (Transition<S, M> transition : transitions) {
            if (transition.getCondition().satisfiedBy(signal, owner)) {
                return transition;
            }
        }
        return null;
    }

    public boolean isBlocking() {
        return blocks;
    }

    /**
     * Called upon entrance of State
     *
     * @param owner   the FSM entering this state
     * @param reEnter true if state is re-entered (transition to self)
     */
    public void enter(M owner, boolean reEnter) {

    }

    /**
     * @param owner      the FSM entering this state
     * @param forReEnter true if state will be re-entered by transition (transition to self)
     */
    public void exit(M owner, boolean forReEnter) {

    }

}

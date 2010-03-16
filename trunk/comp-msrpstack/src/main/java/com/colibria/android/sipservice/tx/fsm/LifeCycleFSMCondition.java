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
package com.colibria.android.sipservice.tx.fsm;

import com.colibria.android.sipservice.fsm.Condition;
import com.colibria.android.sipservice.tx.Participant;


/**
 * @author Sebastian Dehne
 */
public class LifeCycleFSMCondition extends Condition<LifeCycleFSMSignal, Participant> {

    private final LifeCycleFSMSignal.Type type;

    private LifeCycleFSMCondition(LifeCycleFSMSignal.Type type) {
        this.type = type;
    }

    public static final LifeCycleFSMCondition DO_CONNECT = new LifeCycleFSMCondition(LifeCycleFSMSignal.Type.connect);
    public static final LifeCycleFSMCondition CONNECT_FAILED = new LifeCycleFSMCondition(LifeCycleFSMSignal.Type.connectFailed);
    public static final LifeCycleFSMCondition HANDSHAKE_COMPLETED = new LifeCycleFSMCondition(LifeCycleFSMSignal.Type.handshake_completed);
    public static final LifeCycleFSMCondition CONNECTION_UP = new LifeCycleFSMCondition(LifeCycleFSMSignal.Type.connection_up);
    public static final LifeCycleFSMCondition CLOSE = new LifeCycleFSMCondition(LifeCycleFSMSignal.Type.close);
    public static final LifeCycleFSMCondition CLOSE_REQUEST = new LifeCycleFSMCondition(LifeCycleFSMSignal.Type.close) {
        @Override
        public boolean satisfiedBy(LifeCycleFSMSignal signal, Participant owner) {
            return super.satisfiedBy(signal, owner) && owner.cannotOutboundFsmBeClosed() && !signal.isTriggeredByTimeoutTimer();
        }
    };

    public boolean satisfiedBy(LifeCycleFSMSignal signal, Participant owner) {
        return signal.isType(type);
    }
}

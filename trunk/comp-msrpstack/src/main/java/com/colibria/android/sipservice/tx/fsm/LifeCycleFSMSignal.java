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

import com.colibria.android.sipservice.headers.MsrpResponse;
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.io.ChannelState;


/**
 * @author Sebastian Dehne
 */
public class LifeCycleFSMSignal {

    public enum Type {

        /**
         * Causes the switch to establish a new outbound TCP connection
         */
        connect,

        /**
         * The signal used when an outbound connect failes
         */
        connectFailed,

        /**
         * Binds a given TCP connection to a participant
         */
        connection_up,

        /**
         * signal the FSM that the handshake is completed
         */
        handshake_completed,

        /**
         * Closes a participant
         */
        close
    }

    private final Type type;

    private final ChannelState channelState;
    private final MsrpSendRequest handshakeRequest;
    private final MsrpResponse handshakeResponse;

    private final boolean triggeredByTimeoutTimer;


    private LifeCycleFSMSignal(Type type, ChannelState channelState, MsrpSendRequest handshakeRequest, MsrpResponse response, boolean triggeredByTimeoutTimer) {
        this.type = type;
        this.channelState = channelState;
        this.handshakeRequest = handshakeRequest;
        this.handshakeResponse = response;
        this.triggeredByTimeoutTimer = triggeredByTimeoutTimer;
    }

    public boolean isType(Type type) {
        return type == this.type;
    }

    public ChannelState getChannelState() {
        return channelState;
    }

    public MsrpSendRequest getHandshakeRequest() {
        return handshakeRequest;
    }

    public MsrpResponse getHandshakeResponse() {
        return handshakeResponse;
    }

    public boolean isTriggeredByTimeoutTimer() {
        return triggeredByTimeoutTimer;
    }

    public String toString() {
        return type.toString();
    }

    public static LifeCycleFSMSignal createConnectionUpSignal(ChannelState channelState) {
        return new LifeCycleFSMSignal(Type.connection_up, channelState, null, null, false);
    }

    public static LifeCycleFSMSignal createConnectFailedSignal() {
        return new LifeCycleFSMSignal(Type.connectFailed, null, null, null, false);
    }

    public static LifeCycleFSMSignal createHandshakeCompletedSignal(MsrpResponse response) {
        return new LifeCycleFSMSignal(Type.handshake_completed, null, null, response, false);
    }

    public static LifeCycleFSMSignal createCloseSignal(boolean triggeredByTimeoutTimer) {
        return new LifeCycleFSMSignal(Type.close, null, null, null, triggeredByTimeoutTimer);
    }

}

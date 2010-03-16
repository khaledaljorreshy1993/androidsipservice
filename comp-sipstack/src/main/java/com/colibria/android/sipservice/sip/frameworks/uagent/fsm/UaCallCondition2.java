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
package com.colibria.android.sipservice.sip.frameworks.uagent.fsm;

import com.colibria.android.sipservice.fsm.Condition;
import com.colibria.android.sipservice.sip.frameworks.uagent.UAgent2;
import com.colibria.android.sipservice.sip.headers.CSeqHeader;
import com.colibria.android.sipservice.sip.messages.Cancel;
import com.colibria.android.sipservice.sip.messages.Response;


/**
 * @author Sebastian Dehne
 */
public class UaCallCondition2 extends Condition<Signal, UAgent2> {

    private final Signal.Type type;

    protected UaCallCondition2(Signal.Type type) {
        this.type = type;
    }

    @Override
    public boolean satisfiedBy(Signal signal, UAgent2 ua) {
        return signal.isType(type); // default
    }


    /**
     * - In case we are a UAC and are in init-state, this is the initial INVITE to be send out
     * - In case we are in ACTIVE-state, this is an re-INVITE which is to be sent out
     */
    public static final UaCallCondition2 INVITE_SEND = new UaCallCondition2(Signal.Type.inviteSend);
    public static final UaCallCondition2 INVITE_RECEIVED = new UaCallCondition2(Signal.Type.inviteReceived);

    public static final UaCallCondition2 REFER_RECEIVED = new UaCallCondition2(Signal.Type.referReceived);

    public static final UaCallCondition2 TIMEOUT = new UaCallCondition2(Signal.Type.timeout);

    public static final UaCallCondition2 TERMINATE = new UaCallCondition2(Signal.Type.terminate);


    public static final UaCallCondition2 PROCEED_INVITE_REQUEST = new UaCallCondition2(Signal.Type.proceedInviteRequest);
    public static final UaCallCondition2 REJECT_INITIAL_INVITE_REQUEST = new UaCallCondition2(Signal.Type.rejectInviteRequest) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return signal.isType(Signal.Type.rejectInviteRequest) && !ua.isReinviteTransaction();
        }
    };
    public static final UaCallCondition2 REJECT_REINVITE_REQUEST = new UaCallCondition2(Signal.Type.rejectInviteRequest) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return signal.isType(Signal.Type.rejectInviteRequest) && ua.isReinviteTransaction();
        }
    };

    public static final UaCallCondition2 CANCEL_SEND = new UaCallCondition2(Signal.Type.cancelSend);
    public static final UaCallCondition2 CANCEL_RECEIVED = new UaCallCondition2(Signal.Type.cancelReceived);

    public static final UaCallCondition2 ACK_RECEIVED = new UaCallCondition2(Signal.Type.ackReceived);
    public static final UaCallCondition2 ACK_SEND = new UaCallCondition2(Signal.Type.ackSend);

    public static final UaCallCondition2 BYE_SEND = new UaCallCondition2(Signal.Type.byeSend);
    public static final UaCallCondition2 BYE_RECEIVED = new UaCallCondition2(Signal.Type.byeReceived);

    public static final UaCallCondition2 PROVISIONAL_RESPONSE_SEND = new UaCallCondition2(Signal.Type.sendProvisionalResponse);

    public static final UaCallCondition2 RETRANSMIT_LAST_2xx = new UaCallCondition2(Signal.Type.retransmit2xx);

    /**
     * We need to differentiate between normal- and re-INVITE transaction since 4xx-responses during the re-INVITE
     * will transition the state-machine back to the ACTIVE state.
     */
    public static final UaCallCondition2 RESP_ANY_REINVITE = new UaCallCondition2(Signal.Type.response) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return signal.isType(Signal.Type.response) && ua.isReinviteTransaction();
        }
    };

    public static final UaCallCondition2 RESP_ANY = new UaCallCondition2(Signal.Type.response) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return signal.isType(Signal.Type.response);
        }
    };

    public static final UaCallCondition2 RESP_CANCEL = new UaCallCondition2(Signal.Type.response) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return signal.isType(Signal.Type.response) && Cancel.NAME.equals(signal.getResponse().<CSeqHeader>getHeader(CSeqHeader.NAME).getMethod());
        }
    };

    public static final UaCallCondition2 RESP_200 = new UaCallCondition2(Signal.Type.response) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return signal.isType(Signal.Type.response) && signal.getResponse().getStatusCode() == Response.OK;
        }
    };

    public static final UaCallCondition2 RESP_100 = new UaCallCondition2(Signal.Type.response) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return signal.isType(Signal.Type.response) && signal.getResponse().getStatusCode() == Response.TRYING;
        }
    };

    public static final UaCallCondition2 RESP_1XX = new UaCallCondition2(Signal.Type.response) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return signal.isType(Signal.Type.response)
                    && Response.ClassXX.RC1xx.isClass(signal.getResponse().getStatusCode())
                    && signal.getResponse().getStatusCode() != Response.TRYING;
        }
    };

    public static final UaCallCondition2 ANY = new UaCallCondition2(null) {
        public boolean satisfiedBy(Signal signal, UAgent2 ua) {
            return true;
        }
    };
}
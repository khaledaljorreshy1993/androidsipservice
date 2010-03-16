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
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.tx.OutboundFSM;


/**
 * @author Sebastian Dehne
 */
public class OutboundFSMCondition extends Condition<OutboundFSMSignal, OutboundFSM> {


    protected final OutboundFSMSignal.Type type;

    private OutboundFSMCondition(OutboundFSMSignal.Type type) {
        this.type = type;
    }

    public boolean satisfiedBy(OutboundFSMSignal signal, OutboundFSM owner) {
        return signal.isType(type);
    }

    public static final OutboundFSMCondition COMPLETE_HANDSHAKE = new OutboundFSMCondition(OutboundFSMSignal.Type.completeHandshake);
    public static final OutboundFSMCondition DO_HANDSHAKE = new OutboundFSMCondition(OutboundFSMSignal.Type.performHandshake);

    public static final OutboundFSMCondition SEND_REQUEST = new OutboundFSMCondition(OutboundFSMSignal.Type.sendRequest);
    public static final OutboundFSMCondition SEND_REQUEST_COMPLETE = new OutboundFSMCondition(OutboundFSMSignal.Type.sendRequest) {
        @Override
        public boolean satisfiedBy(OutboundFSMSignal signal, OutboundFSM owner) {
            return signal.isType(type) && signal.getToBeSentRequest().isChunkType(MsrpSendRequest.ChunkType.complete);
        }
    };
    public static final OutboundFSMCondition SEND_REQUEST_INCOMPLETE_HEAD = new OutboundFSMCondition(OutboundFSMSignal.Type.sendRequest) {
        @Override
        public boolean satisfiedBy(OutboundFSMSignal signal, OutboundFSM owner) {
            return signal.isType(type) && signal.getToBeSentRequest().isChunkType(MsrpSendRequest.ChunkType.head);
        }
    };
    public static final OutboundFSMCondition SEND_REQUEST_INCOMPLETE_BODY = new OutboundFSMCondition(OutboundFSMSignal.Type.sendRequest) {
        @Override
        public boolean satisfiedBy(OutboundFSMSignal signal, OutboundFSM owner) {
            return signal.isType(type) && signal.getToBeSentRequest().isChunkType(MsrpSendRequest.ChunkType.body_only);
        }
    };
    public static final OutboundFSMCondition SEND_REQUEST_INCOMPLETE_TAIL = new OutboundFSMCondition(OutboundFSMSignal.Type.sendRequest) {
        @Override
        public boolean satisfiedBy(OutboundFSMSignal signal, OutboundFSM owner) {
            return signal.isType(type) && signal.getToBeSentRequest().isChunkType(MsrpSendRequest.ChunkType.tail);
        }
    };
    public static final OutboundFSMCondition SEND_REQUEST_INCOMPLETE_BODY_CONTINUE = new OutboundFSMCondition(OutboundFSMSignal.Type.sendRequest) {
        @Override
        public boolean satisfiedBy(OutboundFSMSignal signal, OutboundFSM owner) {
            return signal.isType(type) &&
                    signal.getToBeSentRequest().isChunkType(MsrpSendRequest.ChunkType.body_only) &&
                    signal.getToBeSentRequest().getTransactionID().equals(owner.getCurrentOrigTransactionID());
        }
    };
    public static final OutboundFSMCondition SEND_REQUEST_INCOMPLETE_TAIL_CONTINUE = new OutboundFSMCondition(OutboundFSMSignal.Type.sendRequest) {
        @Override
        public boolean satisfiedBy(OutboundFSMSignal signal, OutboundFSM owner) {
            return signal.isType(type) &&
                    signal.getToBeSentRequest().isChunkType(MsrpSendRequest.ChunkType.tail) &&
                    signal.getToBeSentRequest().getTransactionID().equals(owner.getCurrentOrigTransactionID());
        }
    };


    public static final OutboundFSMCondition SEND_REPORT = new OutboundFSMCondition(OutboundFSMSignal.Type.sendReport);
    public static final OutboundFSMCondition SEND_RESPONSE = new OutboundFSMCondition(OutboundFSMSignal.Type.sendResponse);

    public static final OutboundFSMCondition BYTES_SENT = new OutboundFSMCondition(OutboundFSMSignal.Type.bytesSent);

    public static final OutboundFSMCondition RESPONSE_RECEIVED = new OutboundFSMCondition(OutboundFSMSignal.Type.responseReceived);
    public static final OutboundFSMCondition ABORT_RESPONSE_RECEIVED = new OutboundFSMCondition(OutboundFSMSignal.Type.responseReceived) {
        @Override
        public boolean satisfiedBy(OutboundFSMSignal signal, OutboundFSM owner) {
            return signal.isType(OutboundFSMSignal.Type.responseReceived) &&
                    signal.getResponse().getStatusCode() == 413 &&
                    owner.getCurrentTransactionID().equals(signal.getResponse().getTransactionID());
        }
    };

    public static final OutboundFSMCondition CLOSE = new OutboundFSMCondition(OutboundFSMSignal.Type.close);
}

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
import com.colibria.android.sipservice.tx.InboundFSM;

/**
 * @author Sebastian Dehne
 */
public class InboundFSMCondition extends Condition<InboundFSMSignal, InboundFSM> {

    protected final InboundFSMSignal.Type type;

    private InboundFSMCondition(InboundFSMSignal.Type type) {
        this.type = type;
    }

    public boolean satisfiedBy(InboundFSMSignal signal, InboundFSM owner) {
        return signal.isType(type);
    }


    public static final InboundFSMCondition CLOSE = new InboundFSMCondition(InboundFSMSignal.Type.close);
    public static final InboundFSMCondition COMPLETE_PAUSE = new InboundFSMCondition(InboundFSMSignal.Type.completePause);
    public static final InboundFSMCondition SEND_RESPONSE = new InboundFSMCondition(InboundFSMSignal.Type.sendResponse);
    public static final InboundFSMCondition RECEIVED_RESPONSE = new InboundFSMCondition(InboundFSMSignal.Type.receivedResponse);
    public static final InboundFSMCondition RECEIVED_REPORT = new InboundFSMCondition(InboundFSMSignal.Type.receivedReportRequest);

    public static final InboundFSMCondition RECEIVED_REQUEST_HAS_END = new InboundFSMCondition(InboundFSMSignal.Type.receivedSendRequest) {
        @Override
        public boolean satisfiedBy(InboundFSMSignal signal, InboundFSM owner) {
            return signal.isType(type) && (
                    signal.getReceivedSendRequest().isChunkType(MsrpSendRequest.ChunkType.complete) ||
                            signal.getReceivedSendRequest().isChunkType(MsrpSendRequest.ChunkType.tail));
        }
    };
    public static final InboundFSMCondition RECEIVED_REQUEST_HAS_NO_END = new InboundFSMCondition(InboundFSMSignal.Type.receivedSendRequest) {
        @Override
        public boolean satisfiedBy(InboundFSMSignal signal, InboundFSM owner) {
            return signal.isType(type) && (
                    signal.getReceivedSendRequest().isChunkType(MsrpSendRequest.ChunkType.head) ||
                            signal.getReceivedSendRequest().isChunkType(MsrpSendRequest.ChunkType.body_only));
        }
    };
    public static final InboundFSMCondition RECEIVED_REQUEST = new InboundFSMCondition(InboundFSMSignal.Type.receivedSendRequest);

    public static final InboundFSMCondition ANY = new InboundFSMCondition(InboundFSMSignal.Type.receivedResponse) {
        @Override
        public boolean satisfiedBy(InboundFSMSignal signal, InboundFSM owner) {
            return true;
        }
    };
}

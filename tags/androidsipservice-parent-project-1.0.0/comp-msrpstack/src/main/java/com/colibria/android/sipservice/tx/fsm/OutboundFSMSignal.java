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
import com.colibria.android.sipservice.headers.MsrpReportRequest;


/**
 * @author Sebastian Dehne
 */
public class OutboundFSMSignal {

    public enum Type {

        /**
         * Used to tell the outbound FSM that the participant is now bound to
         * a connection
         */
        bind,

        /**
         * Signals the FSM to perform the handshake
         */
        performHandshake,

        /**
         * Sends a response to a handshake request
         */
        completeHandshake,

        /**
         * Signals the FSM that a response was received (via the inboundFSM)
         */
        responseReceived,

        /**
         * Signals the FSM to start a new outbound transaction and send the request
         */
        sendRequest,

        /**
         * Signals the FSM to send a request for which no response is expected
         */
        sendReport,

        /**
         * Signals the FSM to send a response
         */
        sendResponse,

        /**
         * Signals the FSM to transition to TERMINATED state
         */
        close,

        /**
         * To indicate that data was actually sent out by grizzly
         */
        bytesSent
    }


    private final Type type;

    /**
     * the received handshake request which is to be responded to
     */
    private final MsrpSendRequest handshakeReuqest;

    /**
     * A received response
     */
    private final MsrpResponse response;

    private final MsrpSendRequest toBeSentRequest;
    private final String messageStateId;

    /**
     * the report to be sent
     */
    private final MsrpReportRequest msrpReportRequest;

    private OutboundFSMSignal(Type type, MsrpResponse response, MsrpSendRequest handshakeReuqest, MsrpSendRequest toBeSentRequest, String messageStateId, MsrpReportRequest report) {
        this.type = type;
        this.response = response;
        this.handshakeReuqest = handshakeReuqest;
        this.toBeSentRequest = toBeSentRequest;
        this.messageStateId = messageStateId;
        this.msrpReportRequest = report;
    }

    public boolean isType(Type type) {
        return type == this.type;
    }

    public MsrpResponse getResponse() {
        return response;
    }

    public MsrpSendRequest getHandshakeReuqest() {
        return handshakeReuqest;
    }

    public MsrpReportRequest getMsrpReportRequest() {
        return msrpReportRequest;
    }

    public MsrpSendRequest getToBeSentRequest() {
        return toBeSentRequest;
    }

    public String getMessageStateId() {
        return messageStateId;
    }

    public String toString() {
        return type.toString();
    }

    public static OutboundFSMSignal getCloseSignal() {
        return new OutboundFSMSignal(Type.close, null, null, null, null, null);
    }

    public static OutboundFSMSignal getResponseReceivedSignal(MsrpResponse response) {
        return new OutboundFSMSignal(Type.responseReceived, response, null, null, null, null);
    }

    public static OutboundFSMSignal getPerformHandshakeSignal() {
        return new OutboundFSMSignal(Type.performHandshake, null, null, null, null, null);
    }

    public static OutboundFSMSignal getCompleteHandshakeSignal() {
        return new OutboundFSMSignal(Type.completeHandshake, null, null, null, null, null);
    }

    public static OutboundFSMSignal getSendRequestSignal(MsrpSendRequest request, String messageStateId) {
        return new OutboundFSMSignal(Type.sendRequest, null, null, request, messageStateId, null);
    }

    public static OutboundFSMSignal getSendResponseSignal(MsrpResponse response) {
        return new OutboundFSMSignal(Type.sendResponse, response, null, null, null, null);
    }

    public static OutboundFSMSignal getSendReportSignal(MsrpReportRequest msrpReportRequest) {
        return new OutboundFSMSignal(Type.sendReport, null, null, null, null, msrpReportRequest);
    }

    public static OutboundFSMSignal getBytesSentSignal() {
        return new OutboundFSMSignal(Type.bytesSent, null, null, null, null, null);
    }
}

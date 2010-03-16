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
public class InboundFSMSignal {
    public enum Type {
        sendResponse,
        receivedResponse,
        receivedSendRequest,
        receivedReportRequest,
        completePause,
        close
    }

    private final Type type;
    private final MsrpSendRequest receivedSendRequest;
    private final MsrpResponse receivedResponse;
    @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
    private final MsrpReportRequest report;

    private InboundFSMSignal(Type type, MsrpSendRequest receivedSendRequest, MsrpResponse receivedResponse, MsrpReportRequest report) {
        this.type = type;
        this.receivedSendRequest = receivedSendRequest;
        this.receivedResponse = receivedResponse;
        this.report = report;
    }

    public boolean isType(Type type) {
        return this.type == type;
    }

    public Type getType() {
        return type;
    }

    public MsrpSendRequest getReceivedSendRequest() {
        return receivedSendRequest;
    }

    public MsrpResponse getReceivedResponse() {
        return receivedResponse;
    }

    public String toString() {
        return type.toString();
    }

    public static InboundFSMSignal getCloseSignal() {
        return new InboundFSMSignal(Type.close, null, null, null);
    }

    public static InboundFSMSignal getSendResponseSignal() {
        return new InboundFSMSignal(Type.sendResponse, null, null, null);
    }

    public static InboundFSMSignal getReceivedResponseSignal(MsrpResponse response) {
        return new InboundFSMSignal(Type.receivedResponse, null, response, null);
    }

    public static InboundFSMSignal getReceivedSendRequestSignal(MsrpSendRequest request) {
        return new InboundFSMSignal(Type.receivedSendRequest, request, null, null);
    }

    public static InboundFSMSignal getReceivedReportRequestSignal(MsrpReportRequest report) {
        return new InboundFSMSignal(Type.receivedReportRequest, null, null, report);
    }

    public static InboundFSMSignal getCompletePauseSignal() {
        return new InboundFSMSignal(Type.completePause, null, null, null);
    }

}

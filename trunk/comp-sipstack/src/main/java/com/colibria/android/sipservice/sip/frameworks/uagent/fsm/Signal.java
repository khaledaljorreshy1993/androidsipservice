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

import com.colibria.android.sipservice.sip.messages.*;


/**
 * <p/>
 * <code>$Id: $</code>
 *
 * @author Arild Nilsen
 * @version $Revision: $
 */
public class Signal {

    public enum Type {

        // invite received or to be send
        inviteSend, inviteReceived,

        // refer
        referReceived,

        // Send a provisional response (includes 180 ringing)
        sendProvisionalResponse,

        // response received or to be send
        response, retransmit2xx,

        // ack received or to be send
        ackReceived, ackSend,

        // cancel
        cancelReceived, cancelSend,

        // bye
        byeReceived, byeSend,

        // in case of any type of timeout
        timeout,

        // used to trigger call termination from the server
        terminate,

        // used to send a final response after an invite has been received
        proceedInviteRequest, rejectInviteRequest,

        notFound,
    }

    public static Signal getInviteSendSignal(Invite invite, boolean isSilentRefresh) {
        return new Signal(Type.inviteSend, -1, invite, null, null, null, isSilentRefresh, null, null);
    }

    public static Signal getReInviteSendSignal(byte[] content) {
        return new Signal(Type.inviteSend, -1, null, null, null, null, false, content, null);
    }

    public static Signal getInviteReceivedSignal(Invite invite) {
        return new Signal(Type.inviteReceived, -1, invite, null, null, null, false, null, null);
    }

    public static Signal getProceedInviteSignal(Response response) {
        return new Signal(Type.proceedInviteRequest, -1, null, response, null, null, false, null, null);
    }

    public static Signal getRejectInviteSignal(Response response) {
        return new Signal(Type.rejectInviteRequest, -1, null, response, null, null, false, null, null);
    }

    public static Signal getResponseSignal(Response response) {
        return new Signal(Type.response, response.getStatusCode(), null, response, null, null, false, null, null);
    }

    public static Signal getByeReceivedSignal(Bye bye) {
        return new Signal(Type.byeReceived, -1, null, null, null, bye, false, null, null);
    }

    public static Signal getByeSendSignal() {
        return new Signal(Type.byeSend, -1, null, null, null, null, false, null, null);
    }

    public static Signal getCancelReceivedSignal(Cancel cancel) {
        return new Signal(Type.cancelReceived, -1, null, null, null, null, false, null, cancel);
    }

    public static Signal getCancelSendSignal() {
        return new Signal(Type.cancelSend, -1, null, null, null, null, false, null, null);
    }

    public static Signal getAckReceivedSignal() {
        return new Signal(Type.ackReceived, -1, null, null, null, null, false, null, null);
    }

    public static Signal getAckSendSignal() {
        return new Signal(Type.ackSend, -1, null, null, null, null, false, null, null);
    }

    public static Signal getReferReceivedSignal(Refer refer) {
        return new Signal(Type.referReceived, -1, null, null, refer, null, false, null, null);
    }

    public static Signal getSendProvisionalResponseSignal(Response response) {
        return new Signal(Type.sendProvisionalResponse, -1, null, response, null, null, false, null, null);
    }

    public static Signal getRetransmitLast2xx() {
        return new Signal(Type.retransmit2xx, -1, null, null, null, null, false, null, null);
    }

    public static Signal getTimeoutSignal() {
        return new Signal(Type.timeout, -1, null, null, null, null, false, null, null);
    }

    public static Signal getTerminateSignal() {
        return new Signal(Type.terminate, -1, null, null, null, null, false, null, null);
    }

    private final int statusCode;
    private final boolean silentRefresh;
    private final Type type;
    private final Invite invite;
    private final Refer refer;
    private final Bye bye;
    private final Response response;
    private final byte[] content;
    private final Cancel cancel;

    public Signal(Type type, int statusCode, Invite invite, Response response, Refer refer, Bye bye, boolean isSilentRefresh, byte[] content, Cancel cancel) {
        this.type = type;
        this.invite = invite;
        this.statusCode = statusCode;
        this.refer = refer;
        this.bye = bye;
        this.response = response;
        this.silentRefresh = isSilentRefresh;
        this.content = content;
        this.cancel = cancel;
    }

    public Type getType() {
        return type;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isType(Type type) {
        return this.type == type;
    }

    public Invite getInvite() {
        return invite;
    }

    public Refer getRefer() {
        return refer;
    }

    public Bye getBye() {
        return bye;
    }

    public Response getResponse() {
        return response;
    }

    public boolean doContinue() {
        return false;
    }

    public boolean isSilentRefresh() {
        return silentRefresh;
    }

    public byte[] getContent() {
        return content;
    }

    public Cancel getCancel() {
        return cancel;
    }

    public String toString() {
        return type.toString();
    }
}

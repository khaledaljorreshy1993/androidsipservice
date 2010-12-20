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
package com.colibria.android.sipservice.endpoint;

import com.colibria.android.sipservice.IMsrpResources;
import com.colibria.android.sipservice.endpoint.api.IMsrpEndpointApplication;
import com.colibria.android.sipservice.endpoint.api.ISendingListener;
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.headers.MsrpURI;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.MsrpRemoteAddress;
import com.colibria.android.sipservice.tx.Participant;
import com.colibria.android.sipservice.endpoint.api.IEndPointSession;
import com.colibria.android.sipservice.headers.ContentDispositionHeader;
import com.colibria.android.sipservice.io.ChannelState;

import java.util.List;


/**
 * @author Sebastian Dehne
 */
public class EndPointSessionImpl implements IEndPointSession {

    private final IMsrpResources msrpResources;
    private final IMsrpEndpointApplication application;
    private final MessageSender messageSender;
    private final MessageReceiver messageReceiver;
    private final OutboundFSMListener outboundFsmListener;

    private volatile Participant participant;

    public EndPointSessionImpl(IMsrpResources msrpResources, IMsrpEndpointApplication application) {
        this.application = application;
        this.msrpResources = msrpResources;
        this.outboundFsmListener = new OutboundFSMListener(this);

        messageSender = new MessageSender(this);
        messageReceiver = new MessageReceiver(this);
    }

    public MsrpURI getLocalMsrpURI() {
        return participant.getLocalMsrpURI();
    }

    public MsrpURI getRemoteMsrpURI() {
        return participant.getRemoteURI();
    }

    public void establishOutgoingConnection(MsrpRemoteAddress remoteAddress) {
        participant.setRemoteURI(remoteAddress.getRemoteURI());
        ChannelState.getOrCreate(msrpResources, participant, remoteAddress.getRemoteSocket());
    }

    @Override
    public String sendNewMessage(String msgId, byte[] content, boolean abortSending, boolean lastChunk, MimeType contentType, List<Address> recipients, ContentDispositionHeader contentDispositionHeader, long msgSize, ISendingListener sendingListener) {
        if (content != null && content.length > SendingMessageState.SEND_AT_A_TIME) {
            throw new IllegalArgumentException("content too large");
        }
        return messageSender.sendNewMessage(msgId, content, abortSending, lastChunk, contentType, contentDispositionHeader, recipients, msgSize, sendingListener);
    }

    public void abortIncomingMessage(String msgID) {
        messageReceiver.abortReceiving(msgID);
    }

    public void close() {
        participant.terminate();
    }

    public IMsrpResources getMsrpResources() {
        return msrpResources;
    }

    public IMsrpEndpointApplication getApplication() {
        return application;
    }

    public void abortSending(String messageStateId) {
        messageSender.abortSending(messageStateId);
    }

    public OutboundFSMListener getOutboundFsmListener() {
        return outboundFsmListener;
    }

    public void setParticipant(Participant participant) {
        this.participant = participant;
    }

    public MessageSender getMessageSender() {
        return messageSender;
    }

    public Address getAddress() {
        return participant.getCpimAddress();
    }

    public void send(MsrpSendRequest request) {
        participant.handleOutgoingRequest(request, request.getMessageID());
    }

    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }
}

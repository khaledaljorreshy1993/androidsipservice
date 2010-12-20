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

import com.colibria.android.sipservice.IMsrpApplication;
import com.colibria.android.sipservice.endpoint.messagebuffer.MessageReassembler;
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.headers.MsrpResponse;
import com.colibria.android.sipservice.tx.IOutboundFSMListener;
import com.colibria.android.sipservice.tx.Participant;

import java.util.concurrent.ConcurrentHashMap;


/**
 * Handles all traffic received by one participant and routes them to their
 * message collectors
 *
 * @author Sebastian Dehne
 */
public class MessageReceiver implements IMsrpApplication {
    private static final String TAG = "MessageReceiver";

    private final ConcurrentHashMap<String, MessageReassembler> collectors;
    private final EndPointSessionImpl parent;
    private final MessageCollectorFactory collectorFactory;

    public MessageReceiver(EndPointSessionImpl parent) {
        this.parent = parent;
        collectors = new ConcurrentHashMap<String, MessageReassembler>();
        collectorFactory = new MessageCollectorFactory(parent);
    }

    public MsrpResponse.ResponseCode requestReceived(Participant participant, MsrpSendRequest request) {
        MessageReassembler messageCollector, existingCollector;
        if ((messageCollector = collectors.get(request.getMessageID())) != null) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Routing request to existing collector");
            }
            return messageCollector.handleNextRequest(request);
        } else {
            messageCollector = collectorFactory.create(request);
            if ((existingCollector = collectors.putIfAbsent(request.getMessageID(), messageCollector)) != null) {
                messageCollector = existingCollector;
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Routing request to existing collector");
                }
            } else {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Routing request to new collector");
                }
            }
            return messageCollector.handleNextRequest(request);
        }
    }

    public void responseReceived(String messageStateId, Participant participant, int responseCode) {
        if ((responseCode / 100) != 2) {
            parent.abortSending(messageStateId);
        }
    }

    public void participantActivated(Participant participant) {
        parent.getApplication().connected();
    }

    public void participantTerminated(Participant participant) {
        parent.getMessageSender().terminate();
        collectors.clear();
        parent.getApplication().terminated();
    }

    @Override
    public IOutboundFSMListener getOutboundFsmListener() {
        return parent.getOutboundFsmListener();
    }

    public void abortReceiving(String msgID) {
        MessageReassembler collector = collectors.get(msgID);
        if (collector != null) {
            collector.abort();
        }
    }

    public void remove(String messageID) {
        collectors.remove(messageID);
    }
}


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

import com.colibria.android.sipservice.endpoint.api.ISendingListener;
import com.colibria.android.sipservice.headers.Continuation;
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.headers.ContentDispositionHeader;
import com.colibria.android.sipservice.sip.Address;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;


/**
 * todo do some check first before spawning off a new task
 *
 * @author Sebastian Dehne
 */
public class MessageSender {
    private static final String TAG = "MessageSender";

    private final EndPointSessionImpl parent;
    private final ReentrantLock sendingLock; // guards the message states and ensures that only one thread can transmit data at a time
    private final HashMap<String, SendingMessageState> queuedMessages; // guarded by 'sendingLock'

    /**
     * This field is updated (producer) by the msrp layer (OutboundFSM)
     * and read (consumer) by the transmitWorkerThread (performSending()).
     * Since the OutboundFSM ensures that only one thread can use it at a time,
     * we dont need to worry about concurrent calls into writeCapacityAvailable()
     */
    private volatile boolean outputQueueReady;

    public MessageSender(EndPointSessionImpl parent) {
        sendingLock = new ReentrantLock();
        this.parent = parent;
        this.queuedMessages = new HashMap<String, SendingMessageState>();

        outputQueueReady = true;
    }

    /**
     * Called by the user
     *
     * @param messageStateId the msgID of the msg to be aborted
     */
    public void abortSending(final String messageStateId) {
        // since we cannot aquire the lock sendingLock from current thread, spawn a new task
        parent.getMsrpResources().getThreadFarm().execute(new Runnable() {
            public void run() {
                sendingLock.lock();
                try {
                    SendingMessageState msgState = queuedMessages.get(messageStateId);
                    if (msgState != null) {
                        msgState.getSendingListener().abortSendingMsg(messageStateId);
                    } else if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "Could not find msgState, unable to request abort");
                    }
                } finally {
                    sendingLock.unlock();
                }
            }
        });

    }

    public String sendNewMessage(String msgId, final byte[] content, final boolean abortSending, final boolean lastChunk, MimeType contentType, ContentDispositionHeader contentDispositionHeader, List<Address> receipients, long msgSize, ISendingListener sendingListener) {
        final SendingMessageState sendingMessageState;
        sendingLock.lock();
        try {
            if (msgId == null) {
                sendingMessageState = new SendingMessageState(
                        parent,
                        msgSize,
                        contentType,
                        contentDispositionHeader,
                        receipients,
                        sendingListener);
                queuedMessages.put(sendingMessageState.getMsgID(), sendingMessageState);
            } else {
                sendingMessageState = queuedMessages.get(msgId);
            }

        } finally {
            sendingLock.unlock();
        }

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Scheduling new message to be sent. MsgID=" + sendingMessageState.getMsgID());
        }


        // try to send it in background
        parent.getMsrpResources().getThreadFarm().execute(new Runnable() {
            public void run() {
                MsrpSendRequest nextChunkPiece = sendingMessageState.getNextChunkPiece(content, abortSending, lastChunk);

                if (nextChunkPiece.getContinuation() != Continuation.more) {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "This is the last chunk (piece) for msgID=" + sendingMessageState.getMsgID());
                    }
                    sendingLock.lock();
                    try {
                        queuedMessages.remove(sendingMessageState.getMsgID());
                    } finally {
                        sendingLock.unlock();
                    }
                }

                // send chunk piece
                parent.send(nextChunkPiece);
            }
        });

        return sendingMessageState.getMsgID();
    }

    /**
     * Called by the msrp layer (OutboudFSM)
     *
     * @param bufferCapacityInUse the capacity indicator for the outbound queue
     */
    public void writeCapacityAvailable(float bufferCapacityInUse) {

        if (bufferCapacityInUse < 0.5) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Outbound queue capacity is less than 50% (" + bufferCapacityInUse + "), allowing sending of more chunks");
            }
            outputQueueReady = true;
        } else {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Outbound queue capacity is more than 50% (" + bufferCapacityInUse + "), not allowing sending of more chunks");
            }
            outputQueueReady = false;
        }

        // channel has become available again, spawn off a sending task
        if (outputQueueReady) {
            parent.getMsrpResources().getThreadFarm().execute(new Runnable() {
                public void run() {
                    sendingLock.lock();
                    try {
                        for (SendingMessageState nextMsg : queuedMessages.values()) {
                            nextMsg.getSendingListener().readyForMore(nextMsg.getMsgID());
                        }
                    } finally {
                        sendingLock.unlock();
                    }
                }
            });
        }

    }

    public void terminate() {
        parent.getMsrpResources().getThreadFarm().execute(new Runnable() {
            public void run() {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Session seems to have terminated, cleaning up");
                }
                sendingLock.lock();
                try {
                    outputQueueReady = false;
                    queuedMessages.clear();
                } finally {
                    sendingLock.unlock();
                }
            }
        });
    }
}

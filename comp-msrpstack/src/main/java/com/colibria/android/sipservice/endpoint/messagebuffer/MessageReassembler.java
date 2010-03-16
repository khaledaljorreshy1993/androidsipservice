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
package com.colibria.android.sipservice.endpoint.messagebuffer;

import com.colibria.android.sipservice.endpoint.EndPointSessionImpl;
import com.colibria.android.sipservice.endpoint.api.IMessageContentStore;
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.headers.Continuation;
import com.colibria.android.sipservice.headers.MsrpResponse;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * Base class for reassembly a complete message based on the received chunks.
 * This class also contains a timer which fires if no additional chunks are receieved
 * for some time, in order to allow resources to be released.
 *
 * @author Sebastian Dehne
 */
public class MessageReassembler {
    private static final String TAG = "MessageReassembler";

    private static final int TIMEOUT_IN_SECONDS = 32;

    private final ReceivedMessageMetaData receivedMessageMetaData;
    private final IMessageContentStore store;
    private final EndPointSessionImpl parent;

    private ScheduledFuture timeoutTimer = null;
    private long byteCounter = 0;
    private boolean wasAborted = false;
    private long skipBytes;

    public MessageReassembler(EndPointSessionImpl parent, ReceivedMessageMetaData metaData, IMessageContentStore store) {
        this.parent = parent;
        this.receivedMessageMetaData = metaData;
        this.store = store;
        skipBytes = 0;
    }

    public final synchronized MsrpResponse.ResponseCode handleNextRequest(MsrpSendRequest request) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter (txID=" + request.getTransactionID() + ", msgID=" + request.getMessageID() + ")");
        }

        MsrpResponse.ResponseCode returnCode = MsrpResponse.RESPONSE_200_OK;

        long start;
        if (request.getChunkType() == MsrpSendRequest.ChunkType.head || request.getChunkType() == MsrpSendRequest.ChunkType.complete)
            start = request.getByteRange().getStart() - skipBytes - 1;
        else
            start = -1; // this is a subsequent chunk piece for an ongoing chunk, we don't need to seek() in the msgStore 

        // store the recevied content
        byteCounter += store.store(
                start,
                request.getBody(),
                request.getBodyStartPosition(),
                request.getBody().length - request.getBodyStartPosition()
        );

        /*
         * A sub-sequent chunk will contains the byte-range start position relative to the msrp-body.
         * Since this msrp-body contains a cpim header which we don't want to have,
         * we need to keep track of this such.
         */
        skipBytes += request.getBodyStartPosition();

        final Continuation continuation = request.getContinuation();

        /*
         * We expect more chunks (or chunk pieces) to arraive
         */
        if (continuation == null || continuation == Continuation.more) {
            // notify the app
            parent.getApplication().moreBytesRecevied(request.getMessageID(), byteCounter, receivedMessageMetaData.getExpectedMsgSize(), store);

            // update timer
            updateTimer();

            if (wasAborted) {
                returnCode = MsrpResponse.RESPONSE_413;
            }
        }

        /*
         * Receiving was finished
         */
        else {
            // cancel
            cancelTimer();

            // release resources
            store.receivingFinished(continuation != Continuation.done);

            // notify the app
            parent.getApplication().messageRecevied(request.getMessageID(), receivedMessageMetaData, store);

            parent.getMessageReceiver().remove(receivedMessageMetaData.getMsgID());

        }

        return returnCode;

    }

    public synchronized final void abort() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Aborting incoming message " + receivedMessageMetaData.getMsgID());
        }
        wasAborted = true;
    }

    private void updateTimer() {

        cancelTimer();

        timeoutTimer = parent.getMsrpResources().getThreadFarm().schedule(new Runnable() {
            public void run() {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "timeout timer fired for " + receivedMessageMetaData.getMsgID());
                }
                synchronized (MessageReassembler.this) {
                    store.receivingFinished(true);

                    // notify the app
                    parent.getApplication().messageRecevied(receivedMessageMetaData.getMsgID(), receivedMessageMetaData, store);
                }
            }
        }, TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel(false);
        }
    }
}

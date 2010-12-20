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
package com.colibria.android.sipservice.tx;

import com.colibria.android.sipservice.IMsrpTrafficLogger;
import com.colibria.android.sipservice.fsm.*;
import com.colibria.android.sipservice.headers.*;
import com.colibria.android.sipservice.io.ChannelState;
import com.colibria.android.sipservice.io.config.Configurator;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.fsm.Machine;
import com.colibria.android.sipservice.fsm.State;
import com.colibria.android.sipservice.fsm.UnhandledConditionException;
import com.colibria.android.sipservice.tx.fsm.OutboundFSMCondition;
import com.colibria.android.sipservice.tx.fsm.OutboundFSMSignal;
import com.colibria.android.sipservice.tx.fsm.OutboundFSMState;
import com.colibria.android.sipservice.tx.fsm.OutboundFSMTransition;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


/**
 * <pre>
 * <p/>
 *                             +------+
 *                             | INIT |
 *                             +------+
 *                               |  |
 *                               |  |
 *                               |  |
 *                onDoHandshake  |  |
 *                    +----------+  |
 *                    v             | onCompleteHandshake
 *             +------------+       |
 *             | START_SENT |       |
 *             +------------+       |
 *                    | onResponse- |                                   +--------+
 *                    | received    |                                   v        | sendBodyOnly
 *                    |             |      sendInComplete  +---------------+     |
 *                    +----------+  |        +------------>| SENDING_CHUNK |-----+
 *                               v  v        |             +---------------+
 *                             +------+      ^                     |
 *                         +-->| IDLE |------+                     | interrupt / completeChunk
 *                         |   +------+      v                     v
 *                         |     |  ^        |             +---------------+
 *                         +-----+  |        +------------>| WAIT_RESPONSE |-----+
 *                  sendResponse    |        sendComplete  +---------------+     |
 *                                  |                              |     ^       | sendResponse
 *                                  +------------------------------+     +-------+
 *                                                      responseReceived
 * <p/>
 * <p/>
 *                         [from all states]
 *                                |
 *                                | onClose
 *                                v
 *                          +------------+
 *                          | TERMINATED |
 *                          +------------+
 * <p/>
 * <p/>
 * INIT          : The initial state
 * START_SENT    : The handshake request was sent and the FSM is currently waiting for a matching response
 * IDLE          : This state is used when the outboundFSM is able to accept a new transaction
 * SENDING_CHUNK : This state is used when the FSM is currently sending parts of a (larger) chunk
 * WAIT_RESPONSE : A request has been sent for which a matching response is expected.
 *                 In case a new sendRequest arrived from the conference when the FSM in the WAIT_RESPONSE
 *                 state, the request will be put on the queue and processed after the current transaction
 *                 is completed.
 *                 Please note that there is no timeout timer since it is not needed: Either the participant
 *                 sends a response which doesn't match (in that case the participant is terminated)
 *                 or nevers sends any response in which case the outbound FIFO queues fills up to its
 *                 max size and the participant is kicked out from the converence. No using a timer
 *                 here increases the overall performance.
 * TERMINATED    : The participant and this FSM is terminated and all new traffic will be rejected
 * <p/>
 * <p/>
 * All states are non-blocking, thus signals for unspecified transitions will bounce off
 * with an UnhandledConditionException
 * </pre>
 *
 * @author Sebastian Dehne
 */
public class OutboundFSM extends Machine<OutboundFSMSignal> {
    private static final String TAG = "OutboundFSM";

    private static final ThreadLocal<ByteBuffer> writeBuffers = new ThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            // the output buffer needs to be somewhat larger then the inbound buffer, since
            // we modify some headers which might lead to a larger msg than the original
            // received request
            return ByteBuffer.allocate(Configurator.getBufferSize() * 2);
        }
    };

    /*
     * The states
     */
    public static final OutboundFSMState INIT = new OutboundFSMState("INIT");
    public static final OutboundFSMState START_SENT = new OutboundFSMState("START_SENT");
    public static final OutboundFSMState IDLE = new OutboundFSMState("IDLE") {
        @Override
        public void enter(OutboundFSM machine, boolean reEnter) {
            machine.scheduleNextRequest();
            machine.currentMessageStateId = null;
            machine.listener.updateOutboundQueueUsage(machine.getCurrentQueueUsage());
        }
    };
    public static final OutboundFSMState SENDING_CHUNK = new OutboundFSMState("SENDING_CHUNK") {
        @Override
        public void enter(OutboundFSM machine, boolean reEnter) {
            // a new chunk of data was sent
            machine.listener.updateOutboundQueueUsage(machine.getCurrentQueueUsage());
            machine.scheduleNextRequest();
        }
    };
    public static final OutboundFSMState SENDING_CHUNK_WAIT = new OutboundFSMState("SENDING_CHUNK_WAIT", true);
    public static final OutboundFSMState WAIT_RESPONSE = new OutboundFSMState("WAIT_RESPONSE");
    public static final OutboundFSMState TERMINATED = new OutboundFSMState("TERMINATED") {
        @Override
        public void enter(OutboundFSM machine, boolean reEnter) {
            // clear some memory now
            machine.messageQueue.clear();
        }
    };


    /*
     * The transitions
     */

    static {
        INIT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.DO_HANDSHAKE, START_SENT) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "INIT -> DO_HANDSHAKE (for " + m.parent.getLocalMsrpURI() + ")");
                }

                // generate handshake request
                MsrpSendRequest handShake = MsrpSendRequest.generateHandShake(
                        m.parent.parentInstance.getNextId(),
                        new MsrpPath(m.parent.getRemoteURI()),
                        new MsrpPath(m.parent.getLocalMsrpURI()),
                        m.parent.parentInstance.getNextId());

                // remeber the txID
                m.currentTransactionID = handShake.getTransactionID();

                // write it to the wire
                ByteBuffer bb = getReadBuffer();
                handShake.marshall(bb, null, null, null, null);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.complete, handShake, null, null, null, null, null, null, bb);

                // write data to challen
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing handshake request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });
            }
        });
        INIT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.COMPLETE_HANDSHAKE, IDLE) {
            @Override
            public void activity(final OutboundFSM machine, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "INIT -> IDLE (for " + machine.parent.getLocalMsrpURI() + ")");
                }
            }
        });
        INIT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(final OutboundFSM machine, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "INIT -> TERMINATED (for " + machine.parent.getLocalMsrpURI() + ")");
                }
            }
        });

        START_SENT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.RESPONSE_RECEIVED, IDLE) {
            @Override
            public void activity(final OutboundFSM machine, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "START_SENT -> IDLE (for " + machine.parent.getLocalMsrpURI() + ")");
                }
                if (!signal.getResponse().getTransactionID().equals(machine.currentTransactionID)) {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "The transactionID (" + signal.getResponse().getTransactionID() + ") of the response does't match the transactionID expected (" + machine.currentTransactionID + "). Sending close signal");
                    }
                    machine.parent.terminate();
                }
            }
        });
        START_SENT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(final OutboundFSM machine, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "START_SENT -> TERMINATED (for " + machine.parent.getLocalMsrpURI() + ")");
                }
            }
        });

        IDLE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REPORT, IDLE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> IDLE (sending report)");
                }
                MsrpReportRequest report = signal.getMsrpReportRequest();
                ByteBuffer bb = getReadBuffer();
                report.marshall(bb);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.complete, report, null, null, null, null, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing report failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });
            }
        });
        IDLE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_RESPONSE, IDLE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> IDLE (sending response)");
                }
                MsrpResponse response = signal.getResponse();
                ByteBuffer bb = getReadBuffer();
                response.marshall(bb);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.complete, response, null, null, null, null, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing response failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });
            }
        });
        IDLE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST_INCOMPLETE_HEAD, SENDING_CHUNK_WAIT) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> SENDING_CHUNK_WAIT (sending a head chunk piece)");
                }
                MsrpSendRequest request = signal.getToBeSentRequest();

                // create msg
                String newTxId = m.parent.parentInstance.getNextId();
                ByteBuffer bb = getReadBuffer();
                MsrpPath fromPath = new MsrpPath(m.parent.getLocalMsrpURI());
                MsrpPath toPath = new MsrpPath(m.parent.getRemoteURI());
                int bodyLength = request.marshall(bb, null, newTxId, fromPath, toPath);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.head, request, null, newTxId, fromPath, toPath, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, new Runnable() {
                    public void run() {
                        m.bytesSent();
                    }
                }, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });

                // create and store state
                m.chunkPieceStates.put(request.getTransactionID(), m.createNewState(bodyLength, newTxId));
                m.currentTransactionID = newTxId;
                m.currentOrigTransactionID = request.getTransactionID();
                m.currentMessageStateId = signal.getMessageStateId();
            }
        });
        IDLE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST_INCOMPLETE_BODY, SENDING_CHUNK_WAIT) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> SENDING_CHUNK_WAIT (sending a body chunk piece)");
                }
                MsrpSendRequest request = signal.getToBeSentRequest();

                // get state
                ChunkPieceState state = m.chunkPieceStates.get(request.getTransactionID());
                state.setOutgoingTransactionID(m.parent.parentInstance.getNextId());

                // first, we need to generate a new header before we can continue with this body chunk piece
                ByteBuffer bb = getReadBuffer();
                ByteRange origRange = request.getByteRange();
                ByteRange newByteRange = ByteRange.create(origRange.getStart() + state.getBytesWritten(), -1, origRange.getTotal());
                MsrpPath newFrompath = new MsrpPath(m.parent.getLocalMsrpURI());
                MsrpPath newToPath = new MsrpPath(m.parent.getRemoteURI());
                request.marshallHead(
                        bb,
                        state.getOutgoingTransactionID(),
                        newByteRange,
                        true,
                        newFrompath,
                        newToPath
                );

                // now, the body piece can be attached
                int bodyLegnth = 0;
                bodyLegnth += request.marshall(bb, null, null, null, null);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.head, request, newByteRange, state.outgoingTransactionID, newFrompath, newToPath, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, new Runnable() {
                    public void run() {
                        m.bytesSent();
                    }
                }, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });

                // update state
                state.increaseBytesWrittenBy(bodyLegnth);
                m.currentOrigTransactionID = request.getTransactionID();
                m.currentMessageStateId = signal.getMessageStateId();
                m.currentTransactionID = state.getOutgoingTransactionID();
            }
        });
        IDLE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST_INCOMPLETE_TAIL, WAIT_RESPONSE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> WAIT_RESPONSE (sending a tail chunk piece)");
                }
                MsrpSendRequest request = signal.getToBeSentRequest();

                // get state
                ChunkPieceState state = m.chunkPieceStates.get(request.getTransactionID());
                state.setOutgoingTransactionID(m.parent.parentInstance.getNextId());

                // first, we need to send a new header before we can continue with this tail chunk piece
                ByteBuffer bb = getReadBuffer();
                ByteRange origRange = request.getByteRange();
                ByteRange newByteRange = ByteRange.create(
                        origRange.getStart() + state.getBytesWritten(),
                        -1,
                        origRange.getTotal());
                MsrpPath newFromPath = new MsrpPath(m.parent.getLocalMsrpURI());
                MsrpPath newToPath = new MsrpPath(m.parent.getRemoteURI());
                request.marshallHead(
                        bb,
                        state.getOutgoingTransactionID(),
                        newByteRange,
                        request.getBody().length > 0,
                        newFromPath,
                        newToPath
                );
                // now, the body piece can be attached
                request.marshall(bb, null, state.getOutgoingTransactionID(), null, null);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.complete, request, newByteRange, state.getOutgoingTransactionID(), newFromPath, newToPath, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });

                // finished with this chunk, remove state
                m.chunkPieceStates.remove(request.getTransactionID());

                // set the outgoing txId such that we can map a response to it
                m.currentTransactionID = state.getOutgoingTransactionID();
                m.currentMessageStateId = signal.getMessageStateId();
            }
        });
        IDLE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST_COMPLETE, WAIT_RESPONSE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> WAIT_RESPONSE (sending a complete chunk)");
                }
                MsrpSendRequest request = signal.getToBeSentRequest();

                String newTxId = m.parent.parentInstance.getNextId();

                ByteBuffer bb = getReadBuffer();
                MsrpPath newFrompath = new MsrpPath(m.parent.getLocalMsrpURI());
                MsrpPath newToPath = new MsrpPath(m.parent.getRemoteURI());
                request.marshall(bb, null, newTxId, newFrompath, newToPath);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.complete, request, null, newTxId, newFrompath, newToPath, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });
                m.currentTransactionID = newTxId;
                m.currentMessageStateId = signal.getMessageStateId();
            }
        });
        IDLE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> TERMINATED");
                }
            }
        });

        SENDING_CHUNK.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST_INCOMPLETE_BODY_CONTINUE, SENDING_CHUNK_WAIT) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK -> SENDING_CHUNK_WAIT (sending additional body part)");
                }
                MsrpSendRequest request = signal.getToBeSentRequest();

                ChunkPieceState state = m.chunkPieceStates.get(m.currentOrigTransactionID);

                ByteBuffer bb = getReadBuffer();
                int bodyLength = +request.marshall(bb, null, null, null, null);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.body_only, request, null, null, null, null, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, new Runnable() {
                    public void run() {
                        m.bytesSent();
                    }
                }, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });

                // keep track of the number of bytes sent
                state.increaseBytesWrittenBy(bodyLength);
            }
        });
        SENDING_CHUNK.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST_INCOMPLETE_TAIL_CONTINUE, WAIT_RESPONSE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK -> WAIT_RESPONSE (completing current chunk)");
                }
                MsrpSendRequest request = signal.getToBeSentRequest();

                ChunkPieceState state = m.chunkPieceStates.get(m.currentOrigTransactionID);

                ByteBuffer bb = getReadBuffer();
                bb.put(request.getBody());
                request.marshallTail(bb, state.getOutgoingTransactionID(), true, null);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.tail, request, null, state.getOutgoingTransactionID(), null, null, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });

                m.chunkPieceStates.remove(request.getTransactionID());
                m.currentTransactionID = state.getOutgoingTransactionID();
            }
        });
        SENDING_CHUNK.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST, WAIT_RESPONSE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK -> WAIT_RESPONSE (interrupting current chunk since new request arrived)");
                }
                MsrpSendRequest request = signal.getToBeSentRequest();

                // queue message
                m.addToQueue(signal.getToBeSentRequest(), signal.getMessageStateId());

                ChunkPieceState state = m.chunkPieceStates.get(m.currentOrigTransactionID);

                ByteBuffer bb = getReadBuffer();
                request.marshallTail(bb, state.getOutgoingTransactionID(), true, Continuation.more);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.tail, null, null, state.getOutgoingTransactionID(), null, null, Continuation.more, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });

                m.currentTransactionID = state.getOutgoingTransactionID();
            }
        });
        SENDING_CHUNK.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_RESPONSE, WAIT_RESPONSE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK -> WAIT_RESPONSE (interrupting current chunk since new response arrived)");
                }

                // queue message
                m.addToQueue(signal.getResponse(), null);

                ChunkPieceState state = m.chunkPieceStates.get(m.currentOrigTransactionID);

                // write a tail which interrupts the current chunk
                MsrpSendRequest tail = new MsrpSendRequest(
                        MsrpSendRequest.ChunkType.tail,
                        state.getOutgoingTransactionID(),
                        null,
                        null,
                        Continuation.more,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0
                );

                ByteBuffer bb = getReadBuffer();
                tail.marshallTail(bb, null, true, Continuation.more);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.tail, null, null, state.getOutgoingTransactionID(), null, null, Continuation.more, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });
                m.currentTransactionID = state.getOutgoingTransactionID();
            }
        });
        SENDING_CHUNK.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REPORT, WAIT_RESPONSE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK -> WAIT_RESPONSE (interrupting current chunk since new report arrived)");
                }

                // queue report
                m.addToQueue(signal.getMsrpReportRequest(), null);

                ChunkPieceState state = m.chunkPieceStates.get(m.currentOrigTransactionID);

                // write a tail which interrupts the current chunk
                MsrpSendRequest tail = new MsrpSendRequest(
                        MsrpSendRequest.ChunkType.tail,
                        state.getOutgoingTransactionID(),
                        null,
                        null,
                        Continuation.more,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0
                );

                ByteBuffer bb = getReadBuffer();
                tail.marshallTail(bb, null, true, Continuation.more);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.tail, null, null, state.getOutgoingTransactionID(), null, null, Continuation.more, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });
                m.currentTransactionID = state.getOutgoingTransactionID();
            }
        });
        SENDING_CHUNK.addTransition(new OutboundFSMTransition(OutboundFSMCondition.ABORT_RESPONSE_RECEIVED, IDLE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK -> IDLE (interrupting current chunk since 413 was received)");
                }

                /*
                 * write tail for current chunk
                 */
                ChunkPieceState state = m.chunkPieceStates.get(m.currentOrigTransactionID);

                // construct a request instance
                MsrpSendRequest request = new MsrpSendRequest(
                        MsrpSendRequest.ChunkType.tail,
                        state.getOutgoingTransactionID(),
                        null,
                        null,
                        Continuation.more,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0);

                ByteBuffer bb = getReadBuffer();
                request.marshallTail(bb, state.getOutgoingTransactionID(), true, Continuation.more);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.tail, null, null, state.getOutgoingTransactionID(), null, null, Continuation.more, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing request failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });

                // signal the app-layer to abort sending this msg
                m.parent.getApplication().responseReceived(m.currentMessageStateId, m.parent, signal.getResponse().getStatusCode());

            }
        });
        SENDING_CHUNK.addTransition(new OutboundFSMTransition(OutboundFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK -> TERMINATED");
                }
            }
        });

        SENDING_CHUNK_WAIT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.BYTES_SENT, SENDING_CHUNK) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK_WAIT -> SENDING_CHUNK");
                }
            }
        });
        SENDING_CHUNK_WAIT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST, SENDING_CHUNK_WAIT) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK_WAIT -> SENDING_CHUNK_WAIT (send request; queuing it for now)");
                }
                m.addToQueue(signal.getToBeSentRequest(), signal.getMessageStateId());
            }
        });
        SENDING_CHUNK_WAIT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REPORT, SENDING_CHUNK_WAIT) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK_WAIT -> SENDING_CHUNK_WAIT (send report)");
                }
                //not implemented so far
            }
        });
        SENDING_CHUNK_WAIT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_RESPONSE, SENDING_CHUNK_WAIT) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK_WAIT -> SENDING_CHUNK_WAIT (send response; queuing it for now)");
                }
                m.addToQueue(signal.getResponse(), signal.getMessageStateId(), true);
            }
        });
        SENDING_CHUNK_WAIT.addTransition(new OutboundFSMTransition(OutboundFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "SENDING_CHUNK_WAIT -> TERMINATED");
                }
            }
        });

        WAIT_RESPONSE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REPORT, WAIT_RESPONSE) {
            @Override
            public void activity(OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "WAIT_RESPONSE -> WAIT_RESPONSE (sending report)");
                }
                // not handling this while waiting for a tx to complete. Queue it and handle it later
                // todo is this correct?
                m.addToQueue(signal.getMsrpReportRequest(), null);
            }
        });
        WAIT_RESPONSE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_RESPONSE, WAIT_RESPONSE) {
            @Override
            public void activity(final OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "WAIT_RESPONSE -> WAIT_RESPONSE (sending response)");
                }
                // always sending responses, since they complete incoming transactions
                MsrpResponse response = signal.getResponse();

                ByteBuffer bb = getReadBuffer();
                response.marshall(bb);

                // log data before sending
                logOutgoingData(m.parent, MsrpSendRequest.ChunkType.complete, response, null, null, null, null, null, null, bb);

                // write data to channel
                m.parent.getChannelState().writeAsync(bb, ChannelState.DO_NOTHING, new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Writing response failed. Sending close signal now");
                        }
                        m.parent.onIOError();
                    }
                });
            }
        });
        WAIT_RESPONSE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.SEND_REQUEST, WAIT_RESPONSE) {
            @Override
            public void activity(OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "WAIT_RESPONSE -> WAIT_RESPONSE (queuing a new request)");
                }
                // not handling this while waiting for a tx to complete. Queue it and handle it later
                m.addToQueue(signal.getToBeSentRequest(), signal.getMessageStateId());
            }
        });
        WAIT_RESPONSE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.RESPONSE_RECEIVED, IDLE) {
            @Override
            public void activity(OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "WAIT_RESPONSE -> IDLE");
                }
                // test whether this response matched the txID
                MsrpResponse response = signal.getResponse();
                if (!m.currentTransactionID.equals(response.getTransactionID())) {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "The transactionID (" + signal.getResponse().getTransactionID() + ") of the response does't match the transactionID expected (" + m.currentTransactionID + "). Sending close signal");
                    }
                    m.parent.terminate();
                }

                /*
                 * Notify the application about this response
                 */
                m.parent.getApplication().responseReceived(m.currentMessageStateId, m.parent, response.getStatusCode());
            }
        });
        WAIT_RESPONSE.addTransition(new OutboundFSMTransition(OutboundFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(OutboundFSM m, OutboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "WAIT_RESPONSE -> TERMINATED");
                }
            }
        });
    }

    private final Participant parent;
    private final IOutboundFSMListener listener;

    // mutable state, guarded by the lock inside the FSM
    private String currentTransactionID;
    private String currentMessageStateId;
    private String currentOrigTransactionID;

    /**
     * The internal FIFO queue where messages are stored until the FSM is ready to handle them
     */
    private final LinkedList<QueuedMessage> messageQueue;
    private int bytesInQueue;
    private final Map<String, ChunkPieceState> chunkPieceStates;

    public OutboundFSM(Participant partent, IOutboundFSMListener listener) {
        super(INIT);
        messageQueue = new LinkedList<QueuedMessage>();
        chunkPieceStates = new HashMap<String, ChunkPieceState>();
        this.parent = partent;
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void input(OutboundFSMSignal signal) {
        throw new RuntimeException("Don't call me directly");
    }

    @Override
    public OutboundFSMSignal getSignalForQueueSizeLimitReached(State currentState) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Returning close signal");
        }
        return OutboundFSMSignal.getCloseSignal();
    }

    /**
     * Called by the Participant class in order to let this class generate and
     * send a response to the received handshake request
     */
    protected void completeHandshake() {
        // When calling this method, the FSM should be in the INIT state
        try {
            super.input(OutboundFSMSignal.getCompleteHandshakeSignal());
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Called by the Participant class in order to let this class
     * generate and send a handshake request and transition to the
     * START_SENT state.
     */
    protected void startHandShake() {
        // When calling this method, the FSM should be in the INIT state
        try {
            super.input(OutboundFSMSignal.getPerformHandshakeSignal());
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Called by the Participant class as a response was recevied
     *
     * @param response the received response
     */
    protected void responseReceived(MsrpResponse response) {
        try {
            super.input(OutboundFSMSignal.getResponseReceivedSignal(response));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Could not handle signal at this moment");
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Instructs this FSM to send a report.
     * In case the FSM is currently not able to handle this request,
     * the message will be stored and handles later.
     *
     * @param reportRequest the report to be sent
     */
    protected void sendReport(MsrpReportRequest reportRequest) {
        try {
            super.input(OutboundFSMSignal.getSendReportSignal(reportRequest));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Could not handle signal at this moment");
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Instructs this FSM to send a response.
     * In case the FSM is currently not able to handle this request,
     * the message will be stored and handles later.
     *
     * @param response the response to be sent
     */
    protected void sendResponse(MsrpResponse response) {
        try {
            super.input(OutboundFSMSignal.getSendResponseSignal(response));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Could not handle signal at this moment");
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Instructs this FSM to send a sendRequest.
     * In case the FSM is currently not able to handle this request,
     * the message will be stored and handles later.
     *
     * @param request        the request to be sent
     * @param messageStateId some id which identifies some state in the app-layer related to this message
     */
    protected void sendSendRequest(MsrpSendRequest request, String messageStateId) {
        try {
            super.input(OutboundFSMSignal.getSendRequestSignal(request, messageStateId));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Could not handle signal at this moment");
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Warning: Only for internal use. Must never be called from anyone else than the FSM itself
     *
     * @return the txID
     */
    public String getCurrentTransactionID() {
        return currentTransactionID;
    }

    /**
     * Warning: Only for internal use. Must never be called from anyone else than the FSM itself
     *
     * @return the orig txID
     */
    public String getCurrentOrigTransactionID() {
        return currentOrigTransactionID;
    }

    /**
     * Adds a new message to the FIFO queue. In case the size limit is
     * exceeded, this method send a terminate signal to the participant
     *
     * @param message        the message to be added
     * @param messageStateId the id which identifies some state in the app-layer related to this message
     */
    private void addToQueue(IMsrpMessage message, String messageStateId) {
        addToQueue(message, messageStateId, false);
    }

    /**
     * Adds a new message to the FIFO queue. In case the size limit is
     * exceeded, this method send a terminate signal to the participant
     *
     * @param message        the message to be added
     * @param messageStateId the id which identifies some state in the app-layer related to this message
     * @param firstPlace     to true in case this message should be added at the top of the queu
     */
    private void addToQueue(IMsrpMessage message, String messageStateId, boolean firstPlace) {
        QueuedMessage queuedMessage = new QueuedMessage(message, messageStateId);
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "About to add a " + message.getType() + " to the outbound message queue");
            if (message.getType() == IMsrpMessage.Type.request) {
                MsrpSendRequest r = (MsrpSendRequest) message;
                Logger.d(TAG, "Request has type: " + r.getChunkType());
            }
            Logger.d(TAG, "Queue size before adding new message is: " + messageQueue.size());
        }

        if (bytesInQueue > listener.getMaxOutboudQueueSize()) {
            /*
             * Queue limit about to be exceeded.
             * terminate this participant
             */
            parent.terminate();
        }

        // proceed no matter what, the terminate signal is fired/handles async


        if (!firstPlace)
            messageQueue.offer(queuedMessage);
        else
            messageQueue.addFirst(queuedMessage);

        bytesInQueue += message.getSize();
    }

    /**
     * Called by the FSM in order to get the next message from the FIFO queue to be sent
     */
    private void scheduleNextRequest() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter (queueSize=" + messageQueue.size() + ")");
        }

        QueuedMessage message;
        if ((message = messageQueue.poll()) != null) {
            bytesInQueue -= message.getMsg().getSize();
            OutboundFSMSignal signal = null;
            switch (message.getMsg().getType()) {
                case report:
                    signal = OutboundFSMSignal.getSendReportSignal((MsrpReportRequest) message.getMsg());
                    break;
                case request:
                    signal = OutboundFSMSignal.getSendRequestSignal((MsrpSendRequest) message.getMsg(), message.getMessageStateId());
                    break;
                case response:
                    signal = OutboundFSMSignal.getSendResponseSignal((MsrpResponse) message.getMsg());
                    break;
            }

            try {
                super.input(signal);
            } catch (UnhandledConditionException e) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "FSM not able to accept this signal");
                }
            } catch (InterruptedException e) {
                Logger.e(TAG, "", e);
            } catch (TransitionActivityException e) {
                Logger.e(TAG, "", e);
            }
        }
    }

    private float getCurrentQueueUsage() {
        return ((float) bytesInQueue) / ((float) listener.getMaxOutboudQueueSize());
    }

    private void bytesSent() {
        try {
            super.input(OutboundFSMSignal.getBytesSentSignal());
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    private ChunkPieceState createNewState(int bytesWritten, String outgoingTransactionID) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Creating new chunkPieceState for txID=" + outgoingTransactionID + ", bytesWritten=" + bytesWritten);
        }
        ChunkPieceState state = new ChunkPieceState();
        state.setOutgoingTransactionID(outgoingTransactionID);
        state.increaseBytesWrittenBy(bytesWritten);
        return state;
    }

    public static ByteBuffer getReadBuffer() {
        ByteBuffer bb = writeBuffers.get();
        bb.clear();
        return bb;
    }

    private static void logOutgoingData(Participant instance,
                                        MsrpSendRequest.ChunkType chunkType,
                                        IMsrpMessage baseRequest,
                                        ByteRange overrideByteRange,
                                        String overrideTransactionID,
                                        MsrpPath overrideFrom,
                                        MsrpPath overrideTo,
                                        Continuation overrideContinuation,
                                        byte[] payload,
                                        ByteBuffer bb) {
        IMsrpTrafficLogger logger = Participant.TRAFFIC_LOGGER.get();
        if (logger != null) {
            logger.logoutgoingData(instance, chunkType, baseRequest, overrideByteRange, overrideTransactionID, overrideFrom, overrideTo, overrideContinuation, payload, bb);
        }
    }

    public boolean cannotBeClosed() {
        return super.getUnsafeCurrentState() == WAIT_RESPONSE;
    }

    private class ChunkPieceState {
        private int bytesWritten = 0;
        private String outgoingTransactionID;

        public int getBytesWritten() {
            return bytesWritten;
        }

        public void increaseBytesWrittenBy(int increment) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Increating byteCounter for txID=" + outgoingTransactionID + ": " + this.bytesWritten + " -> " + (this.bytesWritten + increment));
            }
            this.bytesWritten = this.bytesWritten + increment;
        }

        public String getOutgoingTransactionID() {
            return outgoingTransactionID;
        }

        public void setOutgoingTransactionID(String outgoingTransactionID) {
            this.outgoingTransactionID = outgoingTransactionID;
        }
    }

    private class QueuedMessage {
        private final IMsrpMessage msg;
        private final String messageStateId;

        public QueuedMessage(IMsrpMessage msg, String messageStateId) {
            this.msg = msg;
            this.messageStateId = messageStateId;
        }

        public IMsrpMessage getMsg() {
            return msg;
        }

        public String getMessageStateId() {
            return messageStateId;
        }
    }
}

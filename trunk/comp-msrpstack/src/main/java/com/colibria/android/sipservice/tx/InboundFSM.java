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
import com.colibria.android.sipservice.fsm.UnhandledConditionException;
import com.colibria.android.sipservice.headers.MsrpResponse;
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.fsm.Machine;
import com.colibria.android.sipservice.fsm.TransitionActivityException;
import com.colibria.android.sipservice.headers.Continuation;
import com.colibria.android.sipservice.headers.MsrpReportRequest;
import com.colibria.android.sipservice.tx.fsm.InboundFSMCondition;
import com.colibria.android.sipservice.tx.fsm.InboundFSMSignal;
import com.colibria.android.sipservice.tx.fsm.InboundFSMState;
import com.colibria.android.sipservice.tx.fsm.InboundFSMTransition;

import java.util.LinkedList;
import java.util.Queue;


/**
 * <pre>
 * <p/>
 * <p/>
 *                                                        onPauseComplete
 *                  +------------------------------------------------------+
 *                  |                                                      |           onChunkReceived
 *                  |                                                      |          +--------------+
 *                  |                                                      |          v              |
 *                  |               onChunkWithoutEndReceived            +---------------+           |
 *                  v             +------------------------------------->| PAUSE_CHANNEL |-----------+
 *              +------+          |                                      +---------------+
 *              |      |---->-----+                                                    |
 *              | IDLE |---->------------------------+                                 | onClose
 *              |      |<---<--------------+         | onChunkWithEndReceived          |
 *              +------+  onPauseComplete  |         v                                 |
 *                  |                      |     +-------------------------------+     |
 *                  |                      +-----| PAUSE_BEFORE_SENDING_RESPONSE |     |
 *          onClose |                            +-------------------------------+     |
 *                  |                                     |                            |
 *                  |                        +------------+                            |
 *                  |                        |  onClose                                |
 *                  |                        v                                         |
 *                  |                +------------+                                    |
 *                  +--------------->| TERMINATED |<-----------------------------------+
 *                                   +------------+
 * <p/>
 * <p/>
 * IDLE                           : The inboud FSM is ready to accept new received data
 * PAUSE_CHANNEL                  : This state is entered after a chunk piece was recieved with doesn't have an
 *                                  end (tail). If getPause() returns a positive number, then the underlying
 *                                  channel will be suspended to prevent that grizzly get's new read-events.
 *                                  Suspending is possible by making sure that grizzly does not register OP_READ
 *                                  after handling a read-event. When this state is left, the channel is resumed
 *                                  and new data can be read from it.
 * PAUSE_BEFORE_SENDING_RESPONSE  : This state is used when the incoming chunk is completely read and we are
 *                                  about to send a response to ack the request. However, in order to be able
 *                                  to throttle-down the incoming request rate, a pause might happen if needed.
 *                                  One leaving this state, the response will be sent.
 * TERMINATED                     : This state is used when the FSM has terminated and cannot handle more data
 * </pre>
 *
 * @author Sebastian Dehne
 */
public class InboundFSM extends Machine<InboundFSMSignal> {
    private static final String TAG = "InboundFSM";

    /*
     * The states
     */
    public static final InboundFSMState IDLE = new InboundFSMState("IDLE") {
        @Override
        public void enter(InboundFSM m, boolean reEnter) {
            m.bytesReceived = 0;
            MsrpSendRequest nextRequest;
            if ((nextRequest = m.popFromQueue()) != null) {
                m.receivedSendRequest(nextRequest);
            }
        }
    };
    public static final InboundFSMState PENDING = new InboundFSMState("PENDING") {
        @Override
        public void enter(InboundFSM owner, boolean reEnter) {
            owner.sendResponse();
        }
    };
    public static final InboundFSMState TERMINATED = new InboundFSMState("TERMINATED");

    /*
     * The state transitions
     */

    static {
        IDLE.addTransition(new InboundFSMTransition(InboundFSMCondition.RECEIVED_REQUEST_HAS_END, PENDING) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> PENDING");
                }
                machine.additionalBytesReceived(signal.getReceivedSendRequest());
                machine.pendingRequest = signal.getReceivedSendRequest();
            }
        });
        IDLE.addTransition(new InboundFSMTransition(InboundFSMCondition.RECEIVED_REQUEST_HAS_NO_END, IDLE) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PAUSE_CHANNEL -> IDLE");
                }

                machine.additionalBytesReceived(signal.getReceivedSendRequest());

                // log chunk piece
                logIncomingdata(machine.parent, signal.getReceivedSendRequest());

                // now forward chunk (piece) to application
                MsrpResponse.ResponseCode errorResponse = machine.parent.getApplication().requestReceived(machine.parent, signal.getReceivedSendRequest());

                if (errorResponse != MsrpResponse.RESPONSE_200_OK && !machine.sentResponseForCurrentTransaction) {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "Application requests to send a response for this currently incoming chunk");
                    }
                    MsrpResponse response = MsrpResponse.create(signal.getReceivedSendRequest(), errorResponse);
                    machine.parent.outboundFSM.sendResponse(response);

                    // ensure that we don't send a second response when we see the end-line
                    machine.sentResponseForCurrentTransaction = true;
                }

            }
        });
        IDLE.addTransition(new InboundFSMTransition(InboundFSMCondition.RECEIVED_RESPONSE, IDLE) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> IDLE (response received)");
                }
                machine.parent.outboundFSM.responseReceived(signal.getReceivedResponse());
            }
        });
        IDLE.addTransition(new InboundFSMTransition(InboundFSMCondition.RECEIVED_REPORT, IDLE) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> IDLE received report");
                }
                // machine.additionalBytesReceived();
                //todo do something with the report
            }
        });
        IDLE.addTransition(new InboundFSMTransition(InboundFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "IDLE -> TERMINATED");
                }
            }
        });

        PENDING.addTransition(new InboundFSMTransition(InboundFSMCondition.RECEIVED_REPORT, PENDING) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PENDING -> PENDING (received report)");
                }
                //todo do something with the report
            }
        });
        PENDING.addTransition(new InboundFSMTransition(InboundFSMCondition.RECEIVED_RESPONSE, PENDING) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PENDING -> PENDING (response received)");
                }
                machine.parent.outboundFSM.responseReceived(signal.getReceivedResponse());
            }
        });
        PENDING.addTransition(new InboundFSMTransition(InboundFSMCondition.RECEIVED_REQUEST, PENDING) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PENDING -> PENDING (cannot handle data in pause, queuing it for later)");
                }
                machine.additionalBytesReceived(signal.getReceivedSendRequest());
                machine.addToQueue(signal.getReceivedSendRequest());
            }
        });
        PENDING.addTransition(new InboundFSMTransition(InboundFSMCondition.SEND_RESPONSE, IDLE) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PENDING -> IDLE");
                }

                MsrpResponse.ResponseCode responseCode = MsrpResponse.RESPONSE_200_OK;

                // forward the request to the conference
                if (machine.pendingRequest.isChunkType(MsrpSendRequest.ChunkType.complete) &&
                        machine.pendingRequest.getBody().length < 1 &&
                        machine.pendingRequest.getContinuation() == Continuation.done &&
                        (machine.pendingRequest.getByteRange() == null || machine.pendingRequest.getByteRange().getTotal() <= 0)) {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "Not forwarding the request to the application since this looks like a keep-alive message");
                    }
                } else {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "Forwarding request to msrp application now");
                    }
                    // log chunk piece
                    logIncomingdata(machine.parent, machine.pendingRequest);

                    // send received data to application
                    responseCode = machine.parent.getApplication().requestReceived(machine.parent, machine.pendingRequest);
                }

                if (!machine.sentResponseForCurrentTransaction) {
                    // send the response
                    MsrpResponse response = MsrpResponse.create(machine.pendingRequest, responseCode);
                    machine.parent.outboundFSM.sendResponse(response);
                } else {
                    // response already sent
                    machine.sentResponseForCurrentTransaction = false;
                }

            }
        });
        PENDING.addTransition(new InboundFSMTransition(InboundFSMCondition.CLOSE, IDLE) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PAUSE_BEFORE_SENDING_RESPONSE -> TERMINATED");
                }
            }
        });
        PENDING.addTransition(new InboundFSMTransition(InboundFSMCondition.ANY, PENDING) {
            @Override
            public void activity(InboundFSM machine, InboundFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PENDING -> PENDING (unexpected signal received: " + signal.getType() + "; sending terminate signal now)");
                }
                machine.parent.terminate();
            }
        });
    }

    private final Participant parent;

    // mutable state guarded by the lock of the FSM
    private final Queue<MsrpSendRequest> inputQueue;
    private MsrpSendRequest pendingRequest;
    private long messageTimer;
    private int bytesReceived;
    private boolean sentResponseForCurrentTransaction;

    public InboundFSM(Participant participant) {
        super(IDLE);
        this.parent = participant;
        inputQueue = new LinkedList<MsrpSendRequest>();
        messageTimer = System.currentTimeMillis();

    }

    public void receivedSendRequest(MsrpSendRequest request) {
        try {
            super.input(InboundFSMSignal.getReceivedSendRequestSignal(request));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "FSM not able to handle request now.");
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    public void receivedReportRequest(MsrpReportRequest report) {
        try {
            super.input(InboundFSMSignal.getReceivedReportRequestSignal(report));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "FSM not able to handle request now.");
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    public void recveiedResponse(MsrpResponse response) {
        try {
            super.input(InboundFSMSignal.getReceivedResponseSignal(response));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "FSM not able to handle request now.");
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    public void input(InboundFSMSignal signal) {
        throw new RuntimeException("Don't call this method directly");
    }

    /**
     * Called by the FSM itself in order to send a response out
     * in order to complete an incoming transaction
     */
    private void sendResponse() {
        try {
            super.input(InboundFSMSignal.getSendResponseSignal());
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    private void addToQueue(MsrpSendRequest msrpMessage) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "size before add: " + inputQueue.size());
        }
        inputQueue.offer(msrpMessage);
    }

    private MsrpSendRequest popFromQueue() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "size before pop: " + inputQueue.size());
        }
        return inputQueue.poll();
    }

    private void additionalBytesReceived(MsrpSendRequest request) {
        bytesReceived += request.getSize();
    }

    private static void logIncomingdata(Participant receiver, MsrpSendRequest data) {
        IMsrpTrafficLogger logger = Participant.TRAFFIC_LOGGER.get();
        if (logger != null) {
            logger.logincomingData(receiver, data);
        }
    }
}

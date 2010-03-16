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

import com.colibria.android.sipservice.IMsrpApplication;
import com.colibria.android.sipservice.IMsrpTrafficLogger;
import com.colibria.android.sipservice.fsm.TransitionActivityException;
import com.colibria.android.sipservice.fsm.UnhandledConditionException;
import com.colibria.android.sipservice.headers.*;
import com.colibria.android.sipservice.io.ChannelState;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.fsm.Machine;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.IMsrpResources;
import com.colibria.android.sipservice.tx.fsm.LifeCycleFSMCondition;
import com.colibria.android.sipservice.tx.fsm.LifeCycleFSMSignal;
import com.colibria.android.sipservice.tx.fsm.LifeCycleFSMState;
import com.colibria.android.sipservice.tx.fsm.LifeCycleFSMTransition;
import com.colibria.android.sipservice.headers.MsrpResponse;
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.headers.MsrpURI;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


/**
 * The participant class. It's internal FSM represents the life cycle
 * of an instance of this class. An instance of this class is created when a new
 * participant is added to a conference. This instance will be in the
 * Conference.allParticipants collection. From that moment on wards, there are
 * two possible ways to active this participant:
 * <p/>
 * A) Local connection setup: the msrp-switch is instructed to establish a new outbound
 * connection and perform the handshake.
 * B) Remote connection setup: the msrp-switch waits for an inbound connection and handshake
 * <p/>
 * <pre>
 * <p/>
 *                          +--------+
 *                          |  INIT  |
 *                          +--------+
 *       onOutboundConnect    | |  |
 *         +------------------+ |  +----------+
 *         |                    |             |
 *         |                    |             |
 *         |            +-------+             |
 *         |            |                     |
 *         |            | bind                |
 *         v            |                     |
 *   +------------+     |                     |
 *   | CONNECTING |     |                     |
 *   +------------+     |                     v
 *        |    |        |                     |
 *        |    | bind   |                     |
 *        |    v        v                     |
 *        |  +-----------+                    |
 *        v  | HANDSHAKE |                    |
 *        |  +-----------+                    |
 *        |        |   | onHandshake-         |
 *        |        |   |    completed         v
 *        |<-------+   +--------+             |
 *        |                     |             |
 *        |                     |             |
 *        |                     v             |
 *        |                 +-------+         |
 *        v                 | BOUND |         |
 *        |                 +-------+         | onClose
 *        |                     |             |
 *        | onError             | onClose     v
 *        |                     v             |
 *        |             +------------+        |
 *        +------------>| TERMINATED |<-------+
 *                      +------------+
 * <p/>
 * INIT       : The initial state when an instance of this class is created
 * CONNECTING : In of local connection setup, this state is used when outbound connection setup
 *              is in progress
 * HANDSHAKE  : In this state, the handshake is performed. This state is both used when the handshake is triggered from
 *              the local end or the remote end. When entering this state, the underlying FSM's are prepared.
 *              In case of local connection setup, the inboundFSM will be put to the IDLE state and the
 *              outboundFSM sends the handshake request and transitions to START_SENT state
 *              In case of remote connection setup, the inboundFSM will be put to the IDLE state and the
 *              outboundFSM sends a reponse to the incoming handshake requests and transitions to IDLE.
 * BOUND      : The participant is bound to a connection, handshake is completed and the participant is able
 *              to handle traffic using the inbound- and outboundFSM
 * TERMINATED : the connection setup failed OR the connection has been closed and the
 *              participant terminates and leaves its conference/session.
 * <p/>
 * All states are non-blocking, thus signals for unspecified transitions will bounce off
 * with an UnhandledConditionException
 * </pre>
 *
 * @author Sebastian Dehne
 */
public class Participant extends Machine<LifeCycleFSMSignal> {
    private static final String TAG = "Participant";

    protected static final AtomicReference<IMsrpTrafficLogger> TRAFFIC_LOGGER = new AtomicReference<IMsrpTrafficLogger>();

    /**
     * Sets a logger implementations to be used by the msrp-stack
     *
     * @param logger the logger implementations to be used
     */
    public static void setTrafficLogger(IMsrpTrafficLogger logger) {
        TRAFFIC_LOGGER.set(logger);
    }

    /*
     * The states
     */
    public static final LifeCycleFSMState INIT = new LifeCycleFSMState("INIT");
    public static final LifeCycleFSMState HANDSHAKE = new LifeCycleFSMState("HANDSHAKE");
    public static final LifeCycleFSMState BOUND = new LifeCycleFSMState("BOUND");
    public static final LifeCycleFSMState TERMINATED = new LifeCycleFSMState("TERMINATED") {
        public void enter(Participant p, boolean reEnter) {
            if (!reEnter) {
                // unregister from channelState
                if (p.channelState != null) {
                    p.channelState.unregister(p);
                }

                // stop timeout timer if running
                p.stopTimeoutTimer();

                // remove from the application
                p.application.participantTerminated(p);

                // remove from global map
                Participants.getInstance().unmap(p);
            }
        }
    };

    /*
     * The state transitions
     */

    static {
        INIT.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.CONNECTION_UP, HANDSHAKE) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "CONNECTING -> HANDSHAKE (localURI:" + p.localMsrpURI + "; channel:" + signal.getChannelState() + ")");
                }
                p.channelState = signal.getChannelState();

                // send the handshake request
                p.startHandShake();
            }
        });
        INIT.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.CONNECT_FAILED, TERMINATED) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "CONNECTING -> TERMINATED (CONNECT_FAILED) (localURI:" + p.localMsrpURI + ")");
                }
            }
        });
        INIT.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "CONNECTING -> TERMINATED (CLOSE) (localURI:" + p.localMsrpURI + ")");
                }
            }
        });

        HANDSHAKE.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.HANDSHAKE_COMPLETED, BOUND) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "HANDSHAKE -> BOUND (localURI:" + p.localMsrpURI + ")");
                }
                p.outboundFSM.responseReceived(signal.getHandshakeResponse());

                // tell the app that we are now active
                p.application.participantActivated(p);
            }
        });
        HANDSHAKE.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "HANDSHAKE -> TERMINATED (localURI:" + p.localMsrpURI + ")");
                }
            }
        });

        BOUND.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.CLOSE_REQUEST, BOUND) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "BOUND -> BOUND (CLOSE_REQUEST, localURI:" + p.localMsrpURI + ")");
                }
                p.scheduleTimeoutTimer();
            }
        });
        BOUND.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.CLOSE, TERMINATED) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "BOUND -> TERMINATED (localURI:" + p.localMsrpURI + ")");
                }
            }
        });

        TERMINATED.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.CONNECTION_UP, TERMINATED) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "TERMINATED -> TERMINATED (CONNECTION_UP) (localURI:" + p.localMsrpURI + ")");
                }

                // too late, unregister from channel
                if (signal.getChannelState() != null) {
                    signal.getChannelState().unregister(p);
                }
            }
        });
        TERMINATED.addTransition(new LifeCycleFSMTransition(LifeCycleFSMCondition.CONNECT_FAILED, TERMINATED) {
            @Override
            public void activity(Participant p, LifeCycleFSMSignal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "TERMINATED -> TERMINATED (CONNECT_FAILED) (localURI:" + p.localMsrpURI + ")");
                }
                // ok, no problem
            }
        });
    }

    protected final IMsrpResources parentInstance;
    protected final OutboundFSM outboundFSM;
    protected final InboundFSM inboundFSM;
    private final MsrpURI localMsrpURI;
    private final IMsrpApplication application;
    private final Address cpimAddress;


    /*
     * The following mutable state is guarded by lock in the FSM
     */
    private volatile MsrpURI remoteURI;
    private ScheduledFuture timeoutTimerTask;
    private ChannelState channelState;

    public Participant(IMsrpResources parentInstance, MsrpURI localMsrpURI, IMsrpApplication application, Address cpimAddress, IOutboundFSMListener listener) {
        super(INIT);
        this.parentInstance = parentInstance;
        this.localMsrpURI = localMsrpURI;
        this.application = application;
        this.cpimAddress = cpimAddress;
        this.outboundFSM = new OutboundFSM(this, listener);
        this.inboundFSM = new InboundFSM(this);
    }

    public MsrpURI getLocalMsrpURI() {
        return localMsrpURI;
    }

    /**
     * Returns the remote URL. May return null if the remoteURI has not been
     * discovered yet (before the connection was established)
     *
     * @return the msrpURI of the remote party
     */
    public MsrpURI getRemoteURI() {
        return remoteURI;
    }

    public void setRemoteURI(MsrpURI remoteURI) {
        this.remoteURI = remoteURI;
    }

    /**
     * Called from the channelState in case the underlying bound channel was closed
     */
    public void terminate() {
        try {
            super.input(LifeCycleFSMSignal.createCloseSignal(false));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Could not close channel", e);
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Causes this participant to register itself to a given channelState and perform
     * an outgoing handshake over this channel.
     *
     * @param channelState the channel it should bind itself to
     */
    public void connectionUp(ChannelState channelState) {
        // we are in CONNECTING state when this method gets called
        try {
            super.input(LifeCycleFSMSignal.createConnectionUpSignal(channelState));
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    public void outgoingConnectFailed() {
        try {
            super.input(LifeCycleFSMSignal.createConnectFailedSignal());
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Called from the I/O layer upon receiving a request
     *
     * @param request the request which was received by the InboudFSM
     */
    public void handleIncomingRequest(final MsrpSendRequest request) {
        try {
            super.checkAndPerform(new Callable<Object>() {
                public Object call() throws Exception {
                    inboundFSM.receivedSendRequest(request);
                    return null;
                }
            }, BOUND);
        } catch (UnhandledConditionException e) {
            //current state doesn't allow this
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Called from the I/O layer upon receiving a response
     *
     * @param response the response which was received by the InboudFSM
     */
    public void handleIncomingResponse(final MsrpResponse response) {

        /*
         * Instead of going through a state-transition for each response
         * we receive here, perform the task with the checkAndPerform()
         * function instead.
         */

        try {
            super.checkAndPerform(new Callable<Object>() {
                public Object call() throws Exception {
                    outboundFSM.responseReceived(response);
                    if (timeoutTimerTask != null) {
                        injectCloseSignal(false);
                    }
                    return null;
                }
            }, BOUND);
        } catch (UnhandledConditionException e) {
            // maybe we are in HANDSHAKE state?

            try {
                super.input(LifeCycleFSMSignal.createHandshakeCompletedSignal(response));
            } catch (UnhandledConditionException e1) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Foced to ignore response");
                }
            } catch (InterruptedException e1) {
                Logger.e(TAG, "", e1);
            } catch (TransitionActivityException e1) {
                Logger.e(TAG, "", e1);
            }
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Called by the conference to start a new outbound transaction for this participant
     *
     * @param request        the request to be sent
     * @param messageStateId the id which identifies some state in the app-layer
     */
    public void handleOutgoingRequest(final MsrpSendRequest request, final String messageStateId) {

        try {
            super.checkAndPerform(new Callable<Object>() {
                public Object call() throws Exception {
                    outboundFSM.sendSendRequest(request, messageStateId);
                    return null;
                }
            }, BOUND);
        } catch (UnhandledConditionException e) {
            // participant just terminated or so
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "", e);
            }
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }

    }

    /**
     * Returns the channelState which this participant is bound to.
     * This method must only be called by a thread which owns the lock of
     * the underlying FSM!
     *
     * @return the channelState which this participant is bound to
     */
    protected ChannelState getChannelState() {
        return channelState;
    }

    protected IMsrpApplication getApplication() {
        return application;
    }

    public URI getCpimURI() {
        return cpimAddress.getUri();
    }

    public Address getCpimAddress() {
        return cpimAddress;
    }

    public String toString() {
        return "Participant-" + localMsrpURI.toString();
    }

    public void onIOError() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "IOError detected, closing the channel");
        }
        channelState.close();
    }

    public void input(LifeCycleFSMSignal signal) {
        throw new RuntimeException("Don't call me directly");
    }

    private void injectCloseSignal(boolean triggeredByTimeoutTimer) {
        try {
            super.input(LifeCycleFSMSignal.createCloseSignal(triggeredByTimeoutTimer));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "", e);
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    private void startHandShake() {
        outboundFSM.startHandShake();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Participant that = (Participant) o;

        return !(localMsrpURI != null ? !localMsrpURI.equals(that.localMsrpURI) : that.localMsrpURI != null);

    }

    public int hashCode() {
        return (localMsrpURI != null ? localMsrpURI.hashCode() : 0);
    }

    private void scheduleTimeoutTimer() {
        if (timeoutTimerTask == null) {
            timeoutTimerTask = parentInstance.getThreadFarm().schedule(new Runnable() {
                public void run() {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "Close timer fired");
                    }
                    injectCloseSignal(true);
                }
            }, 10, TimeUnit.SECONDS);
        }
    }

    private void stopTimeoutTimer() {
        if (timeoutTimerTask != null) {
            timeoutTimerTask.cancel(false);
            timeoutTimerTask = null;
        }
    }

    public boolean cannotOutboundFsmBeClosed() {
        return outboundFSM.cannotBeClosed();
    }

    public void sendKeepAlive() {
        parentInstance.getThreadFarm().execute(new Runnable() {
            @Override
            public void run() {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Sending msrp keep-alive now...");
                }
                // send an empty msrp request
                MsrpSendRequest handShake = MsrpSendRequest.generateHandShake(
                        parentInstance.getNextId(),
                        new MsrpPath(getRemoteURI()),
                        new MsrpPath(getLocalMsrpURI()),
                        parentInstance.getNextId());
                handleOutgoingRequest(handShake, null);
            }
        });
    }
}

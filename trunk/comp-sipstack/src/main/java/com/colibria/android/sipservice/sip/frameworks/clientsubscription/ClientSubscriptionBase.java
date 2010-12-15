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
package com.colibria.android.sipservice.sip.frameworks.clientsubscription;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.fsm.State;
import com.colibria.android.sipservice.fsm.TransitionActivityException;
import com.colibria.android.sipservice.fsm.UnhandledConditionException;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.SipStack;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.fsm.Machine;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.frameworks.clientsubscription.fsm.*;
import com.colibria.android.sipservice.sip.headers.MinExpiresHeader;
import com.colibria.android.sipservice.sip.headers.RouteHeader;
import com.colibria.android.sipservice.sip.headers.SubscriptionStateHeader;
import com.colibria.android.sipservice.sip.messages.*;
import com.colibria.android.sipservice.sip.tx.ClientTransaction;
import com.colibria.android.sipservice.sip.tx.IInDialogRequestHandler;
import com.colibria.android.sipservice.sip.frameworks.clientsubscription.fsm.Signal;
import com.colibria.android.sipservice.sip.frameworks.clientsubscription.fsm.SubscriptionState;
import com.colibria.android.sipservice.sip.frameworks.clientsubscription.fsm.SubscriptionTransition;
import com.colibria.android.sipservice.sip.frameworks.clientsubscription.fsm.TerminatedReason;
import com.colibria.android.sipservice.sip.headers.EventHeader;
import com.colibria.android.sipservice.sip.tx.Dialog;
import com.colibria.android.sipservice.sip.tx.TransactionBase;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * <pre>
 * <p/>
 * <p/>
 *                  +------+
 *                  | INIT |
 *                  +------+
 *                      |
 *                      | onFetch() / onSubscribe()
 *                      |
 *                      |
 *                      |     +------------+
 *  onError()/          v     v            | on481() / onIntervalTooBrief()
 * onTimeout()  +-------------------+      |
 *       +--<---| WAIT_FOR_RESPONSE |------+
 *       |      +-------------------+
 *       |               | | ^
 *       |               | | |  onUnsubscribe() / onRefresh()
 *       |               | | +------------------------------+
 *       |               | |                                |
 *       |       on2XX & | | on2XX & expires > 0            ^
 *       |  expires <= 0 | +-----------------------------+  |
 *       |               |                               |  |
 *       |               |                               |  |  +---------+
 *       |               v                               v  |  v         | onNotifyActive()
 *       |      +-----------------+                    +--------+        |
 *       +--<---| WAIT_FOR_NOTIFY |------------------> | ACTIVE |--------+
 *       |      +-----------------+  onNotifyActive()  +--------+
 *       |             |
 *       |             | onNotifyNonActive()
 *       |             |
 *       |             v
 *       |       +------------+
 *       +------>| TERMINATED |
 *               +------------+
 * <p/>
 * The following states are configured to be blocking (All signals which are applied
 * during a "blocking-state" which do not result in a valid transaction will be blocked)
 * <p/>
 *  INIT                : non-blocking
 *  WAIT_FOR_RESPONSE   : blocking (only responses are accepted; otherwise SIP-stack will send timeout)
 *  WAIT_FOR_NOTIFY     : blocking (only Notify requests are accepted, otherwise: timeout)
 *  ACTIVE              : non-blocking
 *  TERMINATED          : non-blocking
 * </pre>
 * <p/>
 * A word about thread-safety:
 * This class is thread-safe (no additional locking is required when calling any of the public methods)
 * Thread-safety is achieved through the underlying state-machine; all mutable state variables
 * are only accessed throught the state-machine.
 * Classes which extend this class can therefore rely on the fact that
 * any of the abstract method will only be called in a serialized manner, since those methods
 * will always be called through the state-machine.
 * Also, classes which extend this class MUST NOT override any of the public method and
 * SHOULD always use the abstract hock-methods for extended implementations.
 * <p/>
 * In order to prevent dead locking, a lock order strategy has been defined, which is upwards.
 * All threads calling from the upper application towards this class MUST not own any locks (safest way is
 * to schedule call into another thread) while all calls from this class upwards to the
 * application (called to the abstract methods, for example handleNotify()) can be safely used
 * to acquire whatever locks in the upper application layer.
 * <p/>
 * <code>$Id: ClientSubscriptionBase.java 61686 2009-10-29 17:49:08Z dehne $</code>
 *
 * @author Arild Nilsen
 * @author Sebastian Dehne (version 2)
 * @version $Revision: 61686 $
 */
public abstract class ClientSubscriptionBase extends Machine<Signal> implements IInDialogRequestHandler {
    private static final String TAG = "ClientSubscriptionBase";

    private static volatile long SAFE_TIME = 10000L; // is volatile since it has a setter method and can be changed after initialization
    private static final long NOTIFY_TIMEOUT = 30000L; // maximum waiting time before this subscription dies; when we expect to receive a NOTIFY

    public static enum SubscribeType {
        initial,
        resubscribe,
        refresh,
        unsubscribe
    }

    /*
     * Defining the FSM:
     */
    public static SubscriptionState INIT = new SubscriptionState("INIT", false) {
        public void exit(ClientSubscriptionBase clientSubscriptionBase, boolean reEnter) {
            clientSubscriptionBase.isInitialOrTerminating = true;
            clientSubscriptionBase.rejectReasonCode = 0;
            clientSubscriptionBase.onStateChanged();
        }
    };
    public static SubscriptionState WAIT_FOR_RESPONSE = new SubscriptionState("WAIT_FOR_RESPONSE", true) {
        @Override
        public void enter(ClientSubscriptionBase owner, boolean reEnter) {
            owner.onStateChanged();
        }
    };
    public static SubscriptionState ACTIVE = new SubscriptionState("ACTIVE", false) {
        public void enter(ClientSubscriptionBase clientSubscriptionBase, boolean reEnter) {
            clientSubscriptionBase.scheduleRefresh();
            clientSubscriptionBase.onStateChanged();
        }

        public void exit(ClientSubscriptionBase clientSubscriptionBase, boolean reEnter) {
            clientSubscriptionBase.cancelRefresh();
        }
    };
    public static SubscriptionState WAIT_FOR_NOTIFY = new SubscriptionState("WAIT_FOR_NOTIFY", true) {
        public void enter(ClientSubscriptionBase clientSubscriptionBase, boolean reEnter) {
            clientSubscriptionBase.scheduleNotifyTimeout();
            clientSubscriptionBase.onStateChanged();
        }

        public void exit(ClientSubscriptionBase clientSubscriptionBase, boolean reEnter) {
            clientSubscriptionBase.cancelNotifyTimeout();
            clientSubscriptionBase.isInitialOrTerminating = false;
        }
    };
    public static SubscriptionState TERMINATED = new SubscriptionState("TERMINATED", false) {
        public void enter(ClientSubscriptionBase clientSubscriptionBase, boolean reEnter) {
            if (!reEnter) {
                if (clientSubscriptionBase.dialog != null) {
                    clientSubscriptionBase.dialog.delete();
                    clientSubscriptionBase.dialog = null;
                }
                clientSubscriptionBase.onSubscriptionTerminated(clientSubscriptionBase.terminatedReason == null ? TerminatedReason.error : clientSubscriptionBase.terminatedReason, clientSubscriptionBase.terminatedRetryAfter, clientSubscriptionBase.rejectReasonCode);
            }
            clientSubscriptionBase.onStateChanged();
        }
    };

    static {
        INIT.addTransition(new SubscriptionTransition(SubscriptionCondition.C_SUBSCRIBE, WAIT_FOR_RESPONSE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_SUBSCRIBE signal in INIT state");
                }

                clientSubscriptionBase.sendSubscribe(clientSubscriptionBase.getSubscribeExpiresTime(), false, SubscribeType.initial);
            }
        });
        INIT.addTransition(new SubscriptionTransition(SubscriptionCondition.C_FETCH, WAIT_FOR_RESPONSE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_FETCH signal in INIT state");
                }

                clientSubscriptionBase.sendSubscribe(0, false, SubscribeType.initial);

            }
        });
        INIT.addTransition(new SubscriptionTransition(SubscriptionCondition.C_UNSUBSCRIBE, TERMINATED, null) {
            @Override
            public void activity(ClientSubscriptionBase machine, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_UNSUBSCRIBE signal in INIT state");
                }
            }
        });

        WAIT_FOR_RESPONSE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_OK_ACCEPT_NOTIFY_NOT_REQUIRED, ACTIVE, null) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_OK_ACCEPT_NOTIFY_NOT_REQUIRED signal in WAIT_FOR_RESPONSE state");
                }
                Response r = signal.getResponse();
                clientSubscriptionBase.dialog = r.getClientTransaction().getDialog();
                clientSubscriptionBase.setAbsolutRefreshTime(r.getExpiresTime());
            }
        });
        WAIT_FOR_RESPONSE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_OK_ACCEPT_NOTIFY_REQUIRED, WAIT_FOR_NOTIFY, null) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_OK_ACCEPT_NOTIFY_REQUIRED signal in WAIT_FOR_RESPONSE state");
                }
                Response r = signal.getResponse();
                clientSubscriptionBase.dialog = r.getClientTransaction().getDialog();
                if (r.getExpiresTime() > 0) {
                    clientSubscriptionBase.setAbsolutRefreshTime(r.getExpiresTime());
                } else {
                    // subscription is dead, final NOTIFY expected
                    clientSubscriptionBase.isInitialOrTerminating = true;
                }

            }
        });
        WAIT_FOR_RESPONSE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_SUBSCRIPTION_DOES_NOT_EXIST, WAIT_FOR_RESPONSE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_SUBSCRIPTION_DOES_NOT_EXIST signal in WAIT_FOR_RESPONSE state");
                }

                clientSubscriptionBase.sendSubscribe(clientSubscriptionBase.getSubscribeExpiresTime(), true, SubscribeType.initial);
            }
        });
        WAIT_FOR_RESPONSE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_SUBSCRIPTION_EXP_TOO_BRIEF, WAIT_FOR_RESPONSE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_SUBSCRIPTION_DOES_NOT_EXIST signal in WAIT_FOR_RESPONSE state");
                }

                MinExpiresHeader meh = (MinExpiresHeader) signal.getResponse().getHeader(MinExpiresHeader.NAME);
                long expires;
                if (meh != null) {
                    expires = meh.getDeltaSeconds();
                } else {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "MinExpire header not found! Forced to use default again. This will loop :-(");
                    }
                    expires = clientSubscriptionBase.getSubscribeExpiresTime();
                }
                try {
                    clientSubscriptionBase.sendSubscribe(expires, clientSubscriptionBase.isInitialOrTerminating, clientSubscriptionBase.isInitialOrTerminating ? SubscribeType.initial : SubscribeType.refresh);
                } catch (Exception e) {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "", e);
                    }
                    throw new TransitionActivityException("Could not send SUBSCRIBE", e);
                }
            }
        });
        WAIT_FOR_RESPONSE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_SUBSCRIBE_TIMEOUT, TERMINATED, null) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Terminating clientSubscriptionBase " + clientSubscriptionBase + ", due to subscribe timeout");
                }
                clientSubscriptionBase.terminatedReason = TerminatedReason.timeout;
            }
        });
        WAIT_FOR_RESPONSE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_RESPONSE_TRYING, WAIT_FOR_RESPONSE, null) {
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "got 100 Trying response, being patient");
                }
            }
        });
        WAIT_FOR_RESPONSE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_4XXRESPONSE_REFRESH_SUBS, ACTIVE, null) {
            public void activity(ClientSubscriptionBase machine, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Get " + signal.getResponse().getStatusCode() + " response for refresh subscribe");
                }
            }
        });
        WAIT_FOR_RESPONSE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_RESPONSE_ANY, TERMINATED, null) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) {
                Response response = signal.getResponse();
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Terminating " + clientSubscriptionBase + " due to " + response.getStatusCode() + " response");
                }
                clientSubscriptionBase.terminatedReason = TerminatedReason.rejected;
                clientSubscriptionBase.rejectReasonCode = response.getStatusCode();
            }
        });

        WAIT_FOR_NOTIFY.addTransition(new SubscriptionTransition(SubscriptionCondition.C_NOTIFY_TIMEOUT, TERMINATED, null) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_NOTIFY_TIMEOUT signal in WAIT_FOR_NOTIFY state");
                }
                clientSubscriptionBase.terminatedReason = TerminatedReason.timeout;
            }
        });
        WAIT_FOR_NOTIFY.addTransition(new SubscriptionTransition(SubscriptionCondition.C_NOTIFY_ACTIVE_PENDING, ACTIVE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_NOTIFY_ACTIVE_PENDING signal in WAIT_FOR_NOTIFY state");
                }
                SubscriptionStateHeader sth = signal.getNotify().getHeader(SubscriptionStateHeader.NAME);
                clientSubscriptionBase.setAbsolutRefreshTime(sth.getExpires());
                Response r = signal.getNotify().createResponse(Response.OK);
                r.send();
                clientSubscriptionBase.handleNotify(signal.getNotify());
            }
        });
        WAIT_FOR_NOTIFY.addTransition(new SubscriptionTransition(SubscriptionCondition.C_NOTIFY_TERMINATED, TERMINATED, null) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_NOTIFY_TERMINATED signal in WAIT_FOR_NOTIFY state");
                }
                Response r = signal.getNotify().createResponse(Response.OK);
                r.send();

                clientSubscriptionBase.handleNotify(signal.getNotify());

                clientSubscriptionBase.setTerminateReason(signal.getNotify());
            }
        });

        ACTIVE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_SUBSCRIBE, WAIT_FOR_RESPONSE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_SUBSCRIBE signal in ACTIVE state");
                }
                clientSubscriptionBase.sendSubscribe(clientSubscriptionBase.getSubscribeExpiresTime(), false, SubscribeType.resubscribe);
            }
        });
        ACTIVE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_REFRESH, WAIT_FOR_RESPONSE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_REFRESH signal in ACTIVE state");
                }

                clientSubscriptionBase.sendSubscribe(clientSubscriptionBase.getSubscribeExpiresTime(), false, SubscribeType.refresh);
            }
        });
        ACTIVE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_UNSUBSCRIBE, WAIT_FOR_RESPONSE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_UNSUBSCRIBE signal in ACTIVE state");
                }

                clientSubscriptionBase.sendSubscribe(0, false, SubscribeType.unsubscribe);
                clientSubscriptionBase.isInitialOrTerminating = true; // final Notify
            }
        });
        ACTIVE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_NOTIFY_ACTIVE_PENDING, ACTIVE, TERMINATED) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_NOTIFY_ACTIVE_PENDING signal in ACTIVE state");
                }

                SubscriptionStateHeader sth = signal.getNotify().getHeader(SubscriptionStateHeader.NAME);
                clientSubscriptionBase.setAbsolutRefreshTime(sth.getExpires());
                Response r = signal.getNotify().createResponse(Response.OK);
                r.send();

                clientSubscriptionBase.handleNotify(signal.getNotify());

            }
        });
        ACTIVE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_NOTIFY_TERMINATED, TERMINATED, null) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received C_NOTIFY_TERMINATED signal in ACTIVE state");
                }

                Response r = signal.getNotify().createResponse(Response.OK);
                r.send();

                clientSubscriptionBase.handleNotify(signal.getNotify());
                clientSubscriptionBase.setTerminateReason(signal.getNotify());
            }
        });
        ACTIVE.addTransition(new SubscriptionTransition(SubscriptionCondition.C_KILL, TERMINATED, null) {
            @Override
            public void activity(ClientSubscriptionBase machine, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Killing this subcsription");
                }
                machine.terminatedReason = TerminatedReason.timeout;
            }
        });

        TERMINATED.addTransition(new SubscriptionTransition(SubscriptionCondition.C_ANY, TERMINATED, null) {
            @Override
            public void activity(ClientSubscriptionBase clientSubscriptionBase, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received " + signal.getType() + " signal in TERMINATED state");
                }
            }
        });
    }

    /**
     * Set safe value for refresh, only to be used from Unit Tests
     *
     * @param graceVal grace period
     */
    protected static void setSafeTime(long graceVal) {
        SAFE_TIME = graceVal;
    }

    private final URI requestUri;
    private final Address subscriber;
    private final Address publisher;

    /*
     * The following mutable state variables are protected by the underlying
     * FSM and must never be accessed (includes read/write) from
     * outside the FSM!
     */
    protected Dialog dialog;
    private long refreshTime;
    private boolean isInitialOrTerminating; // a NOTIFY is required on session set-up and session tear-down
    private ScheduledFuture refreshTask, waitForNotifyTimeoutTask;
    private TerminatedReason terminatedReason;
    private long terminatedRetryAfter;
    private int rejectReasonCode;

    protected ClientSubscriptionBase(URI requestUri, Address from, Address to) {
        super(INIT);
        this.requestUri = requestUri;
        this.subscriber = from;
        this.publisher = to;

        isInitialOrTerminating = false;
    }

    /**
     * Initiates the subscription and sends out the SUBSCRIBE request
     */
    public void subscribe() {
        SipStack.get().getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    ClientSubscriptionBase.super.input(Signal.SUBSCRIBE, 1000L);
                } catch (InterruptedException e) {
                    Logger.e(TAG, "", e);
                } catch (TransitionActivityException e) {
                    Logger.e(TAG, "", e);
                } catch (UnhandledConditionException e) {
                    Logger.e(TAG, "", e);
                }
            }
        });
    }

    /**
     * Initiates the subscription with Expires set to 0.
     */
    public void fetch() {
        SipStack.get().getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    ClientSubscriptionBase.super.input(Signal.FETCH, 1000L);
                } catch (InterruptedException e) {
                    Logger.e(TAG, "", e);
                } catch (TransitionActivityException e) {
                    Logger.e(TAG, "", e);
                } catch (UnhandledConditionException e) {
                    Logger.e(TAG, "", e);
                }
            }
        });
    }

    /**
     * Terminates the subscription by sending an unsubscribe.
     */
    public void unsubscribe() {
        SipStack.get().getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    ClientSubscriptionBase.super.input(Signal.TERMINATE, 1000L);
                } catch (InterruptedException e) {
                    Logger.e(TAG, "", e);
                } catch (TransitionActivityException e) {
                    Logger.e(TAG, "", e);
                } catch (UnhandledConditionException e) {
                    Logger.e(TAG, "", e);
                }
            }
        });
    }

    /**
     * Brings this state-machine to the TERMINATED state and destroyes the dialog without
     * sending the unsubscribe. Calling this method has no effect if the FSM
     * is currently not in ACTIVE state
     *
     * @return returns true if this signal was accepted; else false
     */
    public boolean kill() {
        boolean done = false;
        try {
            super.input(Signal.getKillSignal(), 1000L);
            done = true;
        } catch (InterruptedException e) {
            //void
        } catch (TransitionActivityException e) {
            //void
        } catch (UnhandledConditionException e) {
            //void
        }

        return done;
    }

    /**
     * Handle a response for this SUBSCRIBE-transaction. This method should only be used by the EventHandler
     */
    public void processResponse(Response response) {
        try {
            super.input(Signal.getResponseSignal(response), 1000L);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        }
    }

    /**
     * Signal the FSM that the sent SUBSCRIBE-transaction has timed out.
     */
    public void processTimeout(TransactionBase base) {
        try {
            super.input(Signal.getSubscribeTimeoutSignal(), 1000L);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        }
    }

    @Override
    public void processRequest(Request request) {
        if (request instanceof Notify) {
            try {
                super.input(Signal.getNotifyReceivedSignal((Notify) request), 1000L);
            } catch (InterruptedException e) {
                Logger.e(TAG, "", e);
            } catch (TransitionActivityException e) {
                Logger.e(TAG, "", e);
            } catch (UnhandledConditionException e) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "", e);
                }
            }
        }
    }

    /**
     * Checks if a Notify is expected.
     * This method must never be used from outside the FSM.
     *
     * @return true if notify is expected
     */
    public boolean isInitialOrTerminatingTransaction() {
        return isInitialOrTerminating;
    }

    public String toString() {
        return "ClientSubscriptionBase, publisher: " + publisher.toString() + ", subscriber: " + subscriber.toString();
    }

    public Address getPublisher() {
        return publisher;
    }

    /**
     * Not used, but stays here for protection
     *
     * @param signal signal
     */
    @Override
    public final void input(Signal signal) {
        throw new RuntimeException("Never call my directly");
    }

    @Override
    public Signal getSignalForQueueSizeLimitReached(State currentState) {
        Signal signal;
        if (currentState == WAIT_FOR_NOTIFY) {
            signal = Signal.getNotifyTimeoutSignal();
        } else {
            signal = Signal.getSubscribeTimeoutSignal();
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Returning signal: " + signal);
        }
        return signal;
    }

    private void refresh() {
        try {
            super.input(Signal.REFRESH, 1000L);
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    private void sendSubscribe(long expires, boolean forceNewDialog, SubscribeType type) {
        Subscribe subscribe;

        byte[] body = getContent(type);

        // send within the dialog
        if (dialog != null && !forceNewDialog) {
            subscribe = new Subscribe(dialog, getEventHeader(), expires, getContentType(type), body);
            populateExtensionHeaders(subscribe, false, expires);
        }

        // new dialog
        else {
            if (dialog != null) {
                dialog.delete();
                dialog = null;
            }
            subscribe = new Subscribe(
                    requestUri,
                    subscriber,
                    publisher,
                    1,
                    subscriber.getUri(),
                    null,
                    getEventHeader(),
                    getInitialRouteSet(),
                    expires,
                    getContentType(type),
                    body,
                    getAcceptedContentTypes());
            populateExtensionHeaders(subscribe, true, expires);
        }

        Collection<String> optionTags;
        if ((optionTags = getOptionTags()) != null) {
            subscribe.addSupportedOptionTag(optionTags);
        }

        /*
         * Send the subscribe
         */

        ClientTransaction cl;
        if ((cl = subscribe.send(this)) != null) {
            dialog = cl.getDialog();
        }

    }

    private void setAbsolutRefreshTime(long expires) {
        refreshTime = System.currentTimeMillis() + expires * 1000 - SAFE_TIME;
    }

    public long getCurrentRelativeRefreshTime() {
        return refreshTime - System.currentTimeMillis();
    }

    private void scheduleNotifyTimeout() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }

        if (waitForNotifyTimeoutTask != null) {
            Logger.w(TAG, "Already started?");
        } else {
            waitForNotifyTimeoutTask = SipStack.get().getThreadPool().schedule(new Runnable() {
                public void run() {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "waitForNotifyTimeout timer fired");
                    }
                    try {
                        ClientSubscriptionBase.super.input(Signal.getNotifyTimeoutSignal(), 1000L);
                    } catch (UnhandledConditionException e) {
                        Logger.e(TAG, "", e);
                    } catch (InterruptedException e) {
                        Logger.e(TAG, "", e);
                    } catch (TransitionActivityException e) {
                        Logger.e(TAG, "", e);
                    }
                }

            }, NOTIFY_TIMEOUT, TimeUnit.MILLISECONDS);
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "started");
            }
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    private void cancelNotifyTimeout() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }

        if (waitForNotifyTimeoutTask == null) {
            Logger.w(TAG, "Already started cancelled?");
        } else {
            waitForNotifyTimeoutTask.cancel(false);
            waitForNotifyTimeoutTask = null;

            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "cancelled");
            }
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    /**
     * Schedule a refresh here,
     * sync is done to avoid that imediate refresh task is not able to execute before
     * the future is is assigned.
     */
    private void scheduleRefresh() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }

        if (refreshTask != null) {
            Logger.w(TAG, "refreshTimer already started?!?");
        } else {

            long refreshTime = getCurrentRelativeRefreshTime();
            if (refreshTime > 0) {

                refreshTask = SipStack.get().getThreadPool().schedule(new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "refresh timer fired");
                        }
                        refresh();
                    }

                }, refreshTime, TimeUnit.MILLISECONDS);

                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "timer started, will fire in " + refreshTime + " milliseconds");
                }
            } else if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Not starting refresh timer since refreshTime=" + refreshTime);
            }
        }

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    /**
     * Cancel a pending refresh here, since we null out the future in the call
     * we will never cancel ourselves.
     */
    private void cancelRefresh() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "cancelled");
            }
        } else if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "was already canceled");
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    private void setTerminateReason(Notify notify) {
        SubscriptionStateHeader sth = notify.getHeader(SubscriptionStateHeader.NAME);
        terminatedRetryAfter = sth.getRetryAfter();

        ReasonCode reasonCode = sth.getReason();
        if (reasonCode == null) {
            terminatedReason = TerminatedReason.error;
        } else {
            terminatedReason = TerminatedReason.fromReasonCode(reasonCode);
        }
    }

    /**
     * Get options tag to be included in the subscribe request supported header.
     * To be overriden by subclass
     *
     * @return desired option tags
     */
    protected Collection<String> getOptionTags() {
        return null;
    }

    /**
     * Populate extension headers.
     * Invoked before initial and in dialog subscribe requests are sent.
     * Override in subclass to populate with any desired "extra" headers.
     *
     * @param subscribe the subscribe request to populate
     * @param initial   indicates if this is an initial subscribe (or if false an in-dialog subscribe)
     * @param expires   the expires value to be used in this subscribe
     */
    @SuppressWarnings({"UnusedDeclaration"})
    protected void populateExtensionHeaders(Subscribe subscribe, boolean initial, long expires) {
    }


    /**
     * Implement to provide the EventHeader to use in the SUBSCRIBE
     *
     * @return event header
     */
    abstract protected EventHeader getEventHeader();

    /**
     * Implementn to provide AcceptHeader to use in the SUBSCRIBE
     *
     * @return the accept header
     */
    abstract protected Set<MimeType> getAcceptedContentTypes();

    /**
     * Override to provide the SUBSCRIBE expires value,
     *
     * @return expires value in seconds
     */
    abstract protected int getSubscribeExpiresTime();

    abstract protected List<RouteHeader> getInitialRouteSet();

    /**
     * Override to extract the content being carried in a NOTIFY
     *
     * @param notify the notify to extract content from
     */
    abstract protected void handleNotify(Notify notify);

    /**
     * Signal the implementation that the subscription is terminated
     * <p/>
     * A subscription can become terminated as one the following events occur:
     * - the initial susbcribe (thus not a re-subs!!) request was rejected (non 2xx response)
     * - a notify was received with subscription-state terminated
     * - a timeout event from the sipstack (didn't receive a response for the outgoing subscribe request in time)
     *
     * @param terminatedReason     terminate reason (received in the notify)
     * @param terminatedRetryAfter retry after value (received in the notify)
     * @param rejectReasonCode     in case the subscription was rejected, this is the status code received in the response
     */
    protected void onSubscriptionTerminated(TerminatedReason terminatedReason, long terminatedRetryAfter, int rejectReasonCode) {
        // to be overridden
    }

    protected void onStateChanged() {
        //to be overridden
    }

    /**
     * To be overridden
     *
     * @param isInitial the type of subscribe request for which the content is requested
     * @return the content to be inbcluded in the subscribe body
     */
    protected byte[] getContent(SubscribeType isInitial) {
        return null;
    }

    /**
     * to be overridden in case of getContent() is used
     *
     * @param isInitial the type of subscribe request for which the content is requested
     * @return the content-type which describes the content returned by getContent()
     */
    protected MimeType getContentType(SubscribeType isInitial) {
        return null;
    }
}
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
package com.colibria.android.sipservice.sip.frameworks.clientpublisher;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.fsm.*;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.fsm.Machine;
import com.colibria.android.sipservice.fsm.TransitionActivityException;
import com.colibria.android.sipservice.fsm.UnhandledConditionException;
import com.colibria.android.sipservice.sip.frameworks.clientpublisher.fsm.PublishCondition;
import com.colibria.android.sipservice.sip.frameworks.clientpublisher.fsm.PublishState;
import com.colibria.android.sipservice.sip.frameworks.clientpublisher.fsm.PublishTransition;
import com.colibria.android.sipservice.sip.frameworks.clientpublisher.fsm.Signal;
import com.colibria.android.sipservice.sip.headers.EventHeader;
import com.colibria.android.sipservice.sip.headers.MinExpiresHeader;
import com.colibria.android.sipservice.sip.headers.SIPETagHeader;
import com.colibria.android.sipservice.sip.headers.SIPIfMatchHeader;
import com.colibria.android.sipservice.sip.messages.Publish;
import com.colibria.android.sipservice.sip.messages.Response;
import com.colibria.android.sipservice.sip.tx.IClientTransactionListener;
import com.colibria.android.sipservice.sip.tx.TransactionBase;


/**
 * ClientPublisher.
 * <p/>
 * A client which is used to send and maintain published state
 * <p/>
 * Using this client makes it easy to publish certain state and make sure that this
 * state is refreshed automatically.
 * <p/>
 * The following diagram describes the lifeCycle FSM of this client
 * <p/>
 * <pre>
 * <p/>
 *               +------+  terminate
 *               | INIT |----------->---------+
 *               +------+                     |
 *                  |                         |
 *          publish |                         |
 *                  v                         |
 *           +--------------+ timeout /       |
 *           | PUBLISH_SENT |---------------->|
 *           +--------------+ errorResponse   |
 *                |   ^                       |
 *                |   | refresh/              |
 *        ack-ed  |   |  modify               |
 *                v   |                       |
 *             +---------+                    |
 *             |  ALIVE  |                    |
 *             +---------+                    |
 *                  |                         |
 *                  | unPublish               |
 *                  v                         |
 *           +------------+                   |
 *           | TERMINATED |<------------------+
 *           +------------+
 * <p/>
 * </pre>
 *
 * @author Sebastian Dehne
 */
public abstract class ClientPublisher extends Machine<Signal> implements IClientTransactionListener {
    private static final String TAG = "ClientPublisher";

    /*
     * The states
     */
    private static final PublishState INIT = new PublishState("INIT", false);
    private static final PublishState PUBLISH_SENT = new PublishState("PUBLISH_SENT", true);
    private static final PublishState ALIVE = new PublishState("ALIVE", false) {
        @Override
        public void enter(ClientPublisher cp, boolean reEnter) {
            // clear some memory
            cp.content = null;
            cp.contentType = null;
            cp.isHandlingrefresh = false;
        }
    };
    private static final PublishState TERMINATED = new PublishState("TERMINATED", false) {
        @Override
        public void enter(ClientPublisher cp, boolean reEnter) {
            // clear some memory
            cp.content = null;
            cp.contentType = null;
            cp.onTerminatedReached(cp.getSignal().getResponse());
        }
    };

    /*
     * The transitions
     */

    static {

        INIT.addTransition(new PublishTransition(PublishCondition.SEND_PUBLISH, PUBLISH_SENT) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "INIT -> PUBLISH_SENT");
                }
                cl.content = signal.getContent();
                cl.contentType = signal.getContentType();

                Publish p = Publish.create(cl.publishURI, cl.getExpires(), new EventHeader(cl.eventPackage, null), cl.contentType, cl.content);
                if (signal.geteTag() != null) {
                    p.setHeader(new SIPIfMatchHeader(signal.geteTag()));
                }
                cl.populateAdditionalHeaders(p, false);

                p.send(cl);
            }
        });
        INIT.addTransition(new PublishTransition(PublishCondition.TERMINATE, TERMINATED) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "INIT -> TERMINATED");
                }
            }
        });

        PUBLISH_SENT.addTransition(new PublishTransition(PublishCondition.RESPONSE_2xx, ALIVE) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PUBLISH_SENT -> ALIVE");
                }
                // store etag
                SIPETagHeader h = signal.getResponse().getHeader(SIPETagHeader.NAME);
                cl.currentETag = h != null ? h.geteTag() : null;

                // signal app
                if (!cl.isHandlingrefresh)
                    cl.onPublishAccepted(signal.getResponse());
            }
        });
        PUBLISH_SENT.addTransition(new PublishTransition(PublishCondition.RESPONSE_412, PUBLISH_SENT) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PUBLISH_SENT -> PUBLISH_SENT (412 Conditional request failed)");
                }

                Publish p = Publish.create(cl.publishURI, cl.getExpires(), new EventHeader(cl.eventPackage, null), cl.contentType, cl.content);
                cl.currentETag = null;
                cl.populateAdditionalHeaders(p, false);
                p.send(cl);
            }
        });
        PUBLISH_SENT.addTransition(new PublishTransition(PublishCondition.RESPONSE_423, PUBLISH_SENT) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PUBLISH_SENT -> PUBLISH_SENT (423 interval too brief received, re-trying)");
                }

                try {
                    MinExpiresHeader mex = (MinExpiresHeader) signal.getResponse().getHeader(MinExpiresHeader.NAME);

                    Publish p = Publish.create(cl.publishURI, mex.getDeltaSeconds(), new EventHeader(cl.eventPackage, null), cl.contentType, cl.content);
                    p.addHeader(new SIPIfMatchHeader(cl.currentETag));
                    cl.populateAdditionalHeaders(p, false);
                    p.send(cl);
                } catch (Exception e) {
                    throw new TransitionActivityException(e);
                }
            }
        });
        PUBLISH_SENT.addTransition(new PublishTransition(PublishCondition.TERMINATE, TERMINATED) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "PUBLISH_SENT -> TERMINATED (timeout/terminate/errorResponse)");
                }
            }
        });

        ALIVE.addTransition(new PublishTransition(PublishCondition.TERMINATE, TERMINATED) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "ALIVE -> TERMINATED (unPublish)");
                }
                Publish p = Publish.create(cl.publishURI, 0, new EventHeader(cl.eventPackage, null), null, null);
                p.setHeader(new SIPIfMatchHeader(cl.currentETag));
                cl.populateAdditionalHeaders(p, false);
                p.send(cl);
            }
        });
        ALIVE.addTransition(new PublishTransition(PublishCondition.SEND_REFRESH, PUBLISH_SENT) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "ALIVE -> PUBLISH_SENT (refresh)");
                }

                cl.isHandlingrefresh = true;
                Publish p = Publish.create(cl.publishURI, cl.getExpires(), new EventHeader(cl.eventPackage, null), null, null);

                p.setHeader(new SIPIfMatchHeader(cl.currentETag));
                cl.populateAdditionalHeaders(p, true);
                p.send(cl);
            }
        });
        ALIVE.addTransition(new PublishTransition(PublishCondition.SEND_PUBLISH, PUBLISH_SENT) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "ALIVE -> PUBLISH_SENT (modify publish)");
                }
                cl.content = signal.getContent();
                cl.contentType = signal.getContentType();

                Publish p = Publish.create(cl.publishURI, cl.getExpires(), new EventHeader(cl.eventPackage, null), cl.contentType, cl.content);
                p.setHeader(new SIPIfMatchHeader(cl.currentETag));

                cl.populateAdditionalHeaders(p, false);
                p.send(cl);
            }
        });

        TERMINATED.addTransition(new PublishTransition(PublishCondition.RESPONSE_2xx, TERMINATED) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "TERMINATED -> TERMINATED (unPublish acked)");
                }
            }
        });
        TERMINATED.addTransition(new PublishTransition(PublishCondition.TERMINATE, TERMINATED) {
            @Override
            public void activity(ClientPublisher cl, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "TERMINATED -> TERMINATED (unPublish timeout/errorResponse)");
                }
            }
        });

    }

    private final String eventPackage;
    private final Address publishURI;

    /*
     * The following variables are guarded by the lock from the underlying
     * FSM. Accessing (read/write) to those variables can only be done
     * after aquiring this lock
     */
    private byte[] content;
    private MimeType contentType;
    private String currentETag;
    private boolean isHandlingrefresh;


    /**
     * Constructs a new clientPublisher
     *
     * @param eventPackage the eventPackages to be used
     * @param publishURI   the URI to which the state is to be published to
     */
    public ClientPublisher(String eventPackage, Address publishURI) {
        super(INIT);

        if (eventPackage == null || publishURI == null) {
            throw new IllegalArgumentException("Neither eventPackage or publishURI can be null");
        }

        this.eventPackage = eventPackage;
        this.publishURI = publishURI;
    }

    /*
     * The following methods are methods to be used by the application
     * to tell this client what it should do
     */

    public void sendPublish(MimeType contentType, byte[] content, String etag) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }

        try {
            super.input(Signal.getPublishSignal(contentType, content, etag));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "", e);
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    /**
     * Causes this client to terminate. In case the client
     * is currently in ALIVE state, a unpublish-request will
     * be sent before terminating
     */
    public void terminate() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getTerminateSignal());
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "", e);
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    /*
     * The following methods are methods to be used by the sip-stack
     * to tell this client about events from the network
     */

    public void processTimeout(TransactionBase transactionBase) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getTerminateSignal());
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "", e);
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    public void processResponse(Response responseReceived) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getResponseSignal(responseReceived));
        } catch (UnhandledConditionException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "", e);
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    /*
     * The following methods provide the API from this client to
     * the application, such taht this client can ask it's implementation
     * for certain input for provides signals about what happenend
     */

    /**
     * Called by this client in order to signal the implemetation
     * that it's lifeCycle has ended
     *
     * @param response if non-null, then this response provides
     *                 a reason why terminated state was reached
     */
    public abstract void onTerminatedReached(Response response);

    public abstract void onPublishAccepted(Response response);

    /**
     * Gets the default expires value in seconds
     *
     * @return the default expires
     */
    public abstract int getExpires();

    /**
     * Provides a last minute hock (before the request is sent out)
     * such that application headers may be set (for example the Route header).
     * <p/>
     * Content, Content-Type, Content-Length, Expires & etag
     * should never be set by the application
     *
     * @param outgoingRequest the request to modify
     * @param isRefreshOnly   true if this is a refresh publish
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void populateAdditionalHeaders(Publish outgoingRequest, boolean isRefreshOnly) {
        //to be overridden if required
    }


    @Override
    public final void input(Signal signal) {
        throw new RuntimeException("Not allowed to call this method from outside");
    }

    @Override
    public Signal getSignalForQueueSizeLimitReached(State currentState) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Returning terminate signal");
        }
        return Signal.getTerminateSignal();
    }
}

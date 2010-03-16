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
package com.colibria.android.sipservice.sip.frameworks.register;

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.fsm.TransitionActivityException;
import com.colibria.android.sipservice.fsm.UnhandledConditionException;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.fsm.Machine;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.headers.ContactHeader;
import com.colibria.android.sipservice.sip.headers.ExpiresHeader;
import com.colibria.android.sipservice.sip.headers.RouteHeader;
import com.colibria.android.sipservice.sip.headers.ServiceRouteHeader;
import com.colibria.android.sipservice.sip.messages.Response;
import com.colibria.android.sipservice.sip.messages.Register;
import com.colibria.android.sipservice.sip.tx.IClientTransactionListener;
import com.colibria.android.sipservice.sip.tx.TransactionBase;
import com.colibria.android.sipservice.sip.tx.Utils;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class RegisterController extends Machine<Signal> implements IClientTransactionListener {

    private static final String TAG = "RegisterController";

    private static final int DEFAULT_EXPIRES = 3600;

    private static final RegisterState INIT = new RegisterState("INIT", false);
    private static final RegisterState REGISTER_SENT = new RegisterState("SEND_REGISTER", true);
    private static final RegisterState REFRESH_SENT = new RegisterState("REFRESH_SENT", true);
    private static final RegisterState ACTIVE = new RegisterState("ACTIVE", false);
    private static final RegisterState UNREGISTER_SENT = new RegisterState("UNREGISTER_SENT", true);

    static {
        // REGISTERING
        INIT.addTransition(new RegisterTransition(RegisterCondition.SEND_REGISTER, REGISTER_SENT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                registerController.sendRegister();
            }
        });
        INIT.addTransition(new RegisterTransition(RegisterCondition.INIT, INIT));

        REGISTER_SENT.addTransition(new RegisterTransition(RegisterCondition.UNAUTHENTICATED, REGISTER_SENT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.d(TAG, "Unauthenticated. Trying WITH authorization header now...");
                registerController.sendRegister(signal.getResponse());
            }
        });
        REGISTER_SENT.addTransition(new RegisterTransition(RegisterCondition.OK, ACTIVE) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.d(TAG, "REGISTER was accepted by SERVER");
                Response response = signal.getResponse();

                registerController.setExpireValue(signal.getResponse());

                /*
                 * For IMS networks: set Service-Route header if present in response
                 */
                ServiceRouteHeader serviceRoute = response.getHeader(ServiceRouteHeader.NAME);
                if (serviceRoute != null) {
                    //SipClient.routeHeader = serviceRoute.getAddress();
                    registerController.listener.setServiceRouteHeaderAddress(serviceRoute.getAddress());
                }

                registerController.listener.activeStateReached();

            }
        });
        REGISTER_SENT.addTransition(new RegisterTransition(RegisterCondition.TIMEOUT, INIT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.i(TAG, "Register timed out, going back to init");
            }
        });
        REGISTER_SENT.addTransition(new RegisterTransition(RegisterCondition.INIT, INIT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.i(TAG, "Forced to give up any state and going back to init");
            }
        });

        REFRESH_SENT.addTransition(new RegisterTransition(RegisterCondition.OK, ACTIVE) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.i(TAG, "Refresh REGISTER was accepted by SERVER");

                registerController.setExpireValue(signal.getResponse());
            }
        });
        REFRESH_SENT.addTransition(new RegisterTransition(RegisterCondition.UNAUTHENTICATED, REFRESH_SENT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.d(TAG, "Unauthenticated. Trying WITH authorization header now...");
                registerController.sendRegister(signal.getResponse());
            }
        });
        REFRESH_SENT.addTransition(new RegisterTransition(RegisterCondition.TIMEOUT, INIT));
        REFRESH_SENT.addTransition(new RegisterTransition(RegisterCondition.INIT, INIT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.i(TAG, "Forced to give up any state and going back to init");
            }
        });

        // refresh registration
        ACTIVE.addTransition(new RegisterTransition(RegisterCondition.SEND_REGISTER, REFRESH_SENT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                registerController.sendRegister();
            }
        });
        ACTIVE.addTransition(new RegisterTransition(RegisterCondition.SEND_UNREGISTER, UNREGISTER_SENT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                registerController.sendUnRegister();
            }
        });
        ACTIVE.addTransition(new RegisterTransition(RegisterCondition.INIT, INIT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.i(TAG, "Forced to give up any state and going back to init");
            }
        });

        UNREGISTER_SENT.addTransition(new RegisterTransition(RegisterCondition.OK, INIT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.i(TAG, "We are now unregistered");
                registerController.listener.initStateReached();
            }
        });
        UNREGISTER_SENT.addTransition(new RegisterTransition(RegisterCondition.UNAUTHENTICATED, UNREGISTER_SENT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                registerController.sendUnRegister(signal.getResponse());
            }
        });
        UNREGISTER_SENT.addTransition(new RegisterTransition(RegisterCondition.TIMEOUT, INIT));
        UNREGISTER_SENT.addTransition(new RegisterTransition(RegisterCondition.INIT, INIT) {
            @Override
            public void activity(RegisterController registerController, Signal signal) {
                Logger.i(TAG, "Forced to give up any state and going back to init");
            }
        });
    }

    /*
     * The following state variables are guarded by "this".
     * Therefore a lock for "this" should be acquire before reading
     * and/or writing to them.
     */
    private final IRegisterControllerListener listener;
    private long cSeq;
    private String callId;

    public RegisterController(IRegisterControllerListener listener, String callId, long cSeq) {
        super(INIT);
        this.listener = listener;
        this.callId = callId;
        this.cSeq = cSeq;
    }

    public void register(boolean unregister) {

        synchronized (this) {
            if (callId == null) {
                this.callId = Utils.generateCallIdentifier(listener.getSenderAddress().getUri());
                this.cSeq = 1;
            }

            try {
                if (unregister) {
                    super.input(Signal.createSendUnregisterSignal());
                } else {
                    super.input(Signal.createSendRegisterSignal());
                }
            } catch (Exception e) {
                Logger.e(TAG, "", e);
            }
        }
    }

    public void kill() {
        try {
            super.input(Signal.createInitSignal());
        } catch (UnhandledConditionException e) {
            Logger.d(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.d(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.d(TAG, "", e);
        }
    }

    /**
     * Overrides the Machine.input() in order to protect it. This method should never be called.
     *
     * @param signal the input signal
     */
    public final void input(Signal signal) {
        throw new RuntimeException("Should not send signals directly to the state machine.");
    }

    public boolean isRegistered() {
        return super.getUnsafeCurrentState() == ACTIVE;
    }

    private void sendRegister() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "sendRegister - enter");
        }

        Register request;
        Address sender = listener.getSenderAddress();

        List<NameValuePair> params = new LinkedList<NameValuePair>();
        params.add(new NameValuePair("expires", Integer.toString(DEFAULT_EXPIRES)));
        params.add(new NameValuePair("+g.oma.sip-im", null));
        params.add(new NameValuePair("+g.oma.sip-im.large-message", null));
        request = Register.create(sender, ++cSeq, callId, params);

        List<RouteHeader> rhl = listener.getInitialRouteSet();
        request.addHeaders(rhl);

        request.send(this);
        Logger.i(TAG, "REGISTER sent");

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "sendRegister - leave");
        }
    }

    private void sendRegister(Response response) {
        throw new RuntimeException("Not implemented for now");
    }

    private void sendUnRegister() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "sendUnRegister - enter");
        }

        Address sender = listener.getSenderAddress();

        List<NameValuePair> params = new LinkedList<NameValuePair>();
        params.add(new NameValuePair("expires", Integer.toString(0)));
        params.add(new NameValuePair("+g.oma.sip-im", null));
        params.add(new NameValuePair("+g.oma.sip-im.large-message", null));
        Register reqRegister = Register.create(sender, ++cSeq, callId, params);

        List<RouteHeader> rhl = listener.getInitialRouteSet();
        reqRegister.addHeaders(rhl);


        reqRegister.send(this);
        Logger.i(TAG, "UNREGISTER sent");

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "sendUnRegister - leave");
        }
    }

    private void sendUnRegister(Response response) {
        throw new RuntimeException("Not implemented for now");
    }

    private void setExpireValue(Response response) {
        long expires = -1;
        ContactHeader ch = response.getHeader(ContactHeader.NAME);
        if (ch != null && ch.getAddress().getUri().isParameterSet("expires")) {
            try {
                expires = Integer.parseInt(ch.getAddress().getUri().getParameterValue("expires"));
            } catch (NumberFormatException e) {
                // ohno
            }
        }

        if (expires < 1 && ch != null && ch.getAddress().isParameterSet("expires")) {
            try {
                expires = Integer.parseInt(ch.getAddress().getParameterValue("expires"));
            } catch (NumberFormatException e) {
                // ohno
            }
        }

        ExpiresHeader expiresHeader;
        if (expires == -1 && (expiresHeader = response.getHeader(ExpiresHeader.NAME)) != null) {
            expires = expiresHeader.getDeltaSeconds();
        }
        if (expires < 1) {
            Logger.d(TAG, "No Expires found, using default of " + DEFAULT_EXPIRES + " as refresh timer");
            expires = DEFAULT_EXPIRES;
        }
    }

    @Override
    public void processTimeout(TransactionBase transactionBase) {
        try {
            super.input(Signal.createTimeoutSignal());
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }

    @Override
    public synchronized void processResponse(Response response) {
        if (getUnsafeCurrentState() != REFRESH_SENT && getUnsafeCurrentState() != REGISTER_SENT && getUnsafeCurrentState() != UNREGISTER_SENT) {
            Logger.e(TAG, "state machine not in expected state. Ignoring this response");
            return;
        }
        try {
            cSeq = response.getCSeq().getSeqNumber();
            if (response.isResponseClass(Response.ClassXX.RC1xx)) {
                Logger.d(TAG, "ingoring 100 class response");
                return;
            }
            Logger.d(TAG, " about to send to FSM");
            super.input(Signal.getResponseSignal(response));
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
    }

}

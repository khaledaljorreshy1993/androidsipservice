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
package com.colibria.android.sipservice.sip.tx;

import com.colibria.android.sipservice.fsm.TransitionActivityException;
import com.colibria.android.sipservice.fsm.UnhandledConditionException;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.ISipStackListener;
import com.colibria.android.sipservice.sip.SipStack;
import com.colibria.android.sipservice.sip.messages.*;
import com.colibria.android.sipservice.sip.headers.ViaHeader;

import static com.colibria.android.sipservice.sip.tx.TransactionCondition.*;

/**
 * The Server Transaction.
 * Comes in one out of two flavours. The INVITE or the non-INVITE server transaction.
 * <pre>
 *                  INVITE Transaction
 * <p/>
 *                             |INVITE
 *                             |pass INV to TU
 *          INVITE             V send 100 if TU won't in 200ms
 *          send response+-----------+
 *              +--------|           |--------+101-199 from TU
 *              |        | Proceeding|        |send response
 *              +------->|           |<-------+
 *                       |           |          Transport Err.
 *                       |           |          Inform TU
 *                       |           |--------------->+
 *                       +-----------+                |
 *          300-699 from TU |     |2xx from TU        |
 *          send response   |     |send response      |
 *                          |     +------------------>+
 *                          |                         |
 *          INVITE          V          Timer G fires  |
 *          send response+-----------+ send response  |
 *              +--------|           |--------+       |
 *              |        | Completed |        |       |
 *              +------->|           |<-------+       |
 *                       +-----------+                |
 *                          |     |                   |
 *                      ACK |     |                   |
 *                      -   |     +------------------>+
 *                          |        Timer H fires    |
 *                          V        or Transport Err.|
 *                       +-----------+  Inform TU     |
 *                       |           |                |
 *                       | Confirmed |                |
 *                       |           |                |
 *                       +-----------+                |
 *                             |                      |
 *                             |Timer I fires         |
 *                             |-                     |
 *                             |                      |
 *                             V                      |
 *                       +-----------+                |
 *                       |           |                |
 *                       | Terminated|<---------------+
 *                       |           |
 *                       +-----------+
 * </pre>
 * <pre>
 * <p/>
 *                             Non-INVITE
 * <p/>
 *                                |Request received
 *                                |pass to TU
 *    -adjust-     Request        v
 *                  ignore  +-----------+                101-199 from TU
 *                  +-------|           |------------------------------------------+
 *                  |       | Trying    |-------------+                            |
 *                  +------>|           |             |                            |
 *                          +-----------+             |200-699 from TU             |
 *                                |                   |send response               |
 *                                |1xx from TU        |                            |
 *                                |send response      |                            |
 *                                |                   |                            |
 *             Request            V      1xx from TU  |                            |
 *             send response+-----------+send response|                            v
 *                 +--------|           |--------+    |                       +---------------+
 *                 |        | Proceeding|        |    |                       |PRR-Proceeding |
 *                 +------->|           |<-------+    |                       |               |
 *          +<--------------|           |             |                       |               |
 *          |Trnsprt Err    +-----------+             |                       |               |
 *          |Inform TU            |                   |                       +---------------+
 *          |                     |                   |
 *          |                     |200-699 from TU    |
 *          |                     |send response      |
 *          |  Request            V                   |
 *          |  send response+-----------+             |
 *          |      +--------|           |             |
 *          |      |        | Completed |<------------+
 *          |      +------->|           |
 *          +<--------------|           |
 *          |Trnsprt Err    +-----------+
 *          |Inform TU            |
 *          |                     |Timer J fires
 *          |                     |-
 *          |                     |
 *          |                     V
 *          |               +-----------+
 *          |               |           |
 *          +-------------->| Terminated|
 *                          |           |
 *                          +-----------+
 * </pre>
 * This code is partly based on the Paradial TL original code.
 * But rewritten to more closely follow the rfc3261 rules.
 * All retransmission handling has been moved away from the provider and onto here.
 * <p/>
 * <code>$Id: RtServerTransaction.java 59999 2009-09-25 10:07:09Z dehne $</code>
 *
 * @author Arild Nisen
 * @version $Revision: 59999 $
 */
public class ServerTransaction extends TransactionBase {

    private static final String TAG = "ServerTransaction";


    // INIVTE server transaction states
    static final ServerTransactionState I_INIT = new ServerTransactionState("I_INIT");
    static final ServerTransactionState I_PROCEEDING = new ServerTransactionState("I_PROCEEDING") {
        public void enter(ServerTransaction transaction, boolean reenter) {
        }
    };
    static final ServerTransactionState I_COMPLETED = new ServerTransactionState("I_COMPLETED") {
        public void enter(ServerTransaction transaction, boolean reenter) {
            if (!transaction.isReliable()) {
                transaction.startTimerG(!reenter);
            }
            if (!reenter) {
                transaction.startTimerH();
            }
        }
    };
    static final ServerTransactionState I_CONFIRMED = new ServerTransactionState("I_CONFIRMED") {
        public void enter(ServerTransaction transaction, boolean reenter) {
            if (!reenter) {
                transaction.startTimerI();
            }
        }
    };

    // non-INIVTE server transaction states
    static final ServerTransactionState NI_INIT = new ServerTransactionState("NI_INIT");
    static final ServerTransactionState NI_TRYING = new ServerTransactionState("NI_TRYING");
    static final ServerTransactionState NI_PROCEEDING = new ServerTransactionState("NI_PROCEEDING");
    static final ServerTransactionState NI_COMPLETED = new ServerTransactionState("NI_COMPLETED") {
        public void enter(ServerTransaction transaction, boolean reenter) {
            if (!reenter) {
                transaction.startTimerJ();
            }
        }
    };

    // common end state
    static final ServerTransactionState TERMINATED = new ServerTransactionState("TERMINATED") {
        public void enter(ServerTransaction transaction, boolean reenter) {
            if (!reenter) {
                transaction.asyncUnmap();
            }
        }
    };

    static {


        // Both non-invite & invite:
        TERMINATED.addTransition(new ServerTransactionTransition(C_ANY, TERMINATED));


        /*
                    INVITE Transaction
                               |INVITE
                               |pass INV to TU
            INVITE             V send 100 if TU won't in 200ms
            send response+-----------+
                +--------|           |--------+101-199 from TU
                |        | Proceeding|        |send response
                +------->|           |<-------+
                         |           |          Transport Err.
                         |           |          Inform TU
                         |           |--------------->+
                         +-----------+                |
            300-699 from TU |     |2xx from TU        |
            send response   |     |send response      |
                            |     +------------------>+
                            |                         |
            INVITE          V          Timer G fires  |
            send response+-----------+ send response  |
                +--------|           |--------+       |
                |        | Completed |        |       |
                +------->|           |<-------+       |
                         +-----------+                |
                            |     |                   |
                        ACK |     |                   |
                        -   |     +------------------>+
                            |        Timer H fires    |
                            V        or Transport Err.|
                         +-----------+  Inform TU     |
                         |           |                |
                         | Confirmed |                |
                         |           |                |
                         +-----------+                |
                               |                      |
                               |Timer I fires         |
                               |-                     |
                               |                      |
                               V                      |
                         +-----------+                |
                         |           |                |
                         | Terminated|<---------------+
                         |           |
                         +-----------+
        */
        I_INIT.addTransition(new ServerTransactionTransition(C_INVITE, I_PROCEEDING) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.createOrGetDialog();
                    if (transaction.dialog != null) {
                        signal.getRequest().setDialog(transaction.dialog);
                    }
                    transaction.setLastResponse(transaction.createResponse(100, signal.getRequest()));
                    transaction.sendResponseToTL(transaction.getLastResponse());
                    signal.setPassRequest(true);
                } catch (SipException e) {
                    Logger.d(TAG, "Couldn't send the provisional response", e);
                }
            }
        });

        I_PROCEEDING.addTransition(new ServerTransactionTransition(C_INVITE, I_PROCEEDING) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Couldn't send the last response for this re-transmit request", e);
                }
            }
        });
        I_PROCEEDING.addTransition(new ServerTransactionTransition(C_101_199_RESP, I_PROCEEDING) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.setLastResponse(signal.getResponse());
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send provisional response", e);
                }
            }
        });
        I_PROCEEDING.addTransition(new ServerTransactionTransition(C_2XX_RESP, TERMINATED) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.sendResponseToTL(signal.getResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send response", e);
                }
            }
        });
        I_PROCEEDING.addTransition(new ServerTransactionTransition(C_300_699_RESP, I_COMPLETED) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.setLastResponse(signal.getResponse());
                    transaction.sendResponseToTL(signal.getResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send response", e);
                }
            }
        });

        I_COMPLETED.addTransition(new ServerTransactionTransition(C_INVITE, I_COMPLETED) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not re-send response", e);
                }
            }
        });
        I_COMPLETED.addTransition(new ServerTransactionTransition(C_TIMER_G_EXP, I_COMPLETED) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not re-send response", e);
                }
            }
        });
        I_COMPLETED.addTransition(new ServerTransactionTransition(C_ACK, I_CONFIRMED));
        I_COMPLETED.addTransition(new ServerTransactionTransition(C_TIMER_H_EXP, TERMINATED));

        // just to ignore pending timers
        I_CONFIRMED.addTransition(new ServerTransactionTransition(C_TIMER_G_EXP, I_CONFIRMED));
        I_CONFIRMED.addTransition(new ServerTransactionTransition(C_TIMER_H_EXP, I_CONFIRMED));
        I_CONFIRMED.addTransition(new ServerTransactionTransition(C_TIMER_I_EXP, TERMINATED));

        /* non-INVITE

                                  |Request received
                                  |pass to TU
      -adjust-     Request        v
                    ignore  +-----------+                101-199 from TU
                    +-------|           |------------------------------------------+
                    |       | Trying    |-------------+                            |
                    +------>|           |             |                            |
                            +-----------+             |200-699 from TU             |
                                  |                   |send response               |
                                  |1xx from TU        |                            |
                                  |send response      |                            |
                                  |                   |                            |
               Request            V      1xx from TU  |                            |
               send response+-----------+send response|                            v
                   +--------|           |--------+    |                       +---------------+
                   |        | Proceeding|        |    |                       |PRR-Proceeding |
                   +------->|           |<-------+    |                       |               |
            +<--------------|           |             |                       |               |
            |Trnsprt Err    +-----------+             |                       |               |
            |Inform TU            |                   |                       +---------------+
            |                     |                   |
            |                     |200-699 from TU    |
            |                     |send response      |
            |  Request            V                   |
            |  send response+-----------+             |
            |      +--------|           |             |
            |      |        | Completed |<------------+
            |      +------->|           |
            +<--------------|           |
            |Trnsprt Err    +-----------+
            |Inform TU            |
            |                     |Timer J fires
            |                     |-
            |                     |
            |                     V
            |               +-----------+
            |               |           |
            +-------------->| Terminated|
                            |           |
                            +-----------+
        */

        NI_INIT.addTransition(new ServerTransactionTransition(C_REQUEST, NI_TRYING) {
            void activity(ServerTransaction transaction, Signal signal) {
                transaction.createOrGetDialog();
                if (transaction.dialog != null) {
                    signal.getRequest().setDialog(transaction.dialog);
                }
                signal.setPassRequest(true);


                if ("ACK".equalsIgnoreCase(transaction.request.getMethod())) {
                    /*
                     * an ACK request which is for a 2xx response (and thus sent within
                     * it's own transaction) should not have gotten a new server-transaction
                     * but only be routed to the dialog handler.
                     * For now, we leave it like this (create the TX) in order to
                     * use the existing code to find the dialog-handler and terminate the TX
                     * straight away.
                     */
                    transaction.ackTerminate();
                }
            }
        });

        NI_TRYING.addTransition(new ServerTransactionTransition(C_PROV_RESP, NI_PROCEEDING) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.setLastResponse(signal.getResponse());
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send provisional response", e);
                }
            }
        });
        NI_TRYING.addTransition(new ServerTransactionTransition(C_200_699_RESP, NI_COMPLETED) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.setLastResponse(signal.getResponse());
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send response", e);
                }
            }
        });
        NI_TRYING.addTransition(new ServerTransactionTransition(C_REQUEST, NI_TRYING));
        NI_TRYING.addTransition(new ServerTransactionTransition(C_ACK_KILL, TERMINATED) {
            @Override
            void activity(ServerTransaction transaction, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Terminating server-transaction now since it is not needed any longer for ACK handling");
                }
            }
        });

        NI_PROCEEDING.addTransition(new ServerTransactionTransition(C_REQUEST, NI_PROCEEDING) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send request", e);
                }
            }
        });
        NI_PROCEEDING.addTransition(new ServerTransactionTransition(C_PROV_RESP, NI_PROCEEDING) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.setLastResponse(signal.getResponse());
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send provisional response", e);
                }
            }
        });
        NI_PROCEEDING.addTransition(new ServerTransactionTransition(C_200_699_RESP, NI_COMPLETED) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.setLastResponse(signal.getResponse());
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send response", e);
                }
            }
        });

        NI_COMPLETED.addTransition(new ServerTransactionTransition(C_REQUEST, NI_COMPLETED) {
            void activity(ServerTransaction transaction, Signal signal) {
                try {
                    transaction.sendResponseToTL(transaction.getLastResponse());
                } catch (SipException e) {
                    Logger.d(TAG, "Could not re-send last response");
                }
            }
        });
        NI_COMPLETED.addTransition(new ServerTransactionTransition(C_TIMER_J_EXP, TERMINATED));

    }

    private boolean reliable;
    private int timerGscale = 0;
    private Response pendingReliableResponse;
    private final ISipStackListener serverTransactionListener;

    public ServerTransaction(Request rq, boolean isReliable, ISipStackListener serverTransactionListener) {
        super(rq, Invite.NAME.equals(rq.getMethod()) ? I_INIT : NI_INIT);
        reliable = isReliable;
        this.serverTransactionListener = serverTransactionListener;
    }

    public void sendResponse(Response response) {
        if (dialog != null && response.getStatusCode() != 100) {
            if (!dialog.checkResponseTags(response))
                throw new RuntimeException(
                        "Response tags dont match with Dialog tags");


            boolean isInitialRequest = getRequest().getToTag() == null;
            updateDialog(response, isInitialRequest);
        }

        try {
            input(new Signal(response));
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.w(TAG, "sendResponse failed, throwing exception", e);
            if (e.getCause() instanceof SipException) {
                throw new RuntimeException(e);
            } else {
                Logger.e(TAG, "FSM threw unexpected exception", e);
            }
        }
    }

    /**
     * Cancel the retransmit timer for the provisional response task.
     *
     * @return true if the tx has seen the prack for the first time and false
     *         otherwise.
     */
    public boolean prackRecieved() {

        if (this.pendingReliableResponse == null)
            return false;
        //this.provisionalResponseTask.cancel();
        this.pendingReliableResponse = null;
        return true;
    }


    public void enableRetransmissionAlerts() throws SipException {
        throw new RuntimeException("no implementation here");
    }

    private void sendResponseToTL(Response response) throws SipException {
        SipStack.get().sendResponse(response);
    }


    public String toString() {
        return "ServerTransaction for " + Utils.getShortDescription(request);
    }

    protected boolean isTerminated() {
        return getUnsafeCurrentState() == TERMINATED;
    }

    protected void updateDialog(Response rsp, boolean isInitialRequest) {
        if (dialog != null) {
            dialog.sendingResponse(rsp, isInitialRequest);
            //dialog.sendingResponse(rsp);
        }
    }

    public void handleRequest(Request request) {
        Signal signal = new Signal(request);
        try {
            input(signal);
        } catch (Exception e) {
            Logger.e(TAG, "Could not handle request", e);
            return;
        }

        if (signal.getPassRequest()) {
            // route in-dialog requests directly to the handler
            if (dialog != null && dialog.getApplicationData() != null) {
                dialog.getApplicationData().processRequest(request);
            }

            // initial or outside dialog requests are routed to the default handler
            else {
                serverTransactionListener.processRequest(request);
            }
        }

    }

    private Response createResponse(int statusCode, Request request) {
        return request.createResponse(statusCode);
    }


    /**
     * Controls timer G,
     * If first time then time = T1,
     * on subsequenct starts the timer is is doubled up the the value of T2
     *
     * @param firstTime first time or not
     */
    private void startTimerG(boolean firstTime) {
        int timerG;
        if (firstTime) {
            timerG = TIMER_G_IVAL;
            timerGscale = 2;
            startTimer(TimerID.G, timerG);
        } else {
            if (!isTimerRunning(TimerID.G)) {
                timerG = Math.min(timerGscale * T1, T2);
                timerGscale *= 2;
                startTimer(TimerID.G, timerG);
            }
        }
    }

    private void startTimerH() {
        startTimer(TimerID.H, TIMER_H);
    }

    private void startTimerI() {
        startTimer(TimerID.I, isReliable() ? TIMER_I_TCP : TIMER_I_UDP);
    }

    private void startTimerJ() {
        startTimer(TimerID.J, isReliable() ? TIMER_J_TCP : TIMER_J_UDP);
    }

    boolean isReliable() {
        return reliable;
    }

    /**
     * Deterines if the message is a part of this transaction.
     *
     * @param messageToTest Message to check if it is part of this transaction.
     * @return True if the message is part of this transaction, false if not.
     */
    public boolean isMessagePartOfTransaction(SipMessage messageToTest) {

        boolean transactionMatches = false;

        if ((messageToTest.getCSeq().getMethod().equals(Invite.NAME) || !isTerminated())) {
            ViaHeader topViaHeader = messageToTest.getFirstViaHeader();
            transactionMatches = getBranchId().equalsIgnoreCase(topViaHeader.getBranch())
                    && topViaHeader.getSentBy().equals((getOriginalRequest().getFirstViaHeader()).getSentBy());
        }
        return transactionMatches;

    }


    public String getMachineId() {
        return "ServerTransaction(" + getBranchId() + ")";
    }

    public Type getType() {
        return Type.serverTransaction;
    }

    private void createOrGetDialog() {

        final String dialogId = getRequest().getDialogId(true, getRequest().getToTag());
        Dialog dialog = SipStack.get().getTxRepository().getDialog(dialogId);

        /*
         * In case we receive a NOTIFY before the final response for the SUBSCRIBE which we sent out,
         * try to find the transaction which initiated the SUBSCRIBE, such that we can create the dialog
         * from there
         */
        if (dialog == null && Notify.NAME.equals(getRequest().getMethod())) {
            ClientTransaction subscribeTransaction = SipStack.get().getTxRepository().findSubscribeTransaction(getRequest());
            if (subscribeTransaction != null) {
                dialog = subscribeTransaction.getDialog();
            }
        }


        if (dialog == null &&
                TransactionRepository.isDialogCreating(getRequest().getMethod()) &&
                Configuration.isAutomaticDialogSupport()) {
            dialog = new Dialog(this); // just create it, don't map it. We will do that when going to CONFIRM state
        }

        if (dialog != null)
            setDialog(dialog);
    }

    private void ackTerminate() {
        try {
            super.input(new Signal(Signal.Type.ackKill));
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
    }
}

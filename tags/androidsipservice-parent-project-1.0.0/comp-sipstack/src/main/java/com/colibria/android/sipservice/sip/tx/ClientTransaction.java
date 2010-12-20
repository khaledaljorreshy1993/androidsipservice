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
import com.colibria.android.sipservice.sip.SipStack;
import com.colibria.android.sipservice.sip.headers.CSeqHeader;
import com.colibria.android.sipservice.sip.headers.RouteHeader;
import com.colibria.android.sipservice.sip.messages.*;

import static com.colibria.android.sipservice.sip.tx.TransactionCondition.*;

import java.util.Collections;
import java.util.List;

/**
 * The Client Transaction.
 * Comes in one out of two flavours. The INVITE or the non-INVITE client transaction.
 * <p/>
 * <pre>
 *             INVITE client transaction
 * <p/>
 *                                          |INVITE from TU
 *                        Timer A fires     |INVITE sent
 *                        Reset A,          V                      Timer B fires
 *                        INVITE sent +-----------+                or Transport Err.
 *                          +---------|           |---------------+inform TU
 *                          |         |  Calling  |               |
 *                          +-------->|           |-------------->|
 *                                    +-----------+ 2xx           |
 *                                       |  |       2xx to TU     |
 *                                       |  |1xx                  |
 *               300-699 +---------------+  |1xx to TU            |
 *              ACK sent |                  |                     |
 *           resp. to TU |  1xx             V                     |
 *                       |  1xx to TU  -----------+               |
 *                       |  +---------|           |               |
 *                       |  |         |Proceeding |-------------->|
 *                       |  +-------->|           | 2xx           |
 *                       |            +-----------+ 2xx to TU     |
 *                       |       300-699    |                     |
 *                       |       ACK sent,  |                     |
 *                       |       resp. to TU|                     |
 *                       |                  |                     |      NOTE:
 *                       |  300-699         V                     |
 *                       |  ACK sent  +-----------+Transport Err. |  transitions
 *                       |  +---------|           |Inform TU      |  labeled with
 *                       |  |         | Completed |-------------->|  the event
 *                       |  +-------->|           |               |  over the action
 *                       |            +-----------+               |  to take
 *                       |              ^   |                     |
 *                       |              |   | Timer D fires       |
 *                       +--------------+   | -                   |
 *                                          |                     |
 *                                          V                     |
 *                                    +-----------+               |
 *                                    |           |               |
 *                                    | Terminated|<--------------+
 *                                    |           |
 *                                    +-----------+
 * </pre>
 * <pre>
 *             Non-INVITE client transaction
 * <p/>
 *                      |Request from TU
 *                      |send request
 *  Timer E             V
 *  send request  +-----------+
 *      +---------|           |-------------------+
 *      |         |  Trying   |  Timer F          |
 *      +-------->|           |  or Transport Err.|
 *                +-----------+  inform TU        |
 *   200-699         |  |                         |
 *   resp. to TU     |  |1xx                      |
 *   +---------------+  |resp. to TU              |
 *   |                  |                         |
 *   |   Timer E        V       Timer F           |
 *   |   send req +-----------+ or Transport Err. |
 *   |  +---------|           | inform TU         |
 *   |  |         |Proceeding |------------------>|
 *   |  +-------->|           |-----+             |
 *   |            +-----------+     |1xx          |
 *   |              |       *      |resp to TU   |
 *   | 200-699      |      +--------+             |
 *   | resp. to TU  |                             |
 *   |              |                             |
 *   |              V           resp              |
 *   |            +-----------+ ignore            |
 *   |            |           |------+            |
 *   |            | Completed |      |            |
 *   |            |           |<-----+            |
 *   |            +-----------+                   |
 *   |               * |                         |
 *   |              |   | Timer K                 |
 *   +--------------+   | -                       |
 *                      |                         |
 *                      V                         |
 *                +-----------+                   |
 *                |           |                   |
 *                | Terminated|<------------------+
 *                |           |
 *                +-----------+
 * <p/>
 * </pre>
 * This code is partly based on the Paradial TL original code.
 * But rewritten to more closely follow the rfc3261 rules.
 * All retransmission handling has been moved away from the provider and onto here.
 * <p/>
 * <code>$Id: RtClientTransaction.java 62034 2009-11-09 14:52:10Z dehne $</code>
 *
 * @author Arild Nisen
 * @version $Revision: 62034 $
 */

public class ClientTransaction extends TransactionBase {
    private static final String TAG = "ClientTransaction";

    // INVITE FSM states
    static final TransactionState I_INIT = new ClientTransactionState("I_INIT");
    static final TransactionState I_CALLING = new ClientTransactionState("I_CALLING") {
        public void enter(ClientTransaction transaction, boolean reenter) {
            if (!transaction.isReliable()) {
                transaction.startTimerA(!reenter);
            }
            if (!reenter) {
                transaction.startTimerB();
            }
        }

        public void exit(ClientTransaction transaction, boolean forReenter) {
            if (!transaction.isReliable()) {
                transaction.stopTimerA();
            }
            if (!forReenter) {
                transaction.stopTimerB();
            }
        }
    };
    static final TransactionState I_PROCEEDING = new ClientTransactionState("I_PROCEEDING") {
        public void enter(ClientTransaction transaction, boolean reenter) {
        }
    };
    static final TransactionState I_COMPLETED = new ClientTransactionState("I_COMPLETED") {
        public void enter(ClientTransaction transaction, boolean reenter) {
            if (!reenter) {
                if (transaction.isReliable()) {
                    transaction.startTimerD(); // start with 0 value here to drive to TERMINATE ??
                } else {
                    transaction.startTimerD(); // 32 secs
                }
            }
        }
    };

    // Non INVITE FSM states
    static final TransactionState NI_INIT = new ClientTransactionState("NI_INIT");
    static final TransactionState NI_TRYING = new ClientTransactionState("NI_TRYING") {
        public void enter(ClientTransaction transaction, boolean reenter) {
            if (!transaction.isReliable()) {
                transaction.startTimerE(!(reenter));
            }
            if (!reenter) {
                transaction.startTimerF();
            }
        }

        public void exit(ClientTransaction transaction, boolean forReenter) {
            if (!transaction.isReliable()) {
                transaction.stopTimerE();
            }
        }
    };
    static final TransactionState NI_PROCEEDING = new ClientTransactionState("NI_PROCEEDING") {
        public void enter(ClientTransaction transaction, boolean reenter) {
            if (!transaction.isReliable()) {
                transaction.startTimerE(!reenter);
            }
            if (!reenter) {
                transaction.startTimerF();
            }
        }

        public void exit(ClientTransaction transaction, boolean forReenter) {
            if (!transaction.isReliable()) {
                transaction.stopTimerE();
            }
        }
    };
    static final TransactionState NI_COMPLETED = new ClientTransactionState("NI_COMPLETED") {
        public void enter(ClientTransaction transaction, boolean reenter) {
            //transaction.stopTimerE();
            if (!reenter) {
                transaction.startTimerK();
            }
        }
    };

    // common end state
    static final ClientTransactionState TERMINATED = new ClientTransactionState("TERMINATED") {
        public void enter(ClientTransaction transaction, boolean reenter) {
            if (!reenter) {
                transaction.asyncUnmap();
            }
        }
    };

    static {

        // for both invite & non-invite transactions
        TERMINATED.addTransition(new ClientTransactionTransition(C_ANY, TERMINATED) {
            @Override
            void activity(ClientTransaction transaction, Signal signal) throws TransitionActivityException {
                Logger.d(TAG, "Ignoring signal " + signal + " in TERMINATED state");
            }
        });

        /*
            INVITE client transaction

                                           |INVITE from TU
                         Timer A fires     |INVITE sent
                         Reset A,          V                      Timer B fires
                         INVITE sent +-----------+                or Transport Err.
                           +---------|           |---------------+inform TU
                           |         |  Calling  |               |
                           +-------->|           |-------------->|
                                     +-----------+ 2xx           |
                                        |  |       2xx to TU     |
                                        |  |1xx                  |
                300-699 +---------------+  |1xx to TU            |
               ACK sent |                  |                     |
            resp. to TU |  1xx             V                     |
                        |  1xx to TU  -----------+               |
                        |  +---------|           |               |
                        |  |         |Proceeding |-------------->|
                        |  +-------->|           | 2xx           |
                        |            +-----------+ 2xx to TU     |
                        |       300-699    |                     |
                        |       ACK sent,  |                     |
                        |       resp. to TU|                     |
                        |                  |                     |      NOTE:
                        |  300-699         V                     |
                        |  ACK sent  +-----------+Transport Err. |  transitions
                        |  +---------|           |Inform TU      |  labeled with
                        |  |         | Completed |-------------->|  the event
                        |  +-------->|           |               |  over the action
                        |            +-----------+               |  to take
                        |              ^   |                     |
                        |              |   | Timer D fires       |
                        +--------------+   | -                   |
                                           |                     |
                                           V                     |
                                     +-----------+               |
                                     |           |               |
                                     | Terminated|<--------------+
                                     |           |
                                     +-----------+
        */
        I_INIT.addTransition(new ClientTransactionTransition(C_INVITE, I_CALLING) {
            void activity(final ClientTransaction transaction, Signal signal) {
                transaction.setReliable(SipStack.get().sendRequest(transaction.getRequest()));
            }
        });

        I_CALLING.addTransition(new ClientTransactionTransition(C_TIMER_A_EXP, I_CALLING) {
            void activity(ClientTransaction transaction, Signal signal) {
                // let provider re-send the request
                SipStack.get().sendRequest(transaction.getRequest());
            }
        });
        I_CALLING.addTransition(new ClientTransactionTransition(C_TIMER_B_EXP, TERMINATED) {
            void activity(ClientTransaction transaction, Signal signal) {
                transaction.listener.processTimeout(transaction);
            }
        });
        I_CALLING.addTransition(new ClientTransactionTransition(C_PROV_RESP, I_PROCEEDING) {
            void activity(ClientTransaction transaction, Signal signal) {
                signal.setPassResponse(true);
            }
        });
        I_CALLING.addTransition(new ClientTransactionTransition(C_300_699_RESP, I_COMPLETED) {
            void activity(ClientTransaction transaction, Signal signal) throws TransitionActivityException {
                signal.setPassResponse(true);
                try {
                    transaction.generateAndSendAck();
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send ACK", e);
                }
            }
        });
        I_CALLING.addTransition(new ClientTransactionTransition(C_2XX_RESP, TERMINATED) {
            void activity(ClientTransaction transaction, Signal signal) {
                signal.setPassResponse(true);
            }
        });

        I_PROCEEDING.addTransition(new ClientTransactionTransition(C_PROV_RESP, I_PROCEEDING) {
            void activity(ClientTransaction transaction, Signal signal) {
                signal.setPassResponse(true);
            }
        });
        I_PROCEEDING.addTransition(new ClientTransactionTransition(C_300_699_RESP, I_COMPLETED) {
            void activity(ClientTransaction transaction, Signal signal) throws TransitionActivityException {
                signal.setPassResponse(true);
                try {
                    transaction.generateAndSendAck();
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send ACK", e);
                }
            }
        });
        I_PROCEEDING.addTransition(new ClientTransactionTransition(C_2XX_RESP, TERMINATED) {
            void activity(ClientTransaction transaction, Signal signal) {
                signal.setPassResponse(true);
            }
        });
        I_PROCEEDING.addTransition(new ClientTransactionTransition(C_TIMER_A_EXP, I_PROCEEDING));

        I_COMPLETED.addTransition(new ClientTransactionTransition(C_300_699_RESP, I_COMPLETED) {
            void activity(ClientTransaction transaction, Signal signal) throws TransitionActivityException {
                signal.setPassResponse(false);
                try {
                    transaction.generateAndSendAck();
                } catch (SipException e) {
                    Logger.d(TAG, "Could not send ACK", e);
                }
            }
        });
        I_COMPLETED.addTransition(new ClientTransactionTransition(C_TIMER_D_EXP, TERMINATED));

        /*
            non-INVITE client transaction
                                   |Request from TU
                                   |send request
               Timer E             V
               send request  +-----------+
                   +---------|           |-------------------+
                   |         |  Trying   |  Timer F          |
                   +-------->|           |  or Transport Err.|
                             +-----------+  inform TU        |
                200-699         |  |                         |
                resp. to TU     |  |1xx                      |
                +---------------+  |resp. to TU              |
                |                  |                         |
                |   Timer E        V       Timer F           |
                |   send req +-----------+ or Transport Err. |
                |  +---------|           | inform TU         |
                |  |         |Proceeding |------------------>|
                |  +-------->|           |-----+             |
                |            +-----------+     |1xx          |
                |              |      ^        |resp to TU   |
                | 200-699      |      +--------+             |
                | resp. to TU  |                             |
                |              |                             |
                |              V           resp              |
                |            +-----------+ ignore            |
                |            |           |------+            |
                |            | Completed |      |            |
                |            |           |<-----+            |
                |            +-----------+                   |
                |              ^   |                         |
                |              |   | Timer K                 |
                +--------------+   | -                       |
                                   |                         |
                                   V                         |
                             +-----------+                   |
                             |           |                   |
                             | Terminated|<------------------+
                             |           |
                             +-----------+

        */

        NI_INIT.addTransition(new ClientTransactionTransition(C_NON_INVITE_REQUEST, NI_TRYING) {
            void activity(final ClientTransaction transaction, Signal signal) {
                transaction.setReliable(SipStack.get().sendRequest(transaction.getRequest()));
            }
        });

        NI_TRYING.addTransition(new ClientTransactionTransition(C_TIMER_E_EXP, NI_TRYING) {
            void activity(ClientTransaction transaction, Signal signal) {
                SipStack.get().sendRequest(transaction.getRequest());
            }
        });
        NI_TRYING.addTransition(new ClientTransactionTransition(C_TIMER_F_EXP, TERMINATED) {
            void activity(ClientTransaction transaction, Signal signal) {
                transaction.listener.processTimeout(transaction);
            }
        });
        NI_TRYING.addTransition(new ClientTransactionTransition(C_PROV_RESP, NI_PROCEEDING) {
            void activity(ClientTransaction transaction, Signal signal) {
                signal.setPassResponse(true);
            }
        });
        NI_TRYING.addTransition(new ClientTransactionTransition(C_200_699_RESP, NI_COMPLETED) {
            void activity(ClientTransaction transaction, Signal signal) {
                signal.setPassResponse(true);
            }
        });

        NI_PROCEEDING.addTransition(new ClientTransactionTransition(C_TIMER_E_EXP, NI_PROCEEDING) {
            void activity(ClientTransaction transaction, Signal signal) {
                SipStack.get().sendRequest(transaction.getRequest());
            }
        });
        NI_PROCEEDING.addTransition(new ClientTransactionTransition(C_TIMER_F_EXP, TERMINATED) {
            void activity(ClientTransaction transaction, Signal signal) {
                transaction.listener.processTimeout(transaction);
            }
        });
        NI_PROCEEDING.addTransition(new ClientTransactionTransition(C_PROV_RESP, NI_PROCEEDING) {
            void activity(ClientTransaction transaction, Signal signal) {
                signal.setPassResponse(true);
            }
        });
        NI_PROCEEDING.addTransition(new ClientTransactionTransition(C_200_699_RESP, NI_COMPLETED) {
            void activity(ClientTransaction transaction, Signal signal) {
                signal.setPassResponse(true);
            }
        });

        NI_COMPLETED.addTransition(new ClientTransactionTransition(C_TIMER_F_EXP, NI_COMPLETED)); // ignore
        NI_COMPLETED.addTransition(new ClientTransactionTransition(C_TIMER_E_EXP, NI_COMPLETED)); // timer E could not be cancelled in time? Ignoring this late signal
        NI_COMPLETED.addTransition(new ClientTransactionTransition(C_XXX_RESP, NI_COMPLETED)); // ignore for unreliable response retransmits
        NI_COMPLETED.addTransition(new ClientTransactionTransition(C_TIMER_K_EXP, TERMINATED));

    }

    private boolean reliable;
    private int timerA;
    private int timerEscale = 0;
    protected IClientTransactionListener listener;

    public ClientTransaction(Request rq, IClientTransactionListener listener, boolean reliable) {
        // init correct state machine according to method being INVITE or not
        super(rq, Invite.NAME.equals(rq.getMethod()) ? I_INIT : NI_INIT);
        timerA = T1;
        this.reliable = reliable;
        this.listener = listener;
    }

    /**
     * Determines if the message is a part of this transaction.
     *
     * @param messageToTest Message to check if it is part of this transaction.
     * @return True if the message is part of this transaction, false if not.
     */
    public boolean isMessagePartOfTransaction(SipMessage messageToTest) {
        return !isTerminated()
                && getBranchId().equalsIgnoreCase(messageToTest.getFirstViaHeader().getBranch())
                && getMethod().equals(messageToTest.getCSeq().getMethod());
    }


    protected boolean isTerminated() {
        return getUnsafeCurrentState() == TERMINATED;
    }

    /**
     * Creats and sends a ACK to a 3xx-6xx final response.
     * This ack is sent automatically by the transaction-layer
     * and is sent outside the dialog.
     *
     * @throws SipException text
     */
    private void generateAndSendAck() throws SipException {
        SipStack.get().sendRequest(createAckForNon2xxCase());

    }

    private boolean isReliable() {
        return reliable;
    }

    private void setReliable(boolean b) {
        reliable = b;
    }

    private void startTimerA(boolean firstTime) {
        if (firstTime) {
            timerA = TIMER_A_IVAL;
        } else {
            timerA *= 2;
        }
        startTimer(TimerID.A, timerA);
    }

    private void stopTimerA() {
        cancelTimer(TimerID.A);
    }

    private void startTimerB() {
        startTimer(TimerID.B, TIMER_B);
    }

    private void stopTimerB() {
        cancelTimer(TimerID.B);
    }

    private void startTimerD() {
        startTimer(TimerID.D, isReliable() ? TIMER_D_TCP : TIMER_D_UDP);
    }


    private void startTimerE(boolean firstTime) {
        int timerE;
        if (!isTimerRunning(TimerID.E)) {
            if (firstTime) {
                timerE = TIMER_E_IVAL;
                timerEscale = 2;
                startTimer(TimerID.E, timerE);
            } else {
                int tmp;
                if ((tmp = timerEscale * T1) <= T2) {
                    timerEscale *= 2;
                    startTimer(TimerID.E, tmp);
                } else {
                    startTimer(TimerID.E, T2);
                }
            }
        }
    }

    private void stopTimerE() {
        cancelTimer(TimerID.E);
    }


    private void startTimerF() {
        startTimer(TimerID.F, TIMER_F);
    }

    private void startTimerK() {
        startTimer(TimerID.K, isReliable() ? TIMER_K_TCP : TIMER_K_UDP);
    }


    public void sendRequest() {
        sendRequest(false);
    }

    void sendRequest(boolean viaDialog) {

        if (this.getOriginalRequest().getMethod().equals(Bye.NAME) || this.getOriginalRequest().getMethod().equals(Notify.NAME)) {

            // I want to behave like a user agent so send the BYE using the
            // Dialog
            if (!viaDialog && Configuration.isAutomaticDialogSupport() && dialog != null) {
                throw new RuntimeException(
                        "Dialog is present and AutomaticDialogSupport is enabled for "
                                + " the provider -- Send the Request using the Dialog.sendRequest(transaction)");
            }
        }
        Signal signal = new Signal(this.getRequest());
        //noinspection EmptyCatchBlock
        try {
            input(signal);
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException ignore) {
            Logger.e(TAG, "", ignore);
        }
    }

    public Request createAckForNon2xxCase() throws SipException {
        Request originalRequest = this.getOriginalRequest();
        if (originalRequest == null) {
            throw new SipException("bad state " + getUnsafeCurrentState());
        }
        if (getMethod().equalsIgnoreCase(Ack.NAME)) {
            throw new SipException("Cannot ACK an ACK!");
        } else if (lastResponse == null) {
            throw new SipException("bad Transaction state");
        } else if (lastResponse.getStatusCode() < 200) {
            throw new SipException("Cannot ACK a provisional response!");
        } else if (lastResponse.getStatusCode() < 300) {
            throw new RuntimeException("This method shouldn't be used to ACK 2xx final responses!");
        }

        CSeqHeader cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        Request ack = Ack.create(
                request.getRequestUri(),
                request.getCallId(),
                cseq.getSeqNumber(),
                request.getFrom().getAddress(),
                request.getTo().getAddress(),
                Collections.singletonList(request.getFirstViaHeader()));

        /*
        * Idea here is:
        *  - if dialog is set and has a non empty route set, use that
        *  else
        * - use route from original request
        */

        List<RouteHeader> rhIter = null;
        if (dialog != null) {
            //noinspection unchecked
            rhIter = dialog.getRouteSet();
        }
        if (rhIter == null || rhIter.size() == 0) {
            //noinspection unchecked
            rhIter = request.getHeaders(RouteHeader.NAME);
        }
        ack.addHeaders(rhIter);

        return ack;
    }

    /**
     * Notifies the transaction that a response has been received.
     * Will handle states, timers, etc.
     *
     * @param rsp the response.
     */
    public void responseReceived(Response rsp) {
        // here or in FSM ??
        // probably move into FSM
        if (dialog != null) {
            dialog.responseReceived(rsp);
        }
        lastResponse = rsp;
        try {
            Signal signal = new Signal(rsp);
            input(signal);

            if (signal.getPassResponse()) {
                listener.processResponse(rsp);
            }
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        }
    }

    public String toString() {
        return "ClientTransaction for " + Utils.getShortDescription(request);
    }

    public String getMachineId() {
        return "ClientTransaction(" + getBranchId() + ")";
    }

    public Type getType() {
        return Type.clientTransaction;
    }
}
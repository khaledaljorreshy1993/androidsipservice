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
package com.colibria.android.sipservice.sip.frameworks.uagent;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.fsm.State;
import com.colibria.android.sipservice.fsm.TransitionActivityException;
import com.colibria.android.sipservice.fsm.UnhandledConditionException;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.fsm.Machine;
import com.colibria.android.sipservice.sdp.api.SessionDescription;
import com.colibria.android.sipservice.sip.SipStack;
import com.colibria.android.sipservice.sip.frameworks.uagent.fsm.CallState;
import com.colibria.android.sipservice.sip.frameworks.uagent.fsm.Signal;
import com.colibria.android.sipservice.sip.frameworks.uagent.fsm.UAgent2CallTransition;
import com.colibria.android.sipservice.sip.frameworks.uagent.fsm.UaCallCondition2;
import com.colibria.android.sipservice.sip.headers.CSeqHeader;
import com.colibria.android.sipservice.sip.headers.MinSeHeader;
import com.colibria.android.sipservice.sip.headers.SessionExpiresHeader;
import com.colibria.android.sipservice.sip.messages.*;
import com.colibria.android.sipservice.sip.tx.ClientTransaction;
import com.colibria.android.sipservice.sip.tx.Dialog;
import com.colibria.android.sipservice.sip.tx.IInDialogRequestHandler;
import com.colibria.android.sipservice.sip.tx.TransactionBase;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The UAgent.
 * <p/>
 * A SIP UAS (UserAgent Server) is the agent which handles incoming INVITE-requests while it is the
 * UAC (userAgent Client) which sends initial INVITE requests. However, once the session is established,
 * handling re-INVITEs (as well as sending as receiving) and session-termination (as well
 * as sending BYE as receiving it) is the same for both types of UA's.
 * Therefore, it was decided to have both the UAS- and the UAC implementation in the same class.
 * <p/>
 * The UAgent takes automatically care of refreshing the session timer (by sending re-INVITE as required
 * with an empty body)
 * <p/>
 * If a new instance is created, the role (uas/uac) must be specified during initialization.
 * <p/>
 * The finite state machine of the UAgent:
 * <p/>
 * <pre>
 *                         onInviteReceived   +------+      onSendInvite
 *                        +-------------------| INIT |------------------+
 *                        |                   +------+                  |
 *                        |                                             |
 *                        |                                             v
 *                        |                       onSendReInvite  +-------------+  onNonOk or timeout
 *                        |                        +------------->| WAIT_TRYING |-------------------+
 *                        |                        |              +-------------+                   |
 *                        |                        |                |   |                           |
 *                        |                        |                |   | onTrying                  |
 *         CancelR        |                        |        +---<---+   |                           |
 *          +------+      |                        |        |           |    +------+ on1xxResp /   |
 *          |      v      v                        |        |           v    v      |  sendCancel   |
 *          |    +------------+  onReInviteReceiv  ^        |      +------------+   |               |
 *          +----| ESTAB_RECV |<-----------------+ |        |      | ESTAB_SEND |---+               |
 *               +------------+                  | |        |      +------------+                   |
 *                   |  |                        | |        |        |      |                       |
 *        rejected/  |  |                        | |        |        |      |                       |
 *           onRespR |  |                        | |        +------->|      |                       |
 *                   |  |                        | |                 |      | timeout/              |
 *                   |  |                        ^ ^                 v      | 4xx response          |
 *                   |  | proceed/rejectReInv    | |                 |      |                       |
 *                   |  +----------------------+ | | +---------------+      |                       |
 *                   |                         | | | |    on2xxResp         |<----------------------+
 *                   |                         | | | |                      |
 *                   |                         v | | v                      |
 *                   |                       +--------+                     |
 *                   |                       | ACITVE |                     |
 *                   |                       +--------+                     |
 *                   |                           |                          |
 *                   |                           | onSendBye /              |
 *                   |                           | onByeReceived            |
 *                   |                           v                          |
 *                   |                     +------------+                   |
 *                   +-------------------->| TERMINATED |<------------------+
 *                                         +------------+
 * </pre>
 * NOTE: On receiving a non-OK during a re-INVITE transaction in WAIT_TRYING/ESTAB_RECV/ESTAB_SEND state, the machine
 * transitions back to ACTIVE, since this doesn't affect the already established session. However, a timeout
 * will trigger the already established session to be terminated. This is not shown in above figure.
 * <p/>
 * User-plane:
 * In case of an uas, the user-plane should be activated (enabled/prepared) after onInviteReceived() is called and
 * before the user calls process(Signal(proceedInvite, response)) to send the response.
 * In case of an uac, the user-plane should be activated (enabled/prepared) after onInviteAccepted() is called and
 * before the user calls process(Signal(ack)) to send the ack.
 * <p/>
 * The user-agent stores the a hashcode of the last SdpOffer and its corresponding SdpAnswer as a string. In case
 * any sub-sequent SDP handling matches. In case a new re-INVITE arrives with the same SDP as before or with
 * an empty body, the UAgent assumes that this is a timer-refresh and will handle this transaction silently
 * without notifying the user (implementing class). Since only the hash is compared, no parsing of the SDP
 * is required, which make refresh-transactions fast. Only in the case a incoming re-INVITE contains a body
 * with a different SDP, the user is called for further processing.
 * <p/>
 * In case the UAgent self is the timetr-refresher, it initializes a refresh re-INVITE transaction silently
 * without notifying the user. This IVNITE contains no body.
 * todo implement handling of 422 responses (in case the offered session expire is rejected)
 * <p/>
 *
 * @author Sebastian Dehne
 */
public abstract class UAgent2 extends Machine<Signal> implements IInDialogRequestHandler {
    private static final String TAG = "UAgent2";


    // add some default values in case config repository is not available (e.g. sipclient)
    private static final boolean SESSION_TIMER_FORCED = true;
    private static final long MIN_SESSION_EXPIRE = 90;
    private static final long PREFERRED_SESSION_EXPIRE = 3600;

    public static final CallState INIT = new CallState("INIT");
    public static final CallState WAIT_TRYING = new CallState("WAIT_TRYING");
    public static final CallState ESTAB_RECV = new CallState("ESTAB_RECV");
    public static final CallState ESTAB_SEND = new CallState("ESTAB_SEND") {
        @Override
        public void enter(UAgent2 ua, boolean reentering) {
            ua.onEstablishSendStateReached();
        }

        @Override
        public void exit(UAgent2 ua, boolean reentering) {
            ua.onEstablishSendStateLeft();
        }
    };
    /**
     * The active state describes a state where this uagent is able to accept new requests for this dialog,
     * such a re-INVITE or BYE.
     * <p/>
     * Please note that this state has a hidden sub-state: in case of a serverInviteTransaction had just been
     * completed with a 2xx response, the ACTIVE state is used to re-transmit this response until an
     * ACK is received.
     */
    public static final CallState ACTIVE = new CallState("ACTIVE") {
        @Override
        public void enter(UAgent2 ua, boolean reentering) {

            // reset some state variables
            ua.isReinviteTransaction = false;
            ua.isSilentRefreshTransaction = false;
            ua.inviteTransaction = null;
            ua.invite = null;

            ua.startSessionTimers();
            ua.startRetransmitTimerFor2xx();
        }

        @Override
        public void exit(UAgent2 ua, boolean reentering) {
            ua.stopRetransmitTimerFor2xx();
            ua.stopSessionTimers();
        }
    };
    public static final CallState TERMINATED = new CallState("TERMINATED") {
        @Override
        public void enter(UAgent2 ua, boolean reentering) {
            if (!reentering) {
                try {
                    ua.onSessionTerminated(ua.getSignal());
                } catch (Exception e) {
                    Logger.e(TAG, "", e);
                }
                ua.dialog.delete();
            }
        }
    };

    public static enum Role {
        uac, uas
    }

    static {

        INIT.addTransition(new UAgent2CallTransition(UaCallCondition2.INVITE_SEND, WAIT_TRYING, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                /*
                * An invite is to be sent out. Triggers a state-transition to WAIT_TRYING,
                * since the uac needs to wait for the 100 Trying before it is allowed to send a
                * CANCEL request.
                */
                Invite outgoingInvite = signal.getInvite();

                // Prepare Session-timer
                outgoingInvite.setHeader(new SessionExpiresHeader(ua.getPreferredSessionExpire(), SipMessage.SessionTimerRefresher.uas));
                outgoingInvite.setHeader(new MinSeHeader(ua.getMinSessionExpire()));
                outgoingInvite.addSupportedOptionTag("timer");

                // send the invite
                // since automatic dialog creation might be turned off, create it now (forceCreation = true)
                ua.inviteTransaction = outgoingInvite.send(ua);

                // store data and bind ourself to it, so that we receive messages
                ua.dialog = ua.inviteTransaction.getDialog();
                ua.invite = outgoingInvite;
                //ua.dialog.setApplicationData(ua);

            }
        });
        INIT.addTransition(new UAgent2CallTransition(UaCallCondition2.INVITE_RECEIVED, ESTAB_RECV, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {

                /*
                 * An invite was received
                 *
                 * 100 trying has already been sent by the stack
                 */
                Invite incomingInvite = signal.getInvite();

                // Store the invite for later processing
                ua.invite = incomingInvite;

                // store the dialog and bind ourself to it, so that we receive messages
                ua.dialog = ua.invite.getServerTransaction().getDialog();
                ua.invite.getServerTransaction().setApplicationData(ua);
                ua.dialog.setApplicationData(ua);

                // let the user do someting with this invite and wait for the next signal
                ua.onInviteReceived(incomingInvite);

            }
        });
        INIT.addTransition(new UAgent2CallTransition(UaCallCondition2.BYE_SEND, INIT, TERMINATED));

        WAIT_TRYING.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_100, ESTAB_SEND, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                /*
                 * The 100 Trying was received. It is now allowed to send a CANCEL request
                 */
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "100 Trying recevied. The (re-)invite-transaction is now cancelable.");
                }
            }
        });
        WAIT_TRYING.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_1XX, ESTAB_SEND, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                /*
                 * The 180 Ringing was received. It is now allowed to send a CANCEL request
                 */
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "1XX recevied. The (re-)invite-transaction is now cancelable.");
                }
                // notify user about this event
                ua.onProvisionalResponseReceived(signal.getResponse());
            }
        });
        WAIT_TRYING.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_200, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received 200 OK response in WAIT_TRYING state. Re-INVITE transaction=" + ua.isReinviteTransaction);
                }
                inviteAccepted(ua, signal);
            }
        });
        WAIT_TRYING.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_ANY_REINVITE, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Re-INVITE rejected, sending ACK and going back to ACTIVE state.");
                }
                // ack has been sent by the stack (TU)
            }
        });
        WAIT_TRYING.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_ANY, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received unexpected '" + signal.getResponse().getStatusCode() + " "
                            + signal.getResponse().getReasonPhrase()
                            + "' during initial invite. Sending ACK and proceeding to TERMINATED");
                }
                // ack has been sent by the stack (TU)
            }
        });
        WAIT_TRYING.addTransition(new UAgent2CallTransition(UaCallCondition2.TIMEOUT, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "(re)Invite transaction timedout while waiting for 100 Trying. " +
                            "Proceeding to TERMINATED");
                }
            }
        });

        ESTAB_SEND.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_100, ESTAB_SEND, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "100 Trying received.");
                }
            }
        });
        ESTAB_SEND.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_1XX, ESTAB_SEND, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Provisional response received.");
                }
                // notify user about this event
                ua.onProvisionalResponseReceived(signal.getResponse());
            }
        });
        ESTAB_SEND.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_CANCEL, ESTAB_SEND, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "ESTAB_SEND -> ESTAB_SEND (response-cancel received)");
                }
                // ignoring this event, waiting for the final response instead
            }
        });
        ESTAB_SEND.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_200, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                inviteAccepted(ua, signal);
            }
        });
        ESTAB_SEND.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_ANY_REINVITE, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Re-INVITE rejected, going back to ACTIVE state.");
                }

                // ack has been sent by the stack (TU)

                // notify user about this event in case he is interested in this signal
                if (!ua.isSilentRefreshTransaction) {
                    ua.onReinviteRejected(signal.getResponse());
                }
            }
        });
        ESTAB_SEND.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_ANY, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received '" + signal.getResponse().getStatusCode() + " "
                            + signal.getResponse().getReasonPhrase()
                            + "' during initial invite. Sending ACK and proceeding to TERMINATED");
                }
                // ack has been sent by the stack (TU)

                ua.onInviteRejected(signal.getResponse());
            }
        });
        ESTAB_SEND.addTransition(new UAgent2CallTransition(UaCallCondition2.TIMEOUT, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "(re)Invite transaction time out while waiting for a response. Proceeding to TERMINATED");
                }
            }
        });

        ESTAB_RECV.addTransition(new UAgent2CallTransition(UaCallCondition2.PROVISIONAL_RESPONSE_SEND, ESTAB_RECV, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Sending provisional response. Staying in ESTAB_RECV state");
                }
                signal.getResponse().send();
            }
        });
        ESTAB_RECV.addTransition(new UAgent2CallTransition(UaCallCondition2.CANCEL_RECEIVED, ESTAB_RECV, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "A CANCEL request has been received. Proceeding to ESTAB_RECV state.");
                }

                /*
                 * Respond to the cancel
                 */
                Response cancelResposne = signal.getCancel().createResponse(Response.OK);
                cancelResposne.send();

                // notify the app
                ua.onCancelReceived();
            }
        });
        ESTAB_RECV.addTransition(new UAgent2CallTransition(UaCallCondition2.PROCEED_INVITE_REQUEST, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Going to send the response for the invite which was received ealier.");
                }

                Response r = signal.getResponse();

                /*
                 * Set state for the session timer
                 */
                SessionExpiresHeader sessionExpiresHeader = ua.invite.getHeader(SessionExpiresHeader.NAME);
                MinSeHeader minSeHeader = ua.invite.getHeader(MinSeHeader.NAME);

                ua.isSessionRefresherLocal = ua.isRefresherLocal(false, sessionExpiresHeader.getRefresher());
                if (ua.isSessionTimerForced() || sessionExpiresHeader.getDeltaSeconds() > 0) {
                    ua.sessionExpiresIn = computeSessionInterval(minSeHeader.getDeltaSeconds(), sessionExpiresHeader.getDeltaSeconds(), ua.getMinSessionExpire(), ua.getPreferredSessionExpire());
                } else {
                    // switch off timer
                    ua.sessionExpiresIn = 0;
                }

                if (ua.sessionExpiresIn > 0) {
                    r.setHeader(new SessionExpiresHeader(ua.sessionExpiresIn, ua.isSessionRefresherLocal ? SipMessage.SessionTimerRefresher.uas : SipMessage.SessionTimerRefresher.uac));
                }
                if (!ua.isSessionRefresherLocal && ua.sessionExpiresIn > 0) {
                    r.addSupportedOptionTag("timer");
                }

                // send it
                ua.sendInitial2xxResponseForInviteTransaction(r);

                // store outgoing SDP-answer for later quick comparison
                ua.setPreviousSdpOfferAnswer(ua.invite.getSdp(), r.getSdp());
            }
        });
        ESTAB_RECV.addTransition(new UAgent2CallTransition(UaCallCondition2.REJECT_INITIAL_INVITE_REQUEST, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received the signal to send out an error response instead of proceeding. " +
                            "Proceeding to TERMINATED state.");
                }

                Response response = signal.getResponse();

                if (response.getStatusCode() < 400) {
                    Logger.e(TAG, "Responding to invite with a " + response.getStatusCode()
                            + " response and going to " +
                            "terminated state? Shouldn't you be using the proceed_invite_request signal?");
                }

                // generate our own response and send it
                response.send();
            }
        });
        ESTAB_RECV.addTransition(new UAgent2CallTransition(UaCallCondition2.REJECT_REINVITE_REQUEST, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received the signal to send out an error response instead of proceeding. " +
                            "Proceeding to ACTIVE state since this is for a re-INVITE.");
                }

                Response response = signal.getResponse();

                if (response.getStatusCode() < 400) {
                    Logger.e(TAG, "Responding to invite with a " + response.getStatusCode()
                            + " response and going to " +
                            "terminated state? Shouldn't you be using the proceed_invite_request signal?");
                }

                // generate our own response and send it
                response.send();
            }
        });

        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.INVITE_RECEIVED, ESTAB_RECV, TERMINATED) {
            @Override
            public void activity(final UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received a re-INVITE. Proceeding to ESTAB_RECV state.");
                }
                ua.cancelRetransmitionOfLast2xxResponse();
                /*
                 * An invite was received
                 *
                 * 100 trying has already been sent by the stack
                 */
                ua.isReinviteTransaction = true;
                final Invite incomingInvite = signal.getInvite();

                // Store the invite for later processing (dialog is kept)
                ua.invite = incomingInvite;

                // In case this is just a callSessionTimer refresh, let's handle it without notifying the user
                final String lastSdpAnswer = ua.getPreviousSdpAnswer(incomingInvite.getSdpAsString());
                if (lastSdpAnswer != null || (incomingInvite.getBody() == null)) {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "SDP is the same, handling request as a refresh only.");
                    }
                    ua.isSilentRefreshTransaction = true;
                    SipStack.get().getThreadPool().execute(new Runnable() {
                        public void run() {
                            Response r;
                            if (lastSdpAnswer != null) {
                                r = incomingInvite.createResponse(Response.OK, Response.getReasonPhrase(Response.OK), MimeType.APPLICATION_SDP, lastSdpAnswer.getBytes(), ua.dialog.getLocalParty().getUri(), null);
                            } else {
                                r = incomingInvite.createResponse(Response.OK);
                            }
                            ua.proceedInvite(r);
                        }
                    });
                } else {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "SDP is NOT the same, handling forwarding request to application.");
                    }
                    // let the user do someting with this invite and wait for the next signal
                    ua.onInviteReceived(incomingInvite);
                }


            }
        });
        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.INVITE_SEND, WAIT_TRYING, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Sending a re-INVITE. Proceeding to WAIT_TRYING state.");
                }
                ua.setIsSilentRefresh(signal.isSilentRefresh());
                ua.cancelRetransmitionOfLast2xxResponse();

                /*
                * Send out an re-INIVTE and wait for the 100 trying
                */
                ua.isReinviteTransaction = true;

                // Obtain INVITE message from signal (in case of a dialog refresh)
                Invite outgoingInvite = signal.getInvite();

                // If NOT set, we need to construct a new INVITE based on the dialog
                if (outgoingInvite == null) {
                    outgoingInvite = new Invite(ua.dialog, MimeType.APPLICATION_SDP, signal.getContent());
                }

                // Prepare Session-timer
                outgoingInvite.setHeader(new SessionExpiresHeader(ua.getPreferredSessionExpire(), ua.isSessionRefresherLocal ? SipMessage.SessionTimerRefresher.uac : SipMessage.SessionTimerRefresher.uas));
                outgoingInvite.setHeader(new MinSeHeader(ua.getMinSessionExpire()));
                outgoingInvite.addSupportedOptionTag("timer");

                // send it
                outgoingInvite.send(ua);

                // store invite
                ua.invite = outgoingInvite;

            }
        });
        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.BYE_SEND, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Going to send a BYE request. Proceeding to TERMINATED state.");
                }
                ua.cancelRetransmitionOfLast2xxResponse();
                Bye bye = new Bye(ua.dialog);

                ua.setByeExtensionHeaders(bye);
                bye.send(ua);

            }
        });
        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.TIMEOUT, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "ACTIVE -> TERMINATED (timeout received, assuming that client has lost network connection).");
                }
                ua.cancelRetransmitionOfLast2xxResponse();
            }
        });
        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.BYE_RECEIVED, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "BYE received. Sending response and proceeding to TERMINATED state.");
                }
                ua.cancelRetransmitionOfLast2xxResponse();

                // send response
                signal.getBye().createResponse(Response.OK).send();
            }
        });
        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.REFER_RECEIVED, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "REFER received. Sending response and proceeding to ACTIVE state.");
                }
                ua.cancelRetransmitionOfLast2xxResponse();

                /*
                 * Since OMA client will always set the Refer-Sub header to false,
                 * we have decided to not support notifications for the REFER at this moment.
                 * So, we only send a reply and proceed in background.
                 */

                boolean requestInvalid = true;
                Response r;
                if ((r = ua.validateRefer(signal.getRefer())) == null) {
                    r = signal.getRefer().createResponse(Response.ACCEPTED);
                    requestInvalid = false;
                }

                // todo check impl. and set correct headers etc

                // send response
                r.send();

                // notify user about this event in case the refer was a valid request!
                if (!requestInvalid) {
                    ua.onReferReceived(signal.getRefer());
                }

            }
        });
        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.RESP_200, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Retransmit of 2xx received, sending ack again");
                }
                ua.cancelRetransmitionOfLast2xxResponse(); // not actually needed here since this is a clientTransaction
                sendAck(ua, signal.getResponse());
            }
        });
        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.ACK_RECEIVED, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Ack received, canceling any re-transmition of the last sent response");
                }
                ua.cancelRetransmitionOfLast2xxResponse();
            }
        });
        ACTIVE.addTransition(new UAgent2CallTransition(UaCallCondition2.RETRANSMIT_LAST_2xx, ACTIVE, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Sending last sent respone again (tue to ack did not arrive)");
                }
                ua.retransmitSentLast2xxResponse();
            }
        });

        TERMINATED.addTransition(new UAgent2CallTransition(UaCallCondition2.BYE_RECEIVED, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) throws TransitionActivityException {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received BYE_RECEIVED signal in TERMINATED. Handling is and proceeding to TERMINATED state.");
                }
                // send response
                signal.getBye().createResponse(Response.OK).send();
            }
        });
        TERMINATED.addTransition(new UAgent2CallTransition(UaCallCondition2.ANY, TERMINATED, TERMINATED) {
            @Override
            public void activity(UAgent2 ua, Signal signal) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Received a signal " + signal.getType() + " in TERMINATED.");
                }
            }
        });

    }

    /*
     * Shared code
     */

    private static void inviteAccepted(UAgent2 ua, Signal signal) {
        /*
         * The (re)INVITE has been accepted; the invite transaction is terminated
         * and the session is established.
         *
         */
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }

        Response r = signal.getResponse();

        // send ack
        sendAck(ua, r);

        // get state for the session timer
        SessionExpiresHeader sessionExpiresHeader = r.getHeader(SessionExpiresHeader.NAME);
        MinSeHeader minSeHeader = r.getHeader(MinSeHeader.NAME);
        ua.isSessionRefresherLocal = ua.isRefresherLocal(true, sessionExpiresHeader.getRefresher());
        if (ua.isSessionTimerForced() || sessionExpiresHeader.getDeltaSeconds() > 0) {
            ua.sessionExpiresIn = computeSessionInterval(
                    ua.getMinSessionExpire(),
                    ua.getPreferredSessionExpire(),
                    minSeHeader != null ? minSeHeader.getDeltaSeconds() : -1,
                    sessionExpiresHeader.getDeltaSeconds());
        } else {
            // switch off
            ua.sessionExpiresIn = 0;
        }

        /*
         * Only notify the user if this is NOT a timer-refresh transaction, since
         * timer-refresh operations are hidden from the user.
         */
        if (!ua.isSilentRefreshTransaction) {
            if (ua.isReinviteTransaction) {
                ua.onReInviteAccepted(r);
            } else {
                ua.onInviteAccepted(r);
            }
        }

        // store outgoing SDP-answer for later quick comparison
        ua.setPreviousSdpOfferAnswer(ua.invite.getSdp(), r.getSdp());

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    /**
     * Must only be used to ack a 2xx response
     *
     * @param ua the UAgent2 instance
     * @param r  the response
     */
    private static void sendAck(UAgent2 ua, Response r) {
        // send the ack
        CSeqHeader header = (CSeqHeader) r.getHeader(CSeqHeader.NAME);
        ua.dialog.sendAck(ua.dialog.createAck(header.getSeqNumber()));
    }

    /*
     * The following variables are immutable and therefore thread-safe
     */
    private final Role role;

    /*
     * The following mutable state-variables are protected by lock
     * of the underlying FSM. Thus, any kind of access
     * (whether it is read or write) to those variables is only
     * permitted through the FSM.
     */
    private Dialog dialog;
    private Invite invite;
    private ClientTransaction inviteTransaction;
    private boolean isReinviteTransaction, isSilentRefreshTransaction;
    private int previousSdpOfferHash;
    private String previousSdpAnswer;
    private Response last2xxResponseSent;
    private int retransmit2xxTimerScale;
    private long retransmitionStartedAt;
    private long sessionExpiresIn;
    private boolean isSessionRefresherLocal;
    private ScheduledFuture sessionRefreshTimer, sessionTimeoutTimer, retransmitTimer2xxResponse;

    /**
     * @param setupRole specifies if dialog-setup is local initiated (uac) or remotely (uas)
     */
    public UAgent2(Role setupRole) {
        super(INIT);
        this.role = setupRole;
    }

    /************************************************************
     *** Public getters
     ***
     *** Safe and can be used by anyone
     ***********************************************************/

    /**
     * Checks if the UAgant's role uac
     *
     * @return true if uac
     */
    public boolean isRoleUac() {
        return role == Role.uac;
    }

    /**
     * Checks if the UAgant's role uas
     *
     * @return true if uas
     */
    public boolean isRoleUas() {
        return role == Role.uas;
    }

    public Role getRole() {
        return role;
    }

    /************************************************************
     *** The following methods describe the
     *** interface between the SipListener (stack) and this UAgent2
     *** (thus should not be used by the applications)
     ***********************************************************/

    /**
     * Signal the UAgent that is has received a (re-)INVITE request.
     * <p/>
     * Since the UAgent will then call onInviteReceived() for further user-processing
     * this method returns nothing.
     *
     * @param request the in-dialog request received
     */
    public void processRequest(Request request) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter (request)");
        }

        Signal s;
        if (request instanceof Invite) {
            s = Signal.getInviteReceivedSignal((Invite) request);
        } else if (request instanceof Cancel) {
            s = Signal.getCancelReceivedSignal((Cancel) request);
        } else if (request instanceof Bye) {
            s = Signal.getByeReceivedSignal((Bye) request);
        } else if (request instanceof Ack) {
            s = Signal.getAckReceivedSignal();
        } else if (request instanceof Refer) {
            s = Signal.getReferReceivedSignal((Refer) request);
        } else {
            Logger.e(TAG, "Don't know how to handle " + request.getMethod());
            return;
        }

        try {
            super.input(s);
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave (invite)");
        }
    }

    /**
     * Signal the UAgent that a response has been received.
     *
     * @param response the response.
     */
    public void processResponse(Response response) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter (response)");
        }
        try {
            super.input(Signal.getResponseSignal(response));
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
            Logger.d(TAG, "leave (response)");
        }
    }

    /**
     * Signals the UAgent that a timeout event has occurred
     *
     * @param transactionBase the inviteTimeout object
     */
    public void processTimeout(TransactionBase transactionBase) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter (timeout)");
        }
        try {
            super.input(Signal.getTimeoutSignal());
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
        } catch (InterruptedException e) {
            Logger.e(TAG, "", e);
        } catch (TransitionActivityException e) {
            Logger.e(TAG, "", e);
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave (timeout)");
        }
    }

    /************************************************************
     *** The following methods describe the
     *** interface between the application and the UAgent2
     *** (Those method are to be used by applications)
     ***********************************************************/

    /**
     * Instructs this UAgent to reject the received invite.
     * This method can be called after the onInviteReceived()
     * signal was received by the app
     *
     * @param errorResponse the error response to be used to reject this invite
     */
    public void rejectInvite(Response errorResponse) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getRejectInviteSignal(errorResponse));
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
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
     * Instructs this UAgent to accept the received invite.
     * This method can be called after the onInviteReceived()
     * signal was received by the app.
     *
     * @param response the 2XX OK response to be used to accept
     *                 the invite. This response will typically include
     *                 the answer SDP.
     */
    public void proceedInvite(Response response) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getProceedInviteSignal(response));
        } catch (UnhandledConditionException e) {
            Logger.e(TAG, "", e);
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
     * Instructs the UAgent to send a BYE and terminate the session.
     * Please note that this signal will be ignored (with a log-statement
     * in debug level) when the current internal state is not ACTIVE
     */
    public void sendBye() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getByeSendSignal());
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
     * Allows application to set extra headers before BYE is sent.
     * Note that you are only allowed to manipulate the headers and return A.S.A.P,
     * no long detours with resource locking from here on.
     *
     * @param bye message to manipulate
     */
    @SuppressWarnings({"UnusedDeclaration"})
    protected void setByeExtensionHeaders(Bye bye) {

    }


    /**
     * Instructs the UAgent to send a Invite. This method can both be used to send the initial- or an re-INVITE (same body).
     * <p/>
     * Please note that this signal will be ignored (with a log-statement in debug level)
     * when the current internal state is not either ACTIVE or INIT.
     *
     * @param invite the invite request to be send out
     */
    public void sendInvite(Invite invite) {
        sendInvite(invite, false);
    }

    /**
     * Instructs the UAgent to send a re-Invite with a new body.
     * <p/>
     * Please note that this signal will be ignored (with a log-statement in debug level)
     * when the current internal state is not either ACTIVE or INIT.
     *
     * @param body The new body to be set for the INVITE
     */
    public void sendReInvite(byte[] body) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getReInviteSendSignal(body));
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
     * Send a non-100 provisional response
     *
     * @param response response to be sent
     */
    public void sendProvisionalResponse(Response response) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getSendProvisionalResponseSignal(response));
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
     * Sends a CANCEL request.
     * Please note that this signal will be ignored (with a log-statement
     * in debug level) when the current internal state is not ESTAB_SEND.
     * Override the methods onEstablishSendState*() to detect this
     * state if required
     */
    public void sendCancel() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        try {
            super.input(Signal.getCancelSendSignal());
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

    /************************************************************
     *** The following methods describe the
     *** interface between the UAgent2 and the implementing application,
     *** providing hock methods for the Uagent2 to communicate (send signals)
     *** to the upper application
     ***********************************************************/

    /**
     * A hock which is used to notify the upper application
     * when the ESTAB_SEND state is entered
     */
    protected void onEstablishSendStateLeft() {
        // to be overridden
    }

    /**
     * A hock which is used to notify the upper application
     * when the ESTAB_SEND state is exited
     */
    protected void onEstablishSendStateReached() {
        // to be overridden
    }

    /**
     * Provides a hock method to set the server-header for outgoing requests
     *
     * @return the server header(s)
     */
    protected List getServerHeader() {
        // to be overridden
        return Collections.EMPTY_LIST;
    }

    /**
     * Called to signal the application that an Invite has been received. The Uagent
     * expects one the following singals:
     * - terminate (to
     * - proceed Invite (to accept it)
     * the user should send the proceed_invite signal to the machine and provide a response. The response
     * is then send by this UAgent.
     *
     * @param incomingInvite the incoming INVITE
     */
    public abstract void onInviteReceived(Invite incomingInvite);

    /**
     * Called in case an INVITE has been accepted by the remote-party.
     * Must be implemented by the UAS as well, since the UAS is also able to initiate a
     * new re-INVITE transaction. The implementor needs to send the ACK using sendAck().
     *
     * @param r the response received from the remote UA.
     */
    public abstract void onInviteAccepted(Response r);

    /**
     * Called in case a re-INVITE has been accepted by the remote-party.
     * Must be implemented by the UAS as well, since the UAS is also able to initiate a
     * new re-INVITE transaction. The implementor needs to send the ACK using sendAck().
     *
     * @param r the response received from the remote UA.
     */
    public abstract void onReInviteAccepted(Response r);

    /**
     * Called if the sent initial invite was rejected by the remote user.
     *
     * @param r received response
     */
    public abstract void onInviteRejected(Response r);

    /**
     * Called in case the session is terminated.
     * <p/>
     * This method is also used for an incoming BYE request. In that case, the UA will automatically
     * generate and send a 200OK response and call this method.
     * <p/>
     * This method is also used in timeout situations.
     *
     * @param signal the signal which caused the termination
     */
    public abstract void onSessionTerminated(Signal signal);

    /**
     * Called in case a provisional response is received (re-)INVITE transaction.
     *
     * @param response the response which was received
     */
    public abstract void onProvisionalResponseReceived(Response response);

    /**
     * Called in case the re-invite which was sent out,
     * has been rejected.
     *
     * @param response the response received
     */
    public abstract void onReinviteRejected(Response response);

    /**
     * Called as soon as a REFER was received inside this dialog.
     * The called application doesn't need to take any actions
     * back towards this UAgent, since a response has already been sent.
     *
     * @param refer the received refer
     */
    public abstract void onReferReceived(Refer refer);

    /**
     * Called by this class to let the user validate the request.
     * If null is returned, then a 200 OK response is sent; otherwise
     * the returned response is used.
     * In case the refer was accepted, then call will be made to onReferReceived(Refer)
     * after the transaction has been completed.
     *
     * @param refer the received REFER request
     * @return the error response or 'null' in case this request is valid
     */
    public abstract Response validateRefer(Refer refer);

    /**
     * Called if a canel was received during session set-up
     */
    public abstract void onCancelReceived();

    /**
     * Detects whether the usage of a call-session timer (as specified in RFC4028) is
     * forced. If set to true, a timer will be started no matter when the remote-side
     * wants.
     *
     * @return true if a timer should be started no matter what
     */
    public boolean isSessionTimerForced() {
        return SESSION_TIMER_FORCED;
    }

    /**
     * Provides the minSessionExpire value which this Uagent instance
     * should use.
     *
     * @return minSessionExpire
     */
    public long getMinSessionExpire() {
        return MIN_SESSION_EXPIRE;
    }

    /**
     * Provides the preferred sessionExpire value
     * which this UAgent instance should use
     *
     * @return preferredSessionExpire
     */
    public long getPreferredSessionExpire() {
        return PREFERRED_SESSION_EXPIRE;
    }

    /**
     * Don't call the fsm directly.
     * <p/>
     * This method is overridden for protection, since all
     * calls to the fsm must first lock "this".
     * <p/>
     * Calling this methods throws a RuntimeException
     *
     * @param signal the signal
     */
    public final void input(Signal signal) {
        throw new RuntimeException("Should never signal the stateMachine directly! Use the processXXX() methods instead.");
    }

    /**
     * This method SHOULD ONLY be used
     * by the UAgent self and the fsm for thread-safe
     * reasons.
     * <p/>
     * Never call this method from another class.
     */
    public final State getCurrentState() {
        throw new RuntimeException("Should never signal the stateMachine directly! Use the processXXX() methods instead.");
    }

    /*
     * ********************************************************
     * *** The private stuff
     * ********************************************************
     */

    private void sendInvite(Invite invite, boolean isSilentRefresh) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter (isSilentRefresh=" + isSilentRefresh + ")");
        }
        try {
            super.input(Signal.getInviteSendSignal(invite, isSilentRefresh));
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
     * Only used by the FSM and should never be used by anyone calling from outside
     *
     * @param silentRefresh silent or not
     */
    public void setIsSilentRefresh(boolean silentRefresh) {
        isSilentRefreshTransaction = silentRefresh;
    }

    /**
     * Checks if the UAgent is currently handling a re-INVITE transaction.
     * <p/>
     * No locking required here, since this method IS ONLY USED BY
     * the UaCallCondition2 class, which means that the fsm already
     * holds a lock on "this"
     *
     * @return true in case the UAgent is currently handling a re-INVITE transaction, else: false
     */
    public boolean isReinviteTransaction() {
        return isReinviteTransaction;
    }

    private void sendRetransmit() {
        try {
            super.input(Signal.getRetransmitLast2xx());
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

    private void sendInitial2xxResponseForInviteTransaction(Response response) {
        last2xxResponseSent = response;
        retransmit2xxTimerScale = 0;
        retransmitSentLast2xxResponse();
        last2xxResponseSent.clearTransaction();
    }

    private void retransmitSentLast2xxResponse() {

        last2xxResponseSent.send();

        // update timer scale
        if (retransmit2xxTimerScale == 0) {
            retransmit2xxTimerScale = 1;
            retransmitionStartedAt = System.currentTimeMillis();
        } else {
            retransmit2xxTimerScale *= 2;
        }

    }

    private void cancelRetransmitionOfLast2xxResponse() {
        retransmit2xxTimerScale = 0;
    }

    private void startRetransmitTimerFor2xx() {

        if (retransmit2xxTimerScale == 0 || retransmitTimer2xxResponse != null) {
            return;
        }

        /*
         * Calculate delay for when the timer is to fire
         */
        final int delay = Math.min(retransmit2xxTimerScale * TransactionBase.T1, TransactionBase.T2);
        final Runnable task;

        if ((System.currentTimeMillis() - retransmitionStartedAt) > (TransactionBase.T1 * 64)) {
            task = new Runnable() {
                public void run() {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "Giving up retransmitting last sent 2xx response, sending BYE signal to fsm now");
                    }
                    sendBye();
                }
            };
        } else {
            task = new Runnable() {
                public void run() {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "Retransmitting last sent 2xx response since we didn't received an ACK for it yet");
                    }
                    sendRetransmit();
                }
            };
        }

        retransmitTimer2xxResponse = SipStack.get().getThreadPool().schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    private void stopRetransmitTimerFor2xx() {
        if (retransmitTimer2xxResponse != null) {
            retransmitTimer2xxResponse.cancel(false);
            retransmitTimer2xxResponse = null;
        }
    }

    /**
     * Store the hashcode of previous receivied SDP offers and the corresponding
     * SDP-answer
     *
     * @param previousSdpOfferHash the SDP-offer to be stored
     * @param previousSdpAnswer    the corresponding answer
     */
    private void setPreviousSdpOfferAnswer(SessionDescription previousSdpOfferHash, SessionDescription previousSdpAnswer) {
        if (previousSdpOfferHash != null && previousSdpAnswer != null) {
            this.previousSdpOfferHash = previousSdpOfferHash.toString().hashCode();
            this.previousSdpAnswer = previousSdpAnswer.toString();
        } else {
            // last SDP remains active
        }
    }

    /**
     * Returns the previous SDP-answer in case the applied SDP-offer is the same as before
     *
     * @param offer the offer the compare to
     * @return returns the previously sent SDP-answer if offer matches, in any other case: null
     */
    private String getPreviousSdpAnswer(String offer) {
        if (previousSdpAnswer != null && offer != null && offer.hashCode() == previousSdpOfferHash) {
            return previousSdpAnswer;
        } else {
            return null;
        }
    }

    private boolean isRefresherLocal(boolean isClientTransaction, SipMessage.SessionTimerRefresher refresherFromAnswer) {
        if (refresherFromAnswer == null) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "No refresher specified, assuming local"); // the remote-side is not the refresher it seems, then it has to be us
            }
            return true;
        }

        if (isClientTransaction) {
            return refresherFromAnswer == SipMessage.SessionTimerRefresher.uac;
        } else {
            return refresherFromAnswer == SipMessage.SessionTimerRefresher.uas;
        }
    }

    private void startSessionTimers() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }

        if (sessionExpiresIn > 0) {
            sessionTimeoutTimer = SipStack.get().getThreadPool().schedule(new Runnable() {
                public void run() {
                    if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                        Logger.d(TAG, "session timeout timer fired. Sending BYE to terminate the session");
                    }
                    sendBye();
                }
            }, sessionExpiresIn, TimeUnit.SECONDS);
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Scheduled timeout timer in " + sessionExpiresIn + " seconds.");
            }

            if (isSessionRefresherLocal) {
                sessionRefreshTimer = SipStack.get().getThreadPool().schedule(new Runnable() {
                    public void run() {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "session refresh timer fired. Sending re-INVITE to refresh the session");
                        }
                        sendInvite(new Invite(dialog, null, null), true);
                    }
                }, sessionExpiresIn / 2, TimeUnit.SECONDS);
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Scheduled refresh timer in " + (sessionExpiresIn / 2) + " seconds.");
                }
            }
        } else {
            Logger.w(TAG, "Not starting session timer. This could lead to a memory leak in case this session is never closed or the BYE request never make it to this server");
        }

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    private void stopSessionTimers() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        if (sessionTimeoutTimer != null) {
            sessionTimeoutTimer.cancel(false);
        }
        if (sessionRefreshTimer != null) {
            sessionRefreshTimer.cancel(false);
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    private static long computeSessionInterval(long offererMin, long offererPrefered, long answererMin, long answererPrefered) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter (offererMin=" + offererMin + ", offererPrefered=" + offererPrefered + ", answererMin=" + answererMin + ", answererPrefered=" + answererPrefered + ")");
        }

        // choose the largest minimum among the two minimums
        final long min = offererMin > answererMin ? offererMin : answererMin;

        // choose the lowest prefered interval, but exclude those which are zero or less
        final long interval;
        if (offererPrefered <= 0) {
            interval = answererPrefered;
        } else if (answererPrefered <= 0) {
            interval = offererPrefered;
        } else {
            interval = offererPrefered > answererPrefered ? answererPrefered : offererPrefered;
        }

        final long result = interval > min ? interval : min;

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave (" + result + ")");
        }
        return result;
    }

}

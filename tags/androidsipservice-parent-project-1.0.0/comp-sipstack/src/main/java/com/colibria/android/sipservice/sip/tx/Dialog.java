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

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.SipStack;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.headers.*;
import com.colibria.android.sipservice.sip.messages.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 */
public class Dialog {

    private static final String TAG = "Dialog";

    private final String origRequestMethod;        // Method which started this dialog
    private final String callID;             // Call-ID header
    private final Address localAddress;            // From/To header
    private final Address remoteAddress;           // From/To header
    private final boolean secure;
    private final boolean isStartedByServerTransaction;

    /*
     * todo The following mutable state variables need to be protected by a lock
     */
    private Address remoteTarget;                  // learned from the Contact header
    private String localTag;                       // learned from the From/To header
    private String remoteTag;                      // learned from the From/To header
    private long localSequenceNumber = 0;          // CSeq header
    private long myLastInviteSequenceNumber = 0;   // CSeq header
    private long remoteSequenceNumber = 0;
    private DialogState state;
    private IInDialogRequestHandler applicationData;
    private String dialogId;                       // result of Call-id & remoteTag & localTag

    /**
     * The route set, ordered after the requests I send. First, 2nd server FROM me.
     */
    private final List<RouteHeader> routes = new LinkedList<RouteHeader>();
    private boolean terminateOnBye;
    private Response lastResponse;

    /**
     * Constructs a RtDialog from the server side.
     *
     * @param trans the transaction
     */
    public Dialog(TransactionBase trans) {
        this.callID = trans.getRequest().<CallIDHeader>getHeader(CallIDHeader.NAME).getCallId();
        this.secure = trans.getRequest().getRequestUri().getType() == URI.Type.sips;
        this.localSequenceNumber = ((CSeqHeader) trans.getRequest().getHeader(CSeqHeader.NAME)).getSeqNumber();

        ToHeader th;
        FromHeader fh;
        if (trans instanceof ClientTransaction) {
            th = trans.getRequest().getTo();
            this.remoteAddress = th.getAddress();
            setRemoteTag(th.getTag());

            fh = trans.getRequest().getFrom();
            this.localAddress = fh.getAddress();
            setLocalTag(fh.getTag());

            this.isStartedByServerTransaction = false;
            if ("INVITE".equalsIgnoreCase(trans.getRequest().getMethod())) {
                myLastInviteSequenceNumber = ((CSeqHeader) trans.getRequest().getHeader(CSeqHeader.NAME)).getSeqNumber();
            }
            //remoteTarget, remoteTag and RouteSet is not initialized until we get a confirmation on this.
        } else {
            th = trans.getRequest().getTo();
            this.localAddress = th.getAddress();
            setLocalTag(th.getTag());

            fh = trans.getRequest().getFrom();
            this.remoteAddress = fh.getAddress();
            setRemoteTag(fh.getTag());

            this.isStartedByServerTransaction = true;
            this.remoteTarget = trans.getRequest().<ContactHeader>getHeader(ContactHeader.NAME).getAddress();

            //Let's see if we have a Record-Route here. Keep the order!
            for (RecordRouteHeader rrh : trans.getRequest().<RecordRouteHeader>getHeaders(RecordRouteHeader.NAME)) {
                routes.add(new RouteHeader(rrh.getAddress()));
            }
        }

        origRequestMethod = trans.getRequest().getMethod();
        terminateOnBye = true;
    }

    public long getLocalSeqNumber() {
        return localSequenceNumber;
    }

    public long getRemoteSeqNumber() {
        return remoteSequenceNumber;
    }

    /*
     * (non-Javadoc) The UAC core MUST generate an ACK request for each 2xx
     * received from the transaction layer. The header fields of the ACK are
     * constructed in the same way as for any request sent within a dialog (see
     * Section 12) with the exception of the CSeq and the header fields related
     * to authentication. The sequence number of the CSeq header field MUST be
     * the same as the INVITE being acknowledged, but the CSeq method MUST be
     * ACK. The ACK MUST contain the same credentials as the INVITE. If the 2xx
     * contains an offer (based on the rules above), the ACK MUST carry an
     * answer in its body. If the offer in the 2xx response is not acceptable,
     * the UAC core MUST generate a valid answer in the ACK and then send a BYE
     * immediately.
     *
     * Note that for the case of forked requests, you can create multiple
     * outgoing invites each with a different cseq and hence you need to supply
     * the invite.
     *
     * @see javax.sip.Dialog#createAck(long)
     */

    public Request createAck(long cseqno) {
        if (this.lastResponse == null)
            throw new RuntimeException("Dialog not yet established -- no response!");

        ViaHeader via = createViaHeader();

        URI requestURI;
        if (this.getRemoteTarget() == null) {
            URI fromURI = getRemoteParty().getUri();
            requestURI = new URI(URI.Type.sip, fromURI.getUsername(), fromURI.getPassword(), fromURI.getHost(), fromURI.getPort(), fromURI.getPhonenumber(), null);
        } else {
            requestURI = getRemoteTarget().getUri();
        }

        Ack ack = Ack.create(
                requestURI,
                lastResponse.getCallId(),
                cseqno,
                createWithTag(getLocalParty(), getLocalTag()),
                createWithTag(getRemoteParty(), getRemoteTag()),
                Collections.singletonList(via));
        this.lastResponse = null;

        // ACKs for 2xx responses
        // use the Route values learned from the Record-Route of the 2xx
        // responses.
        this.installRouteSet(ack);

        return ack;
    }

    private Address createWithTag(Address input, String tag) {
        if (input.getDisplayName() == null) {
            // the tag goes into the URI
            return new Address(new URI(
                    input.getUri().getType(),
                    input.getUri().getUsername(),
                    input.getUri().getPassword(),
                    input.getUri().getHost(),
                    input.getUri().getPort(),
                    input.getUri().getPhonenumber(),
                    Collections.singletonList(new NameValuePair("tag", tag))
            ), null, null);
        } else {
            // the tag goes into the Address
            return new Address(input.getUri(), input.getDisplayName(), Collections.singletonList(new NameValuePair("tag", tag)));
        }
    }

    public void terminateOnBye(boolean terminateFlag) throws SipException {
        this.terminateOnBye = terminateFlag;
    }

    public Address getLocalParty() {
        return this.localAddress;
    }

    public Address getRemoteParty() {
        return this.remoteAddress;
    }

    public Address getRemoteTarget() {
        return this.remoteTarget;
    }


    public String getDialogId() {
        return this.dialogId;
    }

    private void setDialogId(String dialogId) {
        this.dialogId = dialogId;
    }

    public long incLocalSequenceNumber() {
        return ++localSequenceNumber;
    }


    public int getRemoteSequenceNumber() {
        return (int) getRemoteSeqNumber();
    }

    public boolean isSecure() {
        return this.secure;
    }

    public boolean isServer() {
        return this.isStartedByServerTransaction;
    }

    public void updateRequestToBeInDialog(Request toBeUpdatedRequest) {
        if (remoteTarget == null) {
            throw new RuntimeException("Dialog not established yet");
        }

        if (toBeUpdatedRequest.getMethod().equals(Cancel.NAME))
            throw new RuntimeException("Dialog.createRequest(): Invalid request");

        if (this.getState() == null
                || (this.getState() == DialogState.TERMINATED && !toBeUpdatedRequest.getMethod().equalsIgnoreCase(Bye.NAME))
                || (this.isServer() && this.getState() == DialogState.EARLY && toBeUpdatedRequest.getMethod().equalsIgnoreCase(Bye.NAME))
                )
            throw new RuntimeException("Dialog  " + getDialogId() + " not yet established or terminated " + this.getState());


        // Check if the dialog is in the right state (RFC 3261 section 15).
        // The caller's UA MAY send a BYE for either
        // CONFIRMED or EARLY dialogs, and the callee's UA MAY send a BYE on
        // CONFIRMED dialogs, but MUST NOT send a BYE on EARLY dialogs.

        long cseq;
        if (Ack.NAME.equals(toBeUpdatedRequest.getMethod()) || Cancel.NAME.equals(toBeUpdatedRequest.getMethod()))
            cseq = myLastInviteSequenceNumber; //Same as last.
        else
            cseq = incLocalSequenceNumber();

        if (Invite.NAME.equalsIgnoreCase(toBeUpdatedRequest.getMethod()))
            myLastInviteSequenceNumber = cseq;


        // inject headers such that this request belongs to this dialog
        toBeUpdatedRequest.setHeader(new CallIDHeader(callID));
        toBeUpdatedRequest.setHeader(new CSeqHeader(cseq, toBeUpdatedRequest.getMethod()));
        toBeUpdatedRequest.setHeader(new FromHeader(createWithTag(getLocalParty(), getLocalTag())));
        toBeUpdatedRequest.setHeader(new ToHeader(createWithTag(getRemoteParty(), getRemoteTag())));
        toBeUpdatedRequest.setHeader(Request.getStackContactHeader(localAddress.getUri(), null));

        //Adding Routes.
        installRouteSet(toBeUpdatedRequest);
    }

    private ViaHeader createViaHeader() {
        return new ViaHeader("TCP",
                SipStack.get().getMyHostName(),
                SipStack.get().getMyPort(),
                Collections.singletonList(new NameValuePair("branch", Utils.generateBranchId()))
        );
    }

    private synchronized void addRoute(SipMessage sipMessage) {

        // cannot add route list after the dialog is initialized.
        if (this.state == DialogState.CONFIRMED || this.state == DialogState.TERMINATED) {
            return;
        }

        if (!isServer()) {
            // I am CLIENT dialog.
            if (sipMessage instanceof Response) {
                Response sipResponse = (Response) sipMessage;
                if (sipResponse.getStatusCode() == 100) {
                    // Do nothing for trying messages.
                    return;
                }
                List<RecordRouteHeader> rrlist = sipMessage.getHeaders(RecordRouteHeader.NAME);
                // Add the route set from the incoming response in reverse
                // order
                if (rrlist != null) {
                    this.addRoute(rrlist);
                } else {
                    // Set the rotue list to the last seen route list.
                    this.routes.clear();
                }

                List<ContactHeader> contactList = sipMessage.getHeaders(ContactHeader.NAME);
                if (contactList != null) {
                    remoteTarget = contactList.get(0).getAddress();
                }
            }
        } else {
            if (sipMessage instanceof Request) {
                // Incoming Request has the route list
                List<RecordRouteHeader> rrlist = sipMessage.getHeaders(RecordRouteHeader.NAME);
                // Add the route set from the incoming response in reverse
                // order
                if (rrlist != null) {

                    this.addRoute(rrlist);
                } else {
                    // Set the rotue list to the last seen route list.
                    this.routes.clear();
                }
                // put the contact header from the incoming request into
                // the route set.
                List<ContactHeader> contactList = sipMessage.getHeaders(ContactHeader.NAME);
                if (contactList != null) {
                    remoteTarget = contactList.get(0).getAddress();
                }
            }
        }
    }

    /**
     * Add a route list extracted from a record route list. If this is a server dialog then we
     * assume that the record are added to the route list IN order. If this is a client dialog then
     * we assume that the record route headers give us the route list to add in reverse order.
     *
     * @param rrlist -- the record route list from the incoming message.
     */
    private void addRoute(List<RecordRouteHeader> rrlist) {
        this.routes.clear();

        if (!this.isServer()) {
            // This is a client dialog so we extract the record
            // route from the response and reverse its order to
            // careate a route list.

            // start at the end of the list and walk backwards
            for (int i = rrlist.size() - 1; i >= 0; i--) {
                RecordRouteHeader rrh = rrlist.get(i);
                RouteHeader rh = new RouteHeader(rrh.getAddress());
                this.routes.add(rh);
            }
        } else {
            // This is a server dialog. The top most record route
            // header is the one that is closest to us. We extract the
            // route list in the same order as the addresses in the
            // incoming request.
            for (RecordRouteHeader rrh : rrlist) {
                routes.add(new RouteHeader(rrh.getAddress()));
            }
        }
    }

    public void sendRequest(ClientTransaction clientTransaction) {
        clientTransaction.setDialog(this);
        clientTransaction.sendRequest(true);
    }

    public void sendAck(Request ackRequest) {
        SipStack.get().sendRequest(ackRequest);
    }

    public DialogState getState() {
        return this.state;
    }

    public void setState(DialogState state) {
        this.state = state;
        if (state == DialogState.TERMINATED) {
            // todo: decide if this should linger
            unmap();
        }
    }

    public void delete() {
        setState(DialogState.TERMINATED);
    }

    public String getLocalTag() {
        return localTag;
    }

    public void setLocalTag(String localTag) {
        this.localTag = localTag;
    }

    public String getRemoteTag() {
        return remoteTag;
    }

    public void setRemoteTag(String remoteTag) {
        this.remoteTag = remoteTag;
    }

    public void setApplicationData(IInDialogRequestHandler applicationData) {
        this.applicationData = applicationData;
    }

    public IInDialogRequestHandler getApplicationData() {
        return applicationData;
    }

    private String getMethod() {
        return origRequestMethod;
    }

    /**
     * update dialog state according to response being received
     *
     * @param sipResponse the response received
     */
    void responseReceived(Response sipResponse) {
        int statusCode = sipResponse.getStatusCode();
        String cseqMethod = sipResponse.getCSeq().getMethod();

        if (statusCode == 100) {
            return;
        }

        if (Invite.NAME.equals(origRequestMethod))
            lastResponse = sipResponse;

        if (TransactionRepository.isDialogCreating(cseqMethod)) {
            if (sipResponse.getStatusCode() / 100 == 1 && state != DialogState.CONFIRMED) {
                setState(DialogState.EARLY);
                if (sipResponse.getToTag() != null && this.getRemoteTag() == null) {
                    setRemoteTag(sipResponse.getToTag());
                    this.setDialogId(sipResponse.getDialogId(false, null));
                    SipStack.get().getTxRepository().addDialog(this);
                    this.addRoute(sipResponse);
                }
            } else if (statusCode / 100 == 2) {
                // This is a dialog creating method (such as INVITE).
                // 2xx response -- set the state to the confirmed
                // state. To tag is MANDATORY for the response.

                // Only do this if method equals initial request!

                if (cseqMethod.equals(getMethod())
                        && sipResponse.getToTag() != null
                        && this.getState() != DialogState.CONFIRMED
                        && this.getState() != DialogState.TERMINATED
                        ) {
                    setRemoteTag(sipResponse.getToTag());
                    this.setDialogId(sipResponse.getDialogId(false, null));
                    SipStack.get().getTxRepository().addDialog(this);
                    this.addRoute(sipResponse);

                    setState(DialogState.CONFIRMED);
                } else if (Request.isTargetRefresh(cseqMethod)) {
                    doTargetRefresh(sipResponse);
                } else if (statusCode >= 300
                        && statusCode <= 699
                        && (getState() == null || (cseqMethod
                        .equals(getMethod()) && getState() == DialogState.EARLY))) {
                    // This case handles 3xx, 4xx, 5xx and 6xx responses.
                    // RFC 3261 Section 12.3 - dialog termination.
                    // Independent of the method, if a request outside of a
                    // dialog generates
                    // a non-2xx final response, any early dialogs created
                    // through
                    // provisional responses to that request are terminated.
                    setState(DialogState.TERMINATED);
                }
            }
            // This code is in support of "proxy" servers
            // that are constructed as back to back user agents.
            // This could be a dialog in the middle of the call setup
            // path somewhere. Hence the incoming invite has
            // record route headers in it. The response will
            // have additional record route headers. However,
            // for this dialog only the downstream record route
            // headers matter. Ideally proxy servers should
            // not be constructed as Back to Back User Agents.
            // Remove all the record routes that are present in
            // the incoming INVITE so you only have the downstream
            // Route headers present in the dialog. Note that
            // for an endpoint - you will have no record route
            // headers present in the original request so
            // the loop will not execute.
//            if (originalRequest != null) {
//                RecordRouteList rrList = originalRequest.getRecordRouteHeaders();
//                if (rrList != null) {
//                    ListIterator it = rrList.listIterator(rrList.size());
//                    while (it.hasPrevious()) {
//                        RecordRoute rr = (RecordRoute) it.previous();
//                        Route route = routes.size() > 0 ? (Route) routes.get(0) : null;
//                        if (route != null
//                                && rr.getAddress().equals(
//                                route.getAddress())) {
//                            routes.remove(0);
//                        } else
//                            break;
//                    }
//                }
//            }
        } else if (cseqMethod.equals(Notify.NAME)
                && (this.getMethod().equals(Subscribe.NAME) || this
                .getMethod().equals(Refer.NAME))
                && sipResponse.getStatusCode() / 100 == 2
                && this.getState() == null) {
            // This is a notify response.
            this.setDialogId(sipResponse.getDialogId(true, null));
            SipStack.get().getTxRepository().addDialog(this);
            this.setState(DialogState.CONFIRMED);

        } else if (cseqMethod.equals(Bye.NAME) && statusCode / 100 == 2
                && isTerminatedOnBye()) {
            // Dialog will be terminated when the transction is terminated.
            setState(DialogState.TERMINATED);
        }


    }

    /**
     * Update dialog state according to response being sent
     *
     * @param sipResponse      the response being sent
     * @param isInitialRequest true if this is a response to an initial request
     */
    void sendingResponse(Response sipResponse, boolean isInitialRequest) {
        int statusCode = sipResponse.getStatusCode();
        String cseqMethod = sipResponse.getCSeq().getMethod();

        if (statusCode == 100) {
            return;
        }

        if (cseqMethod.equals(Cancel.NAME) && statusCode / 100 == 2 && isInitialRequest
                // && sipStack.isDialogCreating(getMethod()) (JvB:true by
                // definition)
                && (getState() == null || getState() == DialogState.EARLY)) {
            // Transaction successfully cancelled but dialog has not yet
            // been established so delete the dialog.
            // Note: this does not apply to re-invite
            this.setState(DialogState.TERMINATED);
        } else if (cseqMethod.equals(Bye.NAME) && statusCode / 100 == 2
                && this.isTerminatedOnBye()) {
            // Only transition to terminated state when
            // 200 OK is returned for the BYE. Other
            // status codes just result in leaving the
            // state in COMPLETED state.
            this.setState(DialogState.TERMINATED);
        } else if (isInitialRequest &&
                sipResponse.getTo().getTag() != null &&
                TransactionRepository.isDialogCreating(cseqMethod) &&
                cseqMethod.equals(getMethod())) {

            setLocalTag(sipResponse.getTo().getTag());
            if (statusCode / 100 != 2) {

                // Check if we want to put the dialog in the dialog table.
                // A dialog is put into the dialog table when the server
                // transaction is responded to by a provisional response
                // or a final response. The Dialog is terminated
                // if the response is an error response.

                if (statusCode / 100 == 1) {
                    setState(DialogState.EARLY);

                    this.setDialogId(sipResponse.getDialogId(true, null));
                    SipStack.get().getTxRepository().addDialog(this);
                } else {
                    this.setState(DialogState.TERMINATED);
                }
            } else {
                // 2XX response handling.
                this.setState(DialogState.CONFIRMED);
                this.setDialogId(sipResponse.getDialogId(true, null));
                SipStack.get().getTxRepository().addDialog(this);
            }
        }

        // In any state: start 2xx retransmission timer for INVITE
//        if ((statusCode / 100 == 2) && cseqMethod.equals(Request.INVITE)) {
//            SIPServerTransaction sipServerTx = (SIPServerTransaction) transaction;
//            this.startTimer(sipServerTx);
//        }

    }

    /**
     * remove from repository
     */
    private void unmap() {
        SipStack.get().getTxRepository().removeDialog(this);
    }

    /**
     * Do taget refresh dialog state updates.
     * <p/>
     * RFC 3261: Requests within a dialog MAY contain Record-Route and Contact
     * header fields. However, these requests do not cause the dialog's route
     * set to be modified, although they may modify the remote target URI.
     * Specifically, requests that are not target refresh requests do not modify
     * the dialog's remote target URI, and requests that are target refresh
     * requests do. For dialogs that have been established with an
     * <p/>
     * INVITE, the only target refresh request defined is re-INVITE (see Section
     * 14). Other extensions may define different target refresh requests for
     * dialogs established in other ways.
     *
     * @param sipMessage message used to update the target set
     */
    private void doTargetRefresh(SipMessage sipMessage) {
        List<ContactHeader> contactList = sipMessage.getHeaders(ContactHeader.NAME);
        remoteTarget = contactList.get(0).getAddress();
    }

    private boolean isTerminatedOnBye() {
        return terminateOnBye;
    }

    private void installRouteSet(Request sipRequest) {

        sipRequest.removeHeaders(RouteHeader.NAME);

        boolean firstIsLooseRouting = true;

        List<RouteHeader> it = getRouteSet();
        if (it != null) {
            if (it.size() > 0) { //First time only: Check lr flag
                RouteHeader rh = it.get(0);
                firstIsLooseRouting = rh.getAddress().isParameterSet("lr");
            }
            for (RouteHeader rh : it) {
                sipRequest.addHeader(rh);
            }

        }
        if (!firstIsLooseRouting) {//Then I must add the target URI to Route as well.
            sipRequest.addHeader(new RouteHeader(remoteTarget));
        }
    }

    /**
     * Check the tags of the response against the tags of the Dialog. Return
     * true if the respnse matches the tags of the dialog. We do this check wehn
     * sending out a response.
     *
     * @param sipResponse the response to check.
     * @return true if ok
     */
    public boolean checkResponseTags(Response sipResponse) {
        if (this.isServer()) {

            if (sipResponse.getToTag() != null
                    && this.getLocalTag() != null
                    && !this.getLocalTag().equals(sipResponse.getToTag())
                    || (sipResponse.getFromTag() != null
                    && this.getRemoteTag() != null && !this
                    .getRemoteTag().equals(sipResponse.getFromTag()))) {
                Logger.w(TAG, "sipResponse.getToTag() = " + sipResponse.getToTag());
                Logger.w(TAG, "this.localTag()  = " + this.getLocalTag());
                Logger.w(TAG, "sipResponse.getFromTag() = " + sipResponse.getFromTag());
                Logger.w(TAG, "this.remoteTag = " + this.getRemoteTag());
                return false;
            } else
                return true;
        } else
            return true;
    }


    public List<RouteHeader> getRouteSet() {
        return routes;
    }
}

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
package com.colibria.android.sipservice.sip.messages;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.sip.SipStack;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.headers.*;
import com.colibria.android.sipservice.sip.tx.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * @author Sebastian Dehne
 */
public abstract class Request extends SipMessage {
    private static final String TAG = "Request";

    public static ContactHeader getStackContactHeader(URI uri, List<NameValuePair> contactHeaderParams) {
        URI sipURI;
        LinkedList<NameValuePair> uriParameters = new LinkedList<NameValuePair>();
        uriParameters.add(new NameValuePair("transport", "tcp"));

        if (uri.getType() == URI.Type.tel) {
            sipURI = new URI(URI.Type.sip, uri.getPhonenumber(), null, SipStack.get().getMyHostName(), SipStack.get().getMyPort(), null, uriParameters);
        } else {
            sipURI = new URI(URI.Type.sip, uri.getUsername(), null, SipStack.get().getMyHostName(), SipStack.get().getMyPort(), null, uriParameters);
        }
        return new ContactHeader(new Address(sipURI, "", contactHeaderParams));
    }

    public static ViaHeader generateTemporarilyViaHeader(Request request, NameValuePair... flags) {
        List<NameValuePair> params = new LinkedList<NameValuePair>();
        params.add(new NameValuePair("branch", request != null ? Utils.generateBranchIdForStateLessFwd(request) : Utils.generateBranchId()));
        if (flags != null && flags.length > 0)
            params.addAll(Arrays.asList(flags));
        return new ViaHeader(
                "TCP",
                SipStack.get().getMyHostName(),
                SipStack.get().getMyPort(),
                params
        );
    }


    private final String method;
    private final URI requestUri;
    private volatile ServerTransaction serverTransaction;
    private volatile Dialog dialog;

    protected Request(String method, URI requestUri, HashMap<String, List<SipHeader>> headers, byte[] body) {
        super(headers, body);
        this.method = method;
        this.requestUri = requestUri;
        if (!this.headers.containsKey(MaxForwardsHeader.NAME)) {
            setHeader(new MaxForwardsHeader(70));
        }
    }

    public ServerTransaction getServerTransaction() {
        return serverTransaction;
    }

    public void setServerTransaction(ServerTransaction serverTransaction) {
        this.serverTransaction = serverTransaction;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    public String getMethod() {
        return method;
    }

    public URI getRequestUri() {
        return requestUri;
    }

    @Override
    protected void writeFirstLineToBuffer(OutputStream bb) throws IOException {
        bb.write(method.getBytes());
        bb.write(" ".getBytes());
        bb.write(requestUri.toString().getBytes());
        bb.write(" SIP/2.0\r\n".getBytes());
    }

    public Response createResponse(int statusCode) {
        return createResponse(statusCode, Response.getReasonPhrase(statusCode), null, null);
    }

    public Response createResponse(int statusCode, String reasonPhrase, MimeType contentType, byte[] body) {
        Response newResponse;

        newResponse = new Response(statusCode, reasonPhrase, new HashMap<String, List<SipHeader>>(), body, serverTransaction);
        if (body != null && contentType != null) {
            newResponse.setHeader(new ContentTypeHeader(contentType));
        }

        String headerName;
        for (Map.Entry<String, List<SipHeader>> e : headers.entrySet()) {
            headerName = e.getKey();

            // just copy any known headers over
            if (headerName.equals(FromHeader.NAME)
                    || headerName.equals(CallIDHeader.NAME)
                    || headerName.equals(CSeqHeader.NAME)
                    || headerName.equals(ViaHeader.NAME)
                    //|| headerName.equals(TimeStampHeader.NAME)  // todo
                    || (statusCode / 100 <= 2 && statusCode / 100 > 1 && headerName.equals(RecordRouteHeader.NAME))
                    ) {
                newResponse.addHeaders(e.getValue());
            }

            // in case of ToHeader and a missing tag, generate it now
            else if (headerName.equals(ToHeader.NAME)) {
                ToHeader toHeader = (ToHeader) e.getValue().get(0);
                if (toHeader.getTag() == null) {
                    List<NameValuePair> list = new LinkedList<NameValuePair>();
                    String tag = null;
                    if (getServerTransaction() != null && getServerTransaction().getDialog() != null && getServerTransaction() != null) {
                        tag = getServerTransaction().getDialog().getLocalTag();
                    }
                    list.add(new NameValuePair("tag", tag != null ? tag : Utils.generateTag()));
                    list.addAll(toHeader.getParameters());
                    toHeader = new ToHeader(new Address(toHeader.getAddress().getUri(), toHeader.getAddress().getDisplayName(), list));
                }
                newResponse.addHeader(toHeader);
            }
        }

        return newResponse;
    }

    public ClientTransaction send(IClientTransactionListener listener) {
        ClientTransaction result = null;

        if (serverTransaction != null) {
            throw new RuntimeException("This request was received and cannot be sent out");
        }

        /*
         * Currently, a temporarily via header is added when a request-wrapper is
         * created AND also when the request-wrapper is sent. This is a temp work-around
         * until we have a better solution.
         * todo fix this
         */
        ViaHeader first = getFirstViaHeader();
        if (first == null || !first.getHostname().equals("localhost")) {
            addHeader(generateTemporarilyViaHeader(null, new NameValuePair("alias", null), new NameValuePair("rport", null)));
        }

        /*
         * ACK SHALL be sent outside the transaction, as done here.
          Cancel is done like this in order to avoid over writing the original transaction mappings
         */
        if (Ack.NAME.equals(getMethod()) || Cancel.NAME.equals(getMethod())) {
            SipStack.get().sendRequest(this);
        } else {

            ClientTransaction clientTransaction = SipStack.get().getTxRepository().getNewClientTransaction(this, listener, dialog);
            if (listener instanceof IInDialogRequestHandler && clientTransaction.getDialog() != null && clientTransaction.getDialog().getApplicationData() == null) {
                clientTransaction.getDialog().setApplicationData((IInDialogRequestHandler) listener);
            }

            // this request is sent outside a dialog
            if (dialog == null) {
                clientTransaction.sendRequest();
            }

            // this request is sent inside a dialog
            else {
                dialog.sendRequest(clientTransaction);
            }
            result = clientTransaction;
        }

        return result;
    }
}

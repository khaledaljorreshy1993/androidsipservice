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
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.headers.*;
import com.colibria.android.sipservice.sip.tx.Dialog;
import com.colibria.android.sipservice.sip.tx.Utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class Invite extends Request {

    public static final String NAME = "INVITE";

    /**
     * Used by the message parser to construct a new instance of this class
     *
     * @param requestURI the requestURI
     * @param headers    all headers
     * @param body       the body
     */
    public Invite(URI requestURI, HashMap<String, List<SipHeader>> headers, byte[] body) {
        super(NAME, requestURI, headers, body);
    }

    /**
     * Constructs a new in-dialog Invite request
     *
     * @param dialog      the dialog instance for which to create a new in-dialog INVITE request
     * @param contentType the content type if a body is present
     * @param body        the body
     */
    public Invite(Dialog dialog, MimeType contentType, byte[] body) {
        this(dialog.getRemoteTarget().getUri(),
                dialog.getLocalParty(),
                dialog.getRemoteParty(),
                dialog.getLocalSeqNumber(),
                dialog.getLocalParty().getUri(),
                null,
                dialog.getRouteSet(),
                contentType,
                body);
        setDialog(dialog);
    }

    /**
     * Use this constructor to create a new outbound INVITE which is to be send via a client transaction
     *
     * @param requestURI           requestURI
     * @param from                 from address
     * @param to                   to address
     * @param cseq                 local cseq number. Use -1 if this is is an initial request
     * @param contactHeaderBaseUri URI to be used for constructing the contact header
     * @param contactHeaderParams  parameters to be set into the contact header
     * @param routeSet             the routeSet
     * @param contentType          the content type if a body is present
     * @param body                 the body
     */
    public Invite(
            URI requestURI,
            Address from,
            Address to,
            long cseq,
            URI contactHeaderBaseUri,
            List<NameValuePair> contactHeaderParams,
            List<RouteHeader> routeSet,
            MimeType contentType, 
            byte[] body) {
        super(NAME, requestURI, null, body);

        setHeader(new ToHeader(to));
        if (!from.isParameterSet("tag")) {
            from = new Address(from.getUri(), from.getDisplayName(), Collections.singletonList(new NameValuePair("tag", Utils.generateTag())));
        }
        setHeader(new FromHeader(from));
        setHeader(new CallIDHeader(Utils.generateCallIdentifier(requestURI)));
        setHeader(new CSeqHeader(cseq > 0 ? cseq : 1, Invite.NAME));
        setHeader(Request.getStackContactHeader(contactHeaderBaseUri, contactHeaderParams));
        if (body != null && body.length > 0) {
            setHeader(new ContentTypeHeader(contentType));
        }
        addHeaders(routeSet);

        // Via is auto added when this request is sent
    }

    public Response createResponse(int statusCode, String reasonPhrase, MimeType contentType, byte[] body, URI contactHeaderBaseUri, List<NameValuePair> contactHeaderParams) {
        Response newResponse = createResponse(statusCode, reasonPhrase, contentType, body);
        if (contactHeaderBaseUri != null)
            newResponse.setHeader(Request.getStackContactHeader(contactHeaderBaseUri, contactHeaderParams));
        return newResponse;
    }
}

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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class Subscribe extends Request {
    public static final String NAME = "SUBSCRIBE";

    /**
     * Used by the message parser to construct a new instance of this class
     *
     * @param requestUri the requestURI
     * @param headers    all headers
     * @param body       the body
     */
    public Subscribe(URI requestUri, HashMap<String, List<SipHeader>> headers, byte[] body) {
        super(NAME, requestUri, headers, body);
    }

    /**
     * Constructs a Subscribe request for a new dialog (subscription)
     *
     * @param requestUri           the requestUri
     * @param from                 the from address
     * @param to                   the to address
     * @param localCSeq            the localCSeq
     * @param contactHeaderBaseUri the uri to be used to generate the contact header
     * @param contactHeaderParams  the parameters to be used in the contact header
     * @param event                the eventHeader to be used
     * @param routeSet             the routeset
     * @param expires              the expires header value
     * @param contentType          the content type of the body (may be null)
     * @param body                 the body (may be null)
     * @param acceptContentTypes   list of accept types
     */
    public Subscribe(
            URI requestUri,
            Address from,
            Address to,
            long localCSeq,
            URI contactHeaderBaseUri,
            List<NameValuePair> contactHeaderParams,
            EventHeader event,
            List<RouteHeader> routeSet,
            long expires,
            MimeType contentType,
            byte[] body,
            Collection<MimeType> acceptContentTypes) {
        super(NAME, requestUri, null, body);

        setHeader(new ToHeader(to));
        if (!from.isParameterSet("tag")) {
            from = new Address(from.getUri(), from.getDisplayName(), Collections.singletonList(new NameValuePair("tag", Utils.generateTag())));
        }
        setHeader(new FromHeader(from));
        setHeader(new CallIDHeader(Utils.generateCallIdentifier(requestUri)));
        setHeader(new CSeqHeader(localCSeq > 0 ? localCSeq : 1, Subscribe.NAME));
        setHeader(Request.getStackContactHeader(contactHeaderBaseUri, contactHeaderParams));

        setHeader(event);
        setHeader(new ExpiresHeader(expires));

        if (acceptContentTypes != null && acceptContentTypes.size() > 0) {
            for (MimeType mt : acceptContentTypes) {
                addHeader(new AcceptHeader(mt));
            }
        }

        if (body != null && body.length > 0) {
            setHeader(new ContentTypeHeader(contentType));
        }
        addHeaders(routeSet);

    }

    /**
     * Constructs a new in-dialog Invite request
     *
     * @param dialog      the dialog instance for which to create a new in-dialog INVITE request
     * @param eventHeader the event header
     * @param expires     the delta-seconds for the expires header
     * @param contentType the content type if a body is present
     * @param body        the body
     */
    public Subscribe(Dialog dialog, EventHeader eventHeader, long expires, MimeType contentType, byte[] body) {
        this(dialog.getRemoteTarget().getUri(), null, body);
        dialog.updateRequestToBeInDialog(this);
        if (body != null && body.length > 0) {
            setHeader(new ContentTypeHeader(contentType));
        }
        setDialog(dialog);

        setHeader(eventHeader);
        setHeader(new ExpiresHeader(expires));
    }
}

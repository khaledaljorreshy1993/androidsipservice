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
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.headers.*;
import com.colibria.android.sipservice.sip.tx.Utils;

import java.util.HashMap;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class Message extends Request {

    public static final String NAME = "MESSAGE";

    /**
     * Used by the message parser to construct a new instance of this class
     *
     * @param requestUri the requestURI
     * @param headers    all headers
     * @param body       the body
     */
    public Message(URI requestUri, HashMap<String, List<SipHeader>> headers, byte[] body) {
        super(NAME, requestUri, headers, body);
    }

    /**
     * Prepares a new MESSAGE request for a new client transaction
     *
     * @param requestUri  the requestURI
     * @param from        the from address
     * @param to          the to address
     * @param cSeq        cSeq value
     * @param routeSet    route set
     * @param contentType content type
     * @param body        body
     */
    public Message(URI requestUri, Address from, Address to, long cSeq, List<RouteHeader> routeSet, MimeType contentType, byte[] body) {
        super(NAME, requestUri, null, body);
        setHeader(new FromHeader(from));
        setHeader(new ToHeader(to));
        setHeader(new CSeqHeader(cSeq, NAME));
        if (body != null && body.length > 0) {
            setHeader(new ContentTypeHeader(contentType));
        }
        setHeader(new CallIDHeader(Utils.generateCallIdentifier(requestUri)));
        addHeaders(routeSet);
    }
}

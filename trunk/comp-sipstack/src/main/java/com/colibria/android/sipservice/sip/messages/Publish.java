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
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.headers.*;
import com.colibria.android.sipservice.sip.tx.Utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class Publish extends Request {

    public static final String NAME = "PUBLISH";

    public Publish(URI requestURI, HashMap<String, List<SipHeader>> headers, byte[] body) {
        super(NAME, requestURI, headers, body);
    }


    public static Publish create(Address sender, long expires, EventHeader eventHeader, MimeType contentType, byte[] body) {
        Publish publish = new Publish(sender.getUri(), new HashMap<String, List<SipHeader>>(), body);
        if (body != null && body.length > 0)
            publish.addHeader(new ContentTypeHeader(contentType));

        publish.addHeader(new FromHeader(new Address(sender.getUri(), sender.getDisplayName(), Collections.singletonList(new NameValuePair("tag", Utils.generateTag())))));
        publish.addHeader(new ToHeader(sender));
        publish.addHeader(new CallIDHeader(Utils.generateCallIdentifier(sender.getUri())));
        publish.addHeader(new CSeqHeader(100, NAME));

        publish.addHeader(eventHeader);

        publish.addHeader(new ExpiresHeader(expires));
        return publish;
    }
}
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

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.SipStack;
import com.colibria.android.sipservice.sip.headers.*;
import com.colibria.android.sipservice.sip.tx.Utils;
import com.colibria.android.sipservice.sip.URI;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class Register extends Request {

    public static final String NAME = "REGISTER";

    private static ContactHeader getContactHeaderForRegister(URI uri, List<NameValuePair> contactHeaderParams) {
        URI sipURI;
        LinkedList<NameValuePair> uriParameters = new LinkedList<NameValuePair>();
        uriParameters.add(new NameValuePair("transport", "tcp"));

        // let's use 5060 for the port, such that sub-sequent binding updates will match

        if (uri.getType() == URI.Type.tel) {
            sipURI = new URI(URI.Type.sip, uri.getPhonenumber(), null, SipStack.get().getMyHostName(), SipStack.get().getMyPort(), null, uriParameters);
        } else {
            sipURI = new URI(URI.Type.sip, uri.getUsername(), null, SipStack.get().getMyHostName(), SipStack.get().getMyPort(), null, uriParameters);
        }
        return new ContactHeader(new Address(sipURI, "", contactHeaderParams));
    }

    public static Register create(Address sender, long cseq, String callId, List<NameValuePair> contactHeaderParams) {
        Register register = new Register(sender.getUri(), new HashMap<String, List<SipHeader>>(), null);
        register.addHeader(new FromHeader(new Address(sender.getUri(), sender.getDisplayName(), Collections.singletonList(new NameValuePair("tag", Utils.generateTag())))));
        register.addHeader(new ToHeader(sender));
        register.addHeader(new CallIDHeader(callId));
        register.addHeader(new CSeqHeader(cseq, NAME));
        register.addHeader(getContactHeaderForRegister(sender.getUri(), contactHeaderParams));
        return register;
    }

    public Register(URI requestURI, HashMap<String, List<SipHeader>> headers, byte[] body) {
        super(NAME, requestURI, headers, body);
    }

}

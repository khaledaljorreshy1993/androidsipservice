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
package com.colibria.android.sipservice.headers;

import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.UriParser;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sebastian Dehne
 */
public class CPIMToHeader implements ICPIMHeader {
    public static final String NAME = "To";

    private final Address address;

    public CPIMToHeader(Address address) {
        this.address = address;
    }


    public String getDisplayName() {
        return address.getDisplayName();
    }

    public URI getURI() {
        return address.getUri();
    }

    public static CPIMToHeader parse(String headerValue) throws IOException {
        try {
            // split the URI from the display name
            String[] chunks = headerValue.split("<", 2);

            String[] tmp = chunks[1].split(">", 2);

            if (tmp[0].length() <= 0) {
                throw new IOException("Empty uri found; not allowed");
            }

            UriParser up = new UriParser();
            URI result = up.parseMore(ByteBuffer.wrap((tmp[0] + "\r").getBytes()));

            return new CPIMToHeader(new Address(result, chunks[0].trim(), null));
        } catch (Exception e) {
            throw new IOException("Could not create header " + e.getMessage());
        }
    }

    public String getName() {
        return NAME;
    }

    public String getValue() {
        StringBuffer sb = new StringBuffer();
        if (getDisplayName() != null && getDisplayName().length() > 0) {
            sb.append(getDisplayName()).append(" ");
        }
        sb.append("<").append(getURI()).append(">");
        return sb.toString();
    }

    public boolean isInMimeSection() {
        return false;
    }

    public Address getAsAddress() {
        return address;
    }
}

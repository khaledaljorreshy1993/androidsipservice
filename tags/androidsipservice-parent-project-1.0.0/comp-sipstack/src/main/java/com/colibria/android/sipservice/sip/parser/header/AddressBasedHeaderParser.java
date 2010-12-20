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
package com.colibria.android.sipservice.sip.parser.header;

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.AddressParser;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.headers.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class AddressBasedHeaderParser implements IHeaderParser<AddressBasedHeaderBase> {

    private AddressBasedHeaderBase createHeader() throws IOException {
        if (FromHeader.NAME.equalsIgnoreCase(addressParser.getHeaderName())
                || FromHeader.NAME_SHORT.equals(addressParser.getHeaderName())) {
            return new FromHeader(new Address(getUri(), getDisplayName(), getBestParameters()));
        } else if (ToHeader.NAME.equalsIgnoreCase(getHeaderName())
                || ToHeader.NAME_SHORT.equals(getHeaderName())) {
            return new ToHeader(new Address(getUri(), getDisplayName(), getBestParameters()));
        } else if (ContactHeader.NAME.equalsIgnoreCase(getHeaderName())
                || ContactHeader.NAME_SHORT.equals(getHeaderName())) {
            return new ContactHeader(new Address(getUri(), getDisplayName(), getBestParameters()));
        } else if (RouteHeader.NAME.equalsIgnoreCase(getHeaderName())) {
            return new RouteHeader(new Address(getUri(), getDisplayName(), getBestParameters()));
        } else if (RecordRouteHeader.NAME.equalsIgnoreCase(getHeaderName())) {
            return new RecordRouteHeader(new Address(getUri(), getDisplayName(), getBestParameters()));
        } else if (ServiceRouteHeader.NAME.equalsIgnoreCase(getHeaderName())) {
            return new ServiceRouteHeader(new Address(getUri(), getDisplayName(), getBestParameters()));
        }
        throw new IOException("Don't know how to create a header for '" + getDisplayName() + "'");
    }

    private final AddressParser addressParser = new AddressParser();

    @Override
    public void reset(String headerName) {
        addressParser.reset(headerName, isListedHeader(headerName));
    }

    @Override
    public AddressBasedHeaderBase parseMoreData(ByteBuffer bb) throws IOException {
        addressParser.parseMoreData(bb);
        return createHeader();
    }

    @Override
    public String getHeaderName() {
        return addressParser.getHeaderName();
    }

    @Override
    public boolean isListedHeader() {
        return isListedHeader(addressParser.getHeaderName());
    }

    private static boolean isListedHeader(String headerName) {
        return ContactHeader.NAME.equalsIgnoreCase(headerName)
                || ContactHeader.NAME_SHORT.equals(headerName)
                || RouteHeader.NAME.equals(headerName)
                || RecordRouteHeader.NAME.equals(headerName)
                || ServiceRouteHeader.NAME.equals(headerName);
    }

    private URI getUri() {
        return addressParser.getUri();
    }

    private String getDisplayName() {
        return addressParser.getDisplayName();
    }

    private List<NameValuePair> getBestParameters() {
        return getDisplayName() == null ? getUri().getParameters() : addressParser.getParameters();
    }
}

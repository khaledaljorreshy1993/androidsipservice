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
package com.colibria.android.sipservice.sip.headers;

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.URI;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public abstract class AddressBasedHeaderBase extends SipHeader {

    private final String tag;
    private final Address address;

    protected AddressBasedHeaderBase(String name, Address address) {
        super(name);
        this.address = address;
        this.tag = getParameterValueCaseInsensitive("tag");
    }

    public URI getUri() {
        return address.getUri();
    }

    public String getDisplayName() {
        return address.getDisplayName();
    }

    public String getTag() {
        return tag;
    }

    public Address getAddress() {
        return address;
    }

    public List<NameValuePair> getParameters() {
        return address.getParameters();
    }

    public boolean isParameterSet(String parameterName) {
        return address.isParameterSet(parameterName);
    }

    public boolean isParameterSetCaseInsensitive(String parameterName) {
        return address.isParameterSetCaseInsensitive(parameterName);
    }

    public String getParameterValue(String parameterName) {
        return address.getParameterValue(parameterName);
    }

    public String getParameterValueCaseInsensitive(String parameterName) {
        return address.getParameterValueCaseInsensitive(parameterName);
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        address.writeValueToBuffer(bb);
    }

}

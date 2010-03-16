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
import com.colibria.android.sipservice.sip.parser.header.ViaHeaderParser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class ViaHeader extends SipHeader {

    public static final String NAME = "Via";
    public static final String NAME_SHORT = "v";

    private final String transport;
    private final String hostname;
    private final int port;
    private final List<NameValuePair> parameters;

    public ViaHeader(String transport, String hostname, int port, List<NameValuePair> parameters) {
        super(NAME);
        this.transport = transport;
        this.hostname = hostname;
        this.port = port;
        if (parameters != null)
            this.parameters = Collections.unmodifiableList(parameters);
        else
            this.parameters = Collections.emptyList();
    }

    public String getSentBy() {
        if (port < 0) {
            return transport;
        } else {
            return transport + ":" + port;
        }
    }

    public String getTransport() {
        return transport;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getBranch() {
        return getParameterValueCaseInsensitive("branch");
    }

    public List<NameValuePair> getParameters() {
        return parameters;
    }

    public boolean isParameterSet(String parameterName) {
        for (NameValuePair p : parameters) {
            if (p.getName().equals(parameterName))
                return true;
        }
        return false;
    }

    public boolean isParameterSetCaseInsensitive(String parameterName) {
        for (NameValuePair p : parameters) {
            if (p.getName().equalsIgnoreCase(parameterName))
                return true;
        }
        return false;
    }

    public String getParameterValue(String parameterName) {
        for (NameValuePair p : parameters) {
            if (p.getName().equals(parameterName))
                return p.getValue();
        }
        return null;
    }

    public String getParameterValueCaseInsensitive(String parameterName) {
        for (NameValuePair p : parameters) {
            if (p.getName().equalsIgnoreCase(parameterName))
                return p.getValue();
        }
        return null;
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        bb.write(ViaHeaderParser.CONSTANT_SIP);
        bb.write(transport.getBytes());
        bb.write(" ".getBytes());

        bb.write(hostname.getBytes());
        if (port >= 0) {
            bb.write((":" + port).getBytes());
        }

        if (parameters.size() > 0) {
            for (NameValuePair p : parameters) {
                bb.write(";".getBytes());
                bb.write(p.getName().getBytes());
                if (p.getValue() != null) {
                    bb.write("=".getBytes());
                    bb.write(p.getValue().getBytes());
                }
            }
        }
    }
}

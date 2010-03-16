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

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Sebastian Dehne
 */
public class MsrpURI {

    private static final String MSRP = "msrp";
    private static final String MSRPS = "msrps";
    private static final int MSRP_DEFAULT_PORT = 2855;

    private final boolean secure;
    private final String userInfo;
    private final String host;
    private final int port;
    private final String sessionID;
    private final String transport;
    private final Map<String, String> parameters;

    public MsrpURI(boolean secure, String userInfo, String host, int port, String sessionID, String transport, Map<String, String> parameters) {
        this.secure = secure;
        this.userInfo = userInfo;
        this.host = host;
        this.port = port;
        this.sessionID = sessionID;
        this.transport = transport;
        Map<String, String> map = new HashMap<String, String>();
        if (parameters != null) {
            for (String k : parameters.keySet()) {
                map.put(k, parameters.get(k));
            }
        }
        this.parameters = Collections.unmodifiableMap(map);
    }

    public boolean isSecure() {
        return secure;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getSessionID() {
        return sessionID;
    }

    public String getTransport() {
        return transport;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public String getParameter(String name) {
        return parameters.get(name);
    }

    public Set<String> getParameterNames() {
        return parameters.keySet();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(64);

        // add the scheme
        sb.append(secure ? MSRPS : MSRP).append("://");

        // add the optional userInfo
        if (userInfo != null) {
            sb.append(userInfo).append('@');
        }

        // add the host and port
        sb.append(host).append(':').append(port);

        // add the sessionID
        sb.append('/').append(sessionID);

        // the transport
        sb.append(';').append(transport);

        // and additional parameters if exist
        String v;
        for (String k : parameters.keySet()) {
            sb.append(';').append(k);
            if ((v = parameters.get(k)) != null) {
                sb.append('=').append(v);
            }
        }
        return sb.toString();
    }

    public static MsrpURI parse(String uri) throws IOException {
        // 'msrp://user@host:port/sessionID;transport=tcp;bla=bla;bla2'

        URI realURI;
        String uriWithoutParameters;
        String parameters;
        try {
            String[] chunks = uri.split(";", 2);
            uriWithoutParameters = chunks[0];
            parameters = chunks[1];
            realURI = new URI(uriWithoutParameters);

        } catch (Exception e) {
            throw new IOException("Error while parsing msrp-uri: "+ e.getMessage());
        }

        final boolean isSecure;
        if (MSRP.equals(realURI.getScheme())) {
            isSecure = false;
        } else if (MSRPS.equals(realURI.getScheme())) {
            isSecure = true;
        } else {
            throw new IOException("Could not parse scheme");
        }

        String host = realURI.getHost();
        String userInfo = realURI.getUserInfo();
        int port = realURI.getPort();
        if (port < 0) {
            port = MSRP_DEFAULT_PORT; // this might not be correct to do
        }

        String sessionID = realURI.getPath().substring(1); // skip the '/'

        /*
         * parse the parameters now
         */
        String transport = null;
        Map<String, String> parsedParameters = new HashMap<String, String>();
        try {
            String[] chunks = parameters.split(";");
            String[] tmp;
            for (String par : chunks) {
                // first one must be transport
                if (transport == null) {
                    transport = par;
                    continue;
                }
                tmp = par.split("=");
                parsedParameters.put(tmp[0], tmp.length > 1 ? tmp[1] : null);
            }
        } catch (Exception e) {
            throw new IOException("Could not parse parameters "+ e.getMessage());
        }

        /*
         * some validation
         */
        if (!"tcp".equals(transport)) {
            throw new IOException("Unexpected transport parameter found");
        }

        if (host == null) {
            throw new IOException("No host found");
        }

        return new MsrpURI(isSecure, userInfo, host, port, sessionID, transport, parsedParameters);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MsrpURI msrpURI = (MsrpURI) o;

        return !(sessionID != null ? !sessionID.equals(msrpURI.sessionID) : msrpURI.sessionID != null);

    }

    public int hashCode() {
        return (sessionID != null ? sessionID.hashCode() : 0);
    }
}

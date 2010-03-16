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
package com.colibria.android.sipservice.sip;

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.logging.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class URI implements Cloneable {
    private static final String TAG = "URI";

    public static URI fromString(String uri) {
        Logger.d(TAG, "fromString - " + uri);
        UriParser up = new UriParser();
        URI result = null;
        try {
            result = up.parseMore(ByteBuffer.wrap((uri + "\r").getBytes()));
        } catch (IOException e) {
            Logger.d(TAG, "", e);
        }
        return result;
    }

    public static enum Type {
        tel,
        sip,
        sips
    }

    /**
     * Whether port should be skipped in toString in case it has a value of 5060
     */
    private static final boolean OMIT_PORT_5060 = false;

    private final Type type;
    private final String username;
    private final String password;
    private final String host;
    private final int port;
    private final String phonenumber;
    private final List<NameValuePair> parameters;

    public URI(Type type, String username, String password, String host, int port, String phonenumber, List<NameValuePair> parameters) {
        this.type = type;
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
        this.phonenumber = phonenumber;
        if (parameters != null)
            this.parameters = Collections.unmodifiableList(parameters);
        else
            this.parameters = Collections.emptyList();
    }

    public Type getType() {
        return type;
    }

    public boolean isSipURI() {
        return type != Type.tel;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPhonenumber() {
        return phonenumber;
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

    public String toString() {
        StringBuffer sb = new StringBuffer(256);
        if (type == Type.sip || type == Type.sips) {
            sb.append(type).append(":");
            if (username != null) {
                sb.append(username);
                if (password != null) {
                    sb.append(":").append(password);
                }
                sb.append("@");
            }
            sb.append(host);
            //noinspection PointlessBooleanExpression
            if (port > -1 && !(port == 5060 && OMIT_PORT_5060)) {
                sb.append(":").append(port);
            }
        } else {
            sb.append("tel:");
            sb.append(phonenumber);
        }

        for (NameValuePair up : parameters) {
            sb.append(";");
            sb.append(up.getName());
            if (up.getValue() != null) {
                sb.append("=").append(up.getValue());
            }
        }
        return sb.toString();
    }

    public String toLowerCaseStringWithoutParams() {
        StringBuffer sb = new StringBuffer(256);
        if (type == Type.sip || type == Type.sips) {
            sb.append(type).append(":");
            if (username != null) {
                sb.append(username.toLowerCase());
                if (password != null) {
                    sb.append(":").append(password);
                }
                sb.append("@");
            }
            sb.append(host.toLowerCase());
            //noinspection PointlessBooleanExpression
            if (port > -1 && !(port == 5060 && OMIT_PORT_5060)) {
                sb.append(":").append(port);
            }
        } else {
            sb.append("tel:");
            sb.append(phonenumber.toLowerCase());
        }
        return sb.toString();
    }
}

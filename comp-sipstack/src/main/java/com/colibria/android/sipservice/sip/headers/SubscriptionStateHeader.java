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
import com.colibria.android.sipservice.sip.messages.ReasonCode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class SubscriptionStateHeader extends SipHeader {

    public static final String NAME = "Subscription-State";
    public static final String NAME_SHORT = "o";

    public static enum Substate {
        active,
        pending,
        waiting,
        terminated;

        public static Substate fromString(String s) {
            for (Substate ss : values()) {
                if (ss.toString().equalsIgnoreCase(s)) {
                    return ss;
                }
            }
            return null;
        }
    }

    private final Substate state;
    private final ReasonCode reason;
    private final long expires;
    private final long retryAfter;
    private final List<NameValuePair> parameters;

    @SuppressWarnings({"EmptyCatchBlock"})
    public SubscriptionStateHeader(Substate state, Collection<NameValuePair> parameters) throws IOException {
        super(NAME);
        this.state = state;
        this.parameters = new LinkedList<NameValuePair>();
        this.parameters.addAll(parameters);


        String tmpString = getParameterValueCaseInsensitive("reason");
        if (tmpString != null) {
            this.reason = ReasonCode.fromString(tmpString);
            if (this.reason == null) {
                throw new IOException("Unexpected value");
            }
        } else {
            this.reason = null;
        }

        tmpString = getParameterValueCaseInsensitive("expires");
        if (tmpString != null) {
            try {
                this.expires = Long.parseLong(tmpString);
            } catch (NumberFormatException e) {
                throw new IOException("Unexpected value in " + tmpString);
            }
        } else {
            this.expires = -1;
        }

        tmpString = getParameterValueCaseInsensitive("retry-after");
        if (tmpString != null) {
            try {
                this.retryAfter = Long.parseLong(tmpString);
            } catch (NumberFormatException e) {
                throw new IOException("Unexpected value in " + tmpString);
            }
        } else {
            this.retryAfter = -1;
        }
    }

    public ReasonCode getReason() {
        return reason;
    }

    public long getExpires() {
        return expires;
    }

    public long getRetryAfter() {
        return retryAfter;
    }

    public List<NameValuePair> getParameters() {
        return parameters;
    }

    public Substate getState() {
        return state;
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
        bb.write(state.toString().getBytes());

        for (NameValuePair nvp : parameters) {
            bb.write(';');
            nvp.writeValueToBuffer(bb);
        }
    }
}

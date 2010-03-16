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
import com.colibria.android.sipservice.sip.messages.SipMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class SessionExpiresHeader extends SipHeader {

    public static final String NAME = "Session-Expires";
    public static final String NAME_SHORT = "x";

    private final List<NameValuePair> parameters;
    private final long deltaSeconds;

    public SessionExpiresHeader(long deltaSeconds, List<NameValuePair> parameters) {
        super(NAME);
        this.parameters = parameters;
        this.deltaSeconds = deltaSeconds;
    }

    public SessionExpiresHeader(long deltaSeconds, SipMessage.SessionTimerRefresher refresher) {
        this(deltaSeconds, Collections.singletonList(new NameValuePair(SipMessage.SESSION_TIMER_REFRESHER, refresher.toString())));
    }

    public long getDeltaSeconds() {
        return deltaSeconds;
    }

    public SipMessage.SessionTimerRefresher getRefresher() {
        String value = getParameterValueCaseInsensitive(SipMessage.SESSION_TIMER_REFRESHER);
        if (value != null && value.length() > 0) {
            if (SipMessage.SessionTimerRefresher.uac.toString().equals(value)) {
                return SipMessage.SessionTimerRefresher.uac;
            } else if (SipMessage.SessionTimerRefresher.uas.toString().equals(value)) {
                return SipMessage.SessionTimerRefresher.uas;
            }
        }
        return null;
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
        bb.write(Long.toString(deltaSeconds).getBytes());

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

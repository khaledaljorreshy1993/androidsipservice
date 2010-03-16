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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class EventHeader extends SipHeader {

    public static final String NAME = "Event";
    public static final String NAME_SHORT = "o";

    private final String eventPackage;
    private final List<NameValuePair> parameters;

    public EventHeader(String eventPackage, List<NameValuePair> parameters) {
        super(NAME);
        this.eventPackage = eventPackage;
        if (parameters == null)
            this.parameters = Collections.emptyList();
        else
            this.parameters = Collections.unmodifiableList(parameters);
    }

    public String getEventPackage() {
        return eventPackage;
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

    public String getEventId() {
        return getParameterValue("id");
    }

    public boolean match(EventHeader matchTarget) {
        if (matchTarget.eventPackage == null && this.eventPackage != null)
            return false;
        else if (matchTarget.eventPackage != null && this.eventPackage == null)
            return false;
        else if (this.eventPackage == null && matchTarget.eventPackage == null)
            return false;
        else if (getEventId() == null && matchTarget.getEventId() != null)
            return false;
        else if (getEventId() != null && matchTarget.getEventId() == null)
            return false;
        //noinspection StringEquality
        return matchTarget.eventPackage.equalsIgnoreCase(this.eventPackage) && (
                (this.getEventId() == matchTarget.getEventId())
                        || this.getEventId().equalsIgnoreCase(matchTarget.getEventId()));
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        bb.write(eventPackage.getBytes());

        if (parameters.size() > 0) {
            for (NameValuePair p : parameters) {
                bb.write(';');
                p.writeValueToBuffer(bb);
            }
        }
        
    }
}

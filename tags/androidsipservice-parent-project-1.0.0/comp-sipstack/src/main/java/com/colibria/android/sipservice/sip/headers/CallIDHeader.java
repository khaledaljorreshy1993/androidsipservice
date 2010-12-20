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

import java.io.OutputStream;
import java.io.IOException;

/**
 * @author Sebastian Dehne
 */
public class CallIDHeader extends SipHeader {

    public static final String NAME = "Call-ID";
    public static final String NAME_SHORT = "i";

    private final String callId;

    public CallIDHeader(String callId) {
        super(NAME);
        this.callId = callId;
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        bb.write(callId.getBytes());
    }

    public String getCallId() {
        return callId;
    }
}

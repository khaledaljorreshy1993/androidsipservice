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
public class MinSeHeader extends SipHeader {

    public static final String NAME = "Min-SE";

    private final List<NameValuePair> parameters;
    private final long deltaSeconds;

    public MinSeHeader(long deltaSeconds, List<NameValuePair> parameters) {
        super(NAME);
        this.parameters = parameters;
        this.deltaSeconds = deltaSeconds;
    }

    public MinSeHeader(long deltaSeconds) {
        this(deltaSeconds, Collections.<NameValuePair>emptyList());
    }

    public long getDeltaSeconds() {
        return deltaSeconds;
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        bb.write(String.valueOf(deltaSeconds).getBytes());
    }
}

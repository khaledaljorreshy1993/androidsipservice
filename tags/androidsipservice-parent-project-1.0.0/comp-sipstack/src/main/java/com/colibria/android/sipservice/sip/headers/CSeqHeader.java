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

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Sebastian Dehne
 */
public class CSeqHeader extends SipHeader {

    public static final String NAME = "CSeq";

    private final long value;
    private final String method;

    public CSeqHeader(long number, String method) {
        super(NAME);
        this.value = number;
        this.method = method;
    }

    public long getSeqNumber() {
        return value;
    }

    public String getMethod() {
        return method;
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        bb.write(Long.toString(value).getBytes());
        bb.write(" ".getBytes());
        bb.write(method.getBytes());
    }
}

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
package com.colibria.android.sipservice.sip.parser.header;

import com.colibria.android.sipservice.sip.headers.*;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * @author Sebastian Dehne
 */
public class SimpleHeaderParser implements IHeaderParser<SipHeader> {

    private int lastKnownBufferPosition;
    private int startPosition;
    private String headerName;

    @Override
    public void reset(String headerName) {
        this.headerName = headerName;
        lastKnownBufferPosition = -1;
        startPosition = -1;
    }

    @Override
    public SipHeader parseMoreData(ByteBuffer bb) throws IOException {
        if (lastKnownBufferPosition == -1) {
            lastKnownBufferPosition = bb.position();
            startPosition = bb.position();
        } else {
            bb.position(lastKnownBufferPosition);
        }

        try {
            // find the '\r'
            while (bb.get() != '\r') {
                lastKnownBufferPosition = bb.position();
            }

            // now, ensure that the next char is the '\n'
            if (bb.get() != '\n') {
                throw new IOException("Unexpected prolog");
            } else {

                // end found, copy the value and create the header instance
                byte[] data = new byte[bb.position() - startPosition - 2];
                System.arraycopy(bb.array(), bb.arrayOffset() + startPosition, data, 0, data.length);

                bb.position(bb.position() - 2);

                return generateHeader(new String(data));
            }

        } catch (BufferUnderflowException e) {
            throw new EOFException();
        }
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public boolean isListedHeader() {
        return false;
    }

    private SipHeader generateHeader(String value) throws IOException {
        Temp t = creators.get(headerName);
        return t != null ? t.generateHeader(value) : new UnknownHeader(headerName, value);
    }

    private static abstract class Temp {
        abstract SipHeader generateHeader(String value);
    }

    private static final HashMap<String, Temp> creators = new HashMap<String, Temp>();

    static {
        creators.put(ContentLengthHeader.NAME, new Temp() {
            @Override
            SipHeader generateHeader(String value) {
                return new ContentLengthHeader(Integer.parseInt(value));
            }
        });
        creators.put(MaxForwardsHeader.NAME, new Temp() {
            @Override
            SipHeader generateHeader(String value) {
                return new MaxForwardsHeader(Integer.parseInt(value));
            }
        });
        creators.put(ExpiresHeader.NAME, new Temp() {
            @Override
            SipHeader generateHeader(String value) {
                return new ExpiresHeader(Integer.parseInt(value));
            }
        });
        creators.put(MinExpiresHeader.NAME, new Temp() {
            @Override
            SipHeader generateHeader(String value) {
                return new MinExpiresHeader(Integer.parseInt(value));
            }
        });
        creators.put(CallIDHeader.NAME, new Temp() {
            @Override
            SipHeader generateHeader(String value) {
                return new CallIDHeader(value);
            }
        });
        creators.put(CSeqHeader.NAME, new Temp() {
            @Override
            SipHeader generateHeader(String value) {
                String[] parts = value.split(" ");
                return new CSeqHeader(Long.parseLong(parts[0]), parts[1]);
            }
        });
        creators.put(SIPETagHeader.NAME, new Temp() {
            @Override
            SipHeader generateHeader(String value) {
                return new SIPETagHeader(value);
            }
        });
        creators.put(SIPIfMatchHeader.NAME, new Temp() {
            @Override
            SipHeader generateHeader(String value) {
                return new SIPIfMatchHeader(value);
            }
        });
    }
}

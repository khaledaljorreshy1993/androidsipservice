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

import com.colibria.android.sipservice.parse.ByteParser;
import com.colibria.android.sipservice.sip.UriParser;
import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.sip.headers.EventHeader;
import com.colibria.android.sipservice.sip.headers.MinSeHeader;
import com.colibria.android.sipservice.sip.headers.SessionExpiresHeader;
import com.colibria.android.sipservice.sip.headers.SipHeader;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * @author Sebastian Dehne
 */
public class EventHeaderParser implements IHeaderParser<SipHeader> {

    private static final ByteParser.Pattern PATTERN_EVENT_PACKAGE;

    static {
        PATTERN_EVENT_PACKAGE = new ByteParser.Pattern();
        PATTERN_EVENT_PACKAGE.setWordCharacter('A', 'Z');
        PATTERN_EVENT_PACKAGE.setWordCharacter('a', 'z');
        PATTERN_EVENT_PACKAGE.setWordCharacter('0', '9');
        PATTERN_EVENT_PACKAGE.setDelimiterCharacter('.');
        PATTERN_EVENT_PACKAGE.setDelimiterCharacter(' ');
        PATTERN_EVENT_PACKAGE.setDelimiterCharacter(';');
    }

    private String headerName;
    private int lastKnownPosition;
    private ByteParser bp = new ByteParser();

    @Override
    public void reset(String headerName) {
        this.headerName = headerName;
        lastKnownPosition = -1;
        bp.reset();
    }

    @Override
    public SipHeader parseMoreData(ByteBuffer bb) throws IOException {
        if (lastKnownPosition == -1) {
            lastKnownPosition = bb.position();
        } else {
            bb.position(lastKnownPosition);
        }

        int nextChar;
        LinkedList<NameValuePair> parameters = null; // every second entry is a value

        /*
         * presence\r\n
         * presence \r\n
         * presence.winfo\r\n
         * presence.winfo \r\n
         * presence;param2;param1=value\r\n
         * presence ;param2;param1=value\r\n
         * presence.winfo;param2;param1=value\r\n
         * presence.winfo ;param2;param1=value\r\n
         */

        if (bp.read(bb, PATTERN_EVENT_PACKAGE) != ByteParser.READ_WORD) {
            throw new IOException("Unexpected prolog");
        }

        String tmp = bp.getWordAsString(), template = null;
        bp.resetWord();
        bb.position(bb.position() - 1);

        // consume any white space if exists
        try {
            while ((nextChar = bb.get()) == ' ') ;
        } catch (BufferUnderflowException e) {
            throw new EOFException();
        }

        if (nextChar == '.') {
            bp.reset();

            if (bp.read(bb, PATTERN_EVENT_PACKAGE) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }


            template = bp.getWordAsString();
            bp.resetWord();
            bb.position(bb.position() - 1);

            // consume any white space if exists
            try {
                while ((nextChar = bb.get()) == ' ') ;
            } catch (BufferUnderflowException e) {
                throw new EOFException();
            }
        }

        if (nextChar == ';') {
            parameters = new LinkedList<NameValuePair>();

            String name, value;

            while (true) {
                value = null;
                bp.reset();
                if (bp.read(bb, UriParser.PATTERN_URI_PARAMETER) != ByteParser.READ_WORD) {
                    throw new IOException("Unexpected prolog");
                }

                name = bp.getWordAsString();
                bp.resetWord();

                nextChar = bp.read(bb, UriParser.PATTERN_URI_PARAMETER);

                if (nextChar == '=') {
                    // read the parameter value
                    bp.reset();
                    if (bp.read(bb, UriParser.PATTERN_URI_PARAMETER) != ByteParser.READ_WORD) {
                        throw new IOException("Unexpected prolog");
                    }
                    value = bp.getWordAsString();
                    bp.resetWord();

                    nextChar = bp.read(bb, UriParser.PATTERN_URI_PARAMETER);
                }

                parameters.add(new NameValuePair(name, value));

                if (nextChar == ';') {
                    continue;
                }

                // end of parameters
                if (nextChar == '\r') {
                    break;
                } else {
                    throw new IOException("Unexpected prolog " + nextChar);
                }
            }
        }

        if (nextChar == '\r') {
            bb.position(bb.position() - 1);
        }

        if (EventHeader.NAME.equals(headerName) || EventHeader.NAME_SHORT.equals(headerName)) {
            return new EventHeader(template == null ? tmp : tmp + "." + template, parameters);
        } else if (SessionExpiresHeader.NAME.equals(headerName) || SessionExpiresHeader.NAME_SHORT.equals(headerName)) {
            long deltaSeconds;
            try {
                deltaSeconds = Long.parseLong(tmp);
            } catch (NumberFormatException e) {
                throw new IOException("NumberFormatException " + e.getMessage());
            }
            return new SessionExpiresHeader(deltaSeconds, parameters);
        } else if (MinSeHeader.NAME.equals(headerName)) {
            long deltaSeconds;
            try {
                deltaSeconds = Long.parseLong(tmp);
            } catch (NumberFormatException e) {
                throw new IOException("NumberFormatException " + e.getMessage());
            }
            return new MinSeHeader(deltaSeconds, parameters);
        } else {
            throw new IOException("Don't know how to create a header for '" + headerName + "'");
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
}

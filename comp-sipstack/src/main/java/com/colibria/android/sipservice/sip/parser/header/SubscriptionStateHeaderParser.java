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

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.parse.ByteParser;
import com.colibria.android.sipservice.sip.headers.SubscriptionStateHeader;
import com.colibria.android.sipservice.sip.headers.SipHeader;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
@SuppressWarnings({"StatementWithEmptyBody"})
public class SubscriptionStateHeaderParser implements IHeaderParser<SipHeader> {

    private static final ByteParser.Pattern PATTERN_URI_PARAMETER;

    static {
        PATTERN_URI_PARAMETER = new ByteParser.Pattern();
        PATTERN_URI_PARAMETER.setWordCharacter('0', '9');
        PATTERN_URI_PARAMETER.setWordCharacter('A', 'Z');
        PATTERN_URI_PARAMETER.setWordCharacter('a', 'z');
        PATTERN_URI_PARAMETER.setWordCharacter('-');
        PATTERN_URI_PARAMETER.setWordCharacter('+');
        PATTERN_URI_PARAMETER.setWordCharacter('.');      // todo add more
        PATTERN_URI_PARAMETER.setDelimiterCharacter('=');
        PATTERN_URI_PARAMETER.setDelimiterCharacter(';');
        PATTERN_URI_PARAMETER.setDelimiterCharacter(' '); // end of entire URI
        PATTERN_URI_PARAMETER.setDelimiterCharacter('>'); // end of entire URI
        PATTERN_URI_PARAMETER.setDelimiterCharacter('\r'); // end of entire URI
        PATTERN_URI_PARAMETER.setDelimiterCharacter(','); // end of entire URI
    }

    private int lastKnownPosition;
    private int startPosition;
    private String headerName;
    private SubscriptionStateHeader.Substate substate;
    private List<NameValuePair> parameters;
    private final ByteParser parser = new ByteParser();

    @Override
    public void reset(String headerName) {
        this.headerName = headerName;
        lastKnownPosition = -1;
        startPosition = -1;
        parser.reset();
        parameters = new LinkedList<NameValuePair>();
        substate = null;
    }

    @Override
    public SipHeader parseMoreData(ByteBuffer bb) throws IOException {
        if (lastKnownPosition == -1) {
            lastKnownPosition = bb.position();
        } else {
            bb.position(lastKnownPosition);
        }

        consumeWhiteSpaces(bb);

        if (substate == null) {
            readSubState(bb);
            lastKnownPosition = bb.position();
        }

        String name, value;
        int nextChar;
        while (true) {
            name = null;
            value = null;

            nextChar = parser.read(bb, PATTERN_URI_PARAMETER);

            if (nextChar == ByteParser.READ_WORD) {
                name = parser.getWordAsString();
                parser.resetWord();

                nextChar = parser.read(bb, PATTERN_URI_PARAMETER);
            }

            if (nextChar == '=') {
                if (name == null) {
                    throw new IOException("Unexpected prolog");
                }

                if (parser.read(bb, PATTERN_URI_PARAMETER) == ByteParser.READ_WORD) {
                    value = parser.getWordAsString();
                    parser.resetWord();

                    nextChar = parser.read(bb, PATTERN_URI_PARAMETER);
                } else {
                    throw new IOException("Unexpected prolog");
                }
            }

            if (nextChar == ';') {
                if (name != null)
                    parameters.add(new NameValuePair(name, value));

                continue;
            }

            if (name != null)
                parameters.add(new NameValuePair(name, value));
            
            // this is the end, step one char back
            bb.position(bb.position() - 1);
            break;
        }

        return new SubscriptionStateHeader(substate, parameters);
    }

    private void consumeWhiteSpaces(ByteBuffer bb) throws IOException {
        // consume any white spaces if exist
        try {
            while (bb.get() == ' ') ;
        } catch (BufferUnderflowException e) {
            throw new EOFException();
        }
        bb.position(bb.position() - 1);
    }

    private void readSubState(ByteBuffer bb) throws IOException {
        int nextChar = parser.read(bb, PATTERN_URI_PARAMETER);

        if (nextChar == ByteParser.READ_WORD) {
            if ((substate = SubscriptionStateHeader.Substate.fromString(parser.getWordAsString())) == null) {
                throw new IOException("Unexpected prolog");
            }
        } else {
            throw new IOException("Unexpected prolog");
        }

        parser.resetWord();
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

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
import com.colibria.android.sipservice.sip.UriParser;
import com.colibria.android.sipservice.sip.headers.ViaHeader;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class ViaHeaderParser implements IHeaderParser<ViaHeader> {

    public static final byte[] CONSTANT_SIP = "SIP/2.0/".getBytes();

    private static final ByteParser.Pattern PATTERN_TRANSPORT;

    static {
        PATTERN_TRANSPORT = new ByteParser.Pattern();
        PATTERN_TRANSPORT.setWordCharacter('A', 'Z');
        PATTERN_TRANSPORT.setWordCharacter('-');
        PATTERN_TRANSPORT.setDelimiterCharacter(' ');
    }

    private String headerName;
    private int lastKnownBufferPos;
    private ByteParser bp = new ByteParser();

    private String transport;
    private String hostname;
    private int port;
    private List<NameValuePair> parameters;

    @Override
    public void reset(String headerName) {
        this.headerName = headerName;
        lastKnownBufferPos = -1;
        transport = null;
        hostname = null;
        port = -1;
        parameters = null;
    }

    @Override
    public ViaHeader parseMoreData(ByteBuffer bb) throws IOException {
        if (lastKnownBufferPos == -1)
            lastKnownBufferPos = bb.position();
        else
            bb.position(lastKnownBufferPos);

        int nextChar;

        if (transport == null) {

            // consume any white spaces if exist
            try {
                //noinspection StatementWithEmptyBody
                while (bb.get() == ' ');
            } catch (BufferOverflowException e) {
                throw new EOFException();
            }
            bb.position(bb.position() - 1);


            // read constant
            bp.reset();
            bp.readConstant(bb, CONSTANT_SIP, true);

            // read transport
            if (bp.read(bb, PATTERN_TRANSPORT) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }

            String tmp = bp.getWordAsString();
            bp.resetWord();

            // consume the next ' '
            if (bp.read(bb, PATTERN_TRANSPORT) != ByteParser.READ_SPACE) {
                throw new IOException("Unexpected prolog");
            }

            transport = tmp;
            lastKnownBufferPos = bb.position();
        }

        if (hostname == null) {
            bp.reset();

            // read the hostname
            if (bp.read(bb, UriParser.PATTERN_URI_STRING) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }

            String name = bp.getWordAsString(), portValue = "-1";
            bp.resetWord();

            nextChar = bp.read(bb, UriParser.PATTERN_URI_STRING);

            // read the port now
            if (nextChar == ':') {
                bp.reset();
                if (bp.read(bb, UriParser.PATTERN_URI_STRING) != ByteParser.READ_WORD) {
                    throw new IOException("Unexpected prolog");
                }

                portValue = bp.getWordAsString();
                bp.resetWord();

                nextChar = bp.read(bb, UriParser.PATTERN_URI_STRING);
            }

            // parameters expected
            if (nextChar == ';') {
                parameters = new LinkedList<NameValuePair>();
            }

            // end of this via header
            else if (nextChar == ',' || nextChar == '\r') {
                bb.position(bb.position() - 1);
            }

            // unknown char
            else {
                throw new IOException("Unexpected char " + nextChar);
            }

            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                throw new IOException(e.getMessage());
            }
            hostname = name;

            lastKnownBufferPos = bb.position();
        }

        if (parameters != null) {
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

                // new parameter found
                if (nextChar == ';') {
                    continue;
                }

                // end of this via header
                else if (nextChar == ',' || nextChar == '\r') {
                    bb.position(bb.position() - 1);
                }

                break;
            }
        }

        return new ViaHeader(transport, hostname, port, parameters);
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public boolean isListedHeader() {
        return true;
    }
}

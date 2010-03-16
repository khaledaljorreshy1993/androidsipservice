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
package com.colibria.android.sipservice.sip;

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.parse.ByteParser;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class AddressParser {

    private static final ByteParser.Pattern PATTERN_DISPLAYNAME;

    static {
        PATTERN_DISPLAYNAME = new ByteParser.Pattern();
        PATTERN_DISPLAYNAME.setWordCharacter('A', 'Z');
        PATTERN_DISPLAYNAME.setWordCharacter('a', 'z');
        PATTERN_DISPLAYNAME.setWordCharacter('0', '9');
        PATTERN_DISPLAYNAME.setWordCharacter(' ');
        PATTERN_DISPLAYNAME.setWordCharacter('+');
        PATTERN_DISPLAYNAME.setWordCharacter('-');
        PATTERN_DISPLAYNAME.setWordCharacter('"'); // todo add more including utf8 stuff!!
        PATTERN_DISPLAYNAME.setDelimiterCharacter('<'); // start of the URI
    }

    protected String headerName;
    private int lastKnownPosition;
    private UriParser uriParser = new UriParser();
    private ByteParser bp = new ByteParser();
    private volatile boolean isListHeader;

    protected String displayName;
    protected URI uri;
    protected List<NameValuePair> parameters;

    public void reset(String headerName, boolean isListHeader) {
        lastKnownPosition = -1;
        this.headerName = headerName;
        displayName = null;
        uri = null;
        parameters = null;
        bp.reset();
        uriParser.reset();
        this.isListHeader = isListHeader;
    }

    public void parseMoreData(ByteBuffer bb) throws IOException {
        if (lastKnownPosition == -1)
            lastKnownPosition = bb.position();
        else
            bb.position(lastKnownPosition);

        int nextChar;

        /*
        * Possible examples are:
        *     From: "Bob" <sips:bob@biloxi.com> ;tag=a48s
        *     From: sip:+12125551212@phone2net.com;tag=887s
        *     From: Anonymous <sip:c8oqz84zk7z@privacy.org>;tag=hyh8
        */

        // consume any white spaces if exist
        try {
            while (bb.get() == ' ') ;
        } catch (BufferUnderflowException e) {
            throw new EOFException();
        }
        bb.position(bb.position() - 1);

        // try to see if the header value starts with a URI
        uriParser.reset();
        int tmpPosition = bb.position();
        try {
            uri = uriParser.parseMore(bb);
        } catch (EOFException e) {
            throw e;
        } catch (IOException e) {
            // not a uri after all
            bb.position(tmpPosition);
        }
        if (uri != null) {
            // consume any white spaces if exist
            try {
                //noinspection StatementWithEmptyBody
                while ((nextChar = bb.get()) == ' ') ;
            } catch (BufferUnderflowException e) {
                throw new EOFException();
            }

            if (nextChar == '\r') {
                bb.position(bb.position() - 1);
            } else {
                throw new IOException("Unexpected prolog");
            }
            return;
        }

        // else: this header doesn't start with the URI


        bp.reset();
        tmpPosition = bb.position();
        if (bp.read(bb, PATTERN_DISPLAYNAME) == ByteParser.READ_WORD) {
            displayName = bp.getWordAsString();
            bp.resetWord();
            cleanDisplayName();
        } else {
            bb.position(tmpPosition);
            displayName = "";
        }

        // consume the '<'
        if (bp.read(bb, PATTERN_DISPLAYNAME) != '<') {
            throw new IOException("Unexpected prolog");
        }

        // parse the URI
        uriParser.reset();
        uri = uriParser.parseMore(bb);

        // consume the exepcted '>'
        try {
            if (bb.get() != '>') {
                throw new IOException("Unexpected prolog");
            }
        } catch (BufferUnderflowException e) {
            throw new EOFException();
        }

        // consume any white spaces if exist
        try {
            //noinspection StatementWithEmptyBody
            while ((nextChar = bb.get()) == ' ') ;
        } catch (BufferUnderflowException e) {
            throw new EOFException();
        }

        // parameters found, read them
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
                if (nextChar == '\r' || (isListHeader && nextChar == ',')) {
                    bb.position(bb.position() - 1);
                } else {
                    throw new IOException("Unexpected prolog " + nextChar);
                }

                break;
            }
        }

        // end of header reached
        else if (nextChar == '\r' || nextChar == ',') {
            bb.position(bb.position() - 1);
        } else {
            throw new IOException("Unexpected prolog " + nextChar);
        }
    }

    private void cleanDisplayName() {
        // this is not very nice, but it works for now
        displayName = displayName.replaceAll("\"", " ");
        displayName = displayName.trim();
    }

    public String getHeaderName() {
        return headerName;
    }

    public List<NameValuePair> getParameters() {
        return parameters;
    }

    public URI getUri() {
        return uri;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isListHeader() {
        return isListHeader;
    }
}

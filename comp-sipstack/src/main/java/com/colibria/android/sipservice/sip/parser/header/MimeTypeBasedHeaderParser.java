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
import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.sip.headers.AcceptHeader;
import com.colibria.android.sipservice.sip.headers.ContentTypeHeader;
import com.colibria.android.sipservice.sip.headers.MimeTypeBasedHeader;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class MimeTypeBasedHeaderParser implements IHeaderParser<MimeTypeBasedHeader> {

    private static final ByteParser.Pattern PATTERN_TYPE;
    private static final ByteParser.Pattern PATTERN_SUBTYPE;
    private static final ByteParser.Pattern PATTERN_PARAM_NAME;
    private static final ByteParser.Pattern PATTERN_PARAM_VALUE;
    private static final ByteParser.Pattern PATTERN_PARAM_VALUE_QUOTED;

    static {
        PATTERN_TYPE = new ByteParser.Pattern();
        PATTERN_TYPE.setWordCharacter('a', 'z');
        PATTERN_TYPE.setWordCharacter('A', 'Z');
        PATTERN_TYPE.setWordCharacter('0', '9');
        PATTERN_TYPE.setDelimiterCharacter('/');

        PATTERN_SUBTYPE = new ByteParser.Pattern();
        PATTERN_SUBTYPE.setWordCharacter('a', 'z');
        PATTERN_SUBTYPE.setWordCharacter('A', 'Z');
        PATTERN_SUBTYPE.setWordCharacter('0', '9');
        PATTERN_SUBTYPE.setWordCharacter('+');
        PATTERN_SUBTYPE.setDelimiterCharacter(';');
        PATTERN_SUBTYPE.setDelimiterCharacter(',');  // end of entire header
        PATTERN_SUBTYPE.setDelimiterCharacter('\r');  // end of entire header
        PATTERN_SUBTYPE.setDelimiterCharacter(' ');  // end of entire header

        PATTERN_PARAM_NAME = new ByteParser.Pattern();
        PATTERN_PARAM_NAME.setWordCharacter('a', 'z');
        PATTERN_PARAM_NAME.setWordCharacter('A', 'Z');
        PATTERN_PARAM_NAME.setWordCharacter('0', '9');
        PATTERN_PARAM_NAME.setDelimiterCharacter('=');

        PATTERN_PARAM_VALUE = new ByteParser.Pattern();
        PATTERN_PARAM_VALUE.setWordCharacter('a', 'z');
        PATTERN_PARAM_VALUE.setWordCharacter('A', 'Z');
        PATTERN_PARAM_VALUE.setWordCharacter('0', '9');
        PATTERN_PARAM_VALUE.setWordCharacter('-');
        PATTERN_PARAM_VALUE.setWordCharacter('=');
        PATTERN_PARAM_VALUE.setWordCharacter('.');
        PATTERN_PARAM_VALUE.setWordCharacter('<');
        PATTERN_PARAM_VALUE.setWordCharacter('>');
        PATTERN_PARAM_VALUE.setWordCharacter('@');
        PATTERN_PARAM_VALUE.setDelimiterCharacter('"');
        PATTERN_PARAM_VALUE.setDelimiterCharacter(';');
        PATTERN_PARAM_VALUE.setDelimiterCharacter('\r'); // end of entire header
        PATTERN_PARAM_VALUE.setDelimiterCharacter(' ');  // end of entire header

        PATTERN_PARAM_VALUE_QUOTED = new ByteParser.Pattern();
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('a', 'z');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('A', 'Z');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('0', '9');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('-');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('_');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('.');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter(',');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('=');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('/');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('+');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('<');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('>');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter('@');
        PATTERN_PARAM_VALUE_QUOTED.setWordCharacter(';');
        // todo add more
        PATTERN_PARAM_VALUE_QUOTED.setDelimiterCharacter('"');
    }

    private String headerName;
    private int lastKnownPosition;
    private ByteParser bp = new ByteParser();
    private String type;
    private String subType;
    List<NameValuePair> parameters;

    @Override
    public void reset(String headerName) {
        lastKnownPosition = -1;
        this.headerName = headerName;
        type = null;
        subType = null;
        parameters = null;
    }


    @SuppressWarnings({"StatementWithEmptyBody"})
    @Override
    public MimeTypeBasedHeader parseMoreData(ByteBuffer bb) throws IOException {
        if (lastKnownPosition == -1) {
            lastKnownPosition = bb.position();
        } else {
            bb.position(lastKnownPosition);
        }

        // consume any white spaces if exist
        try {
            while (bb.get() == ' ') ;
        } catch (BufferUnderflowException e) {
            throw new EOFException();
        }
        bb.position(bb.position() - 1);

        if (type == null) {
            bp.reset();
            if (bp.read(bb, PATTERN_TYPE) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            String tmp = bp.getWordAsString();
            bp.resetWord();

            // consume the expected '/'
            if (bp.read(bb, PATTERN_TYPE) != '/') {
                throw new IOException("Unexpected prolog");
            }

            type = tmp;
            lastKnownPosition = bb.position();
        }

        if (subType == null) {
            bp.reset();
            if (bp.read(bb, PATTERN_SUBTYPE) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            String tmp = bp.getWordAsString();
            bp.resetWord();

            // consume any leading white spaces
            int nextChar;
            while ((nextChar = bp.read(bb, PATTERN_SUBTYPE)) == ' ') ;

            // start of parameters
            if (nextChar == ';') {
                parameters = new LinkedList<NameValuePair>();
            }

            // '\r': end of entire header found
            else {
                bb.position(bb.position() - 1);
            }

            subType = tmp;
            lastKnownPosition = bb.position();
        }


        if (parameters != null) {
            String name, value;
            int nextChar;
            while (true) {
                name = null;
                value = null;

                nextChar = bp.read(bb, PATTERN_PARAM_NAME);

                if (nextChar == ByteParser.READ_WORD) {
                    name = bp.getWordAsString();
                    bp.resetWord();

                    nextChar = bp.read(bb, PATTERN_PARAM_NAME);
                }

                if (nextChar == '=') {
                    if (name == null) {
                        throw new IOException("Unexpected prolog");
                    }

                    nextChar = bp.read(bb, PATTERN_PARAM_VALUE);
                    if (nextChar == '"') {
                        if (bp.read(bb, PATTERN_PARAM_VALUE_QUOTED) != ByteParser.READ_WORD) {
                            throw new IOException("Unexpected prolog");
                        }

                        value = bp.getWordAsString();
                        bp.resetWord();

                        // consume the '"'
                        if (bp.read(bb, PATTERN_PARAM_VALUE_QUOTED) != '"') {
                            throw new IOException("Unexpected prolog");
                        }
                    } else if (nextChar == ByteParser.READ_WORD) {
                        value = bp.getWordAsString();
                        bp.resetWord();
                    } else {
                        throw new IOException("Unexpected prolog");
                    }

                    nextChar = bp.read(bb, PATTERN_PARAM_VALUE);
                }

                if (name != null)
                    parameters.add(new NameValuePair(name, value));

                if (nextChar == ';') {
                    lastKnownPosition = bb.position();
                    continue;
                }

                // this is the end, step one char back
                bb.position(bb.position() - 1);
                break;
            }
        }

        return createHeader();
    }

    private MimeTypeBasedHeader createHeader() throws IOException {
        if (ContentTypeHeader.NAME.equalsIgnoreCase(headerName) || ContentTypeHeader.NAME_SHORT.equalsIgnoreCase(headerName)) {
            return new ContentTypeHeader(MimeType.create(type, subType, parameters));
        }

        if (AcceptHeader.NAME.equalsIgnoreCase(headerName)) {
            return new AcceptHeader(MimeType.create(type, subType, parameters));
        }
        throw new IOException("Don't know how to create a header for '" + headerName + "'");
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public boolean isListedHeader() {
        if (ContentTypeHeader.NAME.equalsIgnoreCase(headerName) || ContentTypeHeader.NAME_SHORT.equalsIgnoreCase(headerName)) {
            return false;
        } else if (AcceptHeader.NAME.equalsIgnoreCase(headerName)) {
            return true;
        } else {
            throw new IllegalArgumentException("Unsupported header name " + headerName);
        }
    }
}

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class UriParser {

    private static final ByteParser.Pattern PATTERN_URI_SCHEME;
    private static final ByteParser.Pattern PATTERN_URI_PHONENUMBER;
    public static final ByteParser.Pattern PATTERN_URI_PARAMETER;
    public static final ByteParser.Pattern PATTERN_URI_STRING;

    static {
        PATTERN_URI_SCHEME = new ByteParser.Pattern();
        PATTERN_URI_SCHEME.setWordCharacter('s');
        PATTERN_URI_SCHEME.setWordCharacter('i');
        PATTERN_URI_SCHEME.setWordCharacter('p');
        PATTERN_URI_SCHEME.setWordCharacter('t');
        PATTERN_URI_SCHEME.setWordCharacter('e');
        PATTERN_URI_SCHEME.setWordCharacter('l');
        PATTERN_URI_SCHEME.setDelimiterCharacter(':');

        PATTERN_URI_PHONENUMBER = new ByteParser.Pattern();
        PATTERN_URI_PHONENUMBER.setWordCharacter('0', '9');
        PATTERN_URI_PHONENUMBER.setWordCharacter('-');
        PATTERN_URI_PHONENUMBER.setWordCharacter('+'); // todo add more
        PATTERN_URI_PHONENUMBER.setDelimiterCharacter(';'); // start of parameters
        PATTERN_URI_PHONENUMBER.setDelimiterCharacter(' '); // end of entire URI
        PATTERN_URI_PHONENUMBER.setDelimiterCharacter('>'); // end of entire URI
        PATTERN_URI_PHONENUMBER.setDelimiterCharacter('\r'); // end of entire URI

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

        // used for hostname, username & password
        PATTERN_URI_STRING = new ByteParser.Pattern();
        PATTERN_URI_STRING.setWordCharacter('A', 'Z');
        PATTERN_URI_STRING.setWordCharacter('a', 'z');
        PATTERN_URI_STRING.setWordCharacter('0', '9');
        PATTERN_URI_STRING.setWordCharacter('+');
        PATTERN_URI_STRING.setWordCharacter('_');
        PATTERN_URI_STRING.setWordCharacter('%');
        PATTERN_URI_STRING.setWordCharacter('!');
        PATTERN_URI_STRING.setWordCharacter('.'); // todo add more
        PATTERN_URI_STRING.setWordCharacter('-');
        PATTERN_URI_STRING.setDelimiterCharacter('@');
        PATTERN_URI_STRING.setDelimiterCharacter(':');
        PATTERN_URI_STRING.setDelimiterCharacter(';'); // parameters start
        PATTERN_URI_STRING.setDelimiterCharacter(' '); // end of entire URI
        PATTERN_URI_STRING.setDelimiterCharacter('>'); // end of entire URI
        PATTERN_URI_STRING.setDelimiterCharacter('\r'); // end of entire URI
        PATTERN_URI_STRING.setDelimiterCharacter(','); // end of entire URI
    }

    public static enum ParsingState {
        init,
        readingScheme,
        readingHost,
        readingPhonenumber,
        readingParameters
    }

    private ByteParser bp;
    private ParsingState parsingState;
    private int lastBufferPosition;

    private URI.Type scheme;
    private String username, password, host, phonenumber;
    private int port;
    List<NameValuePair> parameters;

    public UriParser() {
        parsingState = ParsingState.readingScheme;
        bp = new ByteParser();
        reset();
    }

    public void reset() {
        parsingState = ParsingState.init;
        bp.reset();
        scheme = null;
        username = null;
        password = null;
        host = null;
        phonenumber = null;
        port = -1;
        parameters = null;
        lastBufferPosition = 0;
    }

    public URI parseMore(ByteBuffer bb) throws IOException {
        boolean finished = false;

        while (!finished) {
            switch (parsingState) {
                case init:
                    lastBufferPosition = bb.position();
                    parsingState = ParsingState.readingScheme;
                    break;
                case readingScheme:
                    bb.position(lastBufferPosition);
                    readScheme(bb);
                    if (scheme == URI.Type.sip) {
                        parsingState = ParsingState.readingHost;
                    } else {
                        parsingState = ParsingState.readingPhonenumber;
                    }
                    break;
                case readingPhonenumber:
                    bb.position(lastBufferPosition);
                    readPhonenumber(bb);
                    if (parameters != null) {
                        parsingState = ParsingState.readingParameters;
                    } else {
                        finished = true;
                    }
                    break;
                case readingParameters:
                    bb.position(lastBufferPosition);
                    readParameters(bb);
                    finished = true;
                    break;
                case readingHost:
                    bb.position(lastBufferPosition);
                    finished = readHostAndPort(bb); // set new parsing state as well
                    break;
            }
        }

        return new URI(
                scheme,
                username,
                password,
                scheme == URI.Type.sip ? host : null,
                port,
                scheme == URI.Type.sip ? null : phonenumber,
                parameters);
    }

    private void readParameters(ByteBuffer bb) throws IOException {
        bp.reset();

        String name, value;
        int nextChar;
        while (true) {
            value = null;

            // try to read something
            nextChar = bp.read(bb, PATTERN_URI_PARAMETER);

            // have we reached the end?
            if (nextChar == ' ' || nextChar == '>') {
                bb.position(bb.position() - 1);
                break;
            }

            // jump over the delimiter
            if (nextChar == ';') {
                continue;
            }

            // a parameter name was read
            if (nextChar == ByteParser.READ_WORD) {
                name = bp.getWordAsString();
                bp.resetWord();
            }

            // something else happend, I don't know what
            else {
                throw new IOException("Unexpected prolog");
            }

            // read something more
            nextChar = bp.read(bb, PATTERN_URI_PARAMETER);

            // aha, a value is about to be parsed
            if (nextChar == '=') {
                // read the parameter value as well
                if (bp.read(bb, PATTERN_URI_PARAMETER) != ByteParser.READ_WORD) {
                    throw new IOException("Unexpected prolog");
                }
                value = bp.getWordAsString();
                bp.resetWord();

                // read something more
                nextChar = bp.read(bb, PATTERN_URI_PARAMETER);
            }

            // add parsed paremeter now
            parameters.add(new NameValuePair(name, value));
            lastBufferPosition = bb.position();

            if (nextChar == ';') {
                continue;
            }

            // this is the end, step one char back
            bb.position(bb.position() - 1);
            break;
        }

    }

    private void readPhonenumber(ByteBuffer bb) throws IOException {
        bp.reset();

        if (bp.read(bb, PATTERN_URI_PHONENUMBER) != ByteParser.READ_WORD) {
            throw new IOException("Unexpected prolog");
        }

        phonenumber = bp.getWordAsString();
        bp.resetWord();

        int nextChar = bp.read(bb, PATTERN_URI_PHONENUMBER);

        if (nextChar == ';') {
            parameters = new LinkedList<NameValuePair>();
        }

        lastBufferPosition = bb.position();
    }

    private boolean readHostAndPort(ByteBuffer bb) throws IOException {
        bp.reset();

        String tmp, tmp2;
        if (bp.read(bb, PATTERN_URI_STRING) != ByteParser.READ_WORD) {
            throw new IOException("Unexpected prolog");
        }
        tmp = bp.getWordAsString();
        bp.resetWord();

        // find out next state
        int nextChar = bp.read(bb, PATTERN_URI_STRING);

        // this turns out to be the username instead, switch to correct parsing state
        if (nextChar == '@') {
            username = tmp;
            parsingState = ParsingState.readingHost;
            lastBufferPosition = bb.position();
            return false;
        }

        // this can either be the start of the password OR the start of the port number
        else if (nextChar == ':') {

            // read next string
            if (bp.read(bb, PATTERN_URI_STRING) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            tmp2 = bp.getWordAsString();
            bp.resetWord();

            nextChar = bp.read(bb, PATTERN_URI_STRING);

            // it was the password!
            if (nextChar == '@') {
                username = tmp;
                password = tmp2;
                parsingState = ParsingState.readingHost;
                lastBufferPosition = bb.position();
                return false;
            }

            // it must have been the port, this also means that we have the hostname in tmp
            else {
                try {
                    port = Integer.parseInt(tmp2);
                } catch (NumberFormatException e) {
                    throw new IOException(e.getMessage());
                }
                host = tmp;

                // do we have parameters after this?
                if (nextChar == ';') {
                    parameters = new LinkedList<NameValuePair>();
                    parsingState = ParsingState.readingParameters;
                    lastBufferPosition = bb.position();
                    return false;
                }

                // no? then we are done
                else {
                    bb.position(bb.position() - 1);
                    return true;
                }
            }

        }

        // this was the hostname, parameters start now
        else if (nextChar == ';') {
            host = tmp;
            parameters = new LinkedList<NameValuePair>();
            parsingState = ParsingState.readingParameters;
            lastBufferPosition = bb.position();
            return false;
        }

        // some other delimiter: this must have been the end of the entire URI
        else {
            host = tmp;
            bb.position(bb.position() - 1);
            return true;
        }
    }

    private void readScheme(ByteBuffer bb) throws IOException {
        bp.reset();

        if (bp.read(bb, PATTERN_URI_SCHEME) != ByteParser.READ_WORD) {
            throw new IOException("Unexpected prolog");
        }

        String scheme = bp.getWordAsString();
        bp.resetWord();

        if ("tel".equals(scheme)) {
            this.scheme = URI.Type.tel;
        } else if ("sip".equals(scheme)) {
            this.scheme = URI.Type.sip;
        } else {
            throw new IOException("Unexpected scheme: " + scheme);
        }

        // jump over the expected ':'
        int c;
        if ((c = bp.read(bb, PATTERN_URI_SCHEME)) != ':') {
            throw new IOException("Unexpected prolog. Expected ':' after scheme part. Found '" + c + "'");
        }

        lastBufferPosition = bb.position();
    }
}

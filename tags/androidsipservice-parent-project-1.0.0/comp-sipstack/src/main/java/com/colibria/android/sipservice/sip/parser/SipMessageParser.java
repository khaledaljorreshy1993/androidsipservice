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
package com.colibria.android.sipservice.sip.parser;

import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.parse.ByteParser;
import com.colibria.android.sipservice.sip.UriParser;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.headers.*;
import com.colibria.android.sipservice.sip.messages.*;
import com.colibria.android.sipservice.sip.parser.header.*;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * @author Sebastian Dehne
 */
public class SipMessageParser {
    private static final String TAG = "SipMessageParser";

    private static enum MessageParseState {
        init,
        readingHeaderName,
        readingHeaderValue,
        readingBody,
        done
    }

    private static enum MessageType {
        unknown,
        request,
        response
    }

    private static final ByteParser.Pattern PATTERN_REQUEST_METHOD;
    private static final ByteParser.Pattern PATTERN_NUMBER;
    private static final ByteParser.Pattern PATTERN_RESPONSE_STRING;
    private static final ByteParser.Pattern PATTERN_HEADER_NAME;

    static {
        PATTERN_REQUEST_METHOD = new ByteParser.Pattern();
        PATTERN_REQUEST_METHOD.setWordCharacter('A', 'Z');
        PATTERN_REQUEST_METHOD.setDelimiterCharacter(' ');

        PATTERN_NUMBER = new ByteParser.Pattern();
        PATTERN_NUMBER.setWordCharacter('0', '9');
        PATTERN_NUMBER.setDelimiterCharacter(' ');

        // used for hostname, username & password
        PATTERN_RESPONSE_STRING = new ByteParser.Pattern();
        PATTERN_RESPONSE_STRING.setWordCharacter('A', 'Z');
        PATTERN_RESPONSE_STRING.setWordCharacter('a', 'z');
        PATTERN_RESPONSE_STRING.setWordCharacter('0', '9');
        PATTERN_RESPONSE_STRING.setWordCharacter('/');
        PATTERN_RESPONSE_STRING.setWordCharacter(' '); // todo add more ?
        PATTERN_RESPONSE_STRING.setDelimiterCharacter('\r');
        PATTERN_RESPONSE_STRING.setDelimiterCharacter('\n');

        PATTERN_HEADER_NAME = new ByteParser.Pattern();
        PATTERN_HEADER_NAME.setWordCharacter('A', 'Z');
        PATTERN_HEADER_NAME.setWordCharacter('a', 'z');
        PATTERN_HEADER_NAME.setWordCharacter('0', '9');
        PATTERN_HEADER_NAME.setWordCharacter('-'); // todo add more?
        PATTERN_HEADER_NAME.setDelimiterCharacter(':');
        PATTERN_HEADER_NAME.setDelimiterCharacter(' ');
    }


    private final Map<String, IHeaderParser<? extends SipHeader>> headerParsers;
    private volatile ParseState parseState;
    private volatile ByteParser bp;

    public SipMessageParser() {
        HashMap<String, IHeaderParser<? extends SipHeader>> tmp = new HashMap<String, IHeaderParser<? extends SipHeader>>();

        tmp.put(AcceptHeader.NAME, new MimeTypeBasedHeaderParser());
        tmp.put(CallIDHeader.NAME, new SimpleHeaderParser());
        tmp.put(CallIDHeader.NAME_SHORT, new SimpleHeaderParser());
        tmp.put(ContactHeader.NAME, new AddressBasedHeaderParser());
        tmp.put(ContactHeader.NAME_SHORT, new AddressBasedHeaderParser());
        tmp.put(ContentTypeHeader.NAME, new MimeTypeBasedHeaderParser());
        tmp.put(ContentTypeHeader.NAME_SHORT, new MimeTypeBasedHeaderParser());
        tmp.put(ContentLengthHeader.NAME, new SimpleHeaderParser());
        tmp.put(ContentLengthHeader.NAME_SHORT, new SimpleHeaderParser());
        tmp.put(CSeqHeader.NAME, new SimpleHeaderParser());
        tmp.put(EventHeader.NAME, new EventHeaderParser());
        tmp.put(EventHeader.NAME_SHORT, new EventHeaderParser());
        tmp.put(ExpiresHeader.NAME, new SimpleHeaderParser());
        tmp.put(MinExpiresHeader.NAME, new SimpleHeaderParser());
        tmp.put(FromHeader.NAME, new AddressBasedHeaderParser());
        tmp.put(FromHeader.NAME_SHORT, new AddressBasedHeaderParser());
        tmp.put(MaxForwardsHeader.NAME, new SimpleHeaderParser());
        tmp.put(MinSeHeader.NAME, new EventHeaderParser());
        tmp.put(RecordRouteHeader.NAME, new AddressBasedHeaderParser());
        tmp.put(RouteHeader.NAME, new AddressBasedHeaderParser());
        tmp.put(ServiceRouteHeader.NAME, new AddressBasedHeaderParser());
        tmp.put(SessionExpiresHeader.NAME, new EventHeaderParser());
        tmp.put(SessionExpiresHeader.NAME_SHORT, new EventHeaderParser());
        tmp.put(SIPETagHeader.NAME, new SimpleHeaderParser());
        tmp.put(SubscriptionStateHeader.NAME, new SubscriptionStateHeaderParser());
        tmp.put(SubscriptionStateHeader.NAME_SHORT, new SubscriptionStateHeaderParser());
        tmp.put(SupportedHeader.NAME, new SupportedHeaderParser());
        tmp.put(SupportedHeader.NAME_SHORT, new SupportedHeaderParser());
        tmp.put(ToHeader.NAME, new AddressBasedHeaderParser());
        tmp.put(ToHeader.NAME_SHORT, new AddressBasedHeaderParser());
        tmp.put(ViaHeader.NAME, new ViaHeaderParser());
        tmp.put(ViaHeader.NAME_SHORT, new ViaHeaderParser());
        tmp.put("*", new SimpleHeaderParser());

        headerParsers = Collections.unmodifiableMap(tmp);


        this.bp = new ByteParser();
        reset();
    }

    /**
     * Called as soon as additional data was received over the TCP connection
     *
     * @param bb the buffer containing the received data
     * @return returns true in case the data was accepted. In case false is received, the connection handler should close the connection
     * @throws java.io.EOFException in case of data is missing to complete the parse process
     * @throws java.io.IOException  in case of invalid/unparsable data was found
     */
    public SipMessage parseMoreBytes(ByteBuffer bb) throws IOException {
        SipMessage result;

        while (parseState.messageParseState != MessageParseState.done) {
            switch (parseState.messageParseState) {
                case init:
                    // check if we received a keep-alive
                    if (bb.remaining() > 0) {
                        int pos = bb.position();
                        int nextChar;
                        if ((nextChar = bb.get()) == '\n') {
                            Logger.d(TAG, "Keep-alive received");
                            return null;
                        } else if (nextChar == '\r' && bb.remaining() > 0 && bb.get() == '\n') {
                            Logger.d(TAG, "Keep-alive received");
                            return null;
                        } else {
                            bb.position(pos);
                        }
                    }
                    readFirstLine(bb);
                    break;
                case readingHeaderName:
                    readHeaderName(bb);
                    break;
                case readingHeaderValue:
                    readHeaderValue(bb);
                    break;
                case readingBody:
                    readBody(bb);
                    break;
                case done:
                    break;
            }
        }


        if (parseState.messageType == MessageType.request) {
            if (Register.NAME.equals(parseState.requestMethod)) {
                result = new Register(parseState.requestURI, parseState.headers, parseState.body);
            } else if (Notify.NAME.equals(parseState.requestMethod)) {
                result = new Notify(parseState.requestURI, parseState.headers, parseState.body);
            } else if (Subscribe.NAME.equals(parseState.requestMethod)) {
                result = new Subscribe(parseState.requestURI, parseState.headers, parseState.body);
            } else if (Publish.NAME.equals(parseState.requestMethod)) {
                result = new Publish(parseState.requestURI, parseState.headers, parseState.body);
            } else if (Invite.NAME.equals(parseState.requestMethod)) {
                result = new Invite(parseState.requestURI, parseState.headers, parseState.body);
            } else if (Bye.NAME.equals(parseState.requestMethod)) {
                result = new Bye(parseState.requestURI, parseState.headers, parseState.body);
            } else if (Message.NAME.equals(parseState.requestMethod)) {
                result = new Message(parseState.requestURI, parseState.headers, parseState.body);
            } else if (Ack.NAME.equals(parseState.requestMethod)) {
                result = new Ack(parseState.requestURI, parseState.headers, parseState.body);
            } else {
                result = new UnknownMethodRequest(parseState.requestMethod, parseState.requestURI, parseState.headers, parseState.body);
            }
        } else {
            result = new Response(parseState.responseCode, parseState.responseMessage, parseState.headers, parseState.body, null);
        }

        if (result != null)
            result.validate();

        return result;
    }

    private void readBody(ByteBuffer bb) throws EOFException {
        if (parseState.bodyStartPos == -1) {
            parseState.bodyStartPos = bb.position();
        } else {
            bb.position(parseState.bodyStartPos);
        }

        int endPos = parseState.bodyStartPos + parseState.expectedBodyLength;

        if (bb.limit() >= endPos) {
            byte[] body = new byte[parseState.expectedBodyLength];
            System.arraycopy(bb.array(), bb.arrayOffset() + parseState.bodyStartPos, body, 0, body.length);
            parseState.body = body;
            bb.position(bb.position() + body.length);
            parseState.lastBufferPosition = bb.position();
            parseState.messageParseState = MessageParseState.done;
        } else {
            throw new EOFException("More data needed");
        }
    }

    private void readHeaderValue(ByteBuffer bb) throws IOException {
        bb.position(parseState.lastBufferPosition);
        bp.reset();

        SipHeader parsedHeader = parseState.currentHeaderParser.parseMoreData(bb);

        boolean listedHeaderFound = false;
        try {
            int i = bb.get();
            if (i == '\r') {
                if (bb.get() != '\n')
                    throw new IOException("Unexpected prolog. Got '\\r\\n' while parsing headers.");
            } else if (i == ',' && parseState.currentHeaderParser.isListedHeader()) {
                listedHeaderFound = true;
            } else if (parseState.currentHeaderParser.isListedHeader()) {
                throw new IOException("Unexpected prolog");
            }
        } catch (BufferOverflowException e) {
            throw new EOFException();
        }

        // first, determine the next state
        boolean endOfHeaderBlockFound = false;
        if (!listedHeaderFound) {
            int tempPosition = bb.position();
            try {
                bp.readConstant(bb, "\r\n".getBytes(), true);
                endOfHeaderBlockFound = true;
            } catch (EOFException e) {
                throw e;
            } catch (IOException e) {
                // ok, this is not the end
                bb.position(tempPosition);
            }
        }
        // done, continue accordingly

        List<SipHeader> headerList = parseState.headers.get(parseState.currentHeaderParser.getHeaderName());
        if (headerList == null) {
            headerList = new LinkedList<SipHeader>();
            parseState.headers.put(parseState.currentHeaderParser.getHeaderName(), headerList);
        }
        headerList.add(parsedHeader);

        parseState.lastBufferPosition = bb.position();

        if (endOfHeaderBlockFound) {
            // do we need to read any body?
            boolean bodyAttached = false;
            for (Map.Entry<String, List<SipHeader>> entry : parseState.headers.entrySet()) {
                if (ContentLengthHeader.NAME.equalsIgnoreCase(entry.getKey())) {
                    ContentLengthHeader h = (ContentLengthHeader) entry.getValue().get(0);
                    bodyAttached = (parseState.expectedBodyLength = h.getContentLength()) > 0;
                    break;
                }
            }

            parseState.messageParseState = bodyAttached ? MessageParseState.readingBody : MessageParseState.done;
        } else if (listedHeaderFound) {
            parseState.currentHeaderParser.reset(parseState.currentHeaderParser.getHeaderName());
            parseState.messageParseState = MessageParseState.readingHeaderValue;
        } else {
            parseState.messageParseState = MessageParseState.readingHeaderName;
        }
    }

    private void readHeaderName(ByteBuffer bb) throws IOException {
        bb.position(parseState.lastBufferPosition);
        bp.reset();

        String headerName;
        if (bp.read(bb, PATTERN_HEADER_NAME) != ByteParser.READ_WORD) {
            throw new IOException("Unexpected token when parsing header name");
        }
        headerName = bp.getWordAsString();
        bp.resetWord();

        // consume the :
        if (bp.read(bb, PATTERN_HEADER_NAME) != ':') {
            throw new IOException("Unexpected token when parsing header name");
        }
        // consume the space
        if (bp.read(bb, PATTERN_HEADER_NAME) != ByteParser.READ_SPACE) {
            throw new IOException("Unexpected token when parsing header name");
        }

        IHeaderParser<? extends SipHeader> headerParser = headerParsers.get(headerName);
        if (headerParser == null) {
            headerParser = headerParsers.get("*");
        }
        headerParser.reset(headerName);
        parseState.currentHeaderParser = headerParser;

        parseState.lastBufferPosition = bb.position();
        parseState.messageParseState = MessageParseState.readingHeaderValue;
    }

    private void readFirstLine(ByteBuffer bb) throws IOException {
        bb.position(parseState.lastBufferPosition);
        bp.reset();

        String tmp = null;
        if (parseState.messageType == MessageType.unknown) {

            // try to treat it as a response first
            boolean isResponse = false;
            try {
                bp.readConstant(bb, "SIP/2.0".getBytes(), true);
                isResponse = true;
            } catch (EOFException e) {
                throw e;
            } catch (IOException e) {
                // ok, it is not a response
                bb.position(parseState.lastBufferPosition);
                bp.reset();
            }

            if (!isResponse) {
                if (bp.read(bb, PATTERN_REQUEST_METHOD) != ByteParser.READ_WORD) {
                    throw new IOException("Unexpected prolog");
                }
                tmp = bp.getWordAsString();
                bp.resetWord();
            }

            // skip one space
            if (bp.read(bb, PATTERN_REQUEST_METHOD) != ByteParser.READ_SPACE) {
                throw new IOException("Unexpected prolog");
            }

            if (isResponse) {
                parseState.messageType = MessageType.response;
            } else {
                parseState.messageType = MessageType.request;
                parseState.requestMethod = tmp;

            }
            parseState.lastBufferPosition = bb.position();
        }

        // need to read the status code now
        if (parseState.messageType == MessageType.response && parseState.responseCode == -1) {

            if (bp.read(bb, PATTERN_NUMBER) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            tmp = bp.getWordAsString();
            bp.resetWord();

            try {
                parseState.responseCode = Integer.parseInt(tmp);
            } catch (NumberFormatException e) {
                throw new IOException(e.getMessage());
            }

            // consume and validate next space
            if (bp.read(bb, PATTERN_NUMBER) != ' ') {
                throw new IOException("Unexpected prolog");
            }

            parseState.lastBufferPosition = bb.position();
        }

        // need to read the status text now
        if (parseState.messageType == MessageType.response) {

            if (bp.read(bb, PATTERN_RESPONSE_STRING) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }

            tmp = bp.getWordAsString();
            bp.resetWord();

            if (bp.read(bb, PATTERN_RESPONSE_STRING) != '\r') {
                throw new IOException("Unexpected prolog");
            }
            if (bp.read(bb, PATTERN_RESPONSE_STRING) != '\n') {
                throw new IOException("Unexpected prolog");
            }

            parseState.responseMessage = tmp;

            parseState.lastBufferPosition = bb.position();
            parseState.messageParseState = MessageParseState.readingHeaderName;
            return;
        }

        // need to read the requestURI now
        if (parseState.messageType == MessageType.request && parseState.requestURI == null) {
            URI uri = parseState.uriParser.parseMore(bb);

            // consume the expected ' '
            try {
                if (bb.get() != ' ') {
                    throw new IOException("Unexpected prolog");
                }
            } catch (BufferUnderflowException e) {
                throw new EOFException();
            }
            parseState.requestURI = uri;
            parseState.uriParser.reset();

            // done parsing, record current state
            parseState.lastBufferPosition = bb.position();
        }

        // read "SIP/2.0" constant now
        if (parseState.messageType == MessageType.request && parseState.requestURI != null && !parseState.requestLineDone) {

            bp.readConstant(bb, "SIP/2.0\r\n".getBytes(), true);

            parseState.lastBufferPosition = bb.position();
            parseState.messageParseState = MessageParseState.readingHeaderName;
            return;
        }

        throw new IOException("Something unexpected happened");

    }

    /**
     * Cleans all state and gets this instance ready for parsing the next data
     */
    public void reset() {
        parseState = new ParseState();
        bp.reset();
    }

    private class ParseState {
        int lastBufferPosition = 0;

        MessageType messageType = MessageType.unknown;

        final HashMap<String, List<SipHeader>> headers = new HashMap<String, List<SipHeader>>();

        int expectedBodyLength = -1;
        int bodyStartPos = -1;
        byte[] body;

        String requestMethod;
        URI requestURI;
        UriParser uriParser = new UriParser();
        String responseMessage;
        int responseCode = -1;
        boolean requestLineDone = false;

        IHeaderParser<? extends SipHeader> currentHeaderParser;

        MessageParseState messageParseState = MessageParseState.init;
    }
}

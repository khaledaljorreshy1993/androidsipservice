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
package com.colibria.android.sipservice.io;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.headers.*;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.parse.ByteParser;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import static com.colibria.android.sipservice.parse.ByteParser.*;

/**
 * Msrp parser
 * <p/>
 * This class keeps state and is not thread-safe. Either wrap with a lock or make sure
 * that an instance of this class is tight to one thread only.
 *
 * @author Sebastian Dehne
 */
public class MsrpParser {
    private static final String TAG = "MsrpParser";

    private static final Pattern PATTERN_PROLOG_TRANSACTION;
    private static final Pattern PATTERN_PROLOG_METHOD;
    private static final Pattern PATTERN_STATUS;
    private static final Pattern PATTERN_PROLOG_REASON;
    private static final Pattern PATTERN_HEADER_NAME;
    private static final Pattern PATTERN_HEADER_VALUE;
    private static final Pattern PATTERN_CONTINUATION;

    static {
        PATTERN_PROLOG_TRANSACTION = new Pattern();
        PATTERN_PROLOG_TRANSACTION.setWordCharacter('a', 'z');
        PATTERN_PROLOG_TRANSACTION.setWordCharacter('A', 'Z');
        PATTERN_PROLOG_TRANSACTION.setWordCharacter('0', '9');
        PATTERN_PROLOG_TRANSACTION.setWordCharacter('-');
        PATTERN_PROLOG_TRANSACTION.setWordCharacter('.');
        PATTERN_PROLOG_TRANSACTION.setWordCharacter('+');
        PATTERN_PROLOG_TRANSACTION.setWordCharacter('%');
        PATTERN_PROLOG_TRANSACTION.setWordCharacter(61);
        PATTERN_PROLOG_TRANSACTION.setDelimiterCharacter(' ');

        PATTERN_PROLOG_METHOD = new Pattern();
        PATTERN_PROLOG_METHOD.setWordCharacter('0', '9');
        PATTERN_PROLOG_METHOD.setWordCharacter('A', 'Z');
        PATTERN_PROLOG_METHOD.setDelimiterCharacter(' ');

        PATTERN_STATUS = new Pattern();
        PATTERN_STATUS.setWordCharacter('0', '9');
        PATTERN_STATUS.setDelimiterCharacter(' ');

        PATTERN_PROLOG_REASON = new Pattern();
        PATTERN_PROLOG_REASON.setWordCharacter('0', '9');
        PATTERN_PROLOG_REASON.setWordCharacter('a', 'z');
        PATTERN_PROLOG_REASON.setWordCharacter('A', 'Z');
        PATTERN_PROLOG_REASON.setWordCharacter(0x20);
        PATTERN_PROLOG_REASON.setWordCharacter(0x09);

        PATTERN_HEADER_NAME = new Pattern();
        PATTERN_HEADER_NAME.setWordCharacter('a', 'z');
        PATTERN_HEADER_NAME.setWordCharacter('A', 'Z');
        PATTERN_HEADER_NAME.setWordCharacter('-');
        PATTERN_HEADER_NAME.setWordCharacter('0', '9');
        PATTERN_HEADER_NAME.setWordCharacter('.');
        PATTERN_HEADER_NAME.setWordCharacter('+');
        PATTERN_HEADER_NAME.setWordCharacter('#');
        PATTERN_HEADER_NAME.setWordCharacter('=');
        PATTERN_HEADER_NAME.setWordCharacter('$');
        PATTERN_HEADER_NAME.setDelimiterCharacter(' ');
        PATTERN_HEADER_NAME.setDelimiterCharacter(':');

        PATTERN_HEADER_VALUE = new Pattern();
        PATTERN_HEADER_VALUE.setWordCharacter(0, 255);
        PATTERN_HEADER_VALUE.allowUTF8WordCharacters();

        PATTERN_CONTINUATION = new Pattern();
        PATTERN_CONTINUATION.setDelimiterCharacter('+');
        PATTERN_CONTINUATION.setDelimiterCharacter('#');
        PATTERN_CONTINUATION.setDelimiterCharacter('$');
    }

    public static final String MSRP_HEADER_TOPATH = "To-Path";
    public static final String MSRP_HEADER_FROMPATH = "From-Path";
    public static final String MSRP_HEADER_MESSAGE_ID = "Message-ID";
    public static final String MSRP_HEADER_BYTE_RANGE = "Byte-Range";
    public static final String MSRP_HEADER_CONTENT_TYPE = "Content-Type";
    public static final String MSRP_HEADER_REPORT_SUCC = "Success-Report";
    public static final String MSRP_HEADER_REPORT_FAIL = "Failure-Report";

    private static final String CRLF = "\r\n";
    private static final String END_LINE_PREAMBLE = "-------";
    private static final byte[] MSRP = "MSRP ".getBytes();


    /**
     * The external parse state
     */
    public static enum State {
        /**
         * Set when the parser needs more data in order to produce a
         * usable message object
         */
        needMoreData,

        /**
         * Set when the parser detects that the client sent
         * data which doesn't fit into the msrp protocol.
         * The connection should be terminated.
         * No usable message object will be prepared
         */
        cannotProceed,

        /**
         * Returned by the parser in case only a piece of a chunk
         * could be parsed from the applied buffer.
         * In that case, this parser instance must be used for the nest
         * parse iteration in order to complete parsing
         * this chunk.
         * When this state is returned, then a message is available
         * via getParseMessage().
         */
        chunk_piece_parsed,

        /**
         * Set when the parser has completed parsing one msrp message
         * and the next filter in the chain should be invoked
         * (in this state, the buffer might still conain more data which will
         * be parsed next time)
         */
        done
    }

    private ParseState parseState;
    private ByteParser st;
    private IMsrpMessage parsedMessage;

    public MsrpParser() {
        parseState = new ParseState();
        st = new ByteParser();
    }

    /**
     * Tries unmarhsall a stream into a IMsrpMessage, based on the data present bb.
     * <p/>
     * After invoking this method, the applied byteBuffer's position will
     * equal the it's limit.
     * <p/>
     * Should you wish to compact() or clear() the buffer (or change
     * the content in another way), you MUST make sure that all bytes
     * after the position getStoppedAtPosition() are saved and re-presented
     * to the parser at next iteration.
     * After calling compact() or clear(), the parser must be notified via
     * the resetBufferPosition() method.
     *
     * @param bb the buffer containing the stream
     * @return the state in which the parser is in when it reached the end of the applied stream
     */
    public State parse(ByteBuffer bb) {

        parsedMessage = null;
        boolean wasReadingContentAtBeginning = parseState.getState() == ParseState.State.readingBody;

        boolean eofFound = false;

        try {

            switch (parseState.getState()) {
                case init:
                    parseState.setChunkStartPos(bb.position());
                    parseState.setState(ParseState.State.readingRequestLine);
                    parseState.setLastBufferPosition(bb.position());
                case readingRequestLine:
                    readRequestLine(bb);
                    parseState.setState(ParseState.State.readingHeaders);
                case readingHeaders:
                    readHeaders(bb);

                    validateHeaders();

                    /*
                     * Determine whether we should expect CPIM
                     */
                    boolean expectCPIM = false;
                    if (parseState.getContentType() != null
                            && MimeType.MESSAGE_CPIM.match(parseState.getContentType()) == MimeType.MATCH_SPECIFIC_SUBTYPE) {

                        /*
                         * Now we know that the content-Type is message/CPIM.
                         *
                         * Since sub-sequent chunks within a message might repeat the content-type
                         * message/CPIM but without actually adding cpim in each chunk,
                         * we only try to parse cpim at the first message.
                         * But when is a chunk a 'first' chunk? According to
                         * rfc4975, a first chunk SHOULD have a Byte-Range
                         * header while all sub-sequent chunks MUST have it.
                         */
                        if (parseState.getByteRange() == null || parseState.getByteRange().getStart() == 1) {
                            expectCPIM = true;
                        }
                    }

                    // the msrp body starts here
                    parseState.setMsrpBodyStartPos(parseState.getLastBufferPosition());

                    if (expectCPIM) {
                        parseState.setState(ParseState.State.readingCPIMHeaders);
                    } else {
                        parseState.setState(ParseState.State.readingBody);
                    }
                case readingCPIMHeaders:
                    if (parseState.getState() == ParseState.State.readingCPIMHeaders) {
                        readCPIMHeaders(bb);
                        parseState.setState(ParseState.State.readingCPIMMimeContent);
                    }
                case readingCPIMMimeContent:
                    if (parseState.getState() == ParseState.State.readingCPIMMimeContent) {
                        readCPIMMimeContent(bb);

                        parseState.setCpimBlockLength(parseState.getLastBufferPosition() - parseState.getMsrpBodyStartPos());

                        parseState.setState(ParseState.State.readingBody);
                        parseState.setLastBufferPosition(parseState.getMsrpBodyStartPos()); // go back to were CPIM started, since cpim is part of the body
                    }
                case readingBody:
                    bb.position(parseState.getLastBufferPosition());
                    if (wasReadingContentAtBeginning && isInterruptable()) {
                        parseState.setChunkStartPos(bb.position());
                        parseState.setMsrpBodyStartPos(parseState.getChunkStartPos());
                    }
                    readContent(bb);
                    parseState.setState(ParseState.State.done);
                case done:

                    validateContent();

                    switch (parseState.getType()) {
                        case report:
                            parsedMessage = new MsrpReportRequest(
                                    parseState.getTransactionID(),
                                    parseState.getMessageID(),
                                    parseState.getToPath(),
                                    parseState.getFromPath(),
                                    parseState.getByteRange(),
                                    parseState.getStatusHeader(),
                                    parseState.getExHeaders(),
                                    bb.position() - parseState.getChunkStartPos()
                            );
                            break;
                        case response:
                            parsedMessage = new MsrpResponse(
                                    parseState.getTransactionID(),
                                    parseState.getStatusCode(),
                                    parseState.getStatusString(),
                                    parseState.getToPath(),
                                    parseState.getFromPath(),
                                    bb.position() - parseState.getChunkStartPos()
                            );
                            break;
                        case request:
                            parsedMessage = new MsrpSendRequest(
                                    parseState.isWorkingOnFirstChunkPiece() ? MsrpSendRequest.ChunkType.complete : MsrpSendRequest.ChunkType.tail,
                                    parseState.getTransactionID(),
                                    parseState.getMessageID(),
                                    parseState.getByteRange(),
                                    parseState.getContinuation(),
                                    parseState.getContentType(),
                                    parseState.getContentDispositionHeader(),
                                    parseState.getToPath(),
                                    parseState.getFromPath(),
                                    parseState.getSuccessReportHeader(),
                                    parseState.getFailureReportHeader(),
                                    parseState.getCpimHeaders(),
                                    parseState.getExHeaders(),
                                    parseState.getBody(),
                                    bb.position() - parseState.getChunkStartPos(),
                                    parseState.getMessageContentStartAt());
                            break;
                    }
            }


        } catch (EOFException tmp) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "End-Of-file detected. leaving parsing now and waiting for more bytes (but keeping state)");
            }
            st.reset();
            eofFound = true;

        } catch (IOException tmp) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "", tmp);
            }
            parseState.setState(ParseState.State.error);
        }

        if (eofFound) {
            /*
             * EOF was reached, but we have enough info to create a chunk piece anyway
             */
            try {
                if (parseState.getState() == ParseState.State.readingBody && isInterruptable() && parseState.getBody() != null) {

                    validateContent();

                    if (!wasReadingContentAtBeginning) {
                        parsedMessage = new MsrpSendRequest(
                                MsrpSendRequest.ChunkType.head,
                                parseState.getTransactionID(),
                                parseState.getMessageID(),
                                parseState.getByteRange(),
                                parseState.getContinuation(),
                                parseState.getContentType(),
                                parseState.getContentDispositionHeader(),
                                parseState.getToPath(),
                                parseState.getFromPath(),
                                parseState.getSuccessReportHeader(),
                                parseState.getFailureReportHeader(),
                                parseState.getCpimHeaders(),
                                parseState.getExHeaders(),
                                parseState.getBody(),
                                parseState.getLastBufferPosition() - parseState.getChunkStartPos(),
                                parseState.getMessageContentStartAt());
                    } else if (parseState.getBody() != null) {
                        parsedMessage = new MsrpSendRequest(
                                MsrpSendRequest.ChunkType.body_only,
                                parseState.getTransactionID(),
                                parseState.getMessageID(),
                                parseState.getByteRange(),
                                parseState.getContinuation(),
                                parseState.getContentType(),
                                parseState.getContentDispositionHeader(),
                                parseState.getToPath(),
                                parseState.getFromPath(),
                                parseState.getSuccessReportHeader(),
                                parseState.getFailureReportHeader(),
                                parseState.getCpimHeaders(),
                                parseState.getExHeaders(),
                                parseState.getBody(),
                                parseState.getLastBufferPosition() - parseState.getChunkStartPos(),
                                parseState.getMessageContentStartAt());
                    }

                    parseState.setBody(null, 0);
                    parseState.forgetCpimHeaders();
                    parseState.setMsrpBodyStartPos(0);
                    parseState.setWorkingOnFirstChunkPiece(false); // first chunk piece is now created, sub-seqent onces can never be a complete piece anymore

                }
            } catch (IOException e) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "", e);
                }
                parseState.setState(ParseState.State.error);
            }
        }

        return calculateState();
    }

    public IMsrpMessage getParsedMessage() {
        return parsedMessage;
    }

    private boolean isInterruptable() {
        // response has no body and report's body is not interruptable, so only sendRequest
        return (parseState.getType() == IMsrpMessage.Type.request && parseState.getByteRange() != null && parseState.getByteRange().getEnd() < 0);
    }

    private State calculateState() {
        switch (parseState.getState()) {
            case error:
                return State.cannotProceed;
            case done:
                return State.done;
            default:
                return parsedMessage != null ? State.chunk_piece_parsed : State.needMoreData;
        }
    }

    /**
     * Parses the content of the applied byteBuffer. This method also
     * keep state such that in case of an EOFException is throws, that
     * parsing can continue where left off as soon as more bytes are received
     *
     * @param bb the buffer containing the stream
     * @throws IOException  in case the stream doesn't look like valid msrp
     * @throws EOFException in case end of the buffer was reached
     */
    private void readRequestLine(ByteBuffer bb) throws IOException {

        // continue there where we left off
        bb.position(parseState.getLastBufferPosition());

        // read the transactionID if not already done
        if (parseState.getTransactionID() == null) {
            String tmp;

            // always starting with MSRP
            st.readConstant(bb, MSRP, true);

            // read the transaction ID
            if (st.read(bb, PATTERN_PROLOG_TRANSACTION) != READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            tmp = st.getWordAsString();
            st.resetWord();

            // skip one space
            if (st.read(bb, PATTERN_PROLOG_TRANSACTION) != READ_SPACE) {
                throw new IOException("Unexpected prolog");
            }

            parseState.setTransactionID(tmp);
            parseState.setLastBufferPosition(bb.position());
        }

        // figure out the message-type, if not already done
        if (parseState.getType() == null) {

            // try to read the status code (number). If this fails, then this must be a request
            if (st.read(bb, PATTERN_PROLOG_METHOD) != READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            int statusCode = st.getWordAsInt(-1);

            // this is a response
            if (statusCode > 0) {
                final String reason;
                st.resetWord();

                // try to read the reason string as well, if there is one
                int token = st.read(bb, PATTERN_PROLOG_REASON);
                if (token == READ_SPACE) {
                    if (st.read(bb, PATTERN_PROLOG_REASON) != READ_WORD) {
                        throw new IOException("Unexpected prolog");
                    }
                    reason = st.getWordAsString();
                    st.resetWord();
                    token = st.read(bb, PATTERN_PROLOG_REASON);
                } else {
                    reason = null;
                }

                // test that we have reached eol (crlf)
                if (token != READ_EOL) {
                    throw new IOException("Unexpected prolog");
                }

                parseState.setType(IMsrpMessage.Type.response);
                parseState.setStatusString(reason);
                parseState.setStatusCode(statusCode);
                parseState.setLastBufferPosition(bb.position());
            }

            // this is a request
            else {
                if (st.compareWord("SEND")) {
                    st.resetWord();

                    // test that we've reached the EOL (crlf)
                    if (st.read(bb, PATTERN_PROLOG_METHOD) != READ_EOL) {
                        throw new IOException("Unexpected prolog");
                    }

                    parseState.setType(IMsrpMessage.Type.request);
                    parseState.setRequestString("SEND");
                    parseState.setLastBufferPosition(bb.position());
                } else if (st.compareWord("REPORT")) {
                    st.resetWord();

                    // test that we've reached the EOL (crlf)
                    if (st.read(bb, PATTERN_PROLOG_METHOD) != READ_EOL) {
                        throw new IOException("Unexpected prolog");
                    }

                    parseState.setType(IMsrpMessage.Type.report);
                    parseState.setRequestString("REPORT");
                    parseState.setLastBufferPosition(bb.position());
                } else {
                    throw new IOException("Unexpected prolog");
                }
            }
        }
    }

    private void readHeaders(ByteBuffer bb) throws IOException {

        // continue there where we left off
        bb.position(parseState.getLastBufferPosition());

        String headerName, headerValue;
        int i;
        while ((i = st.read(bb, PATTERN_HEADER_NAME)) == READ_WORD) {

            headerName = st.getWordAsString();
            st.resetWord();

            // Test whether we've hit the bottum line yet
            if (headerName.startsWith(END_LINE_PREAMBLE)) {
                parseState.setLastBufferPosition(parseState.getLastBufferPosition() - 2); // move back to last known pos min the last crlf
                return;
            }

            if (st.read(bb, PATTERN_HEADER_NAME) != ':') {
                throw new IOException("Invalid header");
            }
            if (st.read(bb, PATTERN_HEADER_NAME) != READ_SPACE) {
                throw new IOException("Invalid header");
            }

            if (st.read(bb, PATTERN_HEADER_VALUE) != READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            headerValue = st.getWordAsString();
            st.resetWord();

            // test that we've reached the EOL (crlf)
            if (st.read(bb, PATTERN_HEADER_VALUE) != READ_EOL) {
                throw new IOException("Unexpected prolog");
            }

            if (MSRP_HEADER_FROMPATH.equals(headerName)) {
                parseState.setFromPath(MsrpPath.parseValue(headerValue));
            } else if (MSRP_HEADER_TOPATH.equals(headerName)) {
                parseState.setToPath(MsrpPath.parseValue(headerValue));
            } else if (MSRP_HEADER_MESSAGE_ID.equals(headerName)) {
                parseState.setMessageID(headerValue);
            } else if (MSRP_HEADER_BYTE_RANGE.equals(headerName)) {
                parseState.setByteRange(ByteRange.parse(headerValue));
            } else if (MSRP_HEADER_CONTENT_TYPE.equals(headerName)) {
                parseState.setContentType(MimeType.parse(headerValue));
            } else if (ContentDispositionHeader.NAME.equals(headerName)) {
                parseState.setContentDispositionHeader(ContentDispositionHeader.parse(headerValue, false));
            } else if (MSRP_HEADER_REPORT_SUCC.equals(headerName)) {
                parseState.setSuccessReportHeader(new SuccessReportHeader(headerValue));
            } else if (MSRP_HEADER_REPORT_FAIL.equals(headerName)) {
                parseState.setFailureReportHeader(new FailureReportHeader(headerValue));
            } else if (StatusHeader.NAME.equals(headerName)) {
                parseState.setStatusHeader(StatusHeader.parse(headerValue));
            } else {
                // note: matching above was case-sensitive; thus it might be that some headers end up as "extention header"
                parseState.addExHeader(headerName, headerValue);
            }

            parseState.setLastBufferPosition(bb.position());

        }

        // test that crlf which splits the header-block from the body is present
        if (i != READ_EOL) {
            throw new IOException("Missing crlf after headers-end");
        }

        parseState.setLastBufferPosition(bb.position());
    }

    private void readCPIMHeaders(ByteBuffer bb) throws IOException {

        // continue there where we left off
        bb.position(parseState.getLastBufferPosition());

        String headerName, headerValue;
        int i;
        while ((i = st.read(bb, PATTERN_HEADER_NAME)) == READ_WORD) {
            headerName = st.getWordAsString();
            st.resetWord();

            if (st.read(bb, PATTERN_HEADER_NAME) != ':') {
                throw new IOException("Invalid header");
            }
            if (st.read(bb, PATTERN_HEADER_NAME) != READ_SPACE) {
                throw new IOException("Invalid header");
            }

            if (st.read(bb, PATTERN_HEADER_VALUE) != READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            headerValue = st.getWordAsString();
            st.resetWord();

            // test that we've reached the EOL (crlf)
            if (st.read(bb, PATTERN_HEADER_VALUE) != READ_EOL) {
                throw new IOException("Unexpected prolog");
            }

            if (CPIMSubjectHeader.NAME.equals(headerName)) {
                parseState.addCPIMHeader(CPIMSubjectHeader.parse(headerValue));
            } else if (CPIMToHeader.NAME.equals(headerName)) {
                parseState.addCPIMHeader(CPIMToHeader.parse(headerValue));
            } else if (CPIMFromHeader.NAME.equals(headerName)) {
                parseState.addCPIMHeader(CPIMFromHeader.parse(headerValue));
            }

            /*else if (CPIM_HEADER_CC.equals(headerName)) {
                // todo
            } else if (CPIM_HEADER_DATETIME.equals(headerName)) {
                // todo
            } else if (CPIM_HEADER_NS.equals(headerName)) {
                // todo
            } else if (CPIM_HEADER_REQUIRE.equals(headerName)) {
                // todo
            }*/

            else {
                parseState.addCPIMHeader(CPIMExtentionHeader.parse(headerName, headerValue, false));
            }

            parseState.setLastBufferPosition(bb.position());
        }

        // test that crlf which splits the header-block from the body is present
        if (i != READ_EOL) {
            throw new IOException("Missing crlf after headers-end");
        }
        parseState.setLastBufferPosition(bb.position());

    }

    private void readCPIMMimeContent(ByteBuffer bb) throws IOException {

        // continue there where we left off
        bb.position(parseState.getLastBufferPosition());

        String headerName, headerValue;
        int i;
        while ((i = st.read(bb, PATTERN_HEADER_NAME)) == READ_WORD) {
            headerName = st.getWordAsString();
            st.resetWord();

            if (st.read(bb, PATTERN_HEADER_NAME) != ':') {
                throw new IOException("Invalid header");
            }
            if (st.read(bb, PATTERN_HEADER_NAME) != READ_SPACE) {
                throw new IOException("Invalid header");
            }

            if (st.read(bb, PATTERN_HEADER_VALUE) != READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            headerValue = st.getWordAsString();
            st.resetWord();

            // test that we've reached the EOL (crlf)
            if (st.read(bb, PATTERN_HEADER_VALUE) != READ_EOL) {
                throw new IOException("Unexpected prolog");
            }

            if (MimeContentTypeHeader.NAME.equals(headerName)) {
                parseState.addCPIMHeader(MimeContentTypeHeader.parse(headerValue));
            } else if (ContentDispositionHeader.NAME.equals(headerName)) {
                parseState.addCPIMHeader(ContentDispositionHeader.parse(headerValue, true));
            } else {
                parseState.addCPIMHeader(CPIMExtentionHeader.parse(headerName, headerValue, true));
            }

            parseState.setLastBufferPosition(bb.position());
        }

        // test that crlf which splits the header-block from the body is present
        if (i != READ_EOL) {
            throw new IOException("Missing crlf after headers-end");
        }
        parseState.setLastBufferPosition(bb.position());

    }


    private void readContent(ByteBuffer bb) throws IOException {

        final byte[] magicSequence = (CRLF + END_LINE_PREAMBLE + parseState.getTransactionID() + '?' + CRLF).getBytes();
        int contMarkPos = magicSequence.length - 3;
        byte r;
        boolean done = false;
        int matchingIx = 0, continuationFlag = 0;
        final int startPosition = bb.position();

        do {
            try {
                r = bb.get();
            } catch (BufferUnderflowException e) {
                break;
            }

            // we are currently trying to parse the end-line
            if (matchingIx > 0) {
                if (magicSequence[matchingIx] == r || matchingIx == contMarkPos) {
                    if (matchingIx == contMarkPos) {
                        continuationFlag = r;
                    }
                    matchingIx++;
                    if (matchingIx == magicSequence.length) {
                        done = true;
                    }
                } else {
                    // had match, but no more, copy those skipped so far
                    parseState.setLastBufferPosition(bb.position() - 1);

                    if (r == magicSequence[0]) {
                        // last read was matching first byte of magic sequence, so
                        // set index to 1 and continue matching from here on
                        matchingIx = 1;
                    } else {
                        // not matching first byte of magic sequence, so copy last read too
                        matchingIx = 0;
                        parseState.setLastBufferPosition(bb.position());
                    }
                }

            }

            // not currently parsing the end-line
            else {
                if (r == magicSequence[0] && (bb.position() - startPosition) > parseState.getCpimBlockLength()) {
                    matchingIx = 1; // start to parse the end-line
                } else {
                    parseState.setLastBufferPosition(bb.position()); // save r
                }
            }

        } while (!done);

        // parsed until end of chunk
        if (done) {

            // set the body
            final int len = parseState.getLastBufferPosition() - parseState.getMsrpBodyStartPos();
            if (len > 0) {
                byte[] content = new byte[len];
                System.arraycopy(bb.array(), parseState.getMsrpBodyStartPos() + bb.arrayOffset(), content, 0, len);
                parseState.setBody(content, parseState.getCpimBlockLength());
                parseState.setCpimBlockLength(0);
            }

            // set the continuation flag
            switch (continuationFlag) {
                case '+':
                    parseState.setContinuation(Continuation.more);
                    break;
                case '$':
                    parseState.setContinuation(Continuation.done);
                    break;
                case '#':
                    parseState.setContinuation(Continuation.aborted);
                    break;
                default:
                    throw new IOException("bad end line");
            }

            // put the position to the start of the (potential) next message in the stream
            parseState.setLastBufferPosition(bb.position());
        }

        // could not find end of chunk in this buffer, due to EOF
        else {
            if (isInterruptable()) {
                // set the body parsed so far
                final int len = parseState.getLastBufferPosition() - parseState.getMsrpBodyStartPos();
                byte[] content = new byte[len];
                System.arraycopy(bb.array(), parseState.getMsrpBodyStartPos() + bb.arrayOffset(), content, 0, len);
                parseState.setBody(content, parseState.getCpimBlockLength());
                parseState.setCpimBlockLength(0);
            } else {
                parseState.setLastBufferPosition(parseState.getMsrpBodyStartPos()); // go back to the beginning of the body such that our later compact() operation doesn't delete any bytes
            }
        }

        if (!done) {
            throw new EOFException();
        }
    }

    public void reset() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }
        parseState = new ParseState();
        parsedMessage = null;
        st.reset();
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "leave");
        }
    }

    /**
     * Notifies the parser that the buffer has been modified such that it
     * must resume parsing at the next iteration at position 0.
     */
    public void resetBufferPosition() {
        parseState.setLastBufferPosition(0);
        parseState.setMsrpBodyStartPos(0);
    }

    public int getStoppedAtPosition() {
        return parseState.getLastBufferPosition();
    }


    private void validateHeaders() throws IOException {
        if (parseState.getType() == IMsrpMessage.Type.request) {

            /*
             * Each chunk of a message MUST contain a Message-ID header field containing the Message-ID.
             */
            if (parseState.getMessageID() == null) {
                throw new IOException("Chunk didn't contain a Message-ID");
            }

            /*
             * check that we have a to header
             */
            if (parseState.getToPath() == null) {
                throw new IOException("Chunk didn't contain a To-path header");
            }

            /*
             * check that we have a from header
             */
            if (parseState.getFromPath() == null) {
                throw new IOException("Chunk didn't contain a From-path header");
            }

            /*
             * The first chunk of the message SHOULD, and all subsequent chunks MUST, include a Byte-Range header field.
             */
            if (parseState.getByteRange() == null) {
                // we don't know whether this is the first chunk or not. We have to assume that this is the first one
                // add the byteRange header since the rest of the app assumes that it is there
                parseState.setByteRange(ByteRange.create(1, -1, -1));
            }

            /*
            * We don't support msrp-path with more than one msrpURI
            */
            if (parseState.getToPath().getURIs().size() != 1) {
                throw new IOException("This implementation supports only one msrpURI in the To-path. " + parseState.getToPath().getURIs().size() + " were found");
            }

            /*
             * We don't support msrp-path with more than one msrpURI
             */
            if (parseState.getFromPath().getURIs().size() != 1) {
                throw new IOException("This implementation supports only one msrpURI in the From-path. " + parseState.getFromPath().getURIs().size() + " were found");
            }

        } else if (parseState.getType() == IMsrpMessage.Type.response) {
            /*
             * check that we have a to header
             */
            if (parseState.getToPath() == null) {
                throw new IOException("Response didn't contain a To-path header");
            }

            /*
             * check that we have a from header
             */
            if (parseState.getFromPath() == null) {
                throw new IOException("Response didn't contain a From-path header");
            }

            /*
            * We don't support msrp-path with more than one msrpURI
            */
            if (parseState.getToPath().getURIs().size() != 1) {
                throw new IOException("This implementation supports only one msrpURI in the To-path. " + parseState.getToPath().getURIs().size() + " were found");
            }

            /*
             * We don't support msrp-path with more than one msrpURI
             */
            if (parseState.getFromPath().getURIs().size() != 1) {
                throw new IOException("This implementation supports only one msrpURI in the From-path. " + parseState.getFromPath().getURIs().size() + " were found");
            }

        }

        // report
        else {
            /*
             * check that we have a to header
             */
            if (parseState.getToPath() == null) {
                throw new IOException("Response didn't contain a To-path header");
            }

            /*
             * check that we have a from header
             */
            if (parseState.getFromPath() == null) {
                throw new IOException("Response didn't contain a From-path header");
            }

            /*
             * A report must contain a Status header
             */
            if (parseState.getStatusHeader() == null) {
                throw new IOException("Report didn't contain the required Status header");
            }

            /*
             * A report MUST NOT contain Success-Report header
             */
            if (parseState.getSuccessReportHeader() != null) {
                throw new IOException("Report is not allowed to contain a Success-Report header");
            }

            /*
             * A report MUST NOT contain Failure-Report header
             */
            if (parseState.getFailureReportHeader() != null) {
                throw new IOException("Report is not allowed to contain a Failure-Report header");
            }

            /*
            * We don't support msrp-path with more than one msrpURI
            */
            if (parseState.getToPath().getURIs().size() != 1) {
                throw new IOException("This implementation supports only one msrpURI in the To-path. " + parseState.getToPath().getURIs().size() + " were found");
            }

            /*
             * We don't support msrp-path with more than one msrpURI
             */
            if (parseState.getFromPath().getURIs().size() != 1) {
                throw new IOException("This implementation supports only one msrpURI in the From-path. " + parseState.getFromPath().getURIs().size() + " were found");
            }
        }
    }

    private void validateContent() throws IOException {
        /*
         * If the request has a body, it MUST contain a Content-Type header field.
         */
        if (parseState.getBody() != null && parseState.getContentType() == null) {
            throw new IOException("Body found but no Content-Type header");
        }

        /*
         * If cpim was used, there must be a Content-Type header there as well
         */
        if (parseState.getCpimHeaders() != null && !parseState.isCPIMHeaderSet(MimeContentTypeHeader.NAME)) {
            throw new IOException("cpim found but no cpim Content-Type header");
        }

    }

}
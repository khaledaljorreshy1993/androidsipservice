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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This class is not thread safe
 *
 * @author Sebastian Dehne
 */
public class ParseState {

    public static enum State {

        /**
         * When reading the first bytes "MSRP "
         */
        init,

        /**
         * Set when reading request line
         */
        readingRequestLine,

        /**
         * When reading the headers
         */
        readingHeaders,

        /**
         * When reading the cpim headers
         */
        readingCPIMHeaders,

        /**
         * When reading the mime content headers, like Content-Type
         */
        readingCPIMMimeContent,

        /**
         * When reading the body
         */
        readingBody,

        /**
         * When reading end line
         */
        readingEndLine,

        /**
         * When the complete chunk has been read
         */
        done,

        /*
         * Set when an error occured
         */
        error
    }

    // parse state
    private State state;
    private int lastBufferPosition;

    // general msrp message
    private String transactionID;
    private MsrpPath toPath;
    private MsrpPath fromPath;
    private Continuation continuation;

    // positions relative to the read buffer
    private int chunkStartPos = 0;
    private int msrpBodyStartPos;

    private byte[] body;
    private int messageContentStartAt; // position in the byte[] body buffer


    private IMsrpMessage.Type type;
    // request specific stufff
    private String requestString;
    private String messageID;
    private ByteRange byteRange;
    private SuccessReportHeader successReportHeader;
    private FailureReportHeader failureReportHeader;
    private MimeType contentType;
    private ContentDispositionHeader contentDispositionHeader;
    private StatusHeader statusHeader;
    private Map<String, String> exHeaders; // extentionHeaders <HeaderName>:<Value>
    // response specific stuff
    private int statusCode;
    private String statusString;
    // cpim stuff
    private Map<String, List<ICPIMHeader>> cpimHeaders;
    private boolean workingOnFirstChunkPiece;
    private int cpimBlockLength;

    public ParseState() {
        state = State.init;
        lastBufferPosition = 0;
        workingOnFirstChunkPiece = true;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getTransactionID() {
        return transactionID;
    }

    public void setTransactionID(String transactionID) {
        this.transactionID = transactionID;
    }

    public SuccessReportHeader getSuccessReportHeader() {
        return successReportHeader;
    }

    public void setSuccessReportHeader(SuccessReportHeader successReportHeader) {
        this.successReportHeader = successReportHeader;
    }

    public FailureReportHeader getFailureReportHeader() {
        return failureReportHeader;
    }

    public void setFailureReportHeader(FailureReportHeader failureReportHeader) {
        this.failureReportHeader = failureReportHeader;
    }

    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    public MsrpPath getToPath() {
        return toPath;
    }

    public void setToPath(MsrpPath toPath) {
        this.toPath = toPath;
    }

    public MsrpPath getFromPath() {
        return fromPath;
    }

    public void setFromPath(MsrpPath fromPath) {
        this.fromPath = fromPath;
    }

    public ByteRange getByteRange() {
        return byteRange;
    }

    public void setByteRange(ByteRange byteRange) {
        this.byteRange = byteRange;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusString() {
        return statusString;
    }

    public void setStatusString(String statusString) {
        this.statusString = statusString;
    }

    public Continuation getContinuation() {
        return continuation;
    }

    public void setContinuation(Continuation continuation) {
        this.continuation = continuation;
    }

    public MimeType getContentType() {
        return contentType;
    }

    public void setContentType(MimeType contentType) {
        this.contentType = contentType;
    }

    public Map<String, String> getExHeaders() {
        return exHeaders;
    }

    public void addExHeader(String key, String value) {
        if (this.exHeaders == null) {
            this.exHeaders = new HashMap<String, String>();
        }
        this.exHeaders.put(key, value);
    }

    public String getRequestString() {
        return requestString;
    }

    public void setRequestString(String requestString) {
        this.requestString = requestString;
    }

    public void setType(IMsrpMessage.Type t) {
        type = t;
    }

    public IMsrpMessage.Type getType() {
        return type;
    }

    public void forgetCpimHeaders() {
        cpimHeaders = null;
    }

    public void addCPIMHeader(ICPIMHeader header) throws IOException {
        if (cpimHeaders == null) {
            cpimHeaders = new HashMap<String, List<ICPIMHeader>>();
        }

        if (CPIMToHeader.NAME.equals(header.getName())) {
            List<ICPIMHeader> toHeaders = cpimHeaders.get(header.getName());
            if (toHeaders == null) {
                toHeaders = new LinkedList<ICPIMHeader>();
                cpimHeaders.put(header.getName(), toHeaders);
            }
            toHeaders.add(header);
        } else {
            List<ICPIMHeader> headers;
            if (!cpimHeaders.containsKey(header.getName())) {
                headers = new LinkedList<ICPIMHeader>();
                headers.add(header);
                cpimHeaders.put(header.getName(), headers);
            } else {
                throw new IOException("Cannot override existing headers.");
            }
        }

    }

    public Map<String, List<ICPIMHeader>> getCpimHeaders() {
        return cpimHeaders;
    }

    public boolean isCPIMHeaderSet(String name) {
        return cpimHeaders != null && cpimHeaders.containsKey(name);
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body, int messageContentStartAt) {
        this.body = body;
        this.messageContentStartAt = messageContentStartAt;
    }

    public StatusHeader getStatusHeader() {
        return statusHeader;
    }

    public void setStatusHeader(StatusHeader statusHeader) {
        this.statusHeader = statusHeader;
    }

    public String toString() {
        return state.toString();
    }

    public int getLastBufferPosition() {
        return lastBufferPosition;
    }

    public void setLastBufferPosition(int lastBufferPosition) {
        this.lastBufferPosition = lastBufferPosition;
    }

    public int getChunkStartPos() {
        return chunkStartPos;
    }

    public void setChunkStartPos(int chunkStartPos) {
        this.chunkStartPos = chunkStartPos;
    }

    public ContentDispositionHeader getContentDispositionHeader() {
        return contentDispositionHeader;
    }

    public void setContentDispositionHeader(ContentDispositionHeader contentDispositionHeader) {
        this.contentDispositionHeader = contentDispositionHeader;
    }

    public int getMsrpBodyStartPos() {
        return msrpBodyStartPos;
    }

    public void setMsrpBodyStartPos(int msrpBodyStartPos) {
        this.msrpBodyStartPos = msrpBodyStartPos;
    }

    public int getMessageContentStartAt() {
        return messageContentStartAt;
    }

    public boolean isWorkingOnFirstChunkPiece() {
        return workingOnFirstChunkPiece;
    }

    public void setWorkingOnFirstChunkPiece(boolean workingOnFirstChunkPiece) {
        this.workingOnFirstChunkPiece = workingOnFirstChunkPiece;
    }

    public int getCpimBlockLength() {
        return cpimBlockLength;
    }

    public void setCpimBlockLength(int cpimBlockLength) {
        this.cpimBlockLength = cpimBlockLength;
    }

}

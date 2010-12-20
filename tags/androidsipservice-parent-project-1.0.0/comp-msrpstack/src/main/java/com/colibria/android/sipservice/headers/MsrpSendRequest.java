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
package com.colibria.android.sipservice.headers;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.io.MsrpParser;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This object (and its subobjects) is immutable and can therefore safely be shared among threads
 *
 * @author Sebastian Dehne
 */
public class MsrpSendRequest implements IMsrpMessage {

    public static final String SEND = "SEND";
    public static final String ENDLINE_START = "-------";


    /**
     * Defines the type for this chunk
     */
    public static enum ChunkType {
        /**
         * Contains the head and maybe some content
         */
        head,

        /**
         * Contains only content
         */
        body_only,

        /**
         * Contains the tail and maybe some content
         */
        tail,

        /**
         * Contains the entire request (chunk)
         */
        complete
    }

    private final ChunkType chunkType;
    private final String transactionID;
    private final String messageID;
    private final ByteRange byteRange;
    private final Continuation continuation;
    private final MimeType contentType;
    private final ContentDispositionHeader contentDispositionHeader;
    private final MsrpPath toPath;
    private final MsrpPath fromPath;
    private final SuccessReportHeader successReportHeader;
    private final FailureReportHeader failureReportHeader;
    private final Map<String, List<ICPIMHeader>> cpimHeaders;
    private final Map<String, String> extentionHeaders;
    private final byte[] body;
    private final int msgSizeOnWire;
    private final int bodyStartPosition;

    /**
     * @param chunkType                the chunk type of this request
     * @param transactionID            the txID
     * @param messageID                the MsgID
     * @param byteRange                the byteRange header
     * @param continuation             the continuation flag
     * @param contentType              the msrp content type header
     * @param contentDispositionHeader the Content-Disposition header
     * @param toPath                   the toPath
     * @param fromPath                 the fromPath
     * @param successReportHeader      the successReport header
     * @param failureReportHeader      the failureReport header
     * @param cpimHeaders              the cpim headers read from the body array
     * @param extentionHeaders         any extention headers, those who were not regognized by the parser
     * @param body                     the body array, containing the cpim header block if present
     * @param msgSizeOnWire            number of bytes this message was long when it was received
     * @param bodyStartPosition        The relative start position of the cpim header in the body array (since the body array also contains the cpim header block)
     */
    public MsrpSendRequest(ChunkType chunkType,
                           String transactionID,
                           String messageID,
                           ByteRange byteRange,
                           Continuation continuation,
                           MimeType contentType,
                           ContentDispositionHeader contentDispositionHeader,
                           MsrpPath toPath,
                           MsrpPath fromPath,
                           SuccessReportHeader successReportHeader,
                           FailureReportHeader failureReportHeader,
                           Map<String, List<ICPIMHeader>> cpimHeaders,
                           Map<String, String> extentionHeaders,
                           byte[] body,
                           int msgSizeOnWire,
                           int bodyStartPosition) {

        this.chunkType = chunkType;
        this.transactionID = transactionID;
        this.messageID = messageID;
        this.byteRange = byteRange;
        this.continuation = continuation;
        this.contentType = contentType;
        this.contentDispositionHeader = contentDispositionHeader;
        this.toPath = toPath;
        this.fromPath = fromPath;
        this.successReportHeader = successReportHeader;
        this.failureReportHeader = failureReportHeader;
        if (body == null) {
            this.body = new byte[0];
        } else {
            this.body = body;
        }
        this.msgSizeOnWire = msgSizeOnWire;
        this.bodyStartPosition = bodyStartPosition;

        this.cpimHeaders = cpimHeaders;

        if (extentionHeaders != null) {
            this.extentionHeaders = Collections.unmodifiableMap(extentionHeaders);
        } else {
            this.extentionHeaders = null;
        }

    }

    /**
     * Writes the header of this request into dst.
     *
     * @param dst                   The buffer to write the data into
     * @param overrideTransactionID the txId which should be used instead of the one in this request
     * @param overrideByteRange     the byteRange which should be used instead of the one in this request
     * @param willBeFollowedByBody  whether this header should contain a crlf at the end
     * @param overrideFrom          the fromPath which should be used instead of the one in this request
     * @param overrideTo            the toPath which should be used instead of the one in this request
     */
    @SuppressWarnings({"ConstantConditions"})
    public void marshallHead(ByteBuffer dst, String overrideTransactionID, ByteRange overrideByteRange, boolean willBeFollowedByBody, MsrpPath overrideFrom, MsrpPath overrideTo) {
        String txID;
        ByteRange byteRange;
        MsrpPath from, to;

        /*
         * Set the override variables
         */
        if (overrideTransactionID != null) {
            txID = overrideTransactionID;
        } else {
            txID = transactionID;
        }
        if (overrideByteRange != null) {
            byteRange = overrideByteRange;
        } else {
            byteRange = this.byteRange;
        }

        if (overrideFrom != null) {
            from = overrideFrom;
        } else {
            from = this.fromPath;
        }
        if (overrideFrom != null) {
            to = overrideTo;
        } else {
            to = this.toPath;
        }

        /*
         * Prepare and write the msrp-header to the buffer
         */
        StringBuffer sb = new StringBuffer();
        sb.append("MSRP").append(" ").append(txID).append(" ").append(SEND).append("\r\n");
        sb.append(MsrpParser.MSRP_HEADER_TOPATH).append(": ").append(to).append("\r\n");
        sb.append(MsrpParser.MSRP_HEADER_FROMPATH).append(": ").append(from).append("\r\n");

        if (this.messageID != null) {
            sb.append(MsrpParser.MSRP_HEADER_MESSAGE_ID).append(": ").append(this.messageID).append("\r\n");
        }
        if (this.byteRange != null) {
            sb.append(MsrpParser.MSRP_HEADER_BYTE_RANGE).append(": ").append(byteRange).append("\r\n");
        }
        if (this.successReportHeader != null) {
            sb.append(MsrpParser.MSRP_HEADER_REPORT_SUCC).append(": ").append(this.successReportHeader).append("\r\n");
        }
        if (this.failureReportHeader != null) {
            sb.append(MsrpParser.MSRP_HEADER_REPORT_FAIL).append(": ").append(this.failureReportHeader).append("\r\n");
        }
        if (extentionHeaders != null) {
            String v;
            for (String n : extentionHeaders.keySet()) {
                if ((v = extentionHeaders.get(n)) != null)
                    sb.append(n).append(": ").append(v).append("\r\n");
            }
        }
        // and the last header is: Content-Type
        if (this.contentType != null) {
            sb.append(MsrpParser.MSRP_HEADER_CONTENT_TYPE).append(": ").append(this.contentType).append("\r\n");
        }
        // write the header to the buffer
        byte[] data = sb.toString().getBytes();
        sb.setLength(0); // reset buffer
        dst.put(data, 0, data.length);

        if (willBeFollowedByBody) {
            dst.put("\r\n".getBytes(), 0, 2);
        }
    }

    public void marshallTail(ByteBuffer dst, String overrideTransactionID, boolean isAfterBody, Continuation overrideContinuation) {
        String txID;
        Continuation c;
        if (overrideTransactionID != null) {
            txID = overrideTransactionID;
        } else {
            txID = transactionID;
        }
        if (overrideContinuation != null) {
            c = overrideContinuation;
        } else {
            c = this.continuation;
        }

        StringBuffer sb = new StringBuffer();
        if (isAfterBody)
            sb.append("\r\n");
        sb.append(ENDLINE_START).append(txID).append(c).append("\r\n");

        byte[] data = sb.toString().getBytes();
        dst.put(data, 0, data.length);
    }

    /**
     * @param dst                   The buffer to write the data into
     * @param overrideTransactionID the txId which should be used instead of the one in this request
     * @param overrideByteRange     the byteRange which should be used instead of the one in this request
     * @param overrideFrom          the fromPath which should be used instead of the one in this request
     * @param overrideTo            the toPath which should be used instead of the one in this request
     * @return Number of bytes of the body
     */
    public int marshall(ByteBuffer dst, ByteRange overrideByteRange, String overrideTransactionID, MsrpPath overrideFrom, MsrpPath overrideTo) {
        String txID;
        if (overrideTransactionID != null) {
            txID = overrideTransactionID;
        } else {
            txID = transactionID;
        }

        int bodyLengh = 0;

        switch (chunkType) {
            case complete:
                marshallHead(dst, txID, overrideByteRange, body.length > 0, overrideFrom, overrideTo);
                dst.put(body, 0, body.length);
                bodyLengh += body.length;
                marshallTail(dst, txID, body.length > 0, null);
                break;
            case head:
                marshallHead(dst, txID, overrideByteRange, body.length > 0, overrideFrom, overrideTo);
                dst.put(body, 0, body.length);
                bodyLengh += body.length;
                break;
            case body_only:
                dst.put(body, 0, body.length);
                bodyLengh += body.length;
                break;
            case tail:
                dst.put(body, 0, body.length);
                bodyLengh += body.length;
                marshallTail(dst, txID, body.length > 0, null);
        }

        return bodyLengh;
    }

    public Type getType() {
        return Type.request;
    }

    public String getTransactionID() {
        return transactionID;
    }

    public String getMessageID() {
        return messageID;
    }

    public ByteRange getByteRange() {
        return byteRange;
    }

    public byte[] getBody() {
        return body;
    }

    public SuccessReportHeader getSuccessReportHeader() {
        return successReportHeader;
    }

    public FailureReportHeader getFailureReportHeader() {
        return failureReportHeader;
    }

    public Continuation getContinuation() {
        return continuation;
    }

    public int getBodyStartPosition() {
        return bodyStartPosition;
    }

    public MimeType getContentType() {
        return contentType;
    }

    public ContentDispositionHeader getContentDispositionHeader() {
        return contentDispositionHeader;
    }

    public MsrpPath getToPath() {
        return toPath;
    }

    public MsrpPath getFromPath() {
        return fromPath;
    }

    public boolean isChunkType(ChunkType chunkType) {
        return this.chunkType == chunkType;
    }

    public ChunkType getChunkType() {
        return this.chunkType;
    }

    public List<ICPIMHeader> getCPIMHeader(String name) {
        if (cpimHeaders != null) {
            return cpimHeaders.get(name);
        }
        return null;
    }

    public Map<String, List<ICPIMHeader>> getCPIMHeaders() {
        return cpimHeaders;
    }

    public String getExtentionHeader(String name) {
        String v = null;
        if (extentionHeaders != null)
            v = extentionHeaders.get(name);
        return v;
    }

    public Map<String, String> getExtentionHeaders() {
        return extentionHeaders;
    }

    public int getSize() {
        return msgSizeOnWire;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        ByteBuffer bb = ByteBuffer.allocate(1024 * 8);
        sb.append("MsrpSendRequest:");
        sb.append(" ChunkType=").append(getChunkType());
        sb.append(" Continuation=").append(getContinuation());
        sb.append(" Msg=").append("\r\n");

        // the header (if present)
        if (getChunkType() == MsrpSendRequest.ChunkType.complete || getChunkType() == MsrpSendRequest.ChunkType.head) {
            marshallHead(bb, null, null, getBody().length > 0, null, null);
            byte[] tmp = new byte[bb.position()];
            bb.flip();
            bb.get(tmp, 0, tmp.length);
            sb.append(new String(tmp));
        }
        // the body (if present)
        if (getBody().length > 0) {
            sb.append("<Not showing the ").append(getBody().length).append(" bytes long body>");
        }
        // the tail (if present)
        if (getChunkType() == MsrpSendRequest.ChunkType.complete || getChunkType() == MsrpSendRequest.ChunkType.tail) {
            bb.clear();
            marshallTail(bb, null, getBody().length > 0, null);
            byte[] tmp = new byte[bb.position()];
            bb.flip();
            bb.get(tmp, 0, tmp.length);
            sb.append(new String(tmp));
        }
        return sb.toString();
    }

    /**
     * Return a string representation of this Request.
     *
     * @param maxContentLength max amount of content to be included
     * @return loggable string representation
     */
    public String toLogString(int maxContentLength) {
        StringBuffer sb = new StringBuffer();
        ByteBuffer bb = ByteBuffer.allocate(1024 * 8);

        // the header (if present)
        if (getChunkType() == MsrpSendRequest.ChunkType.complete || getChunkType() == MsrpSendRequest.ChunkType.head) {
            marshallHead(bb, null, null, getBody().length > 0, null, null);
            byte[] tmp = new byte[bb.position()];
            bb.flip();
            bb.get(tmp, 0, tmp.length);
            sb.append(new String(tmp));
        }
        // the body (if present)
        if (getBody().length > 0) {
            sb.append(new String(getBody(), 0, getBodyStartPosition()));


            int logLength = Math.min(maxContentLength, getBody().length - getBodyStartPosition());
            try {
                String body = new String(getBody(), getBodyStartPosition(), logLength, "UTF-8");
                sb.append(body);
            } catch (UnsupportedEncodingException e) {
                sb.append("hexdump(");
                for (int i = 0; i < logLength; i++) {
                    sb.append(Integer.toHexString(getBody()[getBodyStartPosition() + i]));
                }
                sb.append(")");
            }
            if (logLength < getBody().length) {
                sb.append(" !only first ").append(logLength).append(" bytes shown!");
            }
        }
        // the tail (if present)
        if (getChunkType() == MsrpSendRequest.ChunkType.complete || getChunkType() == MsrpSendRequest.ChunkType.tail) {
            bb.clear();
            marshallTail(bb, null, getBody().length > 0, null);
            byte[] tmp = new byte[bb.position()];
            bb.flip();
            bb.get(tmp, 0, tmp.length);
            sb.append(new String(tmp));
        }
        return sb.toString();
    }


    public MsrpSendRequest generateAbortClone() {
        ChunkType t;
        switch (getChunkType()) {
            case complete:
                t = ChunkType.complete;
                break;
            default:
                t = ChunkType.tail;
                break;
        }
        return new MsrpSendRequest(
                t,
                transactionID,
                messageID,
                byteRange,
                Continuation.aborted,
                getContentType(),
                getContentDispositionHeader(),
                getToPath(),
                getFromPath(),
                getSuccessReportHeader(),
                getFailureReportHeader(),
                cpimHeaders,
                extentionHeaders,
                getBody(),
                getSize(),
                getBodyStartPosition()
        );
    }

    public MsrpSendRequest generateCloneWithOverride(ICPIMHeader newHeader) {

        int newMsgSize;
        byte[] newBody;
        int newBodyStartPos;
        Map<String, List<ICPIMHeader>> cpimHeaders;
        if (chunkType == ChunkType.complete || chunkType == ChunkType.head && this.cpimHeaders != null && this.cpimHeaders.size() > 0 && newHeader != null) {

            /*
             * First, update the header list
             */
            cpimHeaders = new HashMap<String, List<ICPIMHeader>>();
            for (String key : this.cpimHeaders.keySet()) {
                if (newHeader.getName().equals(key)) {
                    cpimHeaders.put(key, Collections.singletonList(newHeader));
                } else {
                    cpimHeaders.put(key, this.cpimHeaders.get(key));
                }
            }

            /*
             * Second, update the body byte array
             */
            final byte[] cpimBytes;
            StringBuffer cpimBlock = getCpimHeaderBlock(cpimHeaders);
            String cpimBlockStr = cpimBlock.toString();
            cpimBytes = cpimBlockStr.getBytes();

            // define the new body
            newBody = new byte[cpimBytes.length + (body.length - bodyStartPosition)];

            // write the cpim bytes
            System.arraycopy(cpimBytes, 0, newBody, 0, cpimBytes.length);

            // write the body part without the old cpim stuff
            System.arraycopy(body, bodyStartPosition, newBody, cpimBytes.length, body.length - bodyStartPosition);

            if (cpimBytes.length >= bodyStartPosition) {
                newMsgSize = msgSizeOnWire + (cpimBytes.length - bodyStartPosition);
            } else {
                newMsgSize = msgSizeOnWire - (bodyStartPosition - cpimBytes.length);
            }

            newBodyStartPos = cpimBytes.length;

        } else {
            cpimHeaders = this.cpimHeaders;
            newBody = body;
            newMsgSize = this.msgSizeOnWire;
            newBodyStartPos = this.bodyStartPosition;
        }


        return new MsrpSendRequest(
                this.chunkType,
                this.transactionID,
                this.messageID,
                this.byteRange,
                this.continuation,
                this.contentType,
                this.contentDispositionHeader,
                this.toPath,
                this.fromPath,
                this.successReportHeader,
                this.failureReportHeader,
                cpimHeaders,
                this.extentionHeaders,
                newBody,
                newMsgSize,
                newBodyStartPos
        );
    }

    private static StringBuffer getCpimHeaderBlock(Map<String, List<ICPIMHeader>> cpimHeaders) {
        StringBuffer mainPart = new StringBuffer();
        StringBuffer mimeSectionPart = new StringBuffer();
        StringBuffer tmp;

        for (String key : cpimHeaders.keySet()) {
            List<ICPIMHeader> list = cpimHeaders.get(key);

            for (ICPIMHeader h : list) {
                tmp = h.isInMimeSection() ? mimeSectionPart : mainPart;
                tmp.append(h.getName());
                tmp.append(": ");
                tmp.append(h.getValue());
                tmp.append("\r\n");
            }
        }

        if (mimeSectionPart.length() > 0) {
            mainPart.append("\r\n");
            mainPart.append(mimeSectionPart);
        }
        mainPart.append("\r\n");
        return mainPart;
    }

    public static MsrpSendRequest generateHandShake(String id, MsrpPath toPath, MsrpPath fromPath, String msgID) {
        return new MsrpSendRequest(
                ChunkType.complete,
                id,
                msgID,
                null,
                Continuation.done,
                null,
                null,
                toPath,
                fromPath,
                null,
                null,
                null,
                null,
                null,
                0,
                0
        );
    }
}

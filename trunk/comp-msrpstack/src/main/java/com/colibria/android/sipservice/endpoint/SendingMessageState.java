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
package com.colibria.android.sipservice.endpoint;

import com.colibria.android.sipservice.endpoint.api.ISendingListener;
import com.colibria.android.sipservice.headers.*;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.MimeType;

import java.nio.ByteBuffer;
import java.util.List;


/**
 * This class is not thread-safe and must therefore be guarded externally
 *
 * @author Sebastian Dehne
 */
public class SendingMessageState {
    private static final String TAG = "SendingMessageState";

    private static final ThreadLocal<ByteBuffer> buffer = new ThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            return ByteBuffer.allocate(1024 * 8); // needs to fit at least SEND_AT_A_TIME plus cpim headers
        }
    };

    private static final int MAX_CHUNK_SIZE = Integer.MAX_VALUE; // according to the msrp-rfc, we should send as many bytes as possible in one chunk (as long as we can be interruptible)
    public static final int SEND_AT_A_TIME = 1024 * 2;

    public enum State {
        init,
        sending,
        sent,
        done,
        abort
    }

    private final EndPointSessionImpl context;
    private final String msgID;
    private final MimeType contentType;
    private final ContentDispositionHeader contentDispositionHeader;
    private final List<Address> receipients;
    private final ISendingListener sendingListener;

    private long msgSize;
    private State state;
    private long bytesSent;
    private long bytesSentCurrentChunk;
    private long currentByteRangeStart;
    private String currentTxID;

    public SendingMessageState(EndPointSessionImpl context, long msgSize, MimeType contentType, ContentDispositionHeader contentDispositionHeader, List<Address> receipients, ISendingListener sendingListener) {
        this.context = context;
        this.msgSize = msgSize;
        this.contentType = contentType;
        this.contentDispositionHeader = contentDispositionHeader;
        this.receipients = receipients;
        this.sendingListener = sendingListener;
        this.msgID = context.getMsrpResources().getNextId();

        currentByteRangeStart = 1;
        state = State.init;
        bytesSent = 0;
        bytesSentCurrentChunk = 0;
    }


    public String getMsgID() {
        return msgID;
    }

    public ISendingListener getSendingListener() {
        return sendingListener;
    }

    public MsrpSendRequest getNextChunkPiece(byte[] content, boolean abortSending, boolean lastChunk) {

        State oldState = state;
        long beforeBytesSent = bytesSent;
        byte[] body = getMoreContent(content, abortSending, lastChunk); // updates "state"
        final MsrpSendRequest.ChunkType type;

        if (oldState == State.sending) {
            if (state == State.sending) {
                type = MsrpSendRequest.ChunkType.body_only;
            } else {
                type = MsrpSendRequest.ChunkType.tail;
            }
        } else {
            currentTxID = context.getMsrpResources().getNextId();
            if (state == State.sending) {
                type = MsrpSendRequest.ChunkType.head;
            } else {
                type = MsrpSendRequest.ChunkType.complete;
            }
            currentByteRangeStart = beforeBytesSent + 1;
        }

        final ByteRange byteRange = ByteRange.create(currentByteRangeStart, -1, msgSize); // todo always interruptible?

        /*
         * Note: we need to ensure that chunk pieces (this type=body_only or tail) carrie the same byte-range header
         * as the head including chunk pice
         */

        Continuation continuation;
        switch (state) {
            case sent:
            case sending:
                continuation = Continuation.more;
                break;
            case abort:
                continuation = Continuation.aborted;
                break;
            case done:
                continuation = Continuation.done;
                break;
            default:
                continuation = null;
                break;
        }

        return new MsrpSendRequest(
                type,
                currentTxID,
                msgID,
                byteRange,
                continuation,
                MimeType.MESSAGE_CPIM,
                null,
                new MsrpPath(context.getRemoteMsrpURI()),
                new MsrpPath(context.getLocalMsrpURI()),
                null,
                null,
                null,
                null,
                body,
                body.length, // this is not accurant since the headers are not included, but it will do for managing the output queue capacity 
                0
        );
    }

    private StringBuffer getCpimHeaderBlock() {

        StringBuffer sb = new StringBuffer();

        // from-header
        CPIMFromHeader fh = new CPIMFromHeader(context.getAddress());
        sb.append(CPIMFromHeader.NAME).append(": ").append(fh.getValue()).append("\r\n");

        // to headers
        CPIMToHeader th;
        for (Address a : receipients) {
            th = new CPIMToHeader(a);
            sb.append(CPIMToHeader.NAME).append(": ").append(th.getValue()).append("\r\n");
        }

        sb.append("\r\n");

        MimeContentTypeHeader mimeContentTypeHeader = new MimeContentTypeHeader(contentType);
        sb.append("Content-Type").append(": ").append(mimeContentTypeHeader.getValue()).append("\r\n");
        if (contentDispositionHeader != null) {
            sb.append(contentDispositionHeader.getName()).append(": ").append(contentDispositionHeader.getValue()).append("\r\n");
        }
        sb.append("\r\n");

        return sb;
    }

    private byte[] getMoreContent(byte[] content, boolean abortSending, boolean lastChunk) {

        if (abortSending) {
            state = State.abort;
            return new byte[0];
        }

        /*
        * Compile and get cpim block
        *
        * Note: cpimBytes.length is information which is actually stored in the MsrpSendRequest (cpimStartPos)
        * But since this request is only sent and not parsed again, we don't care about this
        */
        final byte[] cpimBytes;
        if (state == State.init) {
            StringBuffer cpimBlock = getCpimHeaderBlock();
            String cpimBlockStr = cpimBlock.toString();
            cpimBytes = cpimBlockStr.getBytes();
        } else {
            cpimBytes = new byte[0];
        }

        if (msgSize >= 0)
            msgSize += cpimBytes.length;

        // get a buffer we can use prepare a buffer
        ByteBuffer bb = buffer.get();
        bb.clear();

        // write the cpim block into it if required
        bb.put(cpimBytes, 0, cpimBytes.length);

        // read content into buffer
        boolean eos = false;
        int read = content == null ? -1 : content.length;
        if (read == -1) {
            eos = true;
        } else {
            bb.put(content);
        }
        bb.flip();

        /*
        * Writing done, update state
        */
        bytesSent += bb.limit();
        bytesSentCurrentChunk += bb.limit();

        // stream ended; end of msg detected
        if (eos || lastChunk || (msgSize >= 0 && bytesSent >= msgSize)) {
            state = State.done;
            bytesSentCurrentChunk = 0;
        }

        // config tells us we should interrupt here and create a new chunk
        else if (bytesSentCurrentChunk >= MAX_CHUNK_SIZE) {
            state = State.sent;
            bytesSentCurrentChunk = 0;
        }

        // keep sending data within the current chunk
        else {
            state = State.sending;
        }

        // compile one byte-array which is used in the msg
        byte[] body = new byte[bb.limit()];
        System.arraycopy(bb.array(), bb.position() + bb.arrayOffset(), body, 0, bb.limit());
        return body;

    }
}

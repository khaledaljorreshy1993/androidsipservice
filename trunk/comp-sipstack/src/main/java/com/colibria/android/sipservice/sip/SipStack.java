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

import com.colibria.android.sipservice.ByteBufferOutputStream;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.messages.Request;
import com.colibria.android.sipservice.sip.messages.Response;
import com.colibria.android.sipservice.sip.messages.SipMessage;
import com.colibria.android.sipservice.sip.parser.SipMessageParser;
import com.colibria.android.sipservice.sip.tx.ServerTransaction;
import com.colibria.android.sipservice.sip.tx.TransactionRepository;
import com.colibria.android.sipservice.sip.tx.ClientTransaction;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Sebastian Dehne
 */
public class SipStack {
    private static final String TAG = "SipStack";

    private static volatile SipStack instance;

    public static SipStack get() {
        return instance;
    }

    private final SipMessageParser sipMessageParser;
    private final ScheduledExecutorService mThreadPool;
    private final TransactionRepository transactionRepository;
    private final Queue<ByteBuffer> mByteBufferHolder;
    private volatile String mLocalHostname;
    private volatile ISipStackListener mSipStackListener;
    private volatile ISipTcpConnectionProvider mSipTcpConnectionProvider;
    private volatile InetSocketAddress mLocalAddress;

    public SipStack(ScheduledExecutorService threadPool) {
        // just generate a unique hostname for our connection to ensure that other clients don't use the same hostname
        // this ensures that the alias table (see draft-ietf-sip-connect-reuse-14) doesn't get duplicated destinations
        sipMessageParser = new SipMessageParser();
        this.mThreadPool = threadPool;
        transactionRepository = new TransactionRepository();
        mByteBufferHolder = new ConcurrentLinkedQueue<ByteBuffer>();

        if (instance != null) {
            throw new RuntimeException("Already have an instance?");
        }
        instance = this;
    }

    public void setSipStackListener(ISipStackListener mSipStackListener) {
        this.mSipStackListener = mSipStackListener;
    }

    public void setSipTcpConnectionProvider(ISipTcpConnectionProvider sipTcpConnectionProvider) {
        this.mSipTcpConnectionProvider = sipTcpConnectionProvider;
    }

    public ScheduledExecutorService getThreadPool() {
        return mThreadPool;
    }

    public TransactionRepository getTxRepository() {
        return transactionRepository;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public InetSocketAddress getLocalSocketAddress() {
        return mLocalAddress;
    }

    public void setLocalAddress(InetSocketAddress mLocalAddress) {
        Logger.d(TAG, "setLocalAddress() " + mLocalAddress.toString());
        this.mLocalAddress = mLocalAddress;
    }

    public boolean sendRequest(Request r) {
        sendMessage(r);
        return true;
    }

    public void sendResponse(Response response) {
        sendMessage(response);
    }

    private ByteBuffer getByteBuffer() {
        ByteBuffer bb = mByteBufferHolder.poll();
        if (bb == null) {
            Logger.d(TAG, "Creating a new byteBuffer. size=" + mByteBufferHolder.size());
            bb = ByteBuffer.allocate(1024 * 8); // todo config
        }
        bb.clear();
        return bb;
    }

    private void restoreByteBuffer(ByteBuffer bb) {
        mByteBufferHolder.offer(bb);
    }

    private void sendMessage(SipMessage message) {
        Logger.d(TAG, "Sending msg: \n" + message);
        final ByteBufferOutputStream os = new ByteBufferOutputStream(getByteBuffer());
        try {
            message.writeToBuffer(os);
        } catch (IOException e) {
            //
        }
        os.getBb().flip();
        Runnable restoreBB = new Runnable() {
            @Override
            public void run() {
                restoreByteBuffer(os.getBb());
            }
        };
        mSipTcpConnectionProvider.writeToTcpConnection(os.getBb(), restoreBB, restoreBB);
    }

    private void handleReceivedMsg(final SipMessage parsedMessage) {
        getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                Logger.d(TAG, "msg received:\n" + parsedMessage);
                if (parsedMessage instanceof Response) {
                    Response response = (Response) parsedMessage;
                    ClientTransaction clientTransaction = getTxRepository().getClientTransaction(response);

                    if (clientTransaction != null) {
                        response.setClientTransaction(clientTransaction);
                        clientTransaction.responseReceived(response);
                    } else {
                        Logger.e(TAG, "have no transaction for this response, forced to ignore it");
                    }
                } else {
                    Request request = (Request) parsedMessage;
                    ServerTransaction serverTransaction;
                    if ((serverTransaction = getTxRepository().getServerTransaction(request)) == null) {
                        serverTransaction = transactionRepository.getNewServerTransaction(request, mSipStackListener);
                        request.setServerTransaction(serverTransaction);
                    }
                    serverTransaction.handleRequest(request);
                }
            }
        });
    }

    public void dataReceived(ByteBuffer readBuffer) {
        try {
            readBuffer.flip();
            while (readBuffer.hasRemaining()) {
                SipMessage parsedMessage = sipMessageParser.parseMoreBytes(readBuffer);
                sipMessageParser.reset(); // we are done parsing one msg

                if (parsedMessage != null) {
                    handleReceivedMsg(parsedMessage);
                }

                if (readBuffer.hasRemaining()) {
                    readBuffer.compact();
                    readBuffer.limit(readBuffer.position());
                    readBuffer.position(0);
                }
            }
            readBuffer.clear();

        } catch (EOFException e) {
            Logger.d(TAG, "more data is expected");
            readBuffer.position(readBuffer.limit());
            readBuffer.limit(readBuffer.capacity());

            if (readBuffer.position() == readBuffer.capacity()) {
                Logger.e(TAG, "Read buffer is full", e);
                mSipTcpConnectionProvider.parseError();
            }
        } catch (IOException e) {
            Logger.e(TAG, "Parse error ", e);
            mSipTcpConnectionProvider.parseError();
        }
    }

    public String getMyHostName() {
        return mLocalHostname;
    }

    public void setLocalAddress(String mLocalHostname) {
        this.mLocalHostname = mLocalHostname;
    }

    public int getMyPort() {
        return -1; // will suppress the port, thus using the default port
    }

}

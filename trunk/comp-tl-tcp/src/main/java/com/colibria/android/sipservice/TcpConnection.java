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
package com.colibria.android.sipservice;

import com.colibria.android.sipservice.logging.Logger;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Sebastian Dehne
 */
public class TcpConnection {
    private final String TAG = "TcpConnection";

    public static final byte[] KEEP_ALIVE = "\n".getBytes();

    public static enum ConnectionState {
        connecting,
        connected,
        closed
    }

    private final int id;
    private final ITcpConnectionListener mListener;
    private final TcpController mParent;
    private final InetSocketAddress mRemoteAddress;
    private final ConcurrentLinkedQueue<WriteTask> mWriteQueue;
    private final AtomicBoolean mIsClosed;
    protected final ITcpSocketListener mHiddenListener;
    private volatile SocketChannel mSocketChannel;

    protected TcpConnection(TcpController parent, int id, ITcpConnectionListener listener, InetSocketAddress mRemoteAddress) {
        this.id = id;
        this.mRemoteAddress = mRemoteAddress;
        this.mListener = listener;
        this.mParent = parent;
        this.mWriteQueue = new ConcurrentLinkedQueue<WriteTask>();
        mIsClosed = new AtomicBoolean(false);

        /*
         * Some call-back methods which are used by the TcpController. We hide those such that
         * we don't expose those to the user of this class.
         */
        mHiddenListener = new ITcpSocketListener() {

            @Override
            public void connectFailed() {
                mSocketChannel = null;
                if (!mIsClosed.get()) {
                    mListener.socketConnectFailed();
                }
            }

            @Override
            public void connectionLost() {
                mSocketChannel = null;
                if (!mIsClosed.get()) {
                    mListener.socketConnectionClosed();
                }
            }

            @Override
            public void dataReceived(ByteBuffer bb) {
                if (mIsClosed.get()) {
                    bb.clear();
                } else {
                    mListener.dataReceived(bb);
                }
            }

            @Override
            public void newSocketEstablished(SocketChannel socketChannel) {
                mSocketChannel = socketChannel;
                if (mWriteQueue.size() > 0) {
                    mParent.haveDataToBeWritten(socketChannel);
                }
                mListener.socketConnectionOpened();
            }

            @Override
            public WriteTask getNextWriteTask() {
                return mWriteQueue.poll();
            }
        };
    }

    public InetSocketAddress getRemoteAddress() {
        return mRemoteAddress;
    }

    public InetSocketAddress getLocalSocketAddress() {
        SocketChannel sc = mSocketChannel;
        if (sc != null && sc.socket() != null) {
            return (InetSocketAddress) sc.socket().getLocalSocketAddress();
        } else {
            return null;
        }
    }

    public void reconnect() {
        if (!mIsClosed.get()) {
            closeSocketConnection();

            mParent.openNewConnection(this);
        }
    }

    public void close() {
        if (mIsClosed.compareAndSet(false, true)) {
            closeSocketConnection();
        }
    }

    public void write(ByteBuffer flippedByeBuffer, Runnable whenDone, Runnable whenError) {
        Logger.d(TAG, "write() data.size=" + flippedByeBuffer.limit());
        if (mIsClosed.get()) {
            try {
                whenError.run();
            } catch (Exception e) {
                Logger.i(TAG, "Caught exception when executing whenError() ", e);
            }
            return;
        }

        // do we need to re-connect?
        SocketChannel sc = mSocketChannel;
        if (sc == null) {
            reconnect(); // note: this clears the writeQueue as well
        }

        mWriteQueue.offer(new WriteTask(flippedByeBuffer, whenDone, whenError));

        if ((sc = mSocketChannel) != null) {
            mParent.haveDataToBeWritten(sc);
        }
    }

    public ConnectionState getUnSafeConnectionState() {
        if (mIsClosed.get()) {
            return ConnectionState.closed;
        } else if (mSocketChannel == null) {
            return ConnectionState.connecting;
        } else {
            return ConnectionState.connected;
        }
    }

    private void closeSocketConnection() {
        SocketChannel sc = mSocketChannel;
        if (sc != null) {
            mParent.closeConnection(sc);
            mSocketChannel = null;

            WriteTask wt;
            while ((wt = mWriteQueue.poll()) != null) {
                try {
                    wt.getWhenError().run();
                } catch (Exception e) {
                    Logger.i(TAG, "Caught exception when executing whenError() ", e);
                }
            }
        }
    }

    public void sendKeepAliveNow() {
        mListener.sendKeepAliveNow();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TcpConnection that = (TcpConnection) o;

        return id == that.id && mParent.equals(that.mParent);
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + mParent.hashCode();
        return result;
    }
}

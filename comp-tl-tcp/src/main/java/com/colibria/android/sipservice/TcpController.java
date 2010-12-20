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

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sebastian Dehne
 */
public class TcpController extends Thread {
    private static final String TAG = "TcpController";

    // todo implement a connect timeout

    private static final int CONNECT_TIMEOUT = 1000 * 10;
    private static final int READ_BUFFER_SIZE = 1024 * 8 * 4;

    public static TcpController createPreStartedController(ScheduledExecutorService threadPool) {
        TcpController result = null;
        try {
            result = new TcpController(threadPool);
            result.setDaemon(true);
            result.start();
            while(!result.isRunning) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    //
                }
            }
        } catch (IOException e) {
            Logger.e(TAG, "Could not create/start controller", e);
        }
        return result;
    }

    private final ScheduledExecutorService mThreadPool;
    private final Selector mSelector;
    private final AtomicInteger mConnectionIdCounter;
    private volatile boolean isRunning;

    private TcpController(ScheduledExecutorService threadPool) throws IOException {
        mSelector = Selector.open();
        this.mThreadPool = threadPool;
        isRunning = false;
        mConnectionIdCounter = new AtomicInteger(0);
        Logger.e(TAG, "Done constructing a new controller");
    }

    @Override
    public void run() {
        Logger.i(TAG, "Controller started");
        isRunning = true;

        try {
            while (isRunning) {

                // guard
                synchronized (this) {
                }

                try {
                    if (mSelector.select() <= 0) {
                        Logger.d(TAG, "selector returned with nothing");
                        continue;
                    }
                } catch (IOException e) {
                    Logger.i(TAG, "error during selection", e);
                }

                Set<SelectionKey> keys = mSelector.selectedKeys();
                for (SelectionKey key : keys) {

                    // I can read something
                    if ((key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                        handleReadOperation(key);
                    }

                    // I can write something
                    else if ((key.readyOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                        handleWriteOperation(key);
                    }

                    // I can connect something
                    else if ((key.readyOps() & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT) {
                        handleConnectOperation(key);
                    }

                    // I can accept something
                    else if ((key.readyOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
                        // not used
                        Logger.e(TAG, "Got an unexpected accept signal");
                    }
                }
                keys.clear();

            }
        } catch (Throwable t) {
            // todo what to do now?
            Logger.e(TAG, "Main looper died", t);
        }

        /*
         * Try to close all connections
         */
        try {
            if (mSelector.keys() != null) {
                for (SelectionKey k : mSelector.keys()) {
                    k.cancel();
                    k.channel().close();
                }
            }
            mSelector.close();
        } catch (IOException e) {
            Logger.e(TAG, "Could not close selector", e);
        }
    }

    public TcpConnection createNewManagedConnection(String remoteHostname, int remotePort, ITcpConnectionListener listener) {
        Logger.d(TAG, "createNewManagedConnection() - isRunning=" + isRunning + ", remoteHostname=" + remoteHostname + ", remotePort=" + remotePort);
        if (!isRunning) {
            return null;
        }

        return new TcpConnection(this, mConnectionIdCounter.getAndIncrement(), listener, remoteHostname, remotePort);
    }

    public void shutdown() {
        Logger.d(TAG, "shutdown() - " + isRunning);

        synchronized (this) {
            if (isRunning) {
                isRunning = false;
                mSelector.wakeup();
            }
        }
    }

    public void sendKeepAlives() {
        Logger.d(TAG, "sendKeepAlives() - " + isRunning);

        if (!isRunning) {
            return;
        }

        synchronized (this) {
            mSelector.wakeup();
            Set<SelectionKey> keys = mSelector.keys();
            SelectionKeyAttachment a;
            if (keys != null) {
                for (SelectionKey k : keys) {
                    if ((a = (SelectionKeyAttachment) k.attachment()) != null) {
                        a.connection.sendKeepAliveNow();
                    }
                }
            }
        }
    }

    protected void haveDataToBeWritten(SocketChannel sc) {
        Logger.d(TAG, "haveDataToBeWritten() - " + isRunning);

        if (!isRunning)
            return;

        synchronized (this) {
            mSelector.wakeup();
            SelectionKey sk = sc.keyFor(mSelector);
            if (sk != null) {
                try {
                    sk.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                } catch (CancelledKeyException e) {
                    Logger.d(TAG, "key already canceled, ignoring this event");
                }
            }
        }
    }

    protected void openNewConnection(TcpConnection connection) {
        Logger.d(TAG, "openNewConnection() - " + isRunning);

        if (!isRunning)
            return;

        /*
         * Connect now
         */
        InetSocketAddress remoteSocketAddress = connection.getRemoteAddress();
        Logger.d(TAG, "About to establish a new connection to " + remoteSocketAddress);
        if (remoteSocketAddress.isUnresolved()) {
            Logger.i(TAG, "Could not resolve hostname - connect failed");
            connection.mHiddenListener.connectFailed();
            return;
        }

        SocketChannel sc = null;
        try {
            sc = SocketChannel.open();
            sc.configureBlocking(false);
            if (sc.connect(remoteSocketAddress)) {
                finishedConnect(sc, connection);
            } else {
                synchronized (this) {
                    mSelector.wakeup();
                    SelectionKeyAttachment ska = new SelectionKeyAttachment(sc, connection);
                    sc.register(mSelector, SelectionKey.OP_CONNECT, ska);
                    ska.startConnectTimeoutTask();
                }
            }
        } catch (IOException e) {
            Logger.i(TAG, "error during connect", e);
            closeChannel(sc);
            connection.mHiddenListener.connectFailed();
        }
    }

    protected void closeConnection(SocketChannel mSocketChannel) {
        Logger.d(TAG, "closing connection");
        closeChannel(mSocketChannel);
    }

    private void finishedConnect(final SocketChannel sc, final TcpConnection connectionListener) {
        Logger.d(TAG, "in finishedConnect()");

        synchronized (this) {
            mSelector.wakeup();

            SelectionKey existingKey = sc.keyFor(mSelector);
            SelectionKeyAttachment ska = null;
            if (existingKey != null) {
                ska = (SelectionKeyAttachment) existingKey.attachment();
            }

            try {
                if (sc.finishConnect()) {
                    if (ska != null) {
                        ska.cancelConnectTimeoutTask();
                    } else {
                        ska = new SelectionKeyAttachment(sc, connectionListener);
                    }

                    sc.configureBlocking(false);
                    sc.register(mSelector, SelectionKey.OP_READ, ska);
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            connectionListener.mHiddenListener.newSocketEstablished(sc);
                        }
                    });
                } else {
                    Logger.i(TAG, "finishedConnect() failed");
                    closeChannel(sc);
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            connectionListener.mHiddenListener.connectFailed();
                        }
                    });
                }
            } catch (IOException e) { // thrown by finishConnect()
                Logger.i(TAG, "Could not complete connect", e);
                closeChannel(sc);
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        connectionListener.mHiddenListener.connectFailed();
                    }
                });
            }
        }
    }

    private void closeChannel(SocketChannel sc) {
        Logger.d(TAG, "closeChannel() " + isRunning);

        if (!isRunning || sc == null)
            return;

        synchronized (this) {
            try {
                mSelector.wakeup();
                SelectionKey key = sc.keyFor(mSelector);
                if (key != null) {
                    key.cancel();
                }
                sc.close();
            } catch (IOException e) {
                //ignore
            }
        }
    }

    private void handleReadOperation(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        SelectionKeyAttachment attachment = (SelectionKeyAttachment) key.attachment();
        try {
            if (sc.read(attachment.readBuffer) != -1) {
                Logger.d(TAG, "read " + attachment.readBuffer.position() + " bytes");
                if (attachment.readBuffer.position() > 0) {
                    attachment.connection.mHiddenListener.dataReceived(attachment.readBuffer);
                } else {
                    Logger.d(TAG, "read ignore since 0 bytes was read");
                }
            } else {
                Logger.d(TAG, "read got -1, closing channel");
                closeChannel(sc);
                attachment.connection.mHiddenListener.connectionLost();
            }
        } catch (IOException e) {
            Logger.i(TAG, "Could not read from socket ", e);
            closeChannel(sc);
            attachment.connection.mHiddenListener.connectionLost();
        }
    }

    private void handleWriteOperation(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        SelectionKeyAttachment attachment = (SelectionKeyAttachment) key.attachment();

        WriteTask writeTask;
        if ((writeTask = attachment.connection.mHiddenListener.getNextWriteTask()) != null) {
            try {
                performWrite(sc, writeTask);
                Logger.d(TAG, "wrote " + writeTask.getData().limit() + " bytes");
                if (writeTask.getWhenDone() != null) {
                    try {
                        writeTask.getWhenDone().run();
                    } catch (Exception e) {
                        Logger.e(TAG, "whenDone threw exception", e);
                    }
                }
            } catch (Exception e) {
                Logger.i(TAG, "exception during write", e);
                try {
                    writeTask.getWhenError().run();
                } catch (Exception e2) {
                    Logger.e(TAG, "whenError threw exception", e2);
                }
                attachment.connection.mHiddenListener.connectionLost();
            }
        } else {
            // finished writing, no more data to be written for this connection
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void handleConnectOperation(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        SelectionKeyAttachment attachment = (SelectionKeyAttachment) key.attachment();
        finishedConnect(sc, attachment.connection);
    }

    private void performWrite(SocketChannel socketChannel, WriteTask writeTask) throws IOException {
        ByteBuffer bb = writeTask.getData();
        Selector writeSelector = null;
        SelectionKey key = null;
        int attempts = 0;
        try {
            while (bb.hasRemaining()) {
                int len = socketChannel.write(bb);
                attempts++;
                if (len < 0) {
                    throw new EOFException();
                }
                if (len == 0) {
                    if (writeSelector == null) {
                        writeSelector = Selector.open();
                        if (writeSelector == null) {
                            // Continue using the main one.
                            continue;
                        }
                    }

                    key = socketChannel.register(writeSelector, SelectionKey.OP_WRITE);

                    if (writeSelector.select(5 * 1000) == 0) { // 5 seconds timeout
                        if (attempts > 2)
                            throw new IOException("Client disconnected");
                    } else {
                        attempts--;
                    }
                } else {
                    attempts = 0;
                }
            }
        } finally {
            if (key != null) {
                key.cancel();
            }

            if (writeSelector != null) {
                // Flush the key.
                writeSelector.selectNow();
                writeSelector.close();
            }
        }
    }

    private class SelectionKeyAttachment {
        final TcpConnection connection;
        final SocketChannel sc;
        final ByteBuffer readBuffer;
        volatile ScheduledFuture connectTimeoutTask;

        private SelectionKeyAttachment(SocketChannel sc, TcpConnection connection) {
            this.sc = sc;
            this.connection = connection;
            readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        }

        void startConnectTimeoutTask() {
            connectTimeoutTask = mThreadPool.schedule(new Runnable() {
                @Override
                public void run() {
                    Logger.d(TAG, "Connect timeout timer fired");
                    if (connectTimeoutTask != null) {
                        connectTimeoutTask = null;

                        // remove connect and notify listener
                        synchronized (this) {
                            mSelector.wakeup();
                            SelectionKey k = sc.keyFor(mSelector);
                            if (k != null) {
                                k.cancel();
                                connection.mHiddenListener.connectFailed();
                            }
                        }
                    }
                }
            }, CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
            Logger.d(TAG, "Scheduled connect timeout timer");
        }

        void cancelConnectTimeoutTask() {
            ScheduledFuture sf = connectTimeoutTask;
            if (sf != null) {
                sf.cancel(false);
                connectTimeoutTask = null;
            }
        }
    }
}

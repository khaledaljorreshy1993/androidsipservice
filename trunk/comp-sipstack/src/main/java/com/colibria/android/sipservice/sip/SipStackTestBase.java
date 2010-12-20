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

import com.colibria.android.sipservice.ITcpConnectionListener;
import com.colibria.android.sipservice.TcpConnection;
import com.colibria.android.sipservice.TcpController;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.logging.TestLogger;
import com.colibria.android.sipservice.threadpool.ThreadPool;
import junit.framework.TestCase;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * @author Sebastian Dehne
 */
public class SipStackTestBase extends TestCase implements ISipTcpConnectionProvider {

    static {
        // set the logging impl
        Logger.setLOGGER_IMPL(new TestLogger());
    }

    protected final Semaphore semaphore = new Semaphore(1);
    protected final ThreadPool threadPool = new ThreadPool(2);
    protected volatile TcpController tcpController;
    protected volatile ISipStackListener listener;
    protected volatile String dstHst;
    protected volatile int dstPort;
    protected volatile TcpConnection connection;

    @Override
    protected void setUp() throws Exception {

        // set up the sip-stack
        if (SipStack.get() == null)
            new SipStack(threadPool) {
                @Override
                public void dataReceived(ByteBuffer readBuffer) {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    try {
                        super.dataReceived(readBuffer);    //To change body of overridden methods use File | Settings | File Templates.
                    } finally {
                        semaphore.release();
                    }
                }
            };
        SipStack.get().setSipStackListener(listener);
        SipStack.get().setSipTcpConnectionProvider(this);

        // establish the managed connection
        final LinkedBlockingQueue<Boolean> sync = new LinkedBlockingQueue<Boolean>();
        tcpController = TcpController.createPreStartedController(threadPool);
        connection = tcpController.createNewManagedConnection(dstHst, dstPort, new ITcpConnectionListener() {
            @Override
            public void dataReceived(ByteBuffer mReadBuffer) {
                SipStack.get().dataReceived(mReadBuffer);
            }

            @Override
            public void socketConnectionOpened() {
                sync.offer(Boolean.TRUE);
            }

            @Override
            public void socketConnectionClosed() {
                sync.offer(Boolean.FALSE);
            }

            @Override
            public void socketConnectFailed() {
                sync.offer(Boolean.FALSE);
            }

            @Override
            public void sendKeepAliveNow() {
                connection.write(ByteBuffer.wrap(TcpConnection.KEEP_ALIVE), null, null);
            }
        });
        connection.reconnect();
        assertTrue(sync.take());
    }

    @Override
    protected void tearDown() throws Exception {
        connection.close();
        connection = null;
        tcpController.shutdown();
        tcpController = null;
    }

    public static void readMoreBytes(ByteBuffer dst, byte[] data, int bytesToBeAdded) {
        int alreadyRead = dst.limit();
        dst.position(dst.limit());
        dst.limit(dst.capacity());

        System.arraycopy(data, alreadyRead, dst.array(), dst.arrayOffset() + alreadyRead, bytesToBeAdded);
        dst.position(alreadyRead + bytesToBeAdded);
    }


    @Override
    public void writeToTcpConnection(ByteBuffer bb, Runnable whenDone, Runnable whenError) {
        connection.write(bb, whenDone, whenError);
    }

    @Override
    public void parseError() {
        connection.close();
    }
}

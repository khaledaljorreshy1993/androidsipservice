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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import com.colibria.android.sipservice.logging.AndroidLogger;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.*;
import com.colibria.android.sipservice.sip.frameworks.register.IRegisterControllerListener;
import com.colibria.android.sipservice.sip.frameworks.register.RegisterController;
import com.colibria.android.sipservice.sip.headers.RouteHeader;
import com.colibria.android.sipservice.sip.headers.WarningHeader;
import com.colibria.android.sipservice.sip.messages.Request;
import com.colibria.android.sipservice.sip.messages.Response;
import com.colibria.android.sipservice.sip.tx.ITransactionRepositoryListener;
import com.colibria.android.sipservice.sip.tx.TransactionRepository;
import com.colibria.android.sipservice.threadpool.ThreadPool;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * A Service for android, showing how the sip-stack can be used.
 *
 * @author Sebastian Dehne
 */
public class SipService extends Service implements ISipStackListener, ISipTcpConnectionProvider, ITransactionRepositoryListener {
    private static final String TAG = "SipService";

    public static final String VERSION_STRING = "Android SIP Provider: 0.2";

    static {
        Logger.setLOGGER_IMPL(AndroidLogger.getInstance());
    }

    public static final int SIP_SERVICE_STATE_UNCONFIGURED = 1;
    public static final int SIP_SERVICE_STATE_DISABLED = 2;
    public static final int SIP_SERVICE_STATE_CONNECTING = 3;
    public static final int SIP_SERVICE_STATE_CONNECTED = 4;
    public static final int SIP_SERVICE_STATE_REGISTERED = 5;


    private static final String INTENT_TCP_KEEPALIVE = "com.colibria.android.sipservice.SipService.INTENT_TCP_KEEPALIVE";
    private static final String INTENT_RECONNECT = "com.colibria.android.sipservice.SipService.INTENT_RECONNECT";

    private static final int TCP_KEEP_ALIVE_INTERVAL = 1000 * 60 * 28; // 28 minutes
    private static final int RETRY_MAX_VALUE = 1000 * 60 * 60 * 2; // two hours

    private static final String PREF_NAME_SIP_ENABLED = "PREF_NAME_SIP_ENABLED";
    private static final String PREF_NAME_AUTH_USERNAME = "PREF_NAME_AUTH_USERNAME";
    private static final String PREF_NAME_AUTH_PASSWORD = "PREF_NAME_AUTH_PASSWORD";
    private static final String PREF_NAME_SIP_PROXY_HOST = "PREF_NAME_SIP_PROXY_HOST";
    private static final String PREF_NAME_SIP_PROXY_PORT = "PREF_NAME_SIP_PROXY_PORT";
    private static final String PREF_NAME_LOCAL_USER = "PREF_NAME_LOCAL_USER";
    private static final String PREF_NAME_CONFERENCE_FAC_URI = "PREF_NAME_CONFERENCE_FAC_URI";
    private static final String PREF_NAME_RETRY_INTERVAL = "PREF_NAME_RETRY_INTERVAL";
    private static final String PREF_NAME_LOCAL_HOST_ADDRESS = "PREF_NAME_LOCAL_HOST_ADDRESS";

    private static final int PREF_DEFAULT_RETRY_INTERVAL = 1000 * 5; // 5 seconds;

    private static final int REFRESH_REGISTER_INTERVAL = 1;

    public static volatile SipService sInstance;

    private final RegisterController mRegisterController;
    private final TcpController mTcpController;
    private final AtomicBoolean mIsRunning;
    private volatile SharedPreferences mPrefs;
    private volatile TcpConnection mSipConnection;
    private volatile boolean mIsConnected;
    private volatile URI conferenceFacUri;
    private volatile Address myself;
    private volatile PowerManager.WakeLock mWakeLock;
    private volatile SipServiceSettingsActivity activeInstance;

    public SipService() {
        sInstance = this;

        ThreadPool tp = new ThreadPool(2);
        new SipStack(tp);
        SipStack.get().setSipStackListener(this); // send any incoming requests to this class
        SipStack.get().setSipTcpConnectionProvider(this); // this class/service will manage the TCP connection
        mIsRunning = new AtomicBoolean(false);
        mTcpController = TcpController.createPreStartedController(tp);

        mRegisterController = new RegisterController(new IRegisterControllerListener() {
            @Override
            public void setServiceRouteHeaderAddress(Address address) {

            }

            @Override
            public void activeStateReached() {
                notifyListeners();
            }

            @Override
            public void initStateReached() {
                notifyListeners();
            }

            @Override
            public List<RouteHeader> getInitialRouteSet() {
                Address proxyAddress = new Address(new URI(URI.Type.sip, null, null,
                        mPrefs.getString(PREF_NAME_SIP_PROXY_HOST, null),
                        mPrefs.getInt(PREF_NAME_SIP_PROXY_PORT, -1),
                        null,
                        null
                ), "", null);
                return Collections.singletonList(new RouteHeader(proxyAddress));
            }

            @Override
            public Address getSenderAddress() {
                return Address.fromString(mPrefs.getString(PREF_NAME_LOCAL_USER, null));
            }

        }, null, -1);

    }

    private void startKeepAlives() {
        Logger.d(TAG, "startKeepAlives()");
        Intent i = new Intent();
        i.setClass(this, SipService.class);
        i.setAction(INTENT_TCP_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TCP_KEEP_ALIVE_INTERVAL, TCP_KEEP_ALIVE_INTERVAL, pi);
    }

    private void stopKeepAlives() {
        Logger.d(TAG, "stopKeepAlives()");
        Intent i = new Intent();
        i.setClass(this, SipService.class);
        i.setAction(INTENT_TCP_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmMgr.cancel(pi);
    }

    public void stayConnected(InetSocketAddress sipProxy, Address localUser, String converenceFacUri, String authUsername, String authPassword) {
        if (!mIsRunning.get()) {
            mPrefs.edit().putString(PREF_NAME_SIP_PROXY_HOST, sipProxy.getAddress().getHostAddress()).commit();
            mPrefs.edit().putInt(PREF_NAME_SIP_PROXY_PORT, sipProxy.getPort()).commit();
            mPrefs.edit().putString(PREF_NAME_LOCAL_USER, localUser.toString()).commit();
            mPrefs.edit().putString(PREF_NAME_CONFERENCE_FAC_URI, converenceFacUri).commit();
            mPrefs.edit().putString(PREF_NAME_AUTH_USERNAME, authUsername).commit();
            mPrefs.edit().putString(PREF_NAME_AUTH_PASSWORD, authPassword).commit();

            /*
             * Set the local host address, based on the persisted configuration
             */
            if (mPrefs.getString(PREF_NAME_LOCAL_HOST_ADDRESS, null) == null) {
                mPrefs.edit().putString(PREF_NAME_LOCAL_HOST_ADDRESS, (RandomUtil.randomHexString(16)).toLowerCase() + "." + sipProxy.getHostName()).commit();
            }

            stayConnected();
        }
    }

    private void stayConnected() {
        if (mIsRunning.compareAndSet(false, true)) {
            SipStack.get().setLocalAddress(mPrefs.getString(PREF_NAME_LOCAL_HOST_ADDRESS, null));

            mSipConnection = mTcpController.createNewManagedConnection(new InetSocketAddress(mPrefs.getString(PREF_NAME_SIP_PROXY_HOST, null), mPrefs.getInt(PREF_NAME_SIP_PROXY_PORT, -1)), new ITcpConnectionListener() {

                int keepAliveCounter = 0;
                int refreshRegisterInterval = 0;
                int refreshSubscribeInterval = 0;

                @Override
                public void dataReceived(ByteBuffer mReadBuffer) {
                    SipStack.get().dataReceived(mReadBuffer);
                }

                @Override
                public void socketConnectionOpened() {
                    Logger.d(TAG, "socketConnectionOpened()");
                    resetReconnectRetryInterval();
                    SipStack.get().setLocalAddress(mSipConnection.getLocalSocketAddress());
                    Logger.d(TAG, "Local socket address: " + mSipConnection.getLocalSocketAddress());
                    mIsConnected = true;
                    notifyListeners();
                    mRegisterController.register(false);
                }

                @Override
                public void socketConnectionClosed() {
                    Logger.d(TAG, "socketConnectionClosed()");
                    mIsConnected = false;
                    mRegisterController.kill();
                    scheduleReconnect();
                    notifyListeners();
                }

                @Override
                public void socketConnectFailed() {
                    Logger.d(TAG, "socketConnectFailed()");
                    mIsConnected = false;
                    scheduleReconnect();
                    notifyListeners();
                }

                @Override
                public void sendKeepAliveNow() {
                    boolean sentSomethingOverTcp = false;
                    if (++refreshRegisterInterval > REFRESH_REGISTER_INTERVAL) {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Sending re-REGISTER now");
                        }
                        mRegisterController.register(false);
                        refreshRegisterInterval = 0;
                        sentSomethingOverTcp = true;
                    }

                    if (!sentSomethingOverTcp) {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, "Sending LF as keep-alive now");
                        }
                        mSipConnection.write(ByteBuffer.wrap(TcpConnection.KEEP_ALIVE), null, null);
                    }
                }
            });
            conferenceFacUri = URI.fromString(mPrefs.getString(PREF_NAME_CONFERENCE_FAC_URI, ""));
            myself = Address.fromString(mPrefs.getString(PREF_NAME_LOCAL_USER, ""));
            mSipConnection.reconnect();
            startKeepAlives();

            // remember that we are enabled
            mPrefs.edit().putBoolean(PREF_NAME_SIP_ENABLED, true).commit();

        }
    }

    public void giveUp() {
        // remember that we are disabled
        mPrefs.edit().putBoolean(PREF_NAME_SIP_ENABLED, false).commit();
        if (mIsRunning.compareAndSet(true, false)) {
            stopKeepAlives();
            mSipConnection.close();
            mSipConnection = null;
            resetReconnectRetryInterval();
            mRegisterController.kill();
            SipStack.get().getTxRepository().clear();
        }
    }

    public int getCurrentState() {
        if (mPrefs.getString(PREF_NAME_SIP_PROXY_HOST, null) == null) {
            return SIP_SERVICE_STATE_UNCONFIGURED;
        }

        if (mIsRunning.get()) {
            if (mIsConnected) {
                if (mRegisterController.isRegistered()) {
                    return SIP_SERVICE_STATE_REGISTERED;
                } else {
                    return SIP_SERVICE_STATE_CONNECTED;
                }
            } else {
                return SIP_SERVICE_STATE_CONNECTING;
            }
        } else {
            return SIP_SERVICE_STATE_DISABLED;
        }
    }

    public URI getConferenceFacUri() {
        return conferenceFacUri;
    }

    public Address getMyself() {
        return myself;
    }

    public String getAuthUsername() {
        return mPrefs.getString(PREF_NAME_AUTH_USERNAME, null);
    }

    public String getAuthPassword() {
        return mPrefs.getString(PREF_NAME_AUTH_PASSWORD, null);
    }

    public List<RouteHeader> getInitialRouteSet() {
        return Collections.singletonList(
                new RouteHeader(
                        new Address(
                                new URI(
                                        URI.Type.sip,
                                        null,
                                        null,
                                        mPrefs.getString(PREF_NAME_SIP_PROXY_HOST, null),
                                        mPrefs.getInt(PREF_NAME_SIP_PROXY_PORT, -1),
                                        null,
                                        null),
                                null,
                                null
                        )
                )
        );
    }

    @Override
    public void onCreate() {
        Logger.d(TAG, "onCreate() - enter");
        super.onCreate();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        TransactionRepository.setListener(this);

        mPrefs = getSharedPreferences(TAG, MODE_PRIVATE);
        Logger.d(TAG, "onCreate() - leave");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Logger.d(TAG, "onStart() - enter");
        super.onStart(intent, startId);

        // are we expected to be running/stayConnected?
        if (mPrefs.getBoolean(PREF_NAME_SIP_ENABLED, false)) {
            SipStack.get().getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    stayConnected();
                }
            });
        }

        final TcpConnection connection = mSipConnection;
        if (connection != null
                && intent != null
                && intent.getAction() != null) {

            // trigger keep-alive for any connection the Controller might manage
            if (intent.getAction().equals(INTENT_TCP_KEEPALIVE)) {
                mTcpController.sendKeepAlives();
            }

            // reconnect our TCP connection
            else if (intent.getAction().equals(INTENT_RECONNECT)) {
                connection.reconnect();
            }
        }

        Logger.d(TAG, "onStart() - leave");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void processRequest(final Request request) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Received a new request - " + request.getMethod());
        }

        // not handling any incoming requests for now
        Response response = request.createResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST);
        response.setHeader(new WarningHeader("Nobody is perfect"));
        response.send();
    }

    @Override
    public void reportTransactionCount(int transactionCounter) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "reportTransactionCount() - " + transactionCounter);
        }
        //noinspection SynchronizeOnNonFinalField
        synchronized (mWakeLock) {
            if (transactionCounter > 0) {
                if (!mWakeLock.isHeld())
                    mWakeLock.acquire();
            } else {
                if (mWakeLock.isHeld())
                    mWakeLock.release();
            }
        }
    }

    @Override
    public void writeToTcpConnection(ByteBuffer bb, Runnable whenDone, Runnable whenError) {
        TcpConnection connection = mSipConnection;
        if (connection != null)
            connection.write(bb, whenDone, whenError);
    }

    @Override
    public void parseError() {
        TcpConnection connection = mSipConnection;
        if (connection != null)
            connection.reconnect();
    }


    public void setActiveInstance(SipServiceSettingsActivity activeInstance) {
        this.activeInstance = activeInstance;
    }

    public void notifyListeners() {
        SipServiceSettingsActivity activeInstance = this.activeInstance;
        if (activeInstance != null) {
            activeInstance.stateChanged();
        }
    }

    private void scheduleReconnect() {
        // schedule a one-time fire timer to be fired very soon!
        Intent i = new Intent();
        i.setClass(this, SipService.class);
        i.setAction(INTENT_RECONNECT);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        int fireIn = getNextRetryInterval();
        Logger.i(TAG, "Setting alarm to fire in " + (fireIn / 1000) + " seconds");
        alarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + fireIn, pi);
    }

    private int getNextRetryInterval() {
        int current = mPrefs.getInt(PREF_NAME_RETRY_INTERVAL, PREF_DEFAULT_RETRY_INTERVAL);
        mPrefs.edit().putInt(PREF_NAME_RETRY_INTERVAL, Math.min(current * 2, RETRY_MAX_VALUE)).commit();
        return current;
    }

    private void resetReconnectRetryInterval() {
        mPrefs.edit().remove(PREF_NAME_RETRY_INTERVAL).commit();
    }

}

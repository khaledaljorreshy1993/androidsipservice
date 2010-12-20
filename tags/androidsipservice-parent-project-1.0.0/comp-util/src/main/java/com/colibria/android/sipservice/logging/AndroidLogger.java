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
package com.colibria.android.sipservice.logging;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Sebastian Dehne
 */
public class AndroidLogger implements ILogger {
    private static final String TAG = "AndroidLogger";

    private static final AndroidLogger instance = new AndroidLogger();

    public static AndroidLogger getInstance() {
        return instance;
    }

    private final SimpleDateFormat TIMESTAMP_FMT = new SimpleDateFormat("[HH:mm:ss] ");
    private final PrintWriter mWriter;

    public AndroidLogger() {
        String file = Environment.getExternalStorageDirectory().getAbsolutePath() + "/androidclient-" + android.os.Process.myPid() + ".log";

        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss");
        File logFile = new File(file + "." + df.format(new Date()));

        PrintWriter tmp = null;
        try {
            tmp = new PrintWriter(logFile);
        } catch (IOException e) {
            write(Logger.Level.ERROR, TAG, "Could not open", e);
        }
        mWriter = tmp;

        write(Logger.Level.DEBUG, TAG, "Log opened", null);
    }

    public synchronized void write(Logger.Level level, String tag, String message, Throwable t) {

        try {

            message = "[" + Thread.currentThread().getName() + "] " + message;

            final String logLevelStr;
            switch (level) {
                case DEBUG:
                    logLevelStr = "[DEBUG] ";
                    if (t != null)
                        Log.d(tag, message, t);
                    else
                        Log.d(tag, message);
                    break;
                case INFO:
                    logLevelStr = "[INFO] ";
                    if (t != null)
                        Log.i(tag, message, t);
                    else
                        Log.i(tag, message);
                    break;
                default:
                    logLevelStr = "[ERROR] ";
                    if (t != null)
                        Log.e(tag, message, t);
                    else
                        Log.e(tag, message);
                    break;
            }


            if (mWriter != null) {
                mWriter.append(logLevelStr).append(" - ");
                mWriter.append(TIMESTAMP_FMT.format(new Date())).append(" - ");
                mWriter.append(tag).append(": ");
                mWriter.append(message);
                if (t != null) {
                    t.printStackTrace(mWriter);
                }
                mWriter.append('\n');
                mWriter.flush();
            }
        } catch (Throwable th) {
            Log.e(TAG, "", th);
        }
    }


    public void close() throws IOException {
        if (mWriter != null) {
            mWriter.close();
        }
    }


}

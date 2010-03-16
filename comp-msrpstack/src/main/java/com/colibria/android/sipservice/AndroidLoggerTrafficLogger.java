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

import com.colibria.android.sipservice.headers.*;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.tx.Participant;

import java.nio.ByteBuffer;

/**
 * log4j implementation of the traffic logger. Not a good idea for
 * production environments :-)
 *
 * @author Sebastian Dehne
 */
public class AndroidLoggerTrafficLogger implements IMsrpTrafficLogger {
    private static final String TAG = "AndroidLoggerTrafficLogger";

    private static final int LOG_TAIL_SIZE = 25;
    private static final int LOG_HEAD_SIZE = 350;
    private static final byte[] SPLIT_STR = ("...<skipping to tail section (last " + LOG_TAIL_SIZE + " bytes)>...").getBytes(IMsrpResources.CHARTSET_UTF8);

    public void logincomingData(Participant receiver, MsrpSendRequest data) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Msrp data received from " + receiver + ":\n" + data);
        }
    }

    public void logoutgoingData(Participant instance, MsrpSendRequest.ChunkType chunkType, IMsrpMessage baseRequest, ByteRange overrideByteRange, String overrideTransactionID, MsrpPath overrideFrom, MsrpPath overrideTo, Continuation overrideContinuation, byte[] payload, ByteBuffer bb) {
        logoutgoingData2(instance, bb);
    }

    private void logoutgoingData2(Participant instance, ByteBuffer bb) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {

            StringBuffer sb = new StringBuffer("About to send following data for participant " + instance + ":\r\n");
            byte[] tmp;

            // log entire msg
            if (bb.position() <= (LOG_HEAD_SIZE + LOG_TAIL_SIZE)) {
                tmp = new byte[bb.position()];
                System.arraycopy(bb.array(), bb.arrayOffset(), tmp, 0, tmp.length);
            }

            // log only head and tail, leaving the middle unlogged
            else {
                tmp = new byte[LOG_HEAD_SIZE + SPLIT_STR.length + LOG_TAIL_SIZE];
                int pos = 0;

                // copy the head
                System.arraycopy(bb.array(), bb.arrayOffset(), tmp, pos, LOG_HEAD_SIZE);
                pos += LOG_HEAD_SIZE;

                // copy the skip string
                System.arraycopy(SPLIT_STR, 0, tmp, pos, SPLIT_STR.length);
                pos += SPLIT_STR.length;

                // copy the tail string
                System.arraycopy(bb.array(), bb.arrayOffset() + (bb.position() - LOG_TAIL_SIZE), tmp, pos, LOG_TAIL_SIZE);
            }

            sb.append(new String(tmp, IMsrpResources.CHARTSET_UTF8));
            Logger.d(TAG, sb.toString());

        }

    }
}

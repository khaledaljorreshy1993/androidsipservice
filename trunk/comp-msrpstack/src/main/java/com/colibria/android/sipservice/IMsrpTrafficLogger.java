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
import com.colibria.android.sipservice.tx.Participant;

import java.nio.ByteBuffer;


/**
 * Interface between the msrp-stack and some logger implementation
 *
 * @author Sebastian Dehne
 */
public interface IMsrpTrafficLogger {

    /**
     * Called when new data was received.
     *
     * @param receiver the participant which received the data and which calls this method
     * @param data     the data received
     */
    public void logincomingData(Participant receiver, MsrpSendRequest data);

    /**
     * Called by the msrp stack before data is transmitted
     *
     * @param instance              the participant which is about to send this data and which calls this method
     * @param chunkType             the type of data which is being sent
     * @param baseRequest           the message which the outbound data is based on, maybe null
     * @param overrideByteRange     If non-null, this byteRange replaces the one in the baseRequest
     * @param overrideTransactionID If non-null, this transactionId replaces the one in the baseRequest
     * @param overrideFrom          If non-null, this from-path replaces the one in the baseRequest
     * @param overrideTo            If non-null, this to-path replaces the one in the baseRequest
     * @param overrideContinuation  If non-null, this continuation replaces the one in the baseRequest
     * @param payload               If non-null, this replaces the one in the baseRequest
     * @param data                  the byteBuffer which contains the data to be sent. The byteBuffer is in a state
     *                              after it has been filled with data and no flip() has been called yet; thus
     *                              limit equals to capacity & position points to the end of the content. The
     *                              implementations is NOT ALLOWED to modify this buffer IN ANY WAY! A copy
     *                              should be made first if modification is required.
     */
    public void logoutgoingData(Participant instance,
                                MsrpSendRequest.ChunkType chunkType,
                                IMsrpMessage baseRequest,
                                ByteRange overrideByteRange,
                                String overrideTransactionID,
                                MsrpPath overrideFrom,
                                MsrpPath overrideTo,
                                Continuation overrideContinuation,
                                byte[] payload,
                                ByteBuffer data);

}

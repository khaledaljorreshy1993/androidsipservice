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
package com.colibria.android.sipservice.endpoint.messagebuffer;

import com.colibria.android.sipservice.endpoint.api.IMessageContentStore;
import com.colibria.android.sipservice.logging.Logger;

import java.nio.ByteBuffer;


/**
 * @author Sebastian Dehne
 */
public class MemoryBasedMessageCollector implements IMessageContentStore {
    private static final String TAG = "MemoryBasedMessageCollector";

    private final Object attachment;
    private final ByteBuffer bb;

    private volatile boolean errorOccured = false;
    private volatile boolean wasAborted = false;
    private volatile long bytesReceived;

    public MemoryBasedMessageCollector(Object attachment, int bufferSize) {
        this.attachment = attachment;
        bb = ByteBuffer.allocate(bufferSize);
        bytesReceived = 0;
    }

    public void receivingFinished(boolean wasAborted) {
        this.wasAborted = wasAborted;
        bb.flip();
    }

    public long store(long start, byte[] content, int offSet, int len) {
        if (errorOccured) {
            return len;
        }

        try {
            if (start >= 0)
                bb.position((int) start);
            bb.put(content, offSet, len);
            bytesReceived += len;
        } catch (Exception e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Could not store received content into buffer: ", e);
            }
            errorOccured = true;
        }
        return len;
    }

    public long getBytesReceivedSoFar() {
        return bytesReceived;
    }

    public ByteBuffer getByteBuffer() {
        return bb;
    }

    public boolean hasError() {
        return errorOccured;
    }

    public boolean wasAborted() {
        return wasAborted;
    }

    public Object getAttachment() {
        return attachment;
    }
}

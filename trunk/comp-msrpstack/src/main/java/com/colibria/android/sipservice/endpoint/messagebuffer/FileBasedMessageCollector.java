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

import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.endpoint.api.IMessageContentStore;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;


/**
 * @author Sebastian Dehne
 */
public class FileBasedMessageCollector implements IMessageContentStore {
    private static final String TAG = "FileBasedMessageCollector";

    private static final String TMP_FILE_PREFIX = "msrpendpoint-";
    private static final String TMP_FILE_SUFIX = null;

    private final Object attachment;
    private final File file;
    private RandomAccessFile randomAccessFile;

    private volatile boolean errorOccured = false;
    private volatile boolean wasAborted = false;
    private volatile long bytesReceived;

    public FileBasedMessageCollector(Object attachment) {
        this.attachment = attachment;
        bytesReceived = 0;
        File tmp = null;
        try {
            tmp = File.createTempFile(TMP_FILE_PREFIX, TMP_FILE_SUFIX);
            tmp.deleteOnExit();
        } catch (IOException e) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Could not open tmp-file and therfore won't be able to store the content:", e);
            }
            errorOccured = true;
        }
        file = tmp;
    }

    public FileBasedMessageCollector(File file, Object attachment) {
        this.attachment = attachment;
        this.file = file;
        bytesReceived = 0;
    }

    public void receivingFinished(boolean wasAborted) {
        this.wasAborted = wasAborted;
        if (randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException e) {
                //void
            }
            randomAccessFile = null;
        }
    }

    public long store(long start, byte[] content, int offSet, int len) {
        if (isReady()) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "start=" + start + ", offset=" + offSet + ", len=" + len);
            }
            try {
                if (start >= 0)
                    randomAccessFile.seek(start);
                randomAccessFile.write(content, offSet, len);
                bytesReceived += len;
            } catch (IOException e) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Could not write recevied content to file", e);
                }
                errorOccured = true;
            }
        } else {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "not ready");
            }
        }
        return len;
    }

    public long getBytesReceivedSoFar() {
        return bytesReceived;
    }

    private boolean isReady() {
        if (errorOccured) {
            return false;
        }

        if (randomAccessFile == null) {
            try {
                if (!file.exists())
                    file.createNewFile();
                randomAccessFile = new RandomAccessFile(file, "rw");
            } catch (Exception e) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Could not open tmp-file and therfore won't be able to store the content:", e);
                }
                errorOccured = true;
                return false;
            }
        }

        return true;
    }

    /**
     * May return null
     *
     * @return the file in which the content is stored into
     */
    public File getFile() {
        return file;
    }

    public void releaseResources() {
        if (file != null) {
            file.delete();
        }
    }

    public boolean isErrorOccured() {
        return errorOccured;
    }

    public boolean wasAborted() {
        return wasAborted;
    }

    public Object getAttachment() {
        return attachment;
    }
}

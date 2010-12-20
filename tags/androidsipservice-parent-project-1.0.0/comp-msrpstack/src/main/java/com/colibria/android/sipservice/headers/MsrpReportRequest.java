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
package com.colibria.android.sipservice.headers;


import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sebastian Dehne
 */
public class MsrpReportRequest implements IMsrpMessage {

    private final String transactionID;
    private final String messageID;
    private final MsrpPath toPath;
    private final MsrpPath fromPath;
    private final ByteRange byteRange;
    private final StatusHeader status;
    private final Map<String, String> exHeaders;
    private final int msgSizeOnWire;

    public MsrpReportRequest(String transID,
                             String messageID,
                             MsrpPath toPath,
                             MsrpPath fromPath,
                             ByteRange byteRange,
                             StatusHeader status,
                             Map<String, String> exHeaders,
                             int msgSizeOnWire) {
        this.transactionID = transID;
        this.messageID = messageID;
        this.toPath = toPath;
        this.fromPath = fromPath;
        this.byteRange = byteRange;
        this.status = status;
        this.msgSizeOnWire = msgSizeOnWire;

        Map<String, String> map = new HashMap<String, String>();
        if (exHeaders != null) {
            for (String key : exHeaders.keySet()) {
                map.put(key, exHeaders.get(key));
            }
        }
        this.exHeaders = Collections.unmodifiableMap(map);

    }

    public Type getType() {
        return Type.report;
    }

    public String getTransactionID() {
        return transactionID;
    }

    public String getMessageID() {
        return messageID;
    }

    public MsrpPath getToPath() {
        return toPath;
    }

    public MsrpPath getFromPath() {
        return fromPath;
    }

    public ByteRange getByteRange() {
        return byteRange;
    }

    public StatusHeader getStatus() {
        return status;
    }

    public String getExHeader(String name) {
        return exHeaders.get(name);
    }

    public void marshall(ByteBuffer dst) {
        //todo
    }

    public int getSize() {
        return msgSizeOnWire;
    }
}

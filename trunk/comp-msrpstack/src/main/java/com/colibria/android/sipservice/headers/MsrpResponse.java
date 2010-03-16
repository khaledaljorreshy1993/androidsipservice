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


import com.colibria.android.sipservice.io.MsrpParser;

import java.nio.ByteBuffer;

/**
 * @author Sebastian Dehne
 */
public class MsrpResponse implements IMsrpMessage {

    public static ResponseCode RESPONSE_200_OK = new ResponseCode(200, "OK");
    public static ResponseCode RESPONSE_481_CHUNK_TOO_LARGE = new ResponseCode(481, "Chunk to large");
    public static ResponseCode RESPONSE_400_MESSAGE_STATE_NOT_FOUND = new ResponseCode(400, "Cound not find any existing message state");
    public static ResponseCode RESPONSE_481 = new ResponseCode(481, "No such session");
    public static ResponseCode RESPONSE_413 = new ResponseCode(413, "Abort");
    public static ResponseCode RESPONSE_506 = new ResponseCode(506, "Already bound");

    private final String transactionID;
    private final int statusCode;
    private final String statusString;
    private final MsrpPath toPath;
    private final MsrpPath fromPath;
    private final int msgSizeOnWire;

    public MsrpResponse(String transactionID, int statusCode, String statusString, MsrpPath toPath, MsrpPath fromPath, int msgSizeOnWire) {
        this.transactionID = transactionID;
        this.statusCode = statusCode;
        this.statusString = statusString;
        this.toPath = toPath;
        this.fromPath = fromPath;
        this.msgSizeOnWire = msgSizeOnWire;
    }

    public Type getType() {
        return Type.response;
    }

    public String getTransactionID() {
        return transactionID;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusString() {
        return statusString;
    }

    public MsrpPath getToPath() {
        return toPath;
    }

    public MsrpPath getFromPath() {
        return fromPath;
    }

    public int getSize() {
        return msgSizeOnWire;
    }

    public String toString() {
        return "MsrpResponse: \r\n" + (toStringBuffer().toString());
    }

    public static MsrpResponse create(MsrpSendRequest request, ResponseCode responseCode) {
        return new MsrpResponse(request.getTransactionID(), responseCode.statusCode, responseCode.statusText, request.getFromPath(), request.getToPath(), 0);
    }

    private StringBuffer toStringBuffer() {
        StringBuffer sb = new StringBuffer();
        sb.append("MSRP").append(" ").append(transactionID).append(" ").append(statusCode).append(" ").append(statusString).append("\r\n");
        sb.append(MsrpParser.MSRP_HEADER_TOPATH).append(": ").append(toPath).append("\r\n");
        sb.append(MsrpParser.MSRP_HEADER_FROMPATH).append(": ").append(fromPath).append("\r\n");
        sb.append("-------").append(transactionID).append("$").append("\r\n");
        return sb;
    }

    public void marshall(ByteBuffer dst) {
        byte[] data = toStringBuffer().toString().getBytes();
        dst.put(data, 0, data.length);
    }

    public static class ResponseCode {
        final int statusCode;
        final String statusText;

        public ResponseCode(int statusCode, String statusTxt) {
            this.statusCode = statusCode;
            this.statusText = statusTxt;
        }
    }

}

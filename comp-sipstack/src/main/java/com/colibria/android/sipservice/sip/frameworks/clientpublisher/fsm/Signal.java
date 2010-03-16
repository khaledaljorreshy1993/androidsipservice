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
package com.colibria.android.sipservice.sip.frameworks.clientpublisher.fsm;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.sip.messages.Response;

/**
 * @author Sebastian Dehne
 */
public class Signal {

    public static Signal getPublishSignal(MimeType contentType, byte[] content, String eTag) {
        return new Signal(Type.sendPublish, contentType, content, null, eTag);
    }

    public static Signal getTerminateSignal() {
        return new Signal(Type.terminate, null, null, null, null);
    }

    public static Signal getResponseSignal(Response responseReceived) {
        if (Response.ClassXX.RC2xx.isClass(responseReceived.getStatusCode())) {
            return new Signal(Type.response2xx, null, null, responseReceived, null);
        } else if (responseReceived.getStatusCode() == Response.INTERVAL_TOO_BRIEF) {
            return new Signal(Type.response423, null, null, responseReceived, null);
        } else if (responseReceived.getStatusCode() == Response.CONDITIONAL_REQUEST_FAILED) {
            return new Signal(Type.response412, null, null, responseReceived, null);
        } else {
            return new Signal(Type.terminate, null, null, responseReceived, null);
        }
    }

    public static enum Type {

        /**
         * Triggers the publisher to publish new state
         */
        sendPublish,

        response2xx,

        response423,
        response412,

        /**
         * Terminates the publisher OR signals that a pending transaction has timed out
         */
        terminate

    }


    private final Type type;
    private final Response response;
    private final String eTag;
    private final MimeType contentType;
    private final byte[] content;

    public Signal(Type type, MimeType contentType, byte[] content, Response response, String eTag) {
        this.type = type;
        this.contentType = contentType;
        this.content = content;
        this.response = response;
        this.eTag = eTag;
    }

    public String toString() {
        return type.toString();
    }

    public boolean isType(Type type) {
        return this.type == type;
    }

    public MimeType getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public Response getResponse() {
        return response;
    }

    public String geteTag() {
        return eTag;
    }
}

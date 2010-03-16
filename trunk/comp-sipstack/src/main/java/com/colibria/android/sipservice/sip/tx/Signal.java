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
package com.colibria.android.sipservice.sip.tx;

import com.colibria.android.sipservice.sip.messages.Request;
import com.colibria.android.sipservice.sip.messages.Response;

/**
 * @author Sebastian Dehne
 */
public class Signal {


    enum Type {
        request,
        response,
        timeout,
        ackKill
    }

    enum Method {
        ACK, BYE, CANCEL, INVITE, OPTIONS, REGISTER, PUBLISH, NOTIFY, SUBSCRIBE, MESSAGE, REFER, INFO, PRACK, UPDATE, Unknown
    }

    private Request request;
    private Response response;
    private int statusCode;

    private Type type;
    private Method method;
    private TimerID timer;

    private boolean passResponse;
    private boolean passRequest;

    public Signal(Request request) {
        this.request = request;
        this.type = Type.request;
        this.passRequest = false;
        try {
            this.method = Method.valueOf(request.getMethod());
        } catch (IllegalArgumentException e) {
            this.method = Method.Unknown;
        }
    }

    public Signal(Response response) {
        this.response = response;
        this.type = Type.response;
        this.statusCode = response.getStatusCode();
        this.passResponse = false;
    }

    public Signal(TimerID timer) {
        this.type = Type.timeout;
        this.timer = timer;
    }

    public Signal(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public Method getMethod() {
        return method;
    }

    public Request getRequest() {
        return request;
    }

    public Response getResponse() {
        return response;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public TimerID getTimer() {
        return timer;
    }

    void setPassResponse(boolean flag) {
        this.passResponse = flag;
    }

    public boolean getPassResponse() {
        return passResponse;
    }

    public boolean getPassRequest() {
        return passRequest;
    }

    public void setPassRequest(boolean passRequest) {
        this.passRequest = passRequest;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("Signal ");

        switch (type) {
            case request:
                sb.append("Request:").append(getMethod());
                break;
            case response:
                sb.append("Response: ").append(getStatusCode());
                break;
            case timeout:
                sb.append("Timeout: ").append(getTimer());
                break;
            default:
                sb.append("Unknown ");
        }
        return sb.toString();
    }


}

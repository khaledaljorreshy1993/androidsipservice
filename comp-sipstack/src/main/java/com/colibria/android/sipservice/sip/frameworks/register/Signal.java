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
package com.colibria.android.sipservice.sip.frameworks.register;

import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.messages.Response;

/**
 * @author Sebastian Dehne
 */
public class Signal {
    private static final String TAG = "RegisterControllerSignal";

    public static Signal getResponseSignal(Response response) {
        Type type;
        if (response.getStatusCode() == Response.OK || response.getStatusCode() == Response.ACCEPTED) {
            if (response.getStatusCode() == Response.ACCEPTED) {
                Logger.w(TAG, "Received a 202, don't know what to do with that yet");
            }
            type = Type.ok;
        } else if (response.getStatusCode() == Response.UNAUTHORIZED) {
            type = Type.unauth;
        } else if (response.getStatusCode() == Response.CONDITIONAL_REQUEST_FAILED) {
            type = Type.cond_req_failed;
        } else {
            type = Type.error_or_timeout;
        }
        return new Signal(type, response);
    }

    public static enum Type {
        init, ok, error_or_timeout, not_acceptable_here, unauth, cond_req_failed,

        reg_send_register, reg_send_unregister, reg_terminate,
    }

    public static Signal createTimeoutSignal() {
        return new Signal(Type.error_or_timeout, null);
    }

    public static Signal createSendUnregisterSignal() {
        return new Signal(Type.reg_send_unregister, null);
    }

    public static Signal createSendRegisterSignal() {
        return new Signal(Type.reg_send_register, null);
    }

    public static Signal createInitSignal() {
        return new Signal(Type.init, null);
    }


    private final Type type;
    private final Response response;

    private Signal(Type type, Response response) {
        this.type = type;
        this.response = response;
    }

    public boolean isType(Type type) {
        return this.type == type;
    }

    public Response getResponse() {
        return response;
    }

    public String toString() {
        return type.toString();
    }
}

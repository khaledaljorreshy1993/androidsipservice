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
package com.colibria.android.sipservice.sip.frameworks.clientsubscription.fsm;

import com.colibria.android.sipservice.sip.messages.Notify;
import com.colibria.android.sipservice.sip.messages.Response;


/**
 * @author Arild Nilsen
 * @author Sebastian Dehne
 */
public class Signal {

    public static final Signal SUBSCRIBE = new Signal(Type.subscribe, null, null);
    public static final Signal REFRESH = new Signal(Type.refresh, null, null);
    public static final Signal TERMINATE = new Signal(Type.terminate, null, null);
    public static final Signal FETCH = new Signal(Type.fetch, null, null);

    public static Signal getKillSignal() {
        return new Signal(Type.kill, null, null);
    }

    public static Signal getResponseSignal(Response response) {
        return new Signal(Type.response, null, response);
    }

    public static Signal getNotifyReceivedSignal(Notify notify) {
        return new Signal(Type.notify, notify, null);
    }

    public static Signal getSubscribeTimeoutSignal() {
        return new Signal(Type.subscribeTimeout, null, null);
    }

    public static Signal getNotifyTimeoutSignal() {
        return new Signal(Type.notifyTimeout, null, null);
    }


    enum Type {
        subscribe, fetch, refresh, terminate, notify, response, subscribeTimeout, notifyTimeout, kill
    }

    private final Type type;
    private final Notify notify;
    private final Response response;

    private Signal(Type type, Notify notify, Response response) {
        this.type = type;
        this.notify = notify;
        this.response = response;
    }

    public Response getResponse() {
        return response;
    }

    public Notify getNotify() {
        return notify;
    }

    public Type getType() {
        return type;
    }

    public boolean isType(Type type) {
        return this.type == type;
    }

    public String toString() {
        return type.toString();
    }
}
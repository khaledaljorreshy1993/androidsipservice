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

import com.colibria.android.sipservice.fsm.Condition;
import com.colibria.android.sipservice.sip.frameworks.clientsubscription.ClientSubscriptionBase;
import com.colibria.android.sipservice.sip.headers.SubscriptionStateHeader;
import com.colibria.android.sipservice.sip.messages.Response;

import static com.colibria.android.sipservice.sip.frameworks.clientsubscription.fsm.Signal.Type.*;
import static com.colibria.android.sipservice.sip.messages.Response.*;

/**
 * @author Arild Nilsen
 * @author Sebastian Dehne
 */
public abstract class SubscriptionCondition extends Condition<Signal, ClientSubscriptionBase> {

    public enum ExpiresState {
        doNotCheck, greaterThanZero, lessThanOrEqualsZero
    }

    public static final SubscriptionCondition C_SUBSCRIBE = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(subscribe);
        }
    };
    public static final SubscriptionCondition C_FETCH = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(fetch);
        }
    };
    public static final SubscriptionCondition C_UNSUBSCRIBE = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(terminate);
        }
    };
    public static final SubscriptionCondition C_OK_ACCEPT_NOTIFY_REQUIRED = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            Response r = signal.getResponse();
            return signal.isType(response)
                    && (r.getStatusCode() == OK || r.getStatusCode() == ACCEPTED)
                    && (clientSubscriptionBase.isInitialOrTerminatingTransaction() || r.getExpiresTime() <= 0);
        }
    };
    public static final SubscriptionCondition C_OK_ACCEPT_NOTIFY_NOT_REQUIRED = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            Response r = signal.getResponse();
            return signal.isType(response)
                    && (r.getStatusCode() == OK || r.getStatusCode() == ACCEPTED)
                    && (!clientSubscriptionBase.isInitialOrTerminatingTransaction() && r.getExpiresTime() > 0);
        }
    };
    public static final SubscriptionCondition C_SUBSCRIPTION_DOES_NOT_EXIST = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(response) && signal.getResponse().getStatusCode() == SUBSCRIPTION_DOES_NOT_EXIST;
        }
    };
    public static final SubscriptionCondition C_SUBSCRIPTION_EXP_TOO_BRIEF = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(response) && signal.getResponse().getStatusCode() == Response.INTERVAL_TOO_BRIEF;
        }
    };
    public static final SubscriptionCondition C_RESPONSE_TRYING = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(response) && signal.getResponse().getStatusCode() == Response.TRYING;
        }
    };
    public static final SubscriptionCondition C_4XXRESPONSE_REFRESH_SUBS = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase owner) {
            return signal.getResponse() != null && signal.getResponse().isResponseClass(Response.ClassXX.RC4xx) && !owner.isInitialOrTerminatingTransaction(); // this is a response for a refresh subscribe
        }
    };
    public static final SubscriptionCondition C_RESPONSE_ANY = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(response);
        }
    };
    public static final SubscriptionCondition C_NOTIFY_ACTIVE_PENDING = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            if (signal.isType(notify) && signal.getNotify() != null) {
                SubscriptionStateHeader sth = signal.getNotify().getHeader(SubscriptionStateHeader.NAME);
                return (sth.getState() == SubscriptionStateHeader.Substate.active || sth.getState() == SubscriptionStateHeader.Substate.pending);
            }
            return false;
        }
    };
    public static final SubscriptionCondition C_NOTIFY_TERMINATED = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            if (signal.isType(notify) && signal.getNotify() != null) {
                SubscriptionStateHeader sth = signal.getNotify().getHeader(SubscriptionStateHeader.NAME);
                return (sth.getState() == SubscriptionStateHeader.Substate.terminated || sth.getState() == SubscriptionStateHeader.Substate.waiting);
            }
            return false;
        }
    };
    public static final SubscriptionCondition C_REFRESH = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(refresh);
        }
    };
    public static final SubscriptionCondition C_SUBSCRIBE_TIMEOUT = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(subscribeTimeout);
        }
    };
    public static final SubscriptionCondition C_NOTIFY_TIMEOUT = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(notifyTimeout);
        }
    };
    public static final SubscriptionCondition C_KILL = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return signal.isType(Signal.Type.kill);
        }
    };

    public static final SubscriptionCondition C_ANY = new SubscriptionCondition() {
        public boolean satisfiedBy(Signal signal, ClientSubscriptionBase clientSubscriptionBase) {
            return true;
        }
    };
}
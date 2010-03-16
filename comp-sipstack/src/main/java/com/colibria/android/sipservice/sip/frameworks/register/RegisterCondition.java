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

import com.colibria.android.sipservice.fsm.Condition;

/**
 * @author Sebastian Dehne
 */
public class RegisterCondition extends Condition<Signal, RegisterController> {

    public static final RegisterCondition INIT = new RegisterCondition(Signal.Type.init);
    public static final RegisterCondition UNAUTHENTICATED = new RegisterCondition(Signal.Type.unauth);
    public static final RegisterCondition SEND_REGISTER = new RegisterCondition(Signal.Type.reg_send_register);
    public static final RegisterCondition OK = new RegisterCondition(Signal.Type.ok);
    public static final RegisterCondition SEND_UNREGISTER = new RegisterCondition(Signal.Type.reg_send_unregister);
    public static final RegisterCondition TIMEOUT = new RegisterCondition(Signal.Type.error_or_timeout);

    private final Signal.Type type;

    private RegisterCondition(Signal.Type type) {
        this.type = type;
    }

    public boolean satisfiedBy(Signal signal, RegisterController machine) {
        return signal.isType(type);
    }

}

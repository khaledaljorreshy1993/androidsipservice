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

import com.colibria.android.sipservice.fsm.Condition;
import com.colibria.android.sipservice.sip.messages.Ack;
import com.colibria.android.sipservice.sip.messages.Invite;

/**
 */
public class TransactionCondition extends Condition<Signal, TransactionBase> {

    public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
        return false;
    }

    static final TransactionCondition C_REQUEST = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.request;
        }
    };

    static final TransactionCondition C_INVITE = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.request &&
                    Invite.NAME.equals(signal.getMethod().toString());
        }
    };
    static final TransactionCondition C_NON_INVITE_REQUEST = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.request &&
                    !Invite.NAME.equals(signal.getMethod().toString());
        }
    };

    static final TransactionCondition C_ACK = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.request &&
                    Ack.NAME.equals(signal.getMethod().toString());
        }
    };
    static final TransactionCondition C_ACK_KILL = new TransactionCondition() {
        @Override
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.ackKill;
        }
    };

    static final TransactionCondition C_XXX_RESP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.response;
        }
    };


    static final TransactionCondition C_2XX_RESP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.response &&
                    (signal.getStatusCode() >= 200 && signal.getStatusCode() < 300);
        }
    };

    static final TransactionCondition C_PROV_RESP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.response &&
                    (signal.getStatusCode() >= 100 && signal.getStatusCode() < 200);
        }

    };

    static final TransactionCondition C_101_199_RESP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.response &&
                    (signal.getStatusCode() >= 101 && signal.getStatusCode() < 200);
        }

    };

    static final TransactionCondition C_200_699_RESP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.response &&
                    (signal.getStatusCode() >= 200 && signal.getStatusCode() < 700);
        }
    };
    static final TransactionCondition C_300_699_RESP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.response &&
                    (signal.getStatusCode() >= 300 && signal.getStatusCode() < 700);
        }
    };

    static final TransactionCondition C_TIMER_A_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.A;
        }
    };

    static final TransactionCondition C_TIMER_B_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.B;
        }
    };

    static final TransactionCondition C_TIMER_D_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.D;
        }
    };

    static final TransactionCondition C_TIMER_E_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.E;
        }
    };

    static final TransactionCondition C_TIMER_F_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.F;
        }
    };

    static final TransactionCondition C_TIMER_G_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.G;
        }
    };

    static final TransactionCondition C_TIMER_H_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.H;
        }
    };

    static final TransactionCondition C_TIMER_I_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.I;
        }
    };

    static final TransactionCondition C_TIMER_J_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.J;
        }
    };

    static final TransactionCondition C_TIMER_K_EXP = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return signal.getType() == Signal.Type.timeout &&
                    signal.getTimer() == TimerID.K;
        }
    };

    static final TransactionCondition C_ANY = new TransactionCondition() {
        public boolean satisfiedBy(Signal signal, TransactionBase transactionBase) {
            return true;
        }
    };

}

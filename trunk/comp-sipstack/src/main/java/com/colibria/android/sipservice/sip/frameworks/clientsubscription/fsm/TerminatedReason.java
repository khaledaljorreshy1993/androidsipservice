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

import com.colibria.android.sipservice.sip.messages.ReasonCode;

/**
 * This class differes somewhat from the ReasonCode class, since it also contains 'error'.
 *
 * @author Sebastian Dehne
 */
public enum TerminatedReason {

    error,
    timeout,
    noresource,
    deactivated,
    probation,
    rejected,
    giveup,
    badfilter;

    public static TerminatedReason fromReasonCode(ReasonCode r) {
        if (r == null) {
            throw new IllegalArgumentException("cannot be null");
        }

        switch (r) {
            case Badfilter:
                return badfilter;
            case Deactivated:
                return deactivated;
            case GiveUp:
                return giveup;
            case NoResource:
                return noresource;
            case Probation:
                return probation;
            case Rejected:
                return rejected;
            case Timeout:
                return timeout;
            default:
                return timeout;
        }
    }


}

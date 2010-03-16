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
package com.colibria.android.sipservice.sip.frameworks.uagent.fsm;


import com.colibria.android.sipservice.fsm.State;
import com.colibria.android.sipservice.sip.frameworks.uagent.UAgent2;
import com.colibria.android.sipservice.sip.frameworks.uagent.fsm.Signal;

/**
 * <p/>
 * <code>$Id: $</code>
 *
 * @author Arild Nilsen
 * @version $Revision: $
 */
public class CallState extends State<Signal, UAgent2> {

    public CallState(String name) {
        super(name);
    }

    public void enter(UAgent2 ua, boolean forReenter) {
        //void
    }

    public void exit(UAgent2 ua, boolean reEnter) {
        //void
    }

    public String toString() {
        return getName();
    }
}

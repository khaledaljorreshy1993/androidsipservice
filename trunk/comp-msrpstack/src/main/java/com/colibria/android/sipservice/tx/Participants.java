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
package com.colibria.android.sipservice.tx;

import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.IMsrpApplication;
import com.colibria.android.sipservice.IMsrpResources;
import com.colibria.android.sipservice.headers.MsrpURI;

import java.util.concurrent.ConcurrentHashMap;


/**
 * A container class which maps msrp-uris to their participant instances
 *
 * @author Sebastian Dehne
 */
public class Participants {
    private static final String TAG = "Participants";

    private static final Participants instance = new Participants();

    public static Participants getInstance() {
        return instance;
    }

    private final ConcurrentHashMap<MsrpURI, Participant> participants;

    public Participants() {
        participants = new ConcurrentHashMap<MsrpURI, Participant>();
    }

    public Participant get(MsrpURI localURI) {
        return participants.get(localURI);
    }

    /**
     * Creates a new participant and published it.
     *
     * @param resources      the resources to be used for this instance
     * @param app            the conference instance to which this participant belongs to
     * @param cpimAddr       the address of this participant
     * @param virtualHostKey a string to find a virtual host; may be null
     * @return the newly created participant
     */
    public Participant create(IMsrpResources resources, IMsrpApplication app, Address cpimAddr, String virtualHostKey) {
        boolean added = false;
        Participant newParticipant = null;

        while (!added) {
            newParticipant = new Participant(resources, resources.generateMsrpURI(false, virtualHostKey), app, cpimAddr, app.getOutboundFsmListener());
            if ((participants.putIfAbsent(newParticipant.getLocalMsrpURI(), newParticipant)) == null) {
                added = true;
            } else {
                Logger.w(TAG, "ID generator collision");
            }
        }
        return newParticipant;
    }


    public void unmap(Participant p) {
        participants.remove(p.getLocalMsrpURI());
    }

    public void clear() {
        for(Participant p : participants.values()) {
            p.terminate();
        }
    }
}

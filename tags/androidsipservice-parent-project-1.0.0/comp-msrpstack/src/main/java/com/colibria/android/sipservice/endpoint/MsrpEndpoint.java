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
package com.colibria.android.sipservice.endpoint;

import com.colibria.android.sipservice.IMsrpResources;
import com.colibria.android.sipservice.RandomUtil;
import com.colibria.android.sipservice.TcpController;
import com.colibria.android.sipservice.endpoint.api.IMsrpEndpointApplication;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.tx.Participant;
import com.colibria.android.sipservice.endpoint.api.IEndPointSession;
import com.colibria.android.sipservice.headers.MsrpURI;
import com.colibria.android.sipservice.tx.Participants;

import java.util.concurrent.ScheduledExecutorService;


/**
 * @author Sebastian Dehne
 */
public class MsrpEndpoint implements IMsrpResources {
    public static final String TRANSPORT_TCP = "tcp";

    private final ScheduledExecutorService threadFarm;
    private final TcpController controller;

    public MsrpEndpoint(TcpController controller, ScheduledExecutorService threadFarm) {
        this.controller = controller;
        this.threadFarm = threadFarm;
    }

    public String getNextId() {
        return RandomUtil.nextRandomId();
    }

    public MsrpURI generateMsrpURI(boolean secure, String domain) {
        return new MsrpURI(secure, null, "127.0.0.1", 2855, getNextId(), TRANSPORT_TCP, null);
    }

    public TcpController getController() {
        return controller;
    }

    public ScheduledExecutorService getThreadFarm() {
        return threadFarm;
    }

    /**
     * Adds a new participant to this endpoint
     *
     * @param participantAddr     the cpim address which is assosiated with this participant. This address msut contain a sip-address
     * @param endpointApplication the call-back interface which the endpoint will use for certain events/calls
     * @return a reference to the created endpoint session such that the endpointApplication can communicate with it
     */
    public IEndPointSession addParticipant(Address participantAddr, IMsrpEndpointApplication endpointApplication) {
        EndPointSessionImpl endPointSession = new EndPointSessionImpl(this, endpointApplication);
        Participant p = Participants.getInstance().create(this, endPointSession.getMessageReceiver(), participantAddr, null);
        endPointSession.setParticipant(p);
        return endPointSession;
    }

    public void closeAllConnections() {
        Participants.getInstance().clear();
    }
}

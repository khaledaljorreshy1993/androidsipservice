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
package com.colibria.android.sipservice;

import com.colibria.android.sipservice.headers.MsrpResponse;
import com.colibria.android.sipservice.tx.IOutboundFSMListener;
import com.colibria.android.sipservice.tx.Participant;
import com.colibria.android.sipservice.headers.MsrpSendRequest;

/**
 * Interface which an msrp-application needs to implement in order to receive
 * events/calls from the msrp I/O layer. It is up to the application whether it wants
 * to assosiate a new instance of this IMsrpApplication to each participant or
 * share a certain instance.
 *
 * @author Sebastian Dehne
 */
public interface IMsrpApplication {

    /**
     * A new request was received by the msrp IO layer and is handed off to the application
     *
     * @param participant which participant received the reuqest
     * @param request     the request itself
     * @return in case the request chunk_type indicates that this request (chunk piece) has an end,
     *         this return code is used to generate the response to be sent for this received request
     */
    public MsrpResponse.ResponseCode requestReceived(Participant participant, MsrpSendRequest request);

    /**
     * The I/O layer received a response for a request which was sent.
     *
     * @param messageStateId the id which identifies the message. This is the same id which
     *                       was used at Participant.handleOutgoingRequest(MsrpSendRequest, String)
     * @param participant    the participant calling this method, who received the abort-response
     * @param responseCode   the response status code
     */
    public void responseReceived(String messageStateId, Participant participant, int responseCode);

    /**
     * Notifies the application that a certain participant is now connected and ready to handle traffic
     *
     * @param participant the participant which is now ready to handle traffic
     */
    public void participantActivated(Participant participant);

    /**
     * Notifies the application that a certain participant was terminated. The
     * msrp-layer may call this method without having called participantActiavted(Participant) first
     *
     * @param participant the participant which is now terminated
     */
    public void participantTerminated(Participant participant);

    /**
     * A getter which is used by the msrp-layer in order to query which QoSAgent should be used
     *
     * @return the listener to be contacted for this instance
     */
    public IOutboundFSMListener getOutboundFsmListener();
}

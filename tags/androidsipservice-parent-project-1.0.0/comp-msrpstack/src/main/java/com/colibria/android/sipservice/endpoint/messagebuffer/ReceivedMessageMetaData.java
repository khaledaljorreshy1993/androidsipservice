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
package com.colibria.android.sipservice.endpoint.messagebuffer;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.headers.ContentDispositionHeader;
import com.colibria.android.sipservice.sip.Address;

import java.util.List;


/**
 * @author Sebastian Dehne
 */
public class ReceivedMessageMetaData {

    private final String msgID;
    private final MimeType contentType;
    private final ContentDispositionHeader contentDispositionHeader;
    private final Address originator;
    private final List<Address> destinations;
    private final long expectedMsgSize;

    public ReceivedMessageMetaData(
            String msgID,
            MimeType contentType,
            ContentDispositionHeader contentDispositionHeader,
            Address originator,
            List<Address> destinations,
            long expectedMsgSize) {

        this.msgID = msgID;
        this.destinations = destinations;
        this.contentType = contentType;
        this.contentDispositionHeader = contentDispositionHeader;
        this.originator = originator;
        this.expectedMsgSize = expectedMsgSize;
    }

    public MimeType getContentType() {
        return contentType;
    }

    public ContentDispositionHeader getContentDispositionHeader() {
        return contentDispositionHeader;
    }

    /**
     * Returns the originator
     *
     * @return the originator, taken from the CPIM-From header OR null if no cpim was used
     */
    public Address getOriginator() {
        return originator;
    }

    public String getMsgID() {
        return msgID;
    }

    public long getExpectedMsgSize() {
        return expectedMsgSize;
    }

    /**
     * Returns a list of destinations
     *
     * @return list of destinations, taken from the CPIM-To header. The list will be empty if no
     *         cpim was used
     */
    public List<Address> getDestinations() {
        return destinations;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReceivedMessageMetaData that = (ReceivedMessageMetaData) o;

        return !(msgID != null ? !msgID.equals(that.msgID) : that.msgID != null);

    }

    public int hashCode() {
        return (msgID != null ? msgID.hashCode() : 0);
    }

    public String toString() {
        return ReceivedMessageMetaData.class.getName() + ": msgID=" + msgID + ", contentType=" + contentType + ", contentDispositionHeader=" + contentDispositionHeader + ", originator=" + originator + ", expectedMsgSize=" + expectedMsgSize;
    }
}

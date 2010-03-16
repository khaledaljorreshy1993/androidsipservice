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

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.endpoint.api.IMessageContentStore;
import com.colibria.android.sipservice.endpoint.messagebuffer.ReceivedMessageMetaData;
import com.colibria.android.sipservice.headers.*;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.endpoint.messagebuffer.MessageReassembler;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * @author Sebastian Dehne
 */
public class MessageCollectorFactory {

    private final EndPointSessionImpl parent;

    public MessageCollectorFactory(EndPointSessionImpl parent) {
        this.parent = parent;
    }

    MessageReassembler create(MsrpSendRequest request) {

        Address originator;
        List<ICPIMHeader> tmp = request.getCPIMHeader(CPIMFromHeader.NAME);
        if (tmp != null && tmp.get(0) != null)
            originator = ((CPIMFromHeader) tmp.get(0)).getAsAddress();
        else
            originator = null;

        List<Address> destinations = new LinkedList<Address>();
        if ((tmp = request.getCPIMHeader(CPIMToHeader.NAME)) != null)
            for (ICPIMHeader h : tmp) {
                destinations.add(((CPIMToHeader) h).getAsAddress());
            }

        MimeType contentType = null;
        if ((tmp = request.getCPIMHeader(MimeContentTypeHeader.NAME)) != null) {
            for (ICPIMHeader h : tmp) {
                contentType = ((MimeContentTypeHeader) h).getMimeType();
            }
        }
        if (contentType == null) {
            contentType = request.getContentType();
        }

        ContentDispositionHeader contentDispositionHeader = null;
        if ((tmp = request.getCPIMHeader(ContentDispositionHeader.NAME)) != null)
            for (ICPIMHeader h : tmp) {
                contentDispositionHeader = (ContentDispositionHeader) h;
            }
        if (contentDispositionHeader == null) {
            contentDispositionHeader = request.getContentDispositionHeader();
        }


        ReceivedMessageMetaData receviedMessageMetaData = new ReceivedMessageMetaData(
                request.getMessageID(),
                contentType,
                contentDispositionHeader,
                originator,
                Collections.unmodifiableList(destinations),
                request.getByteRange().getTotal()
        );

        IMessageContentStore store = parent.getApplication().getNewMessageCollector(receviedMessageMetaData);

        return new MessageReassembler(parent, receviedMessageMetaData, store);

    }
}

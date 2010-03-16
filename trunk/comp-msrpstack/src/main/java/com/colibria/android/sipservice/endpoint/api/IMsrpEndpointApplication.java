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
package com.colibria.android.sipservice.endpoint.api;

import com.colibria.android.sipservice.endpoint.messagebuffer.ReceivedMessageMetaData;


/**
 * The interface which the endpoint application must implement such that the endpoint can communicate with it
 *
 * @author Sebastian Dehne
 */
public interface IMsrpEndpointApplication {

    /**
     * A new message is about to arrive.
     * <p/>
     * Requests the endpoint application to return a new message messageReassembler
     * in which the msg content will be stored.
     *
     * @param metaData information describing the msg which is to be collected
     * @return a storage for the incoming message
     */
    public IMessageContentStore getNewMessageCollector(ReceivedMessageMetaData metaData);

    /**
     * Notifies the endpoint application that more bytes hve just been written to the messageContentStore
     *
     * @param msgID     the Message-ID received in the first chunk of the message
     * @param byteCount the number of bytes receivd so far
     * @param totalSize expected msg size (same value as provided in metaData when getNewMessageCollector() was called
     * @param store     the store to which the bytes were written to
     */
    public void moreBytesRecevied(String msgID, long byteCount, long totalSize, IMessageContentStore store);

    /**
     * After the receiving process of a message was completed, the message is delivered to the application via this
     * method.
     *
     * @param msgID the Message-ID received in the first chunk of the message
     * @param metaData information describing the msg which is to be collected
     * @param contentStore the store to which the bytes were written to
     */
    public void messageRecevied(String msgID, ReceivedMessageMetaData metaData, IMessageContentStore contentStore);

    /**
     * Notifies the application that this session is now connected and able to handle traffic
     */
    public void connected();

    /**
     * Notifies the application that this session is now termainted and that all assosiated resources should be released
     */
    public void terminated();

}

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

/**
 * A call-back listener to provide the sender information about the sending process
 *
 * @author Sebastian Dehne
 */
public interface ISendingListener {

    /**
     * The receiver of this msg requests to abort sending this message.
     *
     * @param msgID the msgId of the message which must be aborted
     */
    public void abortSendingMsg(String msgID);

    /**
     * The msrp-stack is now able to send more bytes for this message.
     *
     * @param msgID the msgId of the message which is now ready for more data
     */
    public void readyForMore(String msgID);
}

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
 * An interface to some kind of msg store were all received bytes are handled.
 * <p/>
 * Please note that the CPIM header will sent to this storage
 *
 * @author Sebastian Dehne
 */
public interface IMessageContentStore {

    /**
     * Called when receiving of the message has ended
     *
     * @param wasAbortedOrTimedOut true if the msg stream was aborted. In that case,
     *                             the application must not expect all data to be received in the store
     */
    public void receivingFinished(boolean wasAbortedOrTimedOut);

    /**
     * Stores the received content somewhere
     *
     * @param start   where in the complete msg this piece of data should be stored to.
     *                Should were left off when set to -1
     * @param content the data
     * @param offSet  where in the data the actual content starts
     * @param len     how many bytes of the data should be read
     * @return the number of bytes which were recevied at this interation
     */
    public long store(long start, byte[] content, int offSet, int len);

    /**
     * Returns the number of bytes this store has received so far
     *
     * @return number of bytes this storeage has received so far
     */
    public long getBytesReceivedSoFar();

}

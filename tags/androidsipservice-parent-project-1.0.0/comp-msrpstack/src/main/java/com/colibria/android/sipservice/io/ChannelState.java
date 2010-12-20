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
package com.colibria.android.sipservice.io;

import com.colibria.android.sipservice.IMsrpResources;
import com.colibria.android.sipservice.ITcpConnectionListener;
import com.colibria.android.sipservice.TcpConnection;
import com.colibria.android.sipservice.headers.IMsrpMessage;
import com.colibria.android.sipservice.headers.MsrpResponse;
import com.colibria.android.sipservice.headers.MsrpSendRequest;
import com.colibria.android.sipservice.headers.MsrpURI;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.tx.Participant;
import com.colibria.android.sipservice.tx.OutboundFSM;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Sebastian Dehne
 */
public class ChannelState implements ITcpConnectionListener {
    private static final String TAG = "ChannelState";

    public static final Runnable DO_NOTHING = new Runnable() {
        public void run() {
        }
    };

    private static final ConcurrentHashMap<InetSocketAddress, ChannelState> states = new ConcurrentHashMap<InetSocketAddress, ChannelState>();

    public static ChannelState getOrCreate(IMsrpResources resources, Participant p, InetSocketAddress targetHost) {
        ChannelState state = null, existingState;
        boolean added = false;
        boolean createdNewInstance = false;

        // loop until we have an instance which is usable
        while (!added) {
            if ((state = states.get(targetHost)) == null) {
                state = new ChannelState(resources, targetHost);
                if ((existingState = states.putIfAbsent(targetHost, state)) != null) {
                    state = existingState;
                } else {
                    createdNewInstance = true;
                }
            }
            added = state.register(p);
        }

        if (!createdNewInstance && state.tcpConnection.getUnSafeConnectionState() == TcpConnection.ConnectionState.connected) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Connection already up, re-using it");
            }
            p.connectionUp(state);
        } else {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Connection not up, hoping it will establish soon...");
            }
        }

        return state;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    protected void remove(InetSocketAddress key) {
        states.remove(key);
    }

    private final InetSocketAddress targetHost;
    private final IMsrpResources parentInstance;
    private final ConcurrentHashMap<MsrpURI, Participant> participants;
    private volatile TcpConnection tcpConnection;
    private final MsrpParser parser;


    // mutable state guarded by "this"
    private LifeState lifeCycleState; // completely steered by the register/unregister calls from the Participant

    private ChannelState(IMsrpResources parentInstance, InetSocketAddress targetHost) {
        this.parentInstance = parentInstance;
        this.targetHost = targetHost;
        this.participants = new ConcurrentHashMap<MsrpURI, Participant>();
        parser = new MsrpParser();

        synchronized (this) {
            lifeCycleState = LifeState.unavailable;
        }
    }

    /**
     * Called by grizzly in case a request was received
     *
     * @param request the received request
     */
    public void handleRequest(MsrpSendRequest request) {
        final MsrpURI localURI = request.getToPath().getFirst();
        Participant participant;

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Channel " + toString() + " received message: " + request.toString());
        }

        participant = findParticipantLocally(localURI);

        if (participant == null) {
            ByteBuffer bb = OutboundFSM.getReadBuffer();
            MsrpResponse.create(request, MsrpResponse.RESPONSE_481).marshall(bb);
            writeAsync(bb, DO_NOTHING, DO_NOTHING);
        } else {
            participant.handleIncomingRequest(request);
        }
    }

    /**
     * Called by grizzly when a response was received
     *
     * @param msrpResponse the received response
     */
    public void handleResponse(MsrpResponse msrpResponse) {
        final MsrpURI localURI = msrpResponse.getToPath().getFirst();
        Participant participant = findParticipantLocally(localURI);

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Channel " + toString() + " received message: " + msrpResponse.toString());
        }

        // if found, route response to it
        if (participant != null) {
            participant.handleIncomingResponse(msrpResponse);
        }

        // else ignore the response
        else {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Forced to ignore this response since no participant could be found for it");
            }
        }
    }

    public void writeAsync(ByteBuffer data, final Runnable whenOne, final Runnable onIOException) {
        data.flip();
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Channel " + toString() + " is about to write " + data.limit() + " bytes of data");
        }
        tcpConnection.write(data, whenOne, onIOException);
    }

    /**
     * Called ONLY by the participant to register itself to this channel
     *
     * @param participant the participant to register
     * @return true upon success
     */
    public synchronized boolean register(Participant participant) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            int oldState = participants.size();
            participants.put(participant.getLocalMsrpURI(), participant);
            Logger.d(TAG, oldState + " -> " + participants.size());
        } else {
            participants.put(participant.getLocalMsrpURI(), participant);
        }
        updateLifeCycle();
        return true;
    }

    /**
     * Called ONLY by the participant to unregister itself from this channel
     *
     * @param participant the participant to unregister
     */
    public synchronized void unregister(Participant participant) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            int oldState = participants.size();
            participants.remove(participant.getLocalMsrpURI());
            Logger.d(TAG, oldState + " -> " + participants.size());
        } else {
            participants.remove(participant.getLocalMsrpURI());
        }
        updateLifeCycle();
    }

    public void close() {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "Closing channel");
        }
        // 1. kick all remaining participants
        kickParticipantsAsync();

        // 2. close channel
        tcpConnection.close();
    }

    public InetSocketAddress getLocalAddr() {
        TcpConnection tcpConnection = this.tcpConnection;
        if (tcpConnection != null) {
            return tcpConnection.getLocalSocketAddress();
        } else {
            return null;
        }
    }

    public InetSocketAddress getRemoteAddr() {
        return targetHost;
    }

    public String toString() {
        return "ChannelState: {local:" + getLocalAddr() + "; remote:" + getRemoteAddr() + "}";
    }

    private void kickParticipantsAsync() {
        final List<Participant> tmpList = new LinkedList<Participant>();
        for (Participant p : participants.values()) {
            tmpList.add(p);
        }
        // cannot call into Participant when owning the channelState lock
        if (tmpList.size() > 0)
            parentInstance.getThreadFarm().execute(new Runnable() {
                public void run() {
                    for (Participant p : tmpList) {
                        p.terminate();
                    }
                }
            });
    }

    private Participant findParticipantLocally(MsrpURI localURI) {
        return participants.get(localURI);
    }

    private void updateLifeCycle() {
        if (participants.size() > 0) {
            if (lifeCycleState == LifeState.unavailable) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Changing state to available");
                }
                lifeCycleState = LifeState.available;
                tcpConnection = parentInstance.getController().createNewManagedConnection(targetHost.getHostName(), targetHost.getPort(), this);
                tcpConnection.reconnect();
            }
        } else {
            if (lifeCycleState == LifeState.available) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Changing state to unavailable and closing channel");
                }
                lifeCycleState = LifeState.unavailable;
                close(); // all participant have left, close channel now
            }
        }
    }

    @Override
    public void dataReceived(ByteBuffer readBuffer) {

        // prepare buffer for reading/parsing
        readBuffer.limit(readBuffer.position());
        readBuffer.position(0);


        boolean hasMoreBytesToParse;
        boolean isExpectingMoreData;

        while (true) {

            // read and parse now
            MsrpParser.State result = parser.parse(readBuffer);
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "MsrpParser said: " + result);
            }

            readBuffer.position(parser.getStoppedAtPosition());
            boolean haveMoreBytes = readBuffer.hasRemaining();

            // we got something usable from the parser
            if (result == MsrpParser.State.chunk_piece_parsed || result == MsrpParser.State.done) {
                final IMsrpMessage parsedObject = parser.getParsedMessage();
                if (result == MsrpParser.State.done) {
                    parser.reset();
                }

                // handle the actual received message async and continue parsing the next msg
                parentInstance.getThreadFarm().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (parsedObject instanceof MsrpSendRequest) {
                            handleRequest((MsrpSendRequest) parsedObject);
                        } else if (parsedObject instanceof MsrpResponse) {
                            handleResponse((MsrpResponse) parsedObject);
                        } else {
                            Logger.i(TAG, "Don't know what to do with " + parsedObject);
                        }
                    }
                });
            }

            // evaluate the parse result
            if (result == MsrpParser.State.cannotProceed) {
                close();
                break;
            }

            // prepare the buffer for the next write iteration (reading from the socket into this buffer)
            if (haveMoreBytes) {
                readBuffer.compact();  // prepares for writing
            } else {
                readBuffer.clear();
            }
            parser.resetBufferPosition();


            isExpectingMoreData = (result == MsrpParser.State.needMoreData ||  // we simply do not have enough data to generate a chunk pice

                    // we had enough data to generate a chunk pice, but we left bytes in the buffer since they look like the start of an end-line
                    (result == MsrpParser.State.chunk_piece_parsed && haveMoreBytes)
            );

            // tell grizzly that we are ready for a new parse iteration (without reading from the network socket)
            hasMoreBytesToParse = (result == MsrpParser.State.done && haveMoreBytes);


            if (isExpectingMoreData || !hasMoreBytesToParse) {
                break; // we need to leave and read from data from the socket now
            }

            // switch back to reading mode for more reading round
            readBuffer.limit(readBuffer.position());
            readBuffer.position(0);

        }
    }

    @Override
    public void socketConnectionOpened() {
        for (Participant p : participants.values()) {
            p.connectionUp(this);
        }
    }

    @Override
    public void socketConnectionClosed() {
        kickParticipantsAsync();
    }

    @Override
    public void socketConnectFailed() {
        for (Participant p : participants.values()) {
            p.outgoingConnectFailed();
        }
    }

    @Override
    public void sendKeepAliveNow() {
        //noinspection LoopStatementThatDoesntLoop
        for (Participant p : participants.values()) {
            p.sendKeepAlive();
            break; // we only need one keep-alive per TCP-connection
        }
    }

    private enum LifeState {
        /**
         * Indicates that the channel has made itself available to other and is active
         */
        available,

        /**
         * Indicates that the channel is not usable
         */
        unavailable
    }
}

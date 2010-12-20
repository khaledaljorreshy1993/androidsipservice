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
package com.colibria.android.sipservice.sip.tx;

import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.ISipStackListener;
import com.colibria.android.sipservice.sip.headers.EventHeader;
import com.colibria.android.sipservice.sip.messages.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Sebastian Dehne
 */
public class TransactionRepository {
    private static final String TAG = "TransactionRepository";

    private static final Set<String> DIALOG_CREATING_METHODS;

    static {
        Set<String> temp = new HashSet<String>(3);
        temp.add(Subscribe.NAME);
        temp.add(Invite.NAME);
        //temp.add(Request.REFER);
        DIALOG_CREATING_METHODS = Collections.unmodifiableSet(temp);
    }

    private static volatile ITransactionRepositoryListener listener;

    public static void setListener(ITransactionRepositoryListener listener) {
        TransactionRepository.listener = listener;
    }

    private final ConcurrentHashMap<String, ClientTransaction> clientTransactions;
    private final ConcurrentHashMap<String, ServerTransaction> serverTransactions;
    private final ConcurrentHashMap<String, Dialog> dialogs;


    public TransactionRepository() {
        this.clientTransactions = new ConcurrentHashMap<String, ClientTransaction>();
        this.serverTransactions = new ConcurrentHashMap<String, ServerTransaction>();
        this.dialogs = new ConcurrentHashMap<String, Dialog>();
    }

    public ClientTransaction getNewClientTransaction(Request sipRequest, IClientTransactionListener listener, Dialog dialog) {
        ClientTransaction clientTransaction = new ClientTransaction(sipRequest, listener, true);
        if (clientTransactions.put(clientTransaction.getTransactionId(), clientTransaction) != null) {
            Logger.w(TAG, "Some existing transaction overridden");
        } else {
            reportRepoSize();
        }

        if (DIALOG_CREATING_METHODS.contains(sipRequest.getMethod())) {
            if (dialog == null) {
                // need to create a dialog
                String dialogId = sipRequest.getDialogId(false, null);
                if ((dialog = getDialog(dialogId)) == null && Configuration.isAutomaticDialogSupport()) {
                    dialog = new Dialog(clientTransaction);
                }
            }
            clientTransaction.setDialog(dialog);
        }
        return clientTransaction;
    }

    public ServerTransaction getNewServerTransaction(Request request, ISipStackListener serverTransactionListener) {
        final String txID = request.getTransactionId(false);

        Logger.d(TAG, "getNewServerTransaction - enter - " + txID);

        ServerTransaction rst, existingRst;
        if ((rst = serverTransactions.get(txID)) == null) {
            rst = new ServerTransaction(request, true, serverTransactionListener);
            if ((existingRst = serverTransactions.putIfAbsent(txID, rst)) != null) {
                rst = existingRst;
            } else {
                reportRepoSize();
            }
        }

        /*
         * In case this is a cancel server transaction, find the matching server invite transaction and associate the
         * application data with this new transaction.
         */
        if (Cancel.NAME.equals(request.getMethod())) {
            ServerTransaction matchingServerInviteTX = serverTransactions.get(request.getTransactionId(true));
            if (matchingServerInviteTX != null) {
                Logger.d(TAG, "Cancel request detected, associating matching invite TX appData with this cancel tx");
                rst.setApplicationData(matchingServerInviteTX.getApplicationData());
            } else {
                Logger.d(TAG, "Cancel request detected, but no matching server invite tx could be found for " + request.getTransactionId(true));
            }
        }

        return rst;
    }

    public ServerTransaction getServerTransaction(SipMessage msg) {
        final String key = msg.getTransactionId(false);
        ServerTransaction serverTransaction;

        if ((serverTransaction = serverTransactions.get(key)) != null && !serverTransaction.isMessagePartOfTransaction(msg)) {
            serverTransaction = null;
        }

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            StringBuffer msgB = new StringBuffer();
            if (key != null) {
                msgB.append(key).append(' ');
            } else {
                msgB.append("scan ");
            }
            if (serverTransaction != null) {
                msgB.append("found");
            } else {
                msgB.append("no hit");
            }
            Logger.d(TAG, msgB.toString());
        }

        return serverTransaction;

    }

    public ClientTransaction getClientTransaction(SipMessage msg) {
        final String key = msg.getTransactionId(false);
        ClientTransaction clientTransaction;

        if ((clientTransaction = clientTransactions.get(key)) != null && !clientTransaction.isMessagePartOfTransaction(msg)) {
            clientTransaction = null;
        }

        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            StringBuffer msgB = new StringBuffer();
            if (key != null) {
                msgB.append(key).append(' ');
            } else {
                msgB.append("scan ");
            }
            if (clientTransaction != null) {
                msgB.append("found");
            } else {
                msgB.append("no hit");
            }
            Logger.d(TAG, msgB.toString());
        }

        return clientTransaction;
    }

    /**
     * Remove transaction.
     *
     * @param sipTransaction the transaction to remove
     */
    public void removeTransaction(TransactionBase sipTransaction) {
        String key = sipTransaction.getOriginalRequest().getTransactionId(false);
        if (sipTransaction instanceof ServerTransaction) {
            serverTransactions.remove(key);
        } else {
            clientTransactions.remove(key);
        }
        reportRepoSize();
    }

    public Dialog getDialog(String dialogId) {
        Dialog dialog = dialogs.get(dialogId);
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, dialogId + " :" + (dialog != null ? "found" : "not found"));
        }
        return dialog;
    }

    public void addDialog(Dialog dialog) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, dialog.getDialogId());
        }

        Dialog existing = dialogs.putIfAbsent(dialog.getDialogId(), dialog);

        if (existing != null && existing != dialog) {
            throw new RuntimeException("Could not add dialog since another dialog instance with the same dialogID already exists");
        }
    }

    public void removeDialog(Dialog dialog) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, dialog.getDialogId());
        }

        String dialogId;
        if ((dialogId = dialog.getDialogId()) == null) {
            if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                Logger.d(TAG, "Couldn't lookup the dialogID, thus couldn't remove dialog");
            }
        } else {
            if (dialogs.remove(dialogId) == null) {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "While removing Dialog from the DialogTable, DialogId " + dialogId + " could not be found?!");
                }
            } else {
                if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                    Logger.d(TAG, "Removed call " + dialog.getDialogId() + " from dialogTable.");
                }
            }
        }
    }

    /**
     * Get the invite client transaction to cancel. Searches the client transaction table for a transaction that
     * matches the given cancel request.
     *
     * @param outgoingCancelRequest the cancel requets
     * @return transaction, if found
     */
    public ClientTransaction findClientInviteTransaction(final Request outgoingCancelRequest) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "enter");
        }

        for (ClientTransaction sipClientTransaction : clientTransactions.values()) {
            if (sipClientTransaction.doesCancelMatchTransaction(outgoingCancelRequest))
                return sipClientTransaction;


        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG))
            Logger.d(TAG, "Could not find transaction for cancel request");

        return null;
    }

    /**
     * Find a matching client SUBSCRIBE to the incoming notify. NOTIFY requests are matched to such
     * SUBSCRIBE requests if they contain the same "Call-ID", a "To" header "tag" parameter which
     * matches the "From" header "tag" parameter of the SUBSCRIBE, and the same "Event" header field.
     * Rules for comparisons of the "Event" headers are described in section 7.2.1. If a matching
     * NOTIFY request contains a "Subscription-State" of "active" or "pending", it creates a new
     * subscription and a new dialog (unless they have already been created by a matching response, as
     * described above).
     *
     * @param notifyMessage the notify request
     * @return the subscribe transaction
     */
    public ClientTransaction findSubscribeTransaction(Request notifyMessage) {
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG))
            Logger.d(TAG, "trying to find a subscribe clienttransaction");

        String thisToTag = notifyMessage.getTo().getTag();
        if (thisToTag != null) {
            EventHeader eventHdr = (EventHeader) notifyMessage.getHeader(EventHeader.NAME);
            if (eventHdr != null) {
                for (ClientTransaction ct : clientTransactions.values()) {
                    String fromTag = ct.getRequest().getFromTag();
                    EventHeader hisEvent = ct.getRequest().getHeader(EventHeader.NAME);
                    // Event header is mandatory but some slopply clients
                    // dont include it.
                    if (hisEvent == null) {
                        continue;
                    }
                    if (ct.getMethod().equals(Subscribe.NAME)
                            && fromTag.equalsIgnoreCase(thisToTag)
                            && eventHdr.match(hisEvent)
                            && notifyMessage.getCallId().equalsIgnoreCase(ct.getRequest().getCallId())) {
                        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
                            Logger.d(TAG, notifyMessage.getTransactionId(false) + " found");
                        }
                        return ct;
                    }
                }
            }
        }
        if (Logger.isLoggable(Logger.Level.DEBUG, TAG)) {
            Logger.d(TAG, "not match");
        }
        return null;
    }

    /**
     * For testing only. Shouldn't be used in production. It also doesn't update the stats
     */
    public void clear() {
        clientTransactions.clear();
        serverTransactions.clear();
        dialogs.clear();
        reportRepoSize();
    }

    public static boolean isDialogCreating(String method) {
        return DIALOG_CREATING_METHODS.contains(method);
    }

    public void reportRepoSize() {
        ITransactionRepositoryListener listener = TransactionRepository.listener;
        if (listener != null) {
           listener.reportTransactionCount(clientTransactions.size() + serverTransactions.size());
        }
    }

}

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
import com.colibria.android.sipservice.RandomUtil;
import com.colibria.android.sipservice.fsm.Machine;
import com.colibria.android.sipservice.sip.SipStack;
import com.colibria.android.sipservice.sip.headers.ViaHeader;
import com.colibria.android.sipservice.sip.messages.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Sebastian Dehne
 */
public abstract class TransactionBase extends Machine<Signal> {
    private static final String TAG = "TransactionBase";

    public static enum Type {
        serverTransaction, clientTransaction
    }

    public static final int T1 = Configuration.getT1();
    public static final int T2 = Configuration.getT2();
    public static final int T4 = Configuration.getT4();

    public static final int TIMER_A_IVAL = Configuration.getTIMER_A_IVAL();
    public static final int TIMER_B = Configuration.getTIMER_B();
    public static final int TIMER_C = Configuration.getTIMER_C();
    public static final int TIMER_D_UDP = Configuration.getTIMER_D_UDP();
    public static final int TIMER_D_TCP = Configuration.getTIMER_D_TCP();
    public static final int TIMER_E_IVAL = Configuration.getTIMER_E_IVAL();
    public static final int TIMER_F = Configuration.getTIMER_F();
    public static final int TIMER_G_IVAL = Configuration.getTIMER_G_IVAL();
    public static final int TIMER_H = Configuration.getTIMER_H();
    public static final int TIMER_I_UDP = Configuration.getTIMER_I_UDP();
    public static final int TIMER_I_TCP = Configuration.getTIMER_I_TCP();
    public static final int TIMER_J_UDP = Configuration.getTIMER_J_UDP();
    public static final int TIMER_J_TCP = Configuration.getTIMER_J_TCP();
    public static final int TIMER_K_UDP = Configuration.getTIMER_K_UDP();
    public static final int TIMER_K_TCP = Configuration.getTIMER_K_TCP();

    /**
     * Lock for guarding the value of dialog
     */
    private final ReentrantLock lock;

    protected String branchId = null;
    protected Dialog dialog;

    protected Request request;
    protected Response lastResponse;
    protected String method;
    protected String transactionId;
    private volatile Object applicationData;


    public TransactionBase(Request rq,TransactionState rtStartState) {
        super(rtStartState);
        setOriginalRequest(rq);
        lock = new ReentrantLock();
    }


    public void setApplicationData(Object applicationData) {
        this.applicationData = applicationData;
    }

    public Object getApplicationData() {
        return applicationData;
    }

    /**
     * Sets the request message that this transaction handles.
     *
     * @param newOriginalRequest Request being handled.
     */
    private void setOriginalRequest(Request newOriginalRequest) {

        // Branch value of topmost Via header
        if (this.request != null) {
            Logger.w(TAG, "Attempting to reset originalRequest");
        }
        // This will be cleared later.

        this.request = newOriginalRequest;

        // just cache the control information so the
        // original request can be released later.
        this.method = newOriginalRequest.getMethod();
        this.transactionId = newOriginalRequest.getTransactionId(false);

        this.branchId = newOriginalRequest.getFirstViaHeader().getBranch();
    }

    protected void asyncUnmap() {
        SipStack.get().getTxRepository().removeTransaction(this);
    }

    abstract boolean isMessagePartOfTransaction(SipMessage messageToTest);

    abstract Type getType();

    public Dialog getDialog() {
        return getDialog(false);
    }

    /**
     * Gets the dialog.
     * <p/>
     * If there is no dialog yet, a new one will be created when the following
     * conditions are met:
     * <p/>
     * - the request is a dialog-creating-request
     * - automaticDialogSupport was enabled or forceCreation was set to true
     * <p/>
     * Lock is used to gaurd the value of dialog being read
     *
     * @param forceCreation override the automaticDialogSupport config parameter
     * @return the dialog
     */
    public Dialog getDialog(boolean forceCreation) {
        lock.lock();
        try {
            // Is this a dialog-creating request?
            Logger.d(TAG, "enter: " + dialog);

            if (dialog == null
                    && TransactionRepository.isDialogCreating(request.getMethod())
                    && (forceCreation || Configuration.isAutomaticDialogSupport())) {
                dialog = new Dialog(this);
                Logger.d(TAG, "creating dialog: " + dialog);
            }
            Logger.d(TAG, "leave - returning dialog: " + dialog);
        } finally {
            lock.unlock();
        }
        return dialog;
    }

    /**
     * Sets the dialog.
     * <p/>
     * Will set the dialog if dialog for the transaction is null
     * or the dialog has the same value as dlg
     * <p/>
     * Lock is used to gard the value of dialog being set to transaction
     *
     * @param dlg the RtDialog object created
     */
    public void setDialog(Dialog dlg) {
        lock.lock();
        try {
            Logger.d(TAG, "enter setDialog" + dlg);
            if ((this.dialog != null) && (this.dialog != dlg)) {
                Logger.e(TAG, this + " already has a dialog: " + this.dialog + " Not accepting new " + dlg);
                throw new IllegalStateException(this + " already has a dialog! Not accepting new " + dlg);
            }
            Logger.d(TAG, this + " is setting new dialog " + dlg);
            this.dialog = dlg;
            Logger.d(TAG, "leave");
        } finally {
            lock.unlock();
        }
    }

    public String getBranchId() {
        //Is there a branch ID in the request?
        if (branchId == null) {
            ViaHeader firstViaHeader = request.getFirstViaHeader();
            if (firstViaHeader != null) {
                branchId = firstViaHeader.getBranch();
            }
            if (branchId == null) {
                branchId = RandomUtil.nextRandomId(16);
            }
        }
        return branchId;
    }

    /**
     * Get the Request that started this transaction.
     *
     * @return daRequest.
     */
    public Request getRequest() {
        return request;
    }

    public Request getOriginalRequest() {
        // hmm.. should be separated into request and original request??
        return request;
    }

    boolean isTransactionOfType(String method) {
        return method.equals(getOriginalRequest().getMethod());
    }

    boolean isInviteTransaction() {
        return isTransactionOfType(Invite.NAME);
    }

    /**
     * Get the transaction Id.
     *
     * @return the transaction-id
     */
    public String getTransactionId() {
        return this.transactionId;
    }


    public String getMethod() {
        return method;
    }

    protected Response getLastResponse() {
        return lastResponse;
    }

    protected void setLastResponse(Response response) {
        lastResponse = response;
    }

    /**
     * A method that can be used to test if an incoming request belongs to this
     * transction. This does not take the transaction state into account when
     * doing the check otherwise it is identical to isMessagePartOfTransaction.
     * This is useful for checking if a CANCEL belongs to this transaction.
     *
     * @param requestToTest is the request to test.
     * @return true if the the request belongs to the transaction.
     */
    public boolean doesCancelMatchTransaction(Request requestToTest) {

        // Branch code in the topmost Via header
        String messageBranch;
        // Flags whether the select message is part of this transaction
        boolean transactionMatches;

        transactionMatches = false;

        if (this.getOriginalRequest() == null || this.getOriginalRequest().getMethod().equals(Cancel.NAME))
            return false;

        // Get the topmost Via header and its branch parameter
        ViaHeader topViaHeader = requestToTest.getFirstViaHeader();
        if (topViaHeader != null) {

            messageBranch = topViaHeader.getBranch();
            if (messageBranch != null) {

                // If the branch parameter exists but
                // does not start with the magic cookie,
                if (!messageBranch.startsWith(Constants.BRANCH_MAGIC_COOKIE)) {

                    // Flags this as old
                    // (RFC2543-compatible) client
                    // version
                    messageBranch = null;

                }

            }

            // If a new branch parameter exists,
            if (messageBranch != null && this.getBranchId() != null) {

                // If the branch equals the branch in
                // this message,
                if (getBranchId().equalsIgnoreCase(messageBranch)
                        && topViaHeader.getSentBy().equals(
                        getOriginalRequest().getFirstViaHeader().getSentBy())) {
                    transactionMatches = true;
                    Logger.d(TAG, "returning  true");
                }

            } else {
                Logger.w(TAG, "Don't know how to handle RFC2543 messages");
            }
        }

        return transactionMatches;
    }


    abstract protected boolean isTerminated();

    private Map<TimerID, ScheduledFuture> timerTasks = Collections.synchronizedMap(new HashMap<TimerID, ScheduledFuture>());

    protected void startTimer(final TimerID timer, int timeOut) {
        Logger.d(TAG, timer + " " + timeOut + " ms" + " tid: " + getTransactionId());

        // Need lock to ensure that Runnable is not invoked before it is put on timerTasks map
        final Object lock = new Object();

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
            Runnable task = new Runnable() {
                public void run() {
                    synchronized (lock) {
                        if (timerTasks.remove(timer) != null) {
                            try {
                                Logger.d(TAG, "timer " + timer + " fired, tid: " + getTransactionId());
                                input(new Signal(timer));
                            } catch (Exception e) {
                                Logger.e(TAG, "Caught exception:", e);
                            }
                        } else {
                            Logger.d(TAG, "Task has been cancelled, skipping execution");
                        }
                    }
                }
            };

            timerTasks.put(timer, SipStack.get().getThreadPool().schedule(task, timeOut, TimeUnit.MILLISECONDS));
        }
    }

    protected void cancelTimer(TimerID timer) {
        Logger.d(TAG, timer + " tid: " + getTransactionId());
        ScheduledFuture sf = timerTasks.remove(timer);
        if (sf != null) {
            sf.cancel(false); // this is a bit redundant, since the task will not be executed after it has been removed from the collection anyway
            Logger.d(TAG, "found and cancelled");
        } else {
            Logger.d(TAG, "not found");
        }
    }

    protected boolean isTimerRunning(TimerID timer) {
        return timerTasks.get(timer) != null;
    }

}

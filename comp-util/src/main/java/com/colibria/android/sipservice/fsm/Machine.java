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
package com.colibria.android.sipservice.fsm;

import com.colibria.android.sipservice.logging.Logger;

import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Sebastian Dehne
 */
public class Machine<S> {
    private static final String TAG = "FsmMachine";

    private static volatile int MAX_QUEUED_SIGNALS = 50;

    /**
     * Overrides global queue limit. (Default is 3 signals)
     * <p/>
     * This is a soft limit. In some situations, it must always be
     * possible to queue a signal to ensure correct behaviour.
     *
     * @param maxQueuedSignals number of items the signal queue may contain
     */
    public static void setQueueSizeLimit(int maxQueuedSignals) {
        Machine.MAX_QUEUED_SIGNALS = maxQueuedSignals;
    }

    /*
     * The lock is a lock which is used to proect the FSM
     * from being used by multiple threads.
     */
    private final ReentrantLock lock;

    /*
     * The following state-variables are protected by the lock lock. Therefore,
     * lock.lock/lock.unlock MUST be used before reading and/or writing to
     * those fields!!!
     */
    private S signal; // used for loop prevention and provide the current signal during transitions via the getSignal() method
    private State<S, Machine<S>> currentState;
    private State<S, Machine<S>> targetState;
    private final LinkedList<S> waitingSignals;
    private final Condition waitForState;

    public Machine(State startState) {
        if (startState == null) {
            throw new IllegalArgumentException("startState cannot be null");
        }
        lock = new ReentrantLock();
        lock.lock();
        try {
            waitingSignals = new LinkedList<S>();
            //noinspection unchecked
            currentState = startState;
            waitForState = lock.newCondition();
        } finally {
            lock.unlock();
        }
    }


    /**
     * Get Signal being handled, only valid when machine is handling an input signal
     * <p/>
     * Note: this method can only be used by the same thread which is
     * performing the actual state-transition. Calling it from another thread or calling it when
     * no state-transition is currently being performed makes no sence since
     * this method will block until transition is completed and then return null.
     *
     * @return the signal being handled, null if outside handling
     */
    public S getSignal() {
        lock.lock();
        try {
            return signal;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the target state. This method is only usfull when currently performing a state transition.
     * By calling this method, the implementing class is able to find out which state is being targeted.
     * <p/>
     * This method should always be called by the same thread which also called the input() method.
     *
     * @return the target state when currently in a state transition, else null.
     */
    public State getTargetState() {
        lock.lock();
        try {
            return targetState;
        } finally {
            lock.unlock();
        }
    }

    /**
     * This method is behaviorally equivalent to:
     * <pre>
     *   input(signal, 0)
     * </pre>
     *
     * @param signal the signal to be handles
     * @throws UnhandledConditionException in case of an unhandled condition
     * @throws TransitionActivityException in case there was an exception during the transition
     * @throws InterruptedException        in case the current Thread has been interrupted
     * @throws LoopException               in case the fsm is already performing a state-transition. This can happen if input() is called during activity() by the same thread.
     */
    public void input(S signal) throws UnhandledConditionException, InterruptedException, TransitionActivityException {
        input(signal, 0);
    }

    /**
     * Input signal to the FSM.
     * <p/>
     * If signal is being sent from within a state transition, the signal is queued and handled once the current
     * transition has come to completion.
     * <p/>
     * Important: Calling this method might cause the called to sleep, which might block all resources or lock
     * which is currently owns. Explanation:
     * <p/>
     * If the FSM is currently in a 'blocking' state which doesn't accept this signal, then the calling thread
     * will be put to sleep for max the specified amount of time in milliseconds. If the FSM's state was changed
     * (by another thread) before this timeout then the signal will be re-offered to the FSM for processing.
     * If the specified time elapsed before the signal could be accepted by the FSM, then the
     * signal will be stored in an internal queue for later processing. If the timeout was set to 0 or smaller, then
     * no timeout will be used and the thread will sleep until the FSM has changed to a state which accepts this signal.
     * <p/>
     * In case the number of queued signals reach the queue limit, the very thread will:
     * - ignore the applied signal
     * - get a new signal from by calling getSignalForQueueSizeLimitReached()
     * - and execute this signal instead
     *
     * @param signal  the signal to be handles
     * @param maxWait timeout in milliseconds before giving up. Set to 0 if no timeout should be used.
     * @throws UnhandledConditionException in case of an unhandled condition
     * @throws TransitionActivityException in case there was an exception during the transition
     * @throws InterruptedException        in case the current Thread has been interrupted
     * @throws LoopException               in case the fsm is already performing a state-transition. This can happen if input() is called during activity() by the same thread.
     * @throws WaitingForStateTimeout      in case a timeout occurred while waiting for the currect state
     */
    public void input(S signal, long maxWait) throws UnhandledConditionException, InterruptedException, TransitionActivityException {

        lock.lock();
        try {

            if (this.signal != null) {
                Logger.d(TAG, "Queuing signal '" + signal + "' instead of handling it since current thread is already performing a transition");
                waitingSignals.offer(signal); // we don't need to acquire the lock here, since we already have it :-)
                return;
            }

            /*
            * First, handle the current signal
            */
            try {
                handle(signal, maxWait, false);
            }

            // timeout reached during waiting for a certain state pre-condition
            catch (WaitingForStateTimeout e) {

                // try to queue the signal
                if (waitingSignals.size() < getSignalQueueSizeLimit()) {
                    Logger.d(TAG, "Timeout while waiting for a pre-condition. Giving up for now and queuing signal for later processing");
                    waitingSignals.offer(signal);
                }

                // couldn't queue the signal either
                else {
                    Logger.d(TAG, "Timeout while waiting for a pre-condition. Cannot queue the signal since the size limit has been reached.");
                    S queueSizeLimitReachedSignal = getSignalForQueueSizeLimitReached(currentState);
                    if (queueSizeLimitReachedSignal != null) {
                        handle(queueSizeLimitReachedSignal, 0, true);
                    }
                    throw new TransitionActivityException("Could not handle signal at this point", e);
                }
                return;
            }

            /*
             * Secondly, handle all queued signals
             */
            while ((signal = waitingSignals.poll()) != null) {
                try {
                    handle(signal, maxWait, false);
                } catch (WaitingForStateTimeout e) {
                    Logger.d(TAG, "Timeout while waiting for a pre-condition. Giving up for now and queuing signal for later processing");
                    waitingSignals.addFirst(signal); // place the signal back into the queue
                    break;
                } catch (UnhandledConditionException e) {
                    Logger.e(TAG, "The signal '" + signal + "' from the queue could not be handled", e);
                    break;
                }
            }

        } finally {
            waitForState.signalAll();
            lock.unlock();
        }
    }

    /**
     * Returns the current state of the machine.
     * <p/>
     * Use this method with great care, since the state
     * might have changed shortly after this method returns.
     * <p/>
     * Note: this method will block if being called during an onging transition
     * which is performed by another thread.
     *
     * @return the current state of this machine.
     */
    public State getUnsafeCurrentState() {
        lock.lock();
        try {
            return currentState;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the id of this machine. May be overriden.
     *
     * @return the id
     */
    public String getMachineId() {
        return "";
    }

    /**
     * Called in case the number of signals in the signal queue reached it's limit
     *
     * @param currentState the current state of the FSM which blocks signals
     * @return Should return the signal which this FSM will execute by force, ignoring any blocking states
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public S getSignalForQueueSizeLimitReached(State currentState) {
        // to be overridden
        return null;
    }

    public int getSignalQueueSizeLimit() {
        return MAX_QUEUED_SIGNALS;
    }

    /**
     * Performs a specified task under the condition that the FSM is currently
     * in the sepcified state
     *
     * @param task      the task to be executed
     * @param condition the state in which the FSM must be in order to allow the execution
     * @return result of the callable
     * @throws UnhandledConditionException in case the current FSM state doesn't equal the specified state
     * @throws Exception                   in case the task throws an exception
     */
    public <R> R checkAndPerform(Callable<R> task, State condition) throws Exception {
        lock.lock();
        try {
            if (condition == currentState) {
                return task.call();
            } else {
                throw new UnhandledConditionException("Could not perform task since current state is " + currentState + " while expected state was " + condition);
            }
        } finally {
            lock.unlock();
        }
    }

    private void handle(S signal, long waitTimeoutInMilliseconds, final boolean ignoreBlockingStates) throws UnhandledConditionException, InterruptedException, TransitionActivityException, WaitingForStateTimeout {
        State<S, Machine<S>> targetState;
        Transition<S, Machine<S>> selectedTransition = null;

        try {

            /*
             * Has current thread been interrupted?
             */
            if (Thread.interrupted()) {
                throw new InterruptedException("FSM execution interrupted");
            }

            /*
             * Select a transition:
             *
             * A) In case the current state is a "blocking" state, we will put the current thread to wait
             *    until the FSM has changed to a state where a valid a transition can be selected for the
             *    signal.
             * B) In case the current state is non-blocking, a UnhandledConditionException will be thrown
             *    if no transition can be found which matches the signal and the currentState
             */
            while ((!ignoreBlockingStates && currentState.isBlocking()) && (selectedTransition = currentState.input(signal, this)) == null) {
                Logger.d(TAG, "currentState is blocking and since no transition could be found for signal " + signal + " in " + currentState + ", current thread will put to sleep");

                // if the current thread specified a timeout, we will only wait for this specified time and then throw an exception
                if (waitTimeoutInMilliseconds > 0) {
                    if (!waitForState.await(waitTimeoutInMilliseconds, TimeUnit.MILLISECONDS)) {
                        throw new WaitingForStateTimeout("Timeout while waiting for the next suitable (or non-blocking) state");
                    }
                }

                // no timeout? Wait until we left the current state
                else {
                    waitForState.await();
                }
                Logger.d(TAG, "awaking from sleep");
            }
            if (selectedTransition == null && (selectedTransition = currentState.input(signal, this)) == null) {
                String errorMsg = "Cannot handle signal '" + signal + "' for currentState '" + currentState + "' since no transition has been specified";
                Logger.d(TAG, errorMsg);
                throw new UnhandledConditionException(errorMsg);
            }

            /************************************************************
             * Transition is now selected, perform the actual transition
             ************************************************************/

            /*
             * Disallow re-entrence.
             */
            if (this.signal != null) {
                throw new LoopException("Cannot proceed since the current thread is already performing a transition.");
            }
            this.signal = signal;

            /*
             * Determine the target state
             */
            targetState = selectedTransition.getTargetState();
            boolean reEnter = targetState == currentState;
            this.targetState = targetState;

            /*
             * Let the admin known what we are about to do
             */
            Logger.d(TAG, getMachineId() + " handling signal '" + signal.toString() + "' in currentState '" + currentState + "' targetting state '" + targetState + "'");

            /*
             * Execute the exit-state code
             */
            Logger.d(TAG, getMachineId() + " leave state: " + currentState.getName());
            currentState.exit(this, reEnter);

            /*
             * set the new state
             */
            currentState = targetState;

            /*
             * Execute the transition plus the enter-state code
             */
            handleTransitionActivity(signal, selectedTransition, reEnter);

        } finally {
            this.signal = null;
            this.targetState = null;
        }
    }


    private void handleTransitionActivity(final S signal, final Transition<S, Machine<S>> selectedTransition, final boolean reEnter) throws TransitionActivityException {

        /*
         * Execute the transition
         */
        try {
            selectedTransition.activity(this, signal);
        }

        /*
         * Something went wrong. Change the current state
         * to the exceptionState and abort.
         */
        catch (TransitionActivityException e) {
            if (selectedTransition.getExceptionState() != null) {
                currentState = selectedTransition.getExceptionState();
            } else {
                Logger.i(TAG, "Transition Activity failed, no exception state defined", e);
            }
            throw e;
        }

        /*
         * Execute the enter-state code.
         * This might be the enter-state code of the
         * exception state.
         */
        finally {
            Logger.i(TAG, getMachineId() + " enter state: " + currentState.getName());
            currentState.enter(this, reEnter);
        }
    }
}

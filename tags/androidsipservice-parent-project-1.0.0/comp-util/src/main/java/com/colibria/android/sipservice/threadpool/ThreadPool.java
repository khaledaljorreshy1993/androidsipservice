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
package com.colibria.android.sipservice.threadpool;

import com.colibria.android.sipservice.logging.Logger;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sebastian Dehne
 */
public class ThreadPool implements ScheduledExecutorService {
    private static final String TAG = "ThreadPool";

    protected ThreadFactory createThreadFactory(final String name, final int poolSize) {

        return new ThreadFactory() {
            final AtomicInteger ai = new AtomicInteger(0);

            public Thread newThread(Runnable r) {
                if (ai.get() >= poolSize) {
                    Logger.i(TAG, "Created an additional thread which exceeds the " +
                            "pre-configured poolSize. poolSize=" + poolSize + ", " +
                            "createdCount=" + ai.get());
                }
                Thread t = new Thread(r, name + ai.getAndIncrement());
                t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread t, Throwable e) {
                        Logger.e(TAG, "thread " + t.getName() + " threw exception: ", e);
                    }
                });
                return t;
            }
        };
    }

    private final ScheduledExecutorService pool;

    public ThreadPool(int size) {
        pool = new ScheduledThreadPoolExecutor(size, createThreadFactory("worker", size)) {
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                Throwable realThrowable = null;
                if (r instanceof Future<?>) {
                    try {
                        Future<?> f = (Future<?>) r;
                        if (!f.isCancelled() && f.isDone()) {
                            f.get();
                        }
                    } catch (InterruptedException ie) {
                        Logger.d(TAG, "Execution was interruped: " + ie);
                    } catch (ExecutionException ee) {
                        realThrowable = ee.getCause();
                    } catch (CancellationException ce) {
                        Logger.d(TAG, "Execution was canceled: " + ce);
                    }
                } else {
                    realThrowable = t;
                }
                if (realThrowable != null) {
                    Logger.e(TAG, "thread " + Thread.currentThread().getName() + " threw exception: ", realThrowable);
                }
            }
        };

        ((ThreadPoolExecutor) pool).prestartAllCoreThreads();
        init();

    }

    private void init() {
        /*
         * Since canceled tasks are not removed from the work queue and since
         * they have references to memory object which should be cleaned-up,
         * we schedule a regular purge() here in order to get rid of those
         * objects and make garbage collection possible.
         * todo review this
         */
        pool.scheduleAtFixedRate(new Runnable() {
            public void run() {
                ((ScheduledThreadPoolExecutor) pool).purge();
            }
        }, 60, 60, TimeUnit.SECONDS);

    }

    /**
     * {@inheritDoc}
     */
    public ScheduledFuture<?> schedule(final Runnable command, long delay, TimeUnit unit) {
        final String currentThreadName = Thread.currentThread().getName();
        Logger.d(TAG, "schedule(command, " + delay + ", " + unit + ")");
        return pool.schedule(new Runnable() {
            public void run() {
                Logger.d(TAG, "Starting task which was scheduled by thread " + currentThreadName);
                command.run();
            }
        }, delay, unit);
    }

    /**
     * {@inheritDoc}
     */
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable, long delay, TimeUnit unit) {
        final String currentThreadName = Thread.currentThread().getName();
        Logger.d(TAG, "schedule(callable, " + delay + ", " + unit + ")");
        return pool.schedule(new Callable<V>() {
            public V call() throws Exception {
                Logger.d(TAG, "Starting task which was scheduled by thread " + currentThreadName);
                return callable.call();
            }
        }, delay, unit);
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, long initialDelay, long period, TimeUnit unit) {
        final String currentThreadName = Thread.currentThread().getName();
        Logger.d(TAG, "scheduleAtFixedRate(command, " + initialDelay + ", " + period + ", " + unit + ")");
        return pool.scheduleAtFixedRate(new Runnable() {
            public void run() {
                Logger.d(TAG, "Starting task which was scheduled by thread " + currentThreadName);
                command.run();
            }
        }, initialDelay, period, unit);
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, long initialDelay, long delay, TimeUnit unit) {
        final String currentThreadName = Thread.currentThread().getName();
        Logger.d(TAG, "scheduleWithFixedDelay(command, " + initialDelay + ", " + delay + ", " + unit + ")");
        return pool.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                Logger.d(TAG, "Starting task which was scheduled by thread " + currentThreadName);
                command.run();
            }
        }, initialDelay, delay, unit);
    }

    /**
     * {@inheritDoc}
     */
    public <T> Future<T> submit(final Callable<T> task) {
        final String currentThreadName = Thread.currentThread().getName();
        Logger.d(TAG, "submit(task)");
        return pool.submit(new Callable<T>() {
            public T call() throws Exception {
                Logger.d(TAG, "Starting task which was scheduled by thread " + currentThreadName);
                return task.call();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    public <T> Future<T> submit(final Runnable task, final T result) {
        final String currentThreadName = Thread.currentThread().getName();
        Logger.d(TAG, "submit(task, result)");
        return pool.submit(new Runnable() {
            public void run() {
                Logger.d(TAG, "Starting task which was scheduled by thread " + currentThreadName);
                task.run();
            }
        }, result);
    }

    /**
     * {@inheritDoc}
     */
    public Future<?> submit(final Runnable task) {
        final String currentThreadName = Thread.currentThread().getName();
        Logger.d(TAG, "submit(task)");
        return pool.submit(new Runnable() {
            public void run() {
                Logger.d(TAG, "Starting task which was scheduled by thread " + currentThreadName);
                task.run();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    public void execute(final Runnable command) {
        final String currentThreadName = Thread.currentThread().getName();
        Logger.d(TAG, "execute(command)");
        pool.submit(new Runnable() {
            public void run() {
                Logger.d(TAG, "Starting task which was scheduled by thread " + currentThreadName);
                command.run();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        pool.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        return pool.shutdownNow();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isShutdown() {
        return pool.isShutdown();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isTerminated() {
        return pool.isTerminated();
    }

    /**
     * {@inheritDoc}
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return pool.awaitTermination(timeout, unit);
    }

}

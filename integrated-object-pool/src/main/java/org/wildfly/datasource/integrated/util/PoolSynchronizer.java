/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.datasource.integrated.util;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.AbstractQueuedLongSynchronizer;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public abstract class PoolSynchronizer {

    public static PoolSynchronizer nonFair() {
        return new PoolSynchronizer() {

            private final LongAdder counter = new LongAdder();

            private final AbstractQueuedLongSynchronizer sync = new AbstractQueuedLongSynchronizer() {

                @Override
                protected boolean tryAcquire(long value) {
                    // Advance when counter is greater than value
                    return counter.longValue() > value;

//                if ( counter.longValue() > value) System.out.printf( "     >>>   %s got UNLOCKED!!  (%d > %d)%n", Thread.currentThread().getName(), counter.longValue(), value );
//                else System.out.printf( "  ------  %s got LOCKED on __ %d __ (current %d) %n", Thread.currentThread().getName(), value, counter.longValue() );
//                return counter.longValue() > value;
                }

                @Override
                protected boolean tryRelease(long releases) {
                    counter.add( releases );
//                System.out.printf( "  >>>     %s releases __ %d __%n", Thread.currentThread().getName(), counter.longValue() );
                    return true;
                }

            };

            // --- //

            public int getQueueLength() {
                return sync.getQueueLength();
            }

            public boolean tryAcquire(long nanos) throws InterruptedException {
                return sync.tryAcquireNanos( counter.longValue(), nanos );
            }

            public void releaseConditional() {
                if ( sync.hasQueuedThreads() ) {
                    sync.release( 1 );
                }
            }
        };
    }

    public static PoolSynchronizer monotonic() {
        return new PoolSynchronizer() {

            private final MonotonicCounter counter = new MonotonicCounter();

            private final AbstractQueuedLongSynchronizer sync = new AbstractQueuedLongSynchronizer() {

                @Override
                protected boolean tryAcquire(long value) {
                    // Advance when counter is greater than value
                    return counter.sum() > value;

//                if ( counter.longValue() > value) System.out.printf( "     >>>   %s got UNLOCKED!!  (%d > %d)%n", Thread.currentThread().getName(), counter.longValue(), value );
//                else System.out.printf( "  ------  %s got LOCKED on __ %d __ (current %d) %n", Thread.currentThread().getName(), value, counter.longValue() );
//                return counter.longValue() > value;
                }

                @Override
                protected boolean tryRelease(long releases) {
                    counter.increment();
//                System.out.printf( "  >>>     %s releases __ %d __%n", Thread.currentThread().getName(), counter.longValue() );
                    return true;
                }

            };

            // --- //

            public int getQueueLength() {
                return sync.getQueueLength();
            }

            public boolean tryAcquire(long nanos) throws InterruptedException {
                return sync.tryAcquireNanos( counter.sum(), nanos );
            }

            public void releaseConditional() {
                if ( sync.hasQueuedThreads() ) {
                    sync.release( 1 );
                }
            }
        };
    }

    public abstract int getQueueLength();

    public abstract boolean tryAcquire(long nanos) throws InterruptedException;

    // --- //

    public abstract void releaseConditional();

    private static class MonotonicCounter {

        private final static int kNumCounters = Integer.highestOneBit( Runtime.getRuntime().availableProcessors() ) << 1;
        private final static int CACHE_LINE = 64 / 8;

        private final AtomicLongArray counters = new AtomicLongArray( kNumCounters * CACHE_LINE );

        private int tid2hash() {
            long x = Thread.currentThread().getId();
            x ^= ( x << 21 );
            x ^= ( x >>> 35 );
            x ^= ( x << 4 );
            return (int) ( ( ( kNumCounters - 1 ) & x ) * CACHE_LINE );
        }

        public void increment() {
            counters.getAndIncrement( tid2hash() );
        }

        public long sum() {
            long sum = 0;
            AtomicLongArray localCounters = counters;
            for ( int idx = 0; idx < kNumCounters * CACHE_LINE; idx += CACHE_LINE ) {
                sum += localCounters.get( idx );
            }
            return sum;
        }
    }


}

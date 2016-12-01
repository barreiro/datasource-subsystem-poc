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

package org.wildfly.datasource.impl;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class DefaultMetricsRegistry implements WildFlyDataSourceMetricsRegistry {

    private static final long NANOS_TO_MILLI = 1_000_000;

    private final ConnectionPoolImpl poolImpl;

    private final LongAdder createdCount = new LongAdder();
    private final LongAdder createdDuration = new LongAdder();
    private final LongAdder acquireCount = new LongAdder();
    private final LongAdder acquireDuration = new LongAdder();
    private final LongAdder timeoutCount = new LongAdder();
    private final LongAdder closeCount = new LongAdder();

    private final AtomicLong maxCreatedDuration = new AtomicLong( 0 );
    private final AtomicLong maxAcquireDuration = new AtomicLong( 0 );

    public DefaultMetricsRegistry(ConnectionPoolImpl poolImpl) {
        this.poolImpl = poolImpl;
    }

    private static void setMaxValue(AtomicLong atomicLong, long value) {
        long oldMax = atomicLong.longValue();

        while (value > oldMax) {
            if ( atomicLong.compareAndSet( oldMax, value ) ) {
                break;
            } else {
                // concurrent modification -- retry
                oldMax = atomicLong.longValue();
            }
        }
    }

    @Override
    public long beforeConnectionCreated() {
        return System.nanoTime();
    }

    @Override
    public void afterConnectionCreated(long timestamp) {
        long duration = System.nanoTime() - timestamp;
        createdCount.increment();
        createdDuration.add( duration );
        setMaxValue( maxCreatedDuration, duration );
    }

    @Override
    public long beforeConnectionAcquire() {
        return System.nanoTime();
    }

    @Override
    public void afterConnectionAcquire(long timestamp) {
       long duration = System.nanoTime() - timestamp;
       acquireCount.increment();
       acquireDuration.add( duration);
       setMaxValue( maxAcquireDuration, duration );
    }

    @Override
    public void afterConnectionTimeout() {
        timeoutCount.increment();
    }

    @Override
    public void afterConnectionClose() {
        closeCount.increment();
    }

    // --- //

    @Override
    public long createdCount() {
        return createdCount.longValue();
    }

    @Override
    public double averageCreationTime() {
        return (double) createdDuration.longValue() / createdCount.longValue() / NANOS_TO_MILLI;
    }

    @Override
    public long maxCreationTime() {
        return maxCreatedDuration.longValue() / NANOS_TO_MILLI;
    }

    @Override
    public long totalCreationTime() {
        return createdDuration.longValue() / NANOS_TO_MILLI ;
    }

    @Override
    public long destroyedCount() {
        return timeoutCount.longValue() + closeCount.longValue();
    }

    @Override
    public long timeoutCount() {
        return timeoutCount.longValue();
    }

    @Override
    public long activeCount() {
        return poolImpl.activeCount();
    }

    @Override
    public long maxUsedCount() {
        return poolImpl.maxUsedCount();
    }

    @Override
    public long availableCount() {
        return poolImpl.availableCount();
    }

    @Override
    public double averageBlockingTime() {
        return (double) acquireDuration.longValue() / acquireCount.longValue() / NANOS_TO_MILLI;
    }

    @Override
    public long maxBlockingTime() {
        return maxAcquireDuration.longValue() / NANOS_TO_MILLI;
    }

    @Override
    public long totalBlockingTime() {
        return acquireDuration.longValue() / NANOS_TO_MILLI;
    }

    @Override
    public long awaitingCount() {
        return 0;
    }

    // --- //

    @Override
    public void reset() {
        createdCount.reset();
        createdDuration.reset();
        acquireCount.reset();
        acquireDuration.reset();
        timeoutCount.reset();
        closeCount.reset();

        maxCreatedDuration.set( 0 );
        maxAcquireDuration.set( 0 );
        poolImpl.resetMaxUsedCount();
    }

    // --- //

    @Override
    public String toString() {
        String s1 = "Connections: " + createdCount + " created / " + acquireCount + " acquired / " + closeCount + " closed / " + timeoutCount + " timeout \n";
        String s2 = "Pool: " + availableCount() + " available / " + activeCount() + " active / " + maxUsedCount() + " max\n";
        String s3 = "Created duration: " + averageCreationTime() + "ms average / " + maxCreationTime() + "ms max \n";
        String s4 = "Acquire duration: " + averageBlockingTime() + "ms average / " + maxBlockingTime() + "ms max \n";
        return s1 + s2 + s3 + s4;
    }

}

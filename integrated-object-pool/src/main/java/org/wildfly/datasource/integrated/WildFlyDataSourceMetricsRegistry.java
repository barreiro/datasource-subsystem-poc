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

package org.wildfly.datasource.integrated;

import org.wildfly.datasource.api.WildFlyDataSourceMetrics;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.LongAdder;

import static java.lang.String.format;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public interface WildFlyDataSourceMetricsRegistry extends WildFlyDataSourceMetrics {

    default long beforeConnectionCreated() {
        return 0;
    }

    default void afterConnectionCreated(long timestamp) {}

    default long beforeConnectionAcquire() {
        return 0;
    }

    default void afterConnectionAcquire(long timestamp) {}

    default void afterConnectionTimeout() {}

    default void afterConnectionClose() {}

    // --- //

    class EmptyMetricsRegistry implements WildFlyDataSourceMetricsRegistry {

        @Override
        public String toString() {
            return "Metrics Disabled";
        }

    }

    // --- //

    class DefaultMetricsRegistry implements WildFlyDataSourceMetricsRegistry {

        private static final long NANO_TO_MILLI = 1_000_000;

        private final ConnectionPool connectionPool;

        private final LongAdder createdCount = new LongAdder();
        private final LongAdder createdDuration = new LongAdder();
        private final LongAdder acquireCount = new LongAdder();
        private final LongAdder acquireDuration = new LongAdder();
        private final LongAdder timeoutCount = new LongAdder();
        private final LongAdder closeCount = new LongAdder();

        private static final AtomicLongFieldUpdater<DefaultMetricsRegistry> maxCreated = newUpdater( DefaultMetricsRegistry.class, "maxCreatedDuration" );
        private static final AtomicLongFieldUpdater<DefaultMetricsRegistry> maxAcquire = newUpdater( DefaultMetricsRegistry.class, "maxAcquireDuration" );
        private volatile long maxCreatedDuration = 0;
        private volatile long maxAcquireDuration = 0;

        public DefaultMetricsRegistry(ConnectionPool pool) {
            this.connectionPool = pool;
        }

        private void setMaxValue(AtomicLongFieldUpdater<DefaultMetricsRegistry> updater, long value) {
            long oldMax = updater.get( this );

            while ( value > oldMax ) {
                if ( updater.compareAndSet( this, oldMax, value ) ) {
                    break;
                } else {
                    // concurrent modification -- retry
                    oldMax = updater.get( this );
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
            setMaxValue( maxCreated, duration );
        }

        @Override
        public long beforeConnectionAcquire() {
            return System.nanoTime();
        }

        @Override
        public void afterConnectionAcquire(long timestamp) {
            long duration = System.nanoTime() - timestamp;
            acquireCount.increment();
            acquireDuration.add( duration );
            setMaxValue( maxAcquire, duration );
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
            return (double) createdDuration.longValue() / createdCount.longValue() / NANO_TO_MILLI;
        }

        @Override
        public long maxCreationTime() {
            return maxCreatedDuration / NANO_TO_MILLI;
        }

        @Override
        public long totalCreationTime() {
            return createdDuration.longValue() / NANO_TO_MILLI;
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
            return connectionPool.activeCount();
        }

        @Override
        public long maxUsedCount() {
            return connectionPool.maxUsedCount();
        }

        @Override
        public long availableCount() {
            return connectionPool.availableCount();
        }

        @Override
        public double averageBlockingTime() {
            return (double) acquireDuration.longValue() / acquireCount.longValue() / NANO_TO_MILLI;
        }

        @Override
        public long maxBlockingTime() {
            return maxAcquireDuration / NANO_TO_MILLI;
        }

        @Override
        public long totalBlockingTime() {
            return acquireDuration.longValue() / NANO_TO_MILLI;
        }

        @Override
        public long awaitingCount() {
            return connectionPool.awaitingCount();
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

            maxCreatedDuration = 0;
            maxAcquireDuration = 0;
            connectionPool.resetMaxUsedCount();
        }

        // --- //

        @Override
        public String toString() {
            String s1 = format( "Connections: %s created / %s acquired / %s closed / %s timeout %n", createdCount, acquireCount, closeCount, timeoutCount );
            String s2 = format( "Pool: %d available / %d active / %d max %n", availableCount(), activeCount(), maxUsedCount() );
            String s3 = format( "Created duration: %3.3fµs average / %dms max / %dms total %n", averageCreationTime() * 1000, maxCreationTime(), totalCreationTime() );
            String s4 = format( "Acquire duration: %3.3fµs average / %dms max / %dms total %n", averageBlockingTime() * 1000, maxBlockingTime(), totalBlockingTime() );
            String s5 = format( "Threads awaiting: %d %n", awaitingCount() );
            return s1 + s2 + s3 + s4 + s5;
        }

    }

}
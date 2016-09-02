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

package org.wildfly.datasource.hikari;

import com.zaxxer.hikari.metrics.MetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class HikariMetricsListenerAdaptor extends MetricsTracker {

    public static class Factory implements MetricsTrackerFactory {

        @Override
        public MetricsTracker create(String poolName, PoolStats poolStats) {
            return new HikariMetricsListenerAdaptor( poolName, poolStats );
        }

    }

    // --- //

    private final String poolName;
    private final PoolStats poolStats;

    private HikariMetricsListenerAdaptor(String poolName, PoolStats poolStats) {
        this.poolName = poolName;
        this.poolStats = poolStats;
    }

    // --- //

    private Map<Long, Long> timestamps = new ConcurrentHashMap<>();

    @Override
    public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
        super.recordConnectionAcquiredNanos( elapsedAcquiredNanos );
    }

    @Override
    public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
        super.recordConnectionUsageMillis( elapsedBorrowedMillis );
    }

    @Override
    public void recordConnectionTimeout() {
        super.recordConnectionTimeout();
    }

    // --- //


}

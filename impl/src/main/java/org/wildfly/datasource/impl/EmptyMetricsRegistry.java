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

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class EmptyMetricsRegistry implements WildFlyDataSourceMetricsRegistry {

    @Override
    public long beforeConnectionCreated() {
        return 0;
    }

    @Override
    public void afterConnectionCreated(long timestamp) {}

    @Override
    public long beforeConnectionAcquire() {
        return 0;
    }

    @Override
    public void afterConnectionAcquire(long timestamp) {}

    @Override
    public void afterConnectionTimeout() {}

    @Override
    public void afterConnectionClose() {}

    // --- //

    @Override
    public long createdCount() {
        return 0;
    }

    @Override
    public double averageCreationTime() {
        return 0;
    }

    @Override
    public long maxCreationTime() {
        return 0;
    }

    @Override
    public long totalCreationTime() {
        return 0;
    }

    @Override
    public long destroyedCount() {
        return 0;
    }

    @Override
    public long timeoutCount() {
        return 0;
    }

    @Override
    public long activeCount() {
        return 0;
    }

    @Override
    public long maxUsedCount() {
        return 0;
    }

    @Override
    public long availableCount() {
        return 0;
    }

    @Override
    public double averageBlockingTime() {
        return 0;
    }

    @Override
    public long maxBlockingTime() {
        return 0;
    }

    @Override
    public long totalBlockingTime() {
        return 0;
    }

    @Override
    public long awaitingCount() {
        return 0;
    }

    // --- //

    @Override
    public void reset() {
    }

    // --- //

    @Override
    public String toString() {
        return "Metrics Disabled";
    }
}

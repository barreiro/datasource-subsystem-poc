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

package org.wildfly.datasource.api.configuration;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionPoolConfigurationBuilder {

    private volatile boolean lock;

    private ConnectionPoolConfiguration.PoolImplementation poolImplementation;
    private ConnectionPoolConfiguration.PreFillMode preFillMode;
    private String connectionInitSql;
    private int initialSize;
    private volatile int minSize;
    private volatile int maxSize;
    private volatile int acquisitionTimeout;

    public ConnectionPoolConfigurationBuilder() {
        this.lock = false;
    }

    private void internalCheck() {
        if (lock) {
            throw new IllegalStateException("Attempt to modify an immutable configuration");
        }
    }

    public ConnectionPoolConfigurationBuilder setPoolImplementation(ConnectionPoolConfiguration.PoolImplementation poolImplementation) {
        internalCheck();
        this.poolImplementation = poolImplementation;
        return this;
    }

    public ConnectionPoolConfigurationBuilder setPreFillMode(ConnectionPoolConfiguration.PreFillMode preFillMode) {
        internalCheck();
        this.preFillMode = preFillMode;
        return this;
    }

    public ConnectionPoolConfigurationBuilder setConnectionInitSql(String connectionInitSql) {
        internalCheck();
        this.connectionInitSql = connectionInitSql;
        return this;
    }

    public ConnectionPoolConfigurationBuilder setInitialSize(int initialSize) {
        internalCheck();
        this.initialSize = initialSize;
        return this;
    }

    public ConnectionPoolConfigurationBuilder setMinSize(int minSize) {
        internalCheck();
        this.minSize = minSize;
        return this;
    }

    public ConnectionPoolConfigurationBuilder setMaxSize(int maxSize) {
        internalCheck();
        this.maxSize = maxSize;
        return this;
    }

    public ConnectionPoolConfigurationBuilder setAcquisitionTimeout(int acquisitionTimeout) {
        internalCheck();
        this.acquisitionTimeout = acquisitionTimeout;
        return this;
    }

    public ConnectionPoolConfiguration build() {
        this.lock = true;

        return new ConnectionPoolConfiguration(){

            @Override
            public PoolImplementation getPoolImplementation() {
                return poolImplementation;
            }

            @Override
            public PreFillMode getPreFillMode() {
                return preFillMode;
            }

            @Override
            public String getConnectionInitSql() {
                return connectionInitSql;
            }

            @Override
            public int getInitialSize() {
                return initialSize;
            }

            @Override
            public int getMinSize() {
                return minSize;
            }

            @Override
            public void setMinSize(int size) {
                 minSize = size;
            }

            @Override
            public int getMaxSize() {
                return maxSize;
            }

            @Override
            public void setMaxSize(int size) {
                maxSize = size;
            }

            @Override
            public int getAcquisitionTimeout() {
                return acquisitionTimeout;
            }

            @Override
            public void setAcquisitionTimeout(int timeout) {
                acquisitionTimeout = timeout;
            }
        };
    }

}

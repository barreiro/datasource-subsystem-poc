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
public class DataSourceConfigurationBuilder {

    private volatile boolean lock;

    private String jndiName;
    private long connectionValidationTimeout;
    private long connectionReapTimeout;
    private ConnectionPoolConfiguration connectionPoolConfiguration;
    private DataSourceConfiguration.InterruptHandlingMode interruptHandlingMode;
    private DataSourceConfiguration.PreparedStatementCacheMode preparedStatementCacheMode;
    private DataSourceConfiguration.TransactionIsolation transactionIsolation;
    private volatile boolean metricsEnabled;

    public DataSourceConfigurationBuilder() {
        this.lock = false;
    }

    private void internalCheck() {
        if (lock) {
            throw new IllegalStateException("Attempt to modify an immutable configuration");
        }
    }

    public DataSourceConfigurationBuilder setJndiName(String jndiName) {
        internalCheck();
        this.jndiName = jndiName;
        return this;
    }

    public DataSourceConfigurationBuilder setConnectionValidationTimeout(long connectionValidationTimeout) {
        internalCheck();
        this.connectionValidationTimeout = connectionValidationTimeout;
        return this;
    }

    public DataSourceConfigurationBuilder setConnectionReapTimeout(long connectionReapTimeout) {
        internalCheck();
        this.connectionReapTimeout = connectionReapTimeout;
        return this;
    }

    public DataSourceConfigurationBuilder setConnectionPoolConfiguration(ConnectionPoolConfiguration connectionPoolConfiguration) {
        internalCheck();
        this.connectionPoolConfiguration = connectionPoolConfiguration;
        return this;
    }

    public DataSourceConfigurationBuilder setInterruptHandlingMode(DataSourceConfiguration.InterruptHandlingMode interruptHandlingMode) {
        internalCheck();
        this.interruptHandlingMode = interruptHandlingMode;
        return this;
    }

    public DataSourceConfigurationBuilder setPreparedStatementCacheMode(DataSourceConfiguration.PreparedStatementCacheMode preparedStatementCacheMode) {
        internalCheck();
        this.preparedStatementCacheMode = preparedStatementCacheMode;
        return this;
    }

    public DataSourceConfigurationBuilder setTransactionIsolation(DataSourceConfiguration.TransactionIsolation transactionIsolation) {
        internalCheck();
        this.transactionIsolation = transactionIsolation;
        return this;
    }

    public DataSourceConfigurationBuilder setMetricsEnabled(boolean metricsEnabled) {
        internalCheck();
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    public DataSourceConfiguration build() {
        this.lock = true;

        return new DataSourceConfiguration() {

            @Override
            public String getJndiName() {
                return jndiName;
            }

            @Override
            public long getConnectionValidationTimeout() {
                return connectionValidationTimeout;
            }

            @Override
            public long getConnectionReapTimeout() {
                return connectionReapTimeout;
            }

            @Override
            public ConnectionPoolConfiguration getPoolConfiguration() {
                return connectionPoolConfiguration;
            }

            @Override
            public InterruptHandlingMode getInterruptHandlingMode() {
                return interruptHandlingMode;
            }

            @Override
            public PreparedStatementCacheMode getPreparedStatementCacheMode() {
                return preparedStatementCacheMode;
            }

            @Override
            public TransactionIsolation getTransactionIsolation() {
                return transactionIsolation;
            }

            @Override
            public boolean getMetricsEnabled() {
                return metricsEnabled;
            }

            @Override
            public void setMetricsEnabled(boolean metrics) {
                metricsEnabled = metrics;
            }

        };
    }

}

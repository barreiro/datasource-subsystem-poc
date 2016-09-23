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

import java.util.function.Consumer;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class DataSourceConfigurationBuilder {

    private volatile boolean lock;

    private String jndiName = "";
    private ConnectionPoolConfiguration connectionPoolConfiguration;
    private DataSourceConfiguration.DataSourceImplementation dataSourceImplementation = DataSourceConfiguration.DataSourceImplementation.WILDFLY;


    private volatile boolean metricsEnabled = false;

    public DataSourceConfigurationBuilder() {
        this.lock = false;
    }

    private DataSourceConfigurationBuilder applySetting(Consumer<DataSourceConfigurationBuilder> consumer) {
        if (lock) {
            throw new IllegalStateException("Attempt to modify an immutable configuration");
        }
        consumer.accept( this );
        return this;
    }
    public DataSourceConfigurationBuilder setJndiName(String jndiName) {
        return applySetting( c -> c.jndiName = jndiName );
    }

    public DataSourceConfigurationBuilder setConnectionPoolConfiguration(ConnectionPoolConfiguration connectionPoolConfiguration) {
        return applySetting( c -> c.connectionPoolConfiguration = connectionPoolConfiguration );
    }

    public DataSourceConfigurationBuilder setDataSourceImplementation(DataSourceConfiguration.DataSourceImplementation dataSourceImplementation) {
        return applySetting( c -> c.dataSourceImplementation = dataSourceImplementation );
    }

    public DataSourceConfigurationBuilder setMetricsEnabled(boolean metricsEnabled) {
        return applySetting( c -> c.metricsEnabled = metricsEnabled );
    }

    private void validate() {
        if ( connectionPoolConfiguration == null ) {
            throw new IllegalArgumentException( "Connection poll configuration not defined" );
        }
    }

    public DataSourceConfiguration build() {
        validate();
        this.lock = true;

        return new DataSourceConfiguration() {

            @Override
            public String jndiName() {
                return jndiName;
            }

            @Override
            public ConnectionPoolConfiguration connectionPoolConfiguration() {
                return connectionPoolConfiguration;
            }

            @Override
            public DataSourceImplementation dataSourceImplementation() {
                return dataSourceImplementation;
            }

            @Override
            public boolean metricsEnabled() {
                return metricsEnabled;
            }

            @Override
            public void setMetricsEnabled(boolean metrics) {
                metricsEnabled = metrics;
            }

        };

    }

}

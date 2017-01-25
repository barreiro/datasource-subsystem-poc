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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.WildFlyDataSourceListener;
import org.wildfly.datasource.api.WildFlyDataSourceMetrics;
import org.wildfly.datasource.api.configuration.ConnectionFactoryConfiguration;
import org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration;
import org.wildfly.datasource.api.configuration.DataSourceConfiguration;
import org.wildfly.datasource.api.security.SimplePassword;

import javax.sql.XAConnection;
import java.io.PrintWriter;
import java.security.Principal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class HikariUnderTheCoversDataSourceImpl implements WildFlyDataSource {

    private final DataSourceConfiguration configuration;
    private final ConnectionPoolConfiguration poolConfiguration;
    private final ConnectionFactoryConfiguration factoryConfiguration;

    private final HikariDataSource hikari;

    private WildFlyDataSourceListener listener;

    public HikariUnderTheCoversDataSourceImpl(DataSourceConfiguration configuration) {
        this.configuration = configuration;
        this.poolConfiguration = configuration.connectionPoolConfiguration();
        this.factoryConfiguration = poolConfiguration.connectionFactoryConfiguration();
        this.hikari = new HikariDataSource( getHikariConfig( configuration) );
    }

    private HikariConfig getHikariConfig( DataSourceConfiguration configuration ) {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setDataSourceJNDI( configuration.jndiName() );
        hikariConfig.setIdleTimeout( poolConfiguration.connectionReapTimeout() );
        hikariConfig.setValidationTimeout( poolConfiguration.connectionValidationTimeout() );

        if( factoryConfiguration.transactionIsolation() != null ) {
            hikariConfig.setTransactionIsolation( "TRANSACTION_" + factoryConfiguration.transactionIsolation().name() );
        }

        hikariConfig.setJdbcUrl( factoryConfiguration.jdbcUrl() );
        hikariConfig.setAutoCommit( factoryConfiguration.autoCommit() );
        hikariConfig.setConnectionInitSql( factoryConfiguration.initialSql() );

        Principal principal = factoryConfiguration.principal();
        if ( principal != null ) {
            hikariConfig.setUsername( factoryConfiguration.principal().getName() );
        }
        for ( Object credential : factoryConfiguration.credentials() ) {
            if ( credential instanceof SimplePassword ) {
                hikariConfig.setPassword( ( (SimplePassword) credential ).getWord() );
            }
        }

        hikariConfig.setMaximumPoolSize( poolConfiguration.maxSize() );
        hikariConfig.setConnectionTimeout( poolConfiguration.acquisitionTimeout() );
        hikariConfig.setDriverClassName( factoryConfiguration.driverClassName() );

        if ( configuration.metricsEnabled() ) {
            hikariConfig.setMetricsTrackerFactory( new HikariMetricsListenerAdaptor.Factory() );
        }

        return hikariConfig;
    }

    // --- //

    @Override
    public DataSourceConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public WildFlyDataSourceMetrics getMetrics() {
        return null;
    }

    @Override
    public void addListener(WildFlyDataSourceListener listener) {
        this.listener = listener;
    }

    @Override
    public void close() {
        hikari.close();
    }

    // --- //

    @Override
    public Connection getConnection() throws SQLException {
        if (listener != null) {
            listener.beforeConnectionAcquire();
        }
        return hikari.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        if (listener != null) {
            listener.beforeConnectionAcquire();
        }
        return hikari.getConnection( username, password );
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return hikari.unwrap( iface );
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return hikari.isWrapperFor( iface );
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return hikari.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        hikari.getLogWriter();
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        hikari.setLoginTimeout( seconds );
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return hikari.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return hikari.getParentLogger();
    }

}

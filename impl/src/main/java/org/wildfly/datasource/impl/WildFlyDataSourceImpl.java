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

import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.WildFlyDataSourceListener;
import org.wildfly.datasource.api.WildFlyDataSourceMetrics;
import org.wildfly.datasource.api.configuration.DataSourceConfiguration;

import javax.sql.XAConnection;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class WildFlyDataSourceImpl implements WildFlyDataSource {

    private final DataSourceConfiguration configuration;
    private final List<WildFlyDataSourceListener> listenerList;
    private WildFlyDataSourceMetricsRegistry metricsRegistry;

    private final ConnectionPoolImpl connectionPool;

    public WildFlyDataSourceImpl(DataSourceConfiguration configuration) {
        this.configuration = configuration;

        listenerList = new CopyOnWriteArrayList<>();
        connectionPool = new ConnectionPoolImpl( configuration.connectionPoolConfiguration(), this );

        if ( configuration.metricsEnabled() ) {
            metricsRegistry = new DefaultMetricsRegistry( connectionPool );
        } else {
            metricsRegistry = new EmptyMetricsRegistry();
        }

        init();
    }

    private void init() {
        connectionPool.init();
    }

    List<WildFlyDataSourceListener> listenerList() {
        return listenerList;
    }

    WildFlyDataSourceMetricsRegistry metricsRegistry() {
        return metricsRegistry;
    }

    // --- WildFlyDataSource methods //

    @Override
    public DataSourceConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public WildFlyDataSourceMetrics getMetrics() {
        return metricsRegistry;
    }

    public void addListener(WildFlyDataSourceListener listener) {
        listenerList.add( listener );
    }

    @Override
    public void close() {
        connectionPool.close();

    }

    // --- DataSource methods //

    @Override
    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException( "username/password invalid on a pooled data source" );
    }

    // --- Wrapper methods //

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    // --- CommonDataSource methods //

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {

    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException( "Not Supported" );
    }

}

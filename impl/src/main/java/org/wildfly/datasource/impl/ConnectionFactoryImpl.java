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

import org.wildfly.datasource.api.configuration.ConnectionFactoryConfiguration;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionFactoryImpl {

    private static String CONNECTION_CLOSE_METHOD_NAME = "close";
    private static String CONNECTION_UNWRAP_METHOD_NAME = "unwrap";
    private static String CONNECTION_WRAPPER_METHOD_NAME = "isWrapperFor";

    private ConnectionPoolImpl poolImpl;

    private ConnectionFactoryConfiguration configuration;

    private Driver driver;

    private ClassLoader driverLoader;

    @SuppressWarnings("unchecked")
    public ConnectionFactoryImpl(ConnectionFactoryConfiguration configuration, ConnectionPoolImpl poolImpl) {
        this.configuration = configuration;
        this.poolImpl = poolImpl;

        try {
            driverLoader = configuration.classLoaderProvider().getClassLoader( configuration.driverClassName() );
            Class<Driver> driverClass = (Class<Driver>) driverLoader.loadClass( configuration.driverClassName() );
            driver = driverClass.newInstance();
        } catch ( IllegalAccessException | InstantiationException | ClassNotFoundException e ) {
            throw new RuntimeException( "Unable to load driver class" );
        }
    }

    public ConnectionHandler createHandler() throws SQLException {
        Connection connection = driver.connect( configuration.jdbcUrl(), null );
        connection.setAutoCommit( configuration.autoCommit() );
        connection.createStatement().execute( configuration.initSql() );

        ConnectionHandler handler = new ConnectionHandler( connection );

        // We return a proxy that intercepts the close() calls and return the connection to the pool
        Connection intercepted = (Connection) Proxy.newProxyInstance( driverLoader, new Class[]{Connection.class}, (proxy, method, args) -> {
            if ( CONNECTION_CLOSE_METHOD_NAME.equals( method.getName() ) ) {
                poolImpl.returnConnection( handler );
            }
            return method.invoke( connection, args );
        } );

        handler.setConnection( intercepted );
        return handler;
    }

}

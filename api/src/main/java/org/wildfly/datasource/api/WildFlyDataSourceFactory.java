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

package org.wildfly.datasource.api;

import org.wildfly.datasource.api.configuration.DataSourceConfiguration;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class WildFlyDataSourceFactory {

    private WildFlyDataSourceFactory() {
    }

    public static WildFlyDataSource create(DataSourceConfiguration configuration) throws SQLException {

        switch ( configuration.dataSourceImplementation() ) {
            default:
            case WILDFLY:
                return (WildFlyDataSource) load( "org.wildfly.datasource.impl.WildFlyDataSourceImpl", configuration );
            case HIKARI:
                return (WildFlyDataSource) load( "org.wildfly.datasource.hikari.HikariUnderTheCoversDataSourceImpl", configuration );
        }
    }

    public static WildFlyXADataSource createXA(DataSourceConfiguration configuration) throws SQLException {
        switch ( configuration.dataSourceImplementation() ) {
            default:
            case WILDFLY:
                return (WildFlyXADataSource) load( "org.wildfly.datasource.impl.WildFlyXADataSourceImpl", configuration );
            case HIKARI:
                throw new SQLException( "Unsupported" );
        }
    }

    private static Object load(String className, DataSourceConfiguration configuration) throws SQLException {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> dataSourceClass = classLoader.loadClass( className );
            Constructor<?> dataSourceConstructor = dataSourceClass.getConstructor( DataSourceConfiguration.class );
            return dataSourceConstructor.newInstance( configuration );
        } catch ( ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e ) {
            throw new SQLException( "could not load Data Source class", e );
        }
    }

}

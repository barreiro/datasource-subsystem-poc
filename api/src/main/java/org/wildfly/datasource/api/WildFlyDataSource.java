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
import org.wildfly.datasource.api.configuration.DataSourceConfigurationBuilder;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public interface WildFlyDataSource extends AutoCloseable, DataSource, XADataSource {

    DataSourceConfiguration getConfiguration();

    WildFlyDataSourceMetrics getMetrics();

    void addListener(WildFlyDataSourceListener listener);

    @Override
    void close();

    // --- //

    static WildFlyDataSource from(DataSourceConfigurationBuilder dataSourceConfigurationBuilder) throws SQLException {
        return from( dataSourceConfigurationBuilder.build() );
    }

    static WildFlyDataSource from(DataSourceConfiguration dataSourceConfiguration) throws SQLException {
        String className;
        switch ( dataSourceConfiguration.dataSourceImplementation() ) {
            default:
            case WILDFLY:
                className = "org.wildfly.datasource.impl.WildFlyDataSourceImpl";
                break;
            case INTEGRATED:
                className = "org.wildfly.datasource.integrated.WildFlyDataSourceImpl";
                break;
            case HIKARI:
                if ( dataSourceConfiguration.isXA() ) {
                    throw new UnsupportedOperationException( "Unsupported" );
                }
                className = "org.wildfly.datasource.hikari.HikariUnderTheCoversDataSourceImpl";
                break;
        }

        // refactor after java9 (private methods in interfaces)
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> dataSourceClass = classLoader.loadClass( className );
            Constructor<?> dataSourceConstructor = dataSourceClass.getConstructor( DataSourceConfiguration.class );
            return (WildFlyDataSource) dataSourceConstructor.newInstance( dataSourceConfiguration );
        } catch ( ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e ) {
            throw new SQLException( "could not load Data Source class", e );
        }
    }

}

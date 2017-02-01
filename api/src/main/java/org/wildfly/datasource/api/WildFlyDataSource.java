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

import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public interface WildFlyDataSource extends AutoCloseable, DataSource, Serializable {

    DataSourceConfiguration getConfiguration();

    WildFlyDataSourceMetrics getMetrics();

    void addListener(WildFlyDataSourceListener listener);

    @Override
    void close();

    // --- //

    static WildFlyDataSource from(Supplier<DataSourceConfiguration> dataSourceConfigurationSupplier) throws SQLException {
        return from( dataSourceConfigurationSupplier.get() );
    }

    static WildFlyDataSource from(DataSourceConfiguration dataSourceConfiguration) throws SQLException {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> dataSourceClass = classLoader.loadClass( dataSourceConfiguration.dataSourceImplementation().className() );
            Constructor<?> dataSourceConstructor = dataSourceClass.getConstructor( DataSourceConfiguration.class );
            return (WildFlyDataSource) dataSourceConstructor.newInstance( dataSourceConfiguration );
        } catch ( ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e ) {
            throw new SQLException( "could not load Data Source class", e );
        }
    }

}

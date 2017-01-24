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

package org.wildfly.datasource.integrated;

import org.wildfly.datasource.api.configuration.ConnectionFactoryConfiguration;
import org.wildfly.datasource.api.security.NamePrincipal;
import org.wildfly.datasource.api.security.SimplePassword;

import java.security.Principal;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionFactory {

    private static final String USERNAME_PROPERTY_NAME = "username";
    private static final String PASSWORD_PROPERTY_NAME = "password";

    private ConnectionFactoryConfiguration configuration;
    private Driver driver;
    private Properties jdbcProperties;

    @SuppressWarnings("unchecked")
    public ConnectionFactory(ConnectionFactoryConfiguration configuration) {
        try {
            this.configuration = configuration;
            this.jdbcProperties = configuration.jdbcProperties();
            ClassLoader driverLoader = configuration.classLoaderProvider().getClassLoader( configuration.driverClassName() );
            Class<Driver> driverClass = (Class<Driver>) driverLoader.loadClass( configuration.driverClassName() );
            driver = driverClass.newInstance();

            setupSecurity( configuration );

        } catch ( IllegalAccessException | InstantiationException | ClassNotFoundException e ) {
            throw new RuntimeException( "Unable to load driver class" );
        }
    }

    private void setupSecurity(ConnectionFactoryConfiguration configuration) {
        Principal principal = configuration.principal();
        if ( principal == null ) {
            // skip!
        }
        else if ( principal instanceof NamePrincipal ) {
            jdbcProperties.put( USERNAME_PROPERTY_NAME, principal.getName() );
        }

        // Add other principal types here

        else {
            throw new IllegalArgumentException( "Unknown Principal type: " + principal.getClass().getName() );
        }

        for ( Object credential : configuration.credentials() ) {
            if ( credential instanceof SimplePassword ) {
                jdbcProperties.put( PASSWORD_PROPERTY_NAME, ( (SimplePassword) credential ).getWord() );
            }

            // Add other credential types here

            else {
                throw new IllegalArgumentException( "Unknown Credential type: " + credential.getClass().getName() );
            }
        }

    }

    public ConnectionHandler createHandler() throws SQLException {
        Connection connection = driver.connect( configuration.jdbcUrl(), jdbcProperties );
        connection.setAutoCommit( configuration.autoCommit() );
        connection.createStatement().execute( configuration.initialSql() );
        return new ConnectionHandler( connection );
    }

}

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

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionFactoryConfigurationBuilder {

    private static final String USERNAME_PROPERTY_NAME = "username";
    private static final String PASSWORD_PROPERTY_NAME = "password";

    private volatile boolean lock;

    private boolean autoCommit = false;
    private String jdbcUrl = "";
    private String initialSql = "";
    private String driverClassName = "";
    private ClassLoaderProvider classLoaderProvider;
    private ConnectionFactoryConfiguration.TransactionIsolation transactionIsolation;
    private ConnectionFactoryConfiguration.InterruptHandlingMode interruptHandlingMode;
    private Principal principal;
    private Collection<Object> credentials = new ArrayList<>();
    private Properties jdbcProperties = new Properties();

    public ConnectionFactoryConfigurationBuilder() {
        this.lock = false;
    }

    private ConnectionFactoryConfigurationBuilder applySetting(Consumer<ConnectionFactoryConfigurationBuilder> consumer) {
        if (lock) {
            throw new IllegalStateException("Attempt to modify an immutable configuration");
        }
        consumer.accept( this );
        return this;
    }

    public ConnectionFactoryConfigurationBuilder autoCommit(boolean autoCommit) {
        return applySetting( c -> c.autoCommit = autoCommit );
    }

    public ConnectionFactoryConfigurationBuilder jdbcUrl(String jdbcUrl) {
        return applySetting( c -> c.jdbcUrl = jdbcUrl );
    }

    public ConnectionFactoryConfigurationBuilder initialSql(String initialSql) {
        return applySetting( c -> c.initialSql = initialSql );
    }

    public ConnectionFactoryConfigurationBuilder driverClassName(String driverClassName) {
        return applySetting( c -> c.driverClassName = driverClassName );
    }

    public ConnectionFactoryConfigurationBuilder classLoaderProvider(ClassLoaderProvider classLoaderProvider) {
        return applySetting( c -> c.classLoaderProvider = classLoaderProvider );
    }

    public ConnectionFactoryConfigurationBuilder classLoader(ClassLoader classLoader) {
        return applySetting( c -> c.classLoaderProvider = className -> classLoader );
    }

    public ConnectionFactoryConfigurationBuilder transactionIsolation(ConnectionFactoryConfiguration.TransactionIsolation transactionIsolation) {
        return applySetting( c -> c.transactionIsolation = transactionIsolation );
    }

    public ConnectionFactoryConfigurationBuilder interruptHandlingMode(ConnectionFactoryConfiguration.InterruptHandlingMode interruptHandlingMode) {
        return applySetting( c -> c.interruptHandlingMode = interruptHandlingMode );
    }

    public ConnectionFactoryConfigurationBuilder principal(Principal principal) {
        return applySetting( c -> c.principal = principal );
    }

    public ConnectionFactoryConfigurationBuilder credential(Object credential) {
        return applySetting( c -> c.credentials.add( credential ) );
    }

    public ConnectionFactoryConfigurationBuilder jdbcProperty(String key, String value) {
        validateJdbcProperty( key );
        return applySetting( c -> c.jdbcProperties.put( key, value) );
    }

    private void validateJdbcProperty(String key) {
        if ( USERNAME_PROPERTY_NAME.equalsIgnoreCase( key ) ) {
            throw new IllegalArgumentException( "Invalid property '" + key + "': use principal instead." );
        }
        if ( PASSWORD_PROPERTY_NAME.equalsIgnoreCase( key ) ) {
            throw new IllegalArgumentException( "Invalid property '" + key + "': use credential instead." );
        }
    }

    public ConnectionFactoryConfiguration build() {
        //validate();
        this.lock = true;

        return new ConnectionFactoryConfiguration() {

            @Override
            public boolean autoCommit() {
                return autoCommit;
            }

            @Override
            public String jdbcUrl() {
                return jdbcUrl;
            }

            @Override
            public String initialSql() {
                return initialSql;
            }

            @Override
            public String driverClassName() {
                return driverClassName;
            }

            @Override
            public ClassLoaderProvider classLoaderProvider() {
                return classLoaderProvider != null ? classLoaderProvider : ClassLoaderProvider.systemClassloader();
            }

            @Override
            public TransactionIsolation transactionIsolation() {
                return transactionIsolation;
            }

            @Override
            public InterruptHandlingMode interruptHandlingMode() {
                return interruptHandlingMode != null ? interruptHandlingMode : InterruptHandlingMode.OFF;
            }

            @Override
            public Principal principal() {
                return principal;
            }

            @Override
            public Collection<Object> credentials() {
                return credentials;
            }

            @Override
            public Properties jdbcProperties() {
                return jdbcProperties;
            }
        };

    }

}

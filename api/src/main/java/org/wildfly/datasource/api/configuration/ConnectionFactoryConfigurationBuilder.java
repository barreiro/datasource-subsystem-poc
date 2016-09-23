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
public class ConnectionFactoryConfigurationBuilder {

    private volatile boolean lock;

    private boolean autoCommit = false;
    private String username = "";
    private String password = "";
    private String jdbcUrl = "";
    private String initSql = "";
    private String driverClassName = "";
    private ClassLoaderProvider classLoaderProvider;
    private ConnectionFactoryConfiguration.TransactionIsolation transactionIsolation;

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

    public ConnectionFactoryConfigurationBuilder setAutoCommit(boolean autoCommit) {
        return applySetting( c -> c.autoCommit = autoCommit );
    }

    public ConnectionFactoryConfigurationBuilder setUsername(String username) {
        return applySetting( c -> c.username = username );
    }

    public ConnectionFactoryConfigurationBuilder setPassword(String password) {
        return applySetting( c -> c.password = password );
    }

    public ConnectionFactoryConfigurationBuilder setJdbcUrl(String jdbcUrl) {
        return applySetting( c -> c.jdbcUrl = jdbcUrl );
    }

    public ConnectionFactoryConfigurationBuilder setInitSql(String initSql) {
        return applySetting( c -> c.initSql = initSql );
    }

    public ConnectionFactoryConfigurationBuilder setDriverClassName(String driverClassName) {
        return applySetting( c -> c.driverClassName = driverClassName );
    }

    public ConnectionFactoryConfigurationBuilder setClassLoaderProvider(ClassLoaderProvider classLoaderProvider) {
        return applySetting( c -> c.classLoaderProvider = classLoaderProvider );
    }

    public ConnectionFactoryConfigurationBuilder setClassLoader(ClassLoader classLoader) {
        return applySetting( c -> c.classLoaderProvider = new ClassLoaderProvider() {
            @Override
            public ClassLoader getClassLoader(String className) {
                return classLoader;
            }
        } );
    }

    public ConnectionFactoryConfigurationBuilder setTransactionIsolation(ConnectionFactoryConfiguration.TransactionIsolation transactionIsolation) {
        return applySetting( c -> c.transactionIsolation = transactionIsolation );
    }

    public ConnectionFactoryConfiguration build() {
        this.lock = true;

        return new ConnectionFactoryConfiguration() {

            @Override
            public boolean autoCommit() {
                return autoCommit;
            }

            @Override
            public String username() {
                return username;
            }

            @Override
            public String password() {
                return password;
            }

            @Override
            public String jdbcUrl() {
                return jdbcUrl;
            }

            @Override
            public String initSql() {
                return initSql;
            }

            @Override
            public String driverClassName() {
                return driverClassName;
            }

            @Override
            public ClassLoaderProvider classLoaderProvider() {
                return classLoaderProvider != null ? classLoaderProvider : ClassLoaderProvider.DEFAULT;
            }

            @Override
            public TransactionIsolation transactionIsolation() {
                return transactionIsolation;
            }

        };

    }

}

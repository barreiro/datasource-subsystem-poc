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

import org.wildfly.datasource.api.tx.TransactionIntegration;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionPoolConfigurationBuilder implements Supplier<ConnectionPoolConfiguration> {

    private volatile boolean lock;

    private ConnectionPoolConfiguration.PoolImplementation poolImplementation = ConnectionPoolConfiguration.PoolImplementation.DEFAULT;
    private ConnectionFactoryConfiguration connectionFactoryConfiguration;
    private ConnectionPoolConfiguration.PreFillMode preFillMode = ConnectionPoolConfiguration.PreFillMode.NONE;
    private TransactionIntegration transactionIntegration = TransactionIntegration.none();
    private volatile int minSize = 0;
    private volatile int maxSize = 0;
    private ConnectionValidator connectionValidator = ConnectionValidator.emptyValidator();
    private Duration leakTimeout = Duration.ZERO;
    private Duration validationTimeout = Duration.ZERO;
    private Duration reapTimeout = Duration.ZERO;
    private volatile Duration acquisitionTimeout = Duration.ZERO;

    public ConnectionPoolConfigurationBuilder() {
        this.lock = false;
    }

    public ConnectionPoolConfigurationBuilder(ConnectionPoolConfiguration existingConfiguration) {
        this.lock = false;
        if ( existingConfiguration == null ) {
            return;
        }
        this.poolImplementation = existingConfiguration.poolImplementation();
        this.connectionFactoryConfiguration = existingConfiguration.connectionFactoryConfiguration();
        this.preFillMode = existingConfiguration.preFillMode();
        this.transactionIntegration = existingConfiguration.transactionIntegration();
        this.minSize = existingConfiguration.minSize();
        this.maxSize = existingConfiguration.maxSize();
        this.connectionValidator = existingConfiguration.connectionValidator();
        this.leakTimeout = existingConfiguration.leakTimeout();
        this.validationTimeout = existingConfiguration.validationTimeout();
        this.reapTimeout = existingConfiguration.reapTimeout();
        this.acquisitionTimeout = existingConfiguration.acquisitionTimeout();
    }

    private ConnectionPoolConfigurationBuilder applySetting(Consumer<ConnectionPoolConfigurationBuilder> consumer) {
        if ( lock ) {
            throw new IllegalStateException( "Attempt to modify an immutable configuration" );
        }
        consumer.accept( this );
        return this;
    }

    public ConnectionPoolConfigurationBuilder connectionFactoryConfiguration(Supplier<ConnectionFactoryConfiguration> supplier) {
        return applySetting( c -> c.connectionFactoryConfiguration = supplier.get() );
    }

    public ConnectionPoolConfigurationBuilder connectionFactoryConfiguration(Function<ConnectionFactoryConfigurationBuilder, ConnectionFactoryConfigurationBuilder> function) {
        return applySetting( c -> c.connectionFactoryConfiguration = function.apply( new ConnectionFactoryConfigurationBuilder( connectionFactoryConfiguration ) ).get() );
    }

    @Override
    public ConnectionPoolConfiguration get() {
        return build();
    }

    // --- //

    public ConnectionPoolConfigurationBuilder poolImplementation(ConnectionPoolConfiguration.PoolImplementation poolImplementation) {
        return applySetting( c -> c.poolImplementation = poolImplementation );
    }

    public ConnectionPoolConfigurationBuilder transactionIntegration(TransactionIntegration transactionIntegration) {
        return applySetting( c -> c.transactionIntegration = transactionIntegration );
    }

    public ConnectionPoolConfigurationBuilder preFillMode(ConnectionPoolConfiguration.PreFillMode preFillMode) {
        return applySetting( c -> c.preFillMode = preFillMode );
    }

    public ConnectionPoolConfigurationBuilder minSize(int minSize) {
        return applySetting( c -> c.minSize = minSize );
    }

    public ConnectionPoolConfigurationBuilder maxSize(int maxSize) {
        return applySetting( c -> c.maxSize = maxSize );
    }

    public ConnectionPoolConfigurationBuilder acquisitionTimeout(Duration acquisitionTimeout) {
        return applySetting( c -> c.acquisitionTimeout = acquisitionTimeout );
    }

    public ConnectionPoolConfigurationBuilder connectionValidator(ConnectionValidator connectionValidator) {
        return applySetting( c -> c.connectionValidator = connectionValidator );
    }

    public ConnectionPoolConfigurationBuilder leakTimeout(Duration leakTimeout) {
        return applySetting( c -> c.leakTimeout = leakTimeout );
    }

    public ConnectionPoolConfigurationBuilder validationTimeout(Duration validationTimeout) {
        return applySetting( c -> c.validationTimeout = validationTimeout );
    }

    public ConnectionPoolConfigurationBuilder reapTimeout(Duration reapTimeout) {
        return applySetting( c -> c.reapTimeout = reapTimeout );
    }

    private void validate() {
        if ( minSize < 0 ) {
            throw new IllegalArgumentException( "Invalid min size" );
        }
        if ( minSize > maxSize ) {
            throw new IllegalArgumentException( "Wrong size of min / max size" );
        }
        if ( connectionFactoryConfiguration == null ) {
            throw new IllegalArgumentException( "Connection factory configuration not defined" );
        }
    }

    private ConnectionPoolConfiguration build() {
        validate();
        this.lock = true;

        return new ConnectionPoolConfiguration() {

            @Override
            public PoolImplementation poolImplementation() {
                return poolImplementation;
            }

            @Override
            public ConnectionFactoryConfiguration connectionFactoryConfiguration() {
                return connectionFactoryConfiguration;
            }

            @Override
            public TransactionIntegration transactionIntegration() {
                return transactionIntegration;
            }

            @Override
            public PreFillMode preFillMode() {
                return preFillMode;
            }

            @Override
            public int minSize() {
                return minSize;
            }

            @Override
            public void setMinSize(int size) {
                minSize = size;
            }

            @Override
            public int maxSize() {
                return maxSize;
            }

            @Override
            public void setMaxSize(int size) {
                maxSize = size;
            }

            @Override
            public Duration acquisitionTimeout() {
                return acquisitionTimeout;
            }

            @Override
            public void setAcquisitionTimeout(Duration timeout) {
                acquisitionTimeout = timeout;
            }

            @Override
            public ConnectionValidator connectionValidator() {
                return connectionValidator;
            }

            @Override
            public Duration leakTimeout() {
                return leakTimeout;
            }

            @Override
            public Duration validationTimeout() {
                return validationTimeout;
            }

            @Override
            public Duration reapTimeout() {
                return reapTimeout;
            }
        };
    }
}

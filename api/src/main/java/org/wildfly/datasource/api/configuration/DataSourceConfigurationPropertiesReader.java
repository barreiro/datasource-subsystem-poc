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

import org.wildfly.datasource.api.security.NamePrincipal;
import org.wildfly.datasource.api.security.SimplePassword;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class DataSourceConfigurationPropertiesReader implements Supplier<DataSourceConfigurationBuilder> {

    public static final String IMPLEMENTATION = "implementation";
    public static final String JNDI_NAME = "jndiName";
    public static final String METRICS_ENABLED = "metricsEnabled";
    public static final String XA = "xa";

    // --- //

    public static final String MIN_SIZE = "minSize";
    public static final String MAX_SIZE = "maxSize";
    public static final String PRE_FILL_MODE = "preFillMode";
    public static final String ACQUISITION_TIMEOUT = "acquisitionTimeout";
    public static final String VALIDATION_TIMEOUT = "validationTimeout";
    public static final String LEAK_TIMEOUT = "leakTimeout";
    public static final String REAP_TIMEOUT = "reapTimeout";

    // --- //

    public static final String JDBC_URL = "jdbcUrl";
    public static final String AUTO_COMMIT = "autoCommit";
    public static final String INITIAL_SQL = "initialSQL";
    public static final String DRIVER_CLASS_NAME = "driverClassName";
    public static final String PRINCIPAL = "principal";
    public static final String CREDENTIAL = "credential";
    public static final String JDBC_PROPERTIES = "jdbcProperties";

    // --- //

    private final String prefix;
    private final DataSourceConfigurationBuilder dataSourceBuilder;
    private final ConnectionPoolConfigurationBuilder connectionPoolBuilder;
    private final ConnectionFactoryConfigurationBuilder connectionFactoryBuilder;

    public DataSourceConfigurationPropertiesReader(String prefix) {
        this.prefix = prefix;
        this.dataSourceBuilder = new DataSourceConfigurationBuilder();
        this.connectionPoolBuilder = new ConnectionPoolConfigurationBuilder();
        this.connectionFactoryBuilder = new ConnectionFactoryConfigurationBuilder();
    }

    @Override
    public DataSourceConfigurationBuilder get() {
        return dataSourceBuilder.connectionPoolConfiguration( connectionPoolBuilder.connectionFactoryConfiguration( connectionFactoryBuilder ) );
    }

    // --- //

    public DataSourceConfigurationPropertiesReader readProperties(String filename) throws IOException {
        try ( InputStream inputStream = new FileInputStream( filename ) ) {
            Properties properties = new Properties();
            properties.load( inputStream );
            return readProperties( properties );   
        }
    }

    public DataSourceConfigurationPropertiesReader readProperties(Properties properties) {
        apply( dataSourceBuilder::dataSourceImplementation, DataSourceConfiguration.DataSourceImplementation::valueOf, properties, IMPLEMENTATION );
        apply( dataSourceBuilder::jndiName, Function.identity(), properties, JNDI_NAME );
        apply( dataSourceBuilder::metricsEnabled, Boolean::parseBoolean, properties, METRICS_ENABLED );
        apply( dataSourceBuilder::xa, Boolean::parseBoolean, properties, XA );

        apply( connectionPoolBuilder::minSize, Integer::parseInt, properties, MIN_SIZE );
        apply( connectionPoolBuilder::maxSize, Integer::parseInt, properties, MAX_SIZE );
        apply( connectionPoolBuilder::preFillMode, ConnectionPoolConfiguration.PreFillMode::valueOf, properties, PRE_FILL_MODE );
        apply( connectionPoolBuilder::acquisitionTimeout, Duration::parse, properties, ACQUISITION_TIMEOUT );
        apply( connectionPoolBuilder::validationTimeout, Duration::parse, properties, VALIDATION_TIMEOUT );
        apply( connectionPoolBuilder::leakTimeout, Duration::parse, properties, LEAK_TIMEOUT );
        apply( connectionPoolBuilder::reapTimeout, Duration::parse, properties, REAP_TIMEOUT );

        apply( connectionFactoryBuilder::jdbcUrl, Function.identity(), properties, JDBC_URL );
        apply( connectionFactoryBuilder::autoCommit, Boolean::parseBoolean, properties, AUTO_COMMIT );
        apply( connectionFactoryBuilder::initialSql, Function.identity(), properties, INITIAL_SQL );
        apply( connectionFactoryBuilder::driverClassName, Function.identity(), properties, DRIVER_CLASS_NAME );
        apply( connectionFactoryBuilder::principal, NamePrincipal::new, properties, PRINCIPAL );
        apply( connectionFactoryBuilder::credential, SimplePassword::new, properties, CREDENTIAL );
        applyJdbcProperties( connectionFactoryBuilder::jdbcProperty, properties, JDBC_PROPERTIES );
        return this;
    }

    private <T> void apply(Consumer<T> consumer, Function<String, T> function, Properties properties, String key) {
        String value = properties.getProperty( prefix + key );
        if ( value != null ) {
            consumer.accept( function.apply( value ) );
        }
    }

    private void applyJdbcProperties(BiConsumer<String, String> consumer, Properties properties, String key) {
        String propertiesArray = properties.getProperty( prefix + key );
        if ( propertiesArray != null ) {
            for ( String property : propertiesArray.split( ";" ) ) {
                String[] keyValue = property.split( "=" );
                consumer.accept( keyValue[0], keyValue[1] );
            }
        }
    }
}

package org.wildfly.datasource.hikari.test;

import org.junit.Test;
import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.configuration.ConnectionFactoryConfigurationBuilder;
import org.wildfly.datasource.api.configuration.ConnectionPoolConfigurationBuilder;
import org.wildfly.datasource.api.configuration.DataSourceConfiguration;
import org.wildfly.datasource.api.configuration.DataSourceConfigurationBuilder;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class BasicTest {

    private static final String H2_JDBC_URL = "jdbc:h2:mem:test";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    @Test
    public void basicTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( DataSourceConfiguration.DataSourceImplementation.HIKARI )
                .connectionPoolConfiguration( new ConnectionPoolConfigurationBuilder()
                        .connectionFactoryConfiguration( new ConnectionFactoryConfigurationBuilder()
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                        .maxSize( 10 )
                        .connectionValidationTimeout( 2000 )
                );

        try( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            for ( int i = 0; i < 50; i++ ) {
                Connection connection = dataSource.getConnection();
                System.out.println( "connection = " + connection );
                connection.close();
            }
        }
    }

}

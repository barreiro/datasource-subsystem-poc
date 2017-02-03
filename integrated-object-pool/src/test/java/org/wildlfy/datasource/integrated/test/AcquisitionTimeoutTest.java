package org.wildlfy.datasource.integrated.test;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.configuration.DataSourceConfigurationBuilder;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration.PreFillMode.MIN;
import static org.wildfly.datasource.api.configuration.DataSourceConfiguration.DataSourceImplementation.INTEGRATED;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class AcquisitionTimeoutTest {

    private static final String H2_JDBC_URL = "jdbc:h2:mem:test";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    @Test
    public void basicAcquisitionTimeoutTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( INTEGRATED )
                .metricsEnabled( true )
                .connectionPoolConfiguration( cp -> cp
                        .maxSize( 10 )
                        .validationTimeout( Duration.ofSeconds( 2 ) )
                        .acquisitionTimeout( Duration.ofSeconds( 1 ) )
                        .preFillMode( MIN )
                        .connectionFactoryConfiguration( cf -> cf
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        try( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {

            for ( int i = 0; i < 10; i++ ) {
                Connection connection = dataSource.getConnection();
                System.out.println( "connection = " + connection );
                //connection.close();
            }

            System.out.println( dataSource.getMetrics() );

            long start = System.nanoTime();

            try {
                dataSource.getConnection();
                Assert.fail( "SQLException was expected" );
            } catch ( SQLException e ) {
                long elapsed = TimeUnit.NANOSECONDS.toMillis( System.nanoTime() - start );
                System.out.printf("SQLException after %d miliseconds: %s %n", elapsed, e.getMessage() );
                if ( elapsed < 1000 || elapsed > 1111 ) {
                    Assert.fail( "Timeout not within bounds" );
                }
            }
        }
    }


}

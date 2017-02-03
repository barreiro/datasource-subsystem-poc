package org.wildlfy.datasource.integrated.test;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.configuration.DataSourceConfigurationBuilder;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration.PreFillMode.MAX;
import static org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration.PreFillMode.MIN;
import static org.wildfly.datasource.api.configuration.DataSourceConfiguration.DataSourceImplementation.INTEGRATED;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class BasicTest {

    private static final String H2_JDBC_URL = "jdbc:h2:mem:test";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    @Test
    public void basicTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( INTEGRATED )
                .connectionPoolConfiguration( cp -> cp
                        .minSize( 5 )
                        .maxSize( 10 )
                        .preFillMode( MIN )
                        .connectionFactoryConfiguration( cf -> cf
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        try( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            for ( int i = 0; i < 50; i++ ) {
                Connection connection = dataSource.getConnection();
                System.out.println( "connection = " + connection );
                connection.close();
            }
        }
    }

    @Test
    public void basicConcurrentTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( INTEGRATED )
                .metricsEnabled( true )
                .connectionPoolConfiguration( cp -> cp
                        .minSize( 5 )
                        .maxSize( 10 )
                        .preFillMode( MAX )
                        .connectionFactoryConfiguration( cf -> cf
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        int MAX_SIZE = 10;
        int THREAD_POOL_SIZE = 150;
        int CALLS = 5000;
        int SLEEP_TIME = 10;

        ExecutorService executor = Executors.newFixedThreadPool( THREAD_POOL_SIZE );

        try ( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            CountDownLatch latch = new CountDownLatch( CALLS );

            try {
                while ( dataSource.getMetrics().createdCount() < MAX_SIZE ) {
                    Thread.sleep( 1000 );
                }
            } catch ( InterruptedException ignore ) {
            }

            for ( int i = 0; i < CALLS; i++ ) {
                executor.submit( () -> {
                    try {
                        Connection connection = dataSource.getConnection();
                        System.out.println( "" + Thread.currentThread().getName() + " connection = " + connection );
                        System.out.flush();
                        Thread.sleep( SLEEP_TIME );
                        connection.close();
                    } catch ( SQLException e ) {
                        Assert.fail( "Unexpected SQLException " + e.getMessage() );
                    } catch ( InterruptedException e ) {
                        Assert.fail( "Interrupted " + e.getMessage() );
                    } finally {
                        latch.countDown();
                    }
                } );
            }
            try {
                if ( !latch.await( (long) (SLEEP_TIME * CALLS * 1.5 / MAX_SIZE), TimeUnit.MILLISECONDS ) ) {
                    Assert.fail( "Did not execute on the required amount of time " );
                }
            } catch ( InterruptedException e ) {
                e.printStackTrace();
            }

            System.out.println( dataSource.getMetrics() );
        }

    }

}

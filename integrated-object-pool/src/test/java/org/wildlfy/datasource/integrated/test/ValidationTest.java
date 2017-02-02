package org.wildlfy.datasource.integrated.test;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.WildFlyDataSourceListener;
import org.wildfly.datasource.api.configuration.DataSourceConfigurationBuilder;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration.PreFillMode.MIN;
import static org.wildfly.datasource.api.configuration.DataSourceConfiguration.DataSourceImplementation.INTEGRATED;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ValidationTest {

    private static final String H2_JDBC_URL = "jdbc:h2:mem:test";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    @Test
    public void basicValidationTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( INTEGRATED )
                .metricsEnabled( true )
                .connectionPoolConfiguration( cp -> cp
                        .minSize( 10 )
                        .maxSize( 10 )
                        .connectionValidationTimeout( 2 )
                        .preFillMode( MIN )
                        .connectionFactoryConfiguration( cf -> cf
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        int CALLS = 500;

        try ( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            CountDownLatch latch = new CountDownLatch( dataSource.getConfiguration().connectionPoolConfiguration().maxSize() );

            dataSource.addListener( new WildFlyDataSourceListener() {
                @Override
                public void onConnectionValidation(Connection connection) {
                    System.out.println( "Validating connection = " + connection );
                    latch.countDown();
                }
            } );

            for ( int i = 0; i < CALLS; i++ ) {
                Connection connection = dataSource.getConnection();
                System.out.println( "connection = " + connection );
                connection.close();
            }

            System.out.println( dataSource.getMetrics() );

            try {
                if ( !latch.await( 4, SECONDS ) ) {
                    Assert.fail( "Some connections were not validated" );
                }
            } catch ( InterruptedException e ) {
                Assert.fail( "Some connections were not validated" );
            }
        }

    }

    @Test
    public void basicLeakTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( INTEGRATED )
                .metricsEnabled( true )
                .connectionPoolConfiguration( cp -> cp
                        .minSize( 7 )
                        .maxSize( 10 )
                        .connectionLeakTimeout( 2 )
                        .preFillMode( MIN )
                        .connectionFactoryConfiguration( cf -> cf
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        int CALLS = 5;

        try ( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            CountDownLatch latch = new CountDownLatch( CALLS );

            dataSource.addListener( new WildFlyDataSourceListener() {
                @Override
                public void onConnectionLeak(Connection connection, Thread thread) {
                    System.out.println( "Leak connection = " + connection + " by thread " + thread.getName() );
                    latch.countDown();
                }
            } );

            for ( int i = 0; i < CALLS; i++ ) {
                Connection connection = dataSource.getConnection();
                System.out.println( "connection = " + connection );
                //connection.close();
            }

            System.out.println( dataSource.getMetrics() );

            try {
                if ( !latch.await( 4, SECONDS ) ) {
                    Assert.fail( "Not all connection leaks were identified" );
                }
            } catch ( InterruptedException e ) {
                Assert.fail( "Not all connection leaks were identified" );
            }
        }
    }


    @Test
    public void basicReapTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( INTEGRATED )
                .metricsEnabled( true )
                .connectionPoolConfiguration( cp -> cp
                        .minSize( 10 )
                        .maxSize( 15 )
                        .connectionReapTimeout( 2 )
                        .preFillMode( MIN )
                        .connectionFactoryConfiguration( cf -> cf
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        int THREAD_POOL_SIZE = 25;
        int CALLS = 500;
        int SLEEP_TIME = 50;

        ExecutorService executor = Executors.newFixedThreadPool( THREAD_POOL_SIZE );

        try ( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            CountDownLatch latch = new CountDownLatch( CALLS );

            dataSource.addListener( new WildFlyDataSourceListener() {
                @Override
                public void onConnectionTimeout(Connection connection) {
                    System.out.println( "Timeout connection = " + connection );
                }
            } );

            for ( int i = 0; i < CALLS; i++ ) {
                executor.submit( () -> {
                    try {
                        Connection connection = dataSource.getConnection();
                        System.out.println( "" + Thread.currentThread().getName() + " connection = " + connection );
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
                Thread.sleep( SECONDS.toMillis( 5 ) );
            } catch ( InterruptedException ignore ) {
            }

            System.out.println( dataSource.getMetrics() );

            long minSize = dataSource.getConfiguration().connectionPoolConfiguration().minSize();
            long maxSize = dataSource.getConfiguration().connectionPoolConfiguration().maxSize();
            Assert.assertEquals( minSize, dataSource.getMetrics().availableCount() );
            Assert.assertTrue( maxSize - minSize <= dataSource.getMetrics().timeoutCount() );
        }
    }

}

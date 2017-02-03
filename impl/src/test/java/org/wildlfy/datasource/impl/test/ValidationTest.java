package org.wildlfy.datasource.impl.test;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.WildFlyDataSourceListener;
import org.wildfly.datasource.api.configuration.ConnectionFactoryConfigurationBuilder;
import org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration;
import org.wildfly.datasource.api.configuration.ConnectionPoolConfigurationBuilder;
import org.wildfly.datasource.api.configuration.DataSourceConfiguration;
import org.wildfly.datasource.api.configuration.DataSourceConfigurationBuilder;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ValidationTest {

    private static final String H2_JDBC_URL = "jdbc:h2:mem:test";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    @Test
    public void basicValidationTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( DataSourceConfiguration.DataSourceImplementation.WILDFLY )
                .metricsEnabled( true )
                .connectionPoolConfiguration( new ConnectionPoolConfigurationBuilder()
                        .minSize( 5 )
                        .maxSize( 10 )
                        .validationTimeout( Duration.ofSeconds( 2 ) )
                        .preFillMode( ConnectionPoolConfiguration.PreFillMode.MIN )
                        .connectionFactoryConfiguration( new ConnectionFactoryConfigurationBuilder()
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        int CALLS = 500;

        try( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            CountDownLatch latch = new CountDownLatch( dataSource.getConfiguration().connectionPoolConfiguration().maxSize() );

            dataSource.addListener( new WildFlyDataSourceListener() {
                @Override
                public void onConnectionValidation(Connection connection) {
                    System.out.println("Validating connection = " + connection);
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
                if (! latch.await( 4, TimeUnit.SECONDS ) ) {
                    Assert.fail( "Not all connections were validated" );
                }
            } catch ( InterruptedException e ) {
                Assert.fail( "Not all connections were validated" );
            }
        }

    }

    @Test
    public void basicLeakTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( DataSourceConfiguration.DataSourceImplementation.WILDFLY )
                .metricsEnabled( true )
                .connectionPoolConfiguration( new ConnectionPoolConfigurationBuilder()
                        .minSize( 7 )
                        .maxSize( 10 )
                        .validationTimeout( Duration.ofSeconds( 2 ) )
                        .preFillMode( ConnectionPoolConfiguration.PreFillMode.MIN )
                        .connectionFactoryConfiguration( new ConnectionFactoryConfigurationBuilder()
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
                    System.out.println("Leak connection = " + connection + " by thread:" + thread.getName() );
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
                if (! latch.await( 3, TimeUnit.SECONDS ) ) {
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
                .dataSourceImplementation( DataSourceConfiguration.DataSourceImplementation.WILDFLY )
                .metricsEnabled( true )
                .connectionPoolConfiguration( new ConnectionPoolConfigurationBuilder()
                        .minSize( 10 )
                        .maxSize( 15 )
                        .reapTimeout( Duration.ofSeconds( 2 ) )
                        .preFillMode( ConnectionPoolConfiguration.PreFillMode.MIN )
                        .connectionFactoryConfiguration( new ConnectionFactoryConfigurationBuilder()
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
                    System.out.println("Timeout connection = " + connection);
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
                Thread.sleep( TimeUnit.SECONDS.toMillis( 5 ) );
            } catch ( InterruptedException e ) {
                // Ignore
            }

            System.out.println( dataSource.getMetrics() );

            long minSize = dataSource.getConfiguration().connectionPoolConfiguration().minSize();
            long maxSize = dataSource.getConfiguration().connectionPoolConfiguration().maxSize();
            Assert.assertEquals( minSize, dataSource.getMetrics().availableCount() );
            Assert.assertTrue( maxSize - minSize <= dataSource.getMetrics().timeoutCount() );
        }
    }
}

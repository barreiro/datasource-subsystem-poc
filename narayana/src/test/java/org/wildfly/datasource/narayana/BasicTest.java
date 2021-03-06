package org.wildfly.datasource.narayana;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.configuration.DataSourceConfigurationBuilder;
import org.wildfly.datasource.api.security.NamePrincipal;
import org.wildfly.datasource.api.security.SimplePassword;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.sql.Connection;
import java.sql.SQLException;

import static org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration.PreFillMode.MIN;
import static org.wildfly.datasource.api.configuration.DataSourceConfiguration.DataSourceImplementation.INTEGRATED;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class BasicTest {

    private static final String H2_JDBC_URL = "jdbc:h2:mem:test";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    private TransactionManager txManager = com.arjuna.ats.jta.TransactionManager.transactionManager();
    private TransactionSynchronizationRegistry txSyncRegistry = new com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple();

    @Test
    public void basicTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( INTEGRATED )
                .connectionPoolConfiguration( cp -> cp
                        .transactionIntegration( new NarayanaTransactionIntegration( txManager, txSyncRegistry ) )
                        .minSize( 5 )
                        .maxSize( 10 )
                        .preFillMode( MIN )
                        .connectionFactoryConfiguration( cf -> cf
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        try ( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            for ( int i = 0; i < 50; i++ ) {
                try {
                    txManager.begin();

                    Connection connection = dataSource.getConnection();
                    System.out.println( "connection " + i + " = " + connection );

                    try {
                        connection.setAutoCommit( true );
                        Assert.fail( "Expected exception while setting autocommit" );
                    } catch ( SQLException e ) { // Expected
                    }

                    txManager.commit();
                } catch ( NotSupportedException | SystemException | RollbackException | HeuristicMixedException | HeuristicRollbackException e ) {
                    Assert.fail( "Kaboom: " + e.getMessage() );
                }
            }
        }
    }


    @Test
    public void rollbackTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .dataSourceImplementation( INTEGRATED )
                .connectionPoolConfiguration( cp -> cp
                        .transactionIntegration( new NarayanaTransactionIntegration( txManager, txSyncRegistry ) )
                        .minSize( 5 )
                        .maxSize( 10 )
                        .preFillMode( MIN )
                        .connectionFactoryConfiguration( cf -> cf
                                .driverClassName( H2_DRIVER_CLASS )
                                .jdbcUrl( H2_JDBC_URL )
                        )
                );

        try ( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            for ( int i = 0; i < 50; i++ ) {
                try {
                    txManager.begin();

                    Connection connection = dataSource.getConnection();
                    System.out.println( "connection " + i + " = " + connection );

                    try {
                        connection.setAutoCommit( true );
                        Assert.fail( "Expected exception while setting autocommit" );
                    } catch ( SQLException e ) { // Expected
                    }

                    Assert.assertTrue( connection == dataSource.getConnection() );

                    txManager.rollback();

                    Assert.assertTrue( connection.isClosed() );

                } catch ( NotSupportedException | SystemException e ) {
                    Assert.fail( "Kaboom: " + e.getMessage() );
                }
            }
        }
    }

}

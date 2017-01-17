package org.wildfly.datasource.narayana;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.datasource.api.WildFlyDataSource;
import org.wildfly.datasource.api.configuration.ConnectionFactoryConfigurationBuilder;
import org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration;
import org.wildfly.datasource.api.configuration.ConnectionPoolConfigurationBuilder;
import org.wildfly.datasource.api.configuration.DataSourceConfiguration;
import org.wildfly.datasource.api.configuration.DataSourceConfigurationBuilder;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class BasicTest {

    private static final String H2_JDBC_URL = "jdbc:h2:mem:test";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    private TransactionManager txManager = com.arjuna.ats.jta.TransactionManager.transactionManager();
    private TransactionSynchronizationRegistry txSyncRegistry = new TransactionSynchronizationRegistryImple();

    @Test
    public void basicTest() throws SQLException {
        DataSourceConfigurationBuilder dataSourceConfigurationBuilder = new DataSourceConfigurationBuilder()
                .setDataSourceImplementation( DataSourceConfiguration.DataSourceImplementation.INTEGRATED )
                .setConnectionPoolConfiguration( new ConnectionPoolConfigurationBuilder()
                        .setTransactionIntegration( new NarayanaTransactionIntegration( txManager, txSyncRegistry ) )
                        .setMinSize( 5 )
                        .setMaxSize( 10 )
                        .setPreFillMode( ConnectionPoolConfiguration.PreFillMode.MIN )
                        .setConnectionFactoryConfiguration( new ConnectionFactoryConfigurationBuilder()
                                .setDriverClassName( H2_DRIVER_CLASS )
                                .setJdbcUrl( H2_JDBC_URL )
                        )
                );

        try( WildFlyDataSource dataSource = WildFlyDataSource.from( dataSourceConfigurationBuilder ) ) {
            for ( int i = 0; i < 50; i++ ) {
                try {
                    txManager.begin();

                    Connection connection = dataSource.getConnection();
                    System.out.println( "connection " + i + " = " + connection );

                    txManager.commit();
                    connection.close();
                } catch ( NotSupportedException | SystemException | RollbackException | HeuristicMixedException | HeuristicRollbackException e ) {
                    Assert.fail( "Kaboom: " + e.getMessage() );
                }

            }
        }
    }

}

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

package org.wildfly.datasource.integrated;

import org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration;
import org.wildfly.datasource.api.configuration.InterruptProtection;
import org.wildfly.datasource.api.tx.TransactionIntegration;
import org.wildfly.datasource.integrated.util.PriorityScheduledExecutor;
import org.wildfly.datasource.integrated.util.WildFlyDataSourceSynchronizer;
import org.wildfly.datasource.integrated.util.StampedCopyOnWriteArrayList;
import org.wildfly.datasource.integrated.util.UncheckedArrayList;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.wildfly.datasource.integrated.ConnectionHandler.State.CHECKED_IN;
import static org.wildfly.datasource.integrated.ConnectionHandler.State.CHECKED_OUT;
import static org.wildfly.datasource.integrated.ConnectionHandler.State.DESTROYED;
import static org.wildfly.datasource.integrated.ConnectionHandler.State.FLUSH;
import static org.wildfly.datasource.integrated.ConnectionHandler.State.VALIDATION;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionPool implements AutoCloseable {

    private final ConnectionPoolConfiguration configuration;

    private final WildFlyDataSourceIntegrated dataSource;
    private final ThreadLocal<UncheckedArrayList<ConnectionHandler>> localCache;

//    private final ExposedCopyOnWriteArrayList<ConnectionHandler> allConnections;
//    private final AtomicCopyOnWriteArrayList<ConnectionHandler> allConnections;
//    private final AtomicReferenceCopyOnWriteArrayList<ConnectionHandler> allConnections;
//    private final LockFreeCopyOnWriteArrayList<ConnectionHandler> allConnections;
//    private final SynchronizedCopyOnWriteArrayList<ConnectionHandler> allConnections;
    private final StampedCopyOnWriteArrayList<ConnectionHandler> allConnections;

    private final WildFlyDataSourceSynchronizer synchronizer = new WildFlyDataSourceSynchronizer();
    private final ConnectionFactory connectionFactory;
    private final PriorityScheduledExecutor housekeepingExecutor;
    private final InterruptProtection interruptProtection;
    private final TransactionIntegration transactionIntegration;

    private final boolean leakEnabled, validationEnable, reapEnable;
    private volatile long maxUsed = 0;

    public ConnectionPool(ConnectionPoolConfiguration configuration, WildFlyDataSourceIntegrated dataSource) {
        this.configuration = configuration;
        this.dataSource = dataSource;

//        allConnections = new ExposedCopyOnWriteArrayList<>( ConnectionHandler.class );
//        allConnections = new AtomicCopyOnWriteArrayList<>( ConnectionHandler.class );
//        allConnections = new AtomicReferenceCopyOnWriteArrayList<>( ConnectionHandler.class );
//        allConnections = new LockFreeCopyOnWriteArrayList<>( ConnectionHandler.class );
//        allConnections = new SynchronizedCopyOnWriteArrayList<>( ConnectionHandler.class );
        allConnections = new StampedCopyOnWriteArrayList<>( ConnectionHandler.class );

        localCache = ThreadLocal.withInitial( () -> new UncheckedArrayList<ConnectionHandler>( ConnectionHandler.class ) );
        connectionFactory = new ConnectionFactory( configuration.connectionFactoryConfiguration() );
        housekeepingExecutor = new PriorityScheduledExecutor( 1, "Housekeeping of " + this );

        interruptProtection = configuration.connectionFactoryConfiguration().interruptProtection();
        transactionIntegration = configuration.transactionIntegration();

        leakEnabled = configuration.connectionLeakTimeout() > 0;
        validationEnable = configuration.connectionValidationTimeout() > 0;
        reapEnable = configuration.connectionReapTimeout() > 0;
    }

    public void init() {
        switch ( configuration.preFillMode() ) {
            default:
            case NONE:
                break;
            case MIN:
                fill( configuration.minSize() );
                break;
            case MAX:
                fill( configuration.maxSize() );
                break;
        }

        if ( leakEnabled ) {
            housekeepingExecutor.schedule( new LeakTask(), configuration.connectionLeakTimeout(), SECONDS );
        }
        if ( validationEnable ) {
            housekeepingExecutor.schedule( new ValidationTask(), configuration.connectionValidationTimeout(), SECONDS );
        }
        if ( reapEnable ) {
            housekeepingExecutor.schedule( new ReapTask(), configuration.connectionReapTimeout(), SECONDS );
        }
    }

    private void fill(int newSize) {
        long connectionCount = newSize - allConnections.size();
        while ( connectionCount-- > 0 ) {
            newConnectionHandler();
        }
    }

    @Override
    public void close() {
        housekeepingExecutor.shutdownNow();
    }

    // --- //

    private Future<?> newConnectionHandler() {
        return housekeepingExecutor.executeNow( () -> {
            if ( allConnections.size() >= configuration.maxSize() ) {
                return;
            }

            WildFlyDataSourceListenerHelper.fireBeforeConnectionCreated( dataSource );
            long metricsStamp = dataSource.metricsRegistry().beforeConnectionCreated();

            try {
                ConnectionHandler handler = connectionFactory.createHandler();
                handler.setConnectionPool( this );

                WildFlyDataSourceListenerHelper.fireOnConnectionCreated( dataSource, handler );

                handler.setState( CHECKED_IN );
                allConnections.add( handler );
                maxUsedCount();

                dataSource.metricsRegistry().afterConnectionCreated( metricsStamp );
            } catch ( SQLException e ) {
                throw new RuntimeException( e );
            } finally {
                // not strictly needed, but not harmful either
                synchronizer.releaseConditional();
            }
        } );
    }

    // --- //

    public Connection getConnection() throws SQLException {
        WildFlyDataSourceListenerHelper.fireBeforeConnectionAcquire( dataSource );
        long metricsStamp = dataSource.metricsRegistry().beforeConnectionAcquire();

        ConnectionHandler checkedOutHandler = null;
        ConnectionWrapper connectionWrapper = wrapperFromTransaction();
        if ( connectionWrapper != null ) {
            checkedOutHandler = connectionWrapper.getHandler();
        }
        if ( checkedOutHandler == null ) {
            checkedOutHandler = handlerFromLocalCache();
        }
        if ( checkedOutHandler == null ) {
            checkedOutHandler = handlerFromSharedCache();
        }

        dataSource.metricsRegistry().afterConnectionAcquire( metricsStamp );
        WildFlyDataSourceListenerHelper.fireOnConnectionAcquired( dataSource, checkedOutHandler );

        if ( leakEnabled || validationEnable ) {
            checkedOutHandler.setLastAccess( System.nanoTime() );
        }
        if ( leakEnabled ) {
            checkedOutHandler.setHoldingThread( Thread.currentThread() );
        }

        if ( connectionWrapper == null ) {
            connectionWrapper = new ConnectionWrapper( checkedOutHandler, interruptProtection );
            transactionIntegration.associate( connectionWrapper );
        }
        return connectionWrapper;
    }

    private ConnectionWrapper wrapperFromTransaction() throws SQLException {
        Connection connection = transactionIntegration.getConnection();
        if ( connection != null ) {
            return (ConnectionWrapper) connection;
        }
        return null;
    }

    private ConnectionHandler handlerFromLocalCache() throws SQLException {
        UncheckedArrayList<ConnectionHandler> cachedConnections = localCache.get();
        while ( !cachedConnections.isEmpty() ) {
            ConnectionHandler handler = cachedConnections.removeLast();
            if ( handler.setState( CHECKED_IN, CHECKED_OUT ) ) {
                return handler;
            }
        }
        return null;
    }

    private ConnectionHandler handlerFromSharedCache() throws SQLException {
        long remaining = SECONDS.toNanos( configuration.acquisitionTimeout() );
        remaining = remaining > 0 ? remaining : Long.MAX_VALUE;
        try {
            for ( ; ; ) {
                long synchronizationStamp = synchronizer.getStamp();
                for ( ConnectionHandler handler : allConnections.getUnderlyingArray() ) {
                    if ( handler.setState( CHECKED_IN, CHECKED_OUT ) ) {
                        return handler;
                    }
                }
                if ( allConnections.size() < configuration.maxSize() ) {
                    newConnectionHandler().get();
                    continue;
                }
                long start = System.nanoTime();
                if ( remaining < 0 || !synchronizer.tryAcquire( synchronizationStamp, remaining ) ) {
                    throw new SQLException( "Sorry, acquisition timeout!" );
                }
                remaining -= System.nanoTime() - start;
            }
        } catch ( InterruptedException e ) {
            throw new SQLException( "Interrupted while acquiring" );
        } catch ( ExecutionException e ) {
            throw new SQLException( "Exception while creating new connection", e );
        }
    }

    // --- //

    public void returnConnection(ConnectionHandler handler) throws SQLException {
        if ( leakEnabled ) {
            handler.setHoldingThread( null );
        }
        if ( reapEnable ) {
            handler.setLastAccess( System.nanoTime() );
        }
        if ( transactionIntegration.disassociate( handler.getConnection() ) ) {

            WildFlyDataSourceListenerHelper.fireOnConnectionReturn( dataSource, handler );

            localCache.get().add( handler );
            handler.setState( CHECKED_IN );
            synchronizer.releaseConditional();
        }
    }

    private void closeConnectionSafely(ConnectionHandler handler) {
        try {
            handler.closeConnection();
        } catch ( SQLException e ) {
            WildFlyDataSourceListenerHelper.fireOnWarning( dataSource, e );
        }
    }

    // --- Exposed statistics //

    private long activeCount(ConnectionHandler[] handlers) {
        int l = 0;
        for ( ConnectionHandler handler : handlers ) {
            if ( handler.isActive() ) {
                l++;
            }
        }
        return l;
    }

    public long activeCount() {
        return activeCount( allConnections.getUnderlyingArray() );
    }

    public long availableCount() {
        ConnectionHandler[] handlers = allConnections.getUnderlyingArray();
        return handlers.length - activeCount( handlers );
    }

    public long maxUsedCount() {
        return maxUsed = Math.max( maxUsed, allConnections.size() );
    }

    public void resetMaxUsedCount() {
        maxUsed = 0;
    }

    public long awaitingCount() {
        return synchronizer.getQueueLength();
    }

    // --- leak detection //

    private class LeakTask implements Runnable {

        @Override
        public void run() {
            for ( ConnectionHandler handler : allConnections.getUnderlyingArray() ) {
                housekeepingExecutor.submit( new LeakConnectionTask( handler ) );
            }
            housekeepingExecutor.schedule( this, configuration.connectionLeakTimeout(), SECONDS );
        }

        private class LeakConnectionTask implements Runnable {

            private ConnectionHandler handler;

            public LeakConnectionTask(ConnectionHandler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                Thread thread = handler.getHoldingThread();
                if ( thread != null && System.nanoTime() - handler.getLastAccess() > SECONDS.toNanos( configuration.connectionLeakTimeout() ) ) {
                    WildFlyDataSourceListenerHelper.fireOnConnectionLeak( dataSource, handler );
                }
            }
        }
    }

    // --- validation //

    private class ValidationTask implements Runnable {

        @Override
        public void run() {
            for ( ConnectionHandler handler : allConnections.getUnderlyingArray() ) {
                housekeepingExecutor.submit( new ValidateConnectionTask( handler ) );
            }
            housekeepingExecutor.schedule( this, configuration.connectionValidationTimeout(), SECONDS );
        }

        private class ValidateConnectionTask implements Runnable {

            private ConnectionHandler handler;

            public ValidateConnectionTask(ConnectionHandler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                WildFlyDataSourceListenerHelper.fireOnConnectionValidation( dataSource, handler );

                if ( handler.setState( CHECKED_IN, VALIDATION ) ) {
                    if ( configuration.connectionValidator().isValid( handler.getConnection() ) ) {
                        handler.setState( CHECKED_IN );
                        //System.out.println( "Valid connection " + handler.getConnection() );
                    } else {
                        handler.setState( FLUSH );
                        closeConnectionSafely( handler );
                        dataSource.metricsRegistry().afterConnectionClose();
                        handler.setState( DESTROYED );
                        allConnections.remove( handler );
                    }
                }
            }
        }
    }

    // --- reap //

    private class ReapTask implements Runnable {

        @Override
        public void run() {
            // TODO Clear thread-local connection cache
            // localCache = ThreadLocal.withInitial( () -> new UncheckedArrayList<ConnectionHandler>( ConnectionHandler.class ) ) );

            for ( ConnectionHandler handler : allConnections ) {
                housekeepingExecutor.submit( new ReapConnectionTask( handler ) );
            }
            housekeepingExecutor.schedule( this, configuration.connectionReapTimeout(), SECONDS );
        }

        private class ReapConnectionTask implements Runnable {

            private ConnectionHandler handler;

            public ReapConnectionTask(ConnectionHandler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                if ( allConnections.size() > configuration.minSize() && handler.setState( CHECKED_IN, FLUSH ) ) {
                    if ( System.nanoTime() - handler.getLastAccess() > SECONDS.toNanos( configuration.connectionReapTimeout() ) ) {

                        WildFlyDataSourceListenerHelper.fireOnConnectionTimeout( dataSource, handler );

                        closeConnectionSafely( handler );
                        handler.setState( DESTROYED );
                        allConnections.remove( handler );
                        dataSource.metricsRegistry().afterConnectionTimeout();
                    } else {
                        handler.setState( CHECKED_IN );
                        // System.out.println( "Connection " + handler.getConnection() + " used recently. Do not reap!" );
                    }
                }
            }
        }
    }
}

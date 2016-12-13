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

import org.wildfly.datasource.api.WildFlyDataSourceListener;
import org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration;
import org.wildfly.datasource.integrated.util.StampedCopyOnWriteArrayList;
import org.wildfly.datasource.integrated.util.UncheckedArrayList;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.AbstractQueuedLongSynchronizer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
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

    private final WildFlyDataSourceImpl dataSource;
    private final ThreadLocal<UncheckedArrayList<ConnectionHandler>> localCache;

//    private final ExposedCopyOnWriteArrayList<ConnectionHandler> allConnections;
//    private final AtomicCopyOnWriteArrayList<ConnectionHandler> allConnections;
//    private final AtomicReferenceCopyOnWriteArrayList<ConnectionHandler> allConnections;
//    private final LockFreeCopyOnWriteArrayList<ConnectionHandler> allConnections;
//    private final SynchronizedCopyOnWriteArrayList<ConnectionHandler> allConnections;
    private final StampedCopyOnWriteArrayList<ConnectionHandler> allConnections;

    private final PoolSynchronizer synchronizer = new PoolSynchronizer();
    private final ConnectionFactory connectionFactory;
    private final ScheduledExecutorService housekeepingExecutor;
    private final InterruptProtection interruptProtection;

    private final boolean validatingEnable, reapEnable;
    private volatile long maxUsed = 0;

    public ConnectionPool(ConnectionPoolConfiguration configuration, WildFlyDataSourceImpl dataSource) {
        this.configuration = configuration;
        this.dataSource = dataSource;

//        allConnections = new ExposedCopyOnWriteArrayList<>( ConnectionHandler.class );
//        allConnections = new AtomicCopyOnWriteArrayList<>( ConnectionHandler.class );
//        allConnections = new AtomicReferenceCopyOnWriteArrayList<>( ConnectionHandler.class );
//        allConnections = new LockFreeCopyOnWriteArrayList<>( ConnectionHandler.class );
//        allConnections = new SynchronizedCopyOnWriteArrayList<>( ConnectionHandler.class );
        allConnections = new StampedCopyOnWriteArrayList<>(ConnectionHandler.class);

        localCache = ThreadLocal.withInitial( () -> new UncheckedArrayList<ConnectionHandler>( ConnectionHandler.class ) );
        connectionFactory = new ConnectionFactory( configuration.connectionFactoryConfiguration() );
        housekeepingExecutor = Executors.newSingleThreadScheduledExecutor( Executors.defaultThreadFactory() );

        interruptProtection = InterruptProtection.from(configuration.connectionFactoryConfiguration().interruptHandlingMode());

        validatingEnable = configuration.connectionValidationTimeout() > 0;
        reapEnable = configuration.connectionReapTimeout() > 0;
    }

    public void init() {
        switch ( configuration.preFillMode() ) {
            default:
            case OFF:
                break;
            case MIN:
                fill( configuration.minSize() );
                break;
            case MAX:
                fill( configuration.maxSize() );
                break;
        }

        if ( validatingEnable ) {
            housekeepingExecutor.schedule( new ValidationMainTask(), configuration.connectionValidationTimeout(), SECONDS );
        }
        if ( reapEnable ) {
            housekeepingExecutor.schedule( new ReapMainTask(), configuration.connectionReapTimeout(), SECONDS );
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
        return housekeepingExecutor.submit( () -> {
            if ( allConnections.size() >= configuration.maxSize() ) {
                return;
            }

            WildFlyDataSourceListener.fireBeforeConnectionCreated( dataSource.listenerList() );
            long metricsStamp = dataSource.metricsRegistry().beforeConnectionCreated();

            try {
                ConnectionHandler handler = connectionFactory.createHandler();

                WildFlyDataSourceListener.fireOnConnectionCreated( dataSource.listenerList(), handler.getConnection() );

                handler.setState( CHECKED_IN );
                allConnections.add( handler );
                maxUsedCount();

                dataSource.metricsRegistry().afterConnectionCreated( metricsStamp );
            }
            catch ( SQLException e ) {
                throw new RuntimeException( e );
            }
            finally {
                // not strictly needed, but not harmful either
                synchronizer.releaseConditional();
            }
        } );
    }

    // --- //

    public Connection getConnection() throws SQLException {
        WildFlyDataSourceListener.fireBeforeConnectionAcquire( dataSource.listenerList() );
        long metricsStamp = dataSource.metricsRegistry().beforeConnectionAcquire();

        ConnectionHandler checkedOutHandler = handlerFromLocalCache();

        if ( checkedOutHandler == null ) {
            checkedOutHandler = handlerFromSharedCache();
        }

        dataSource.metricsRegistry().afterConnectionAcquire( metricsStamp );
        WildFlyDataSourceListener.fireOnConnectionAcquired( dataSource.listenerList(), checkedOutHandler.getConnection() );

        if ( validatingEnable ) {
            checkedOutHandler.setLastAccess( System.nanoTime() );
            checkedOutHandler.setHoldingThread( Thread.currentThread() );
        }
        return new ConnectionWrapper(this, checkedOutHandler, interruptProtection);
    }

    private ConnectionHandler handlerFromLocalCache() {
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
        long remaining = MILLISECONDS.toNanos( configuration.acquisitionTimeout() );
        remaining = remaining > 0 ? remaining : Long.MAX_VALUE;
        try {
            for ( ; ; ) {
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
                if ( remaining < 0 || !synchronizer.tryAcquire( remaining ) ) {
                    throw new SQLException( "Sorry, acquisition timeout!" );
                }
                remaining -= System.nanoTime() - start;
            }
        }
        catch ( InterruptedException e) {
            throw new SQLException( "Interrupted while acquiring" );
        } catch ( ExecutionException e ) {
            throw new SQLException( "Exception while creating new connection", e );
        }
    }

    // --- //

    public void returnConnection(ConnectionHandler handler) {
        if ( reapEnable ) {
            handler.setLastAccess( System.nanoTime() );
        }

        localCache.get().add( handler );
        handler.setState( CHECKED_IN );
        synchronizer.releaseConditional();
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
        return handlers.length - activeCount(handlers);
    }

    public long maxUsedCount() {
        return maxUsed = Math.max( maxUsed, allConnections.size() );
    }

    public void resetMaxUsedCount() {
        maxUsed = 0;
    }

    public long awaitingCount() {
        return synchronizer.sync.getQueueLength();
    }

    // --- validation + leak detection //

    private class ValidationMainTask implements Runnable {

        private static final long VALIDATION_INTERVAL_MS = 20;

        @Override
        public void run() {
            int i = 0;
            try {
                for ( ConnectionHandler handler : allConnections.getUnderlyingArray() ) {
                    housekeepingExecutor.schedule( new ValidationTask( handler ), ++i * VALIDATION_INTERVAL_MS, MILLISECONDS );
                }
            }
            finally {
                long validationOffset = MILLISECONDS.toSeconds( ++i * VALIDATION_INTERVAL_MS );
                housekeepingExecutor.schedule( this, validationOffset + configuration.connectionValidationTimeout(), SECONDS );
            }
        }
    }

    private class ValidationTask implements Runnable {

        private static final long LEAK_INTERVAL_S = 1;

        private ConnectionHandler handler;

        public ValidationTask(ConnectionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            WildFlyDataSourceListener.fireOnConnectionValidation( dataSource.listenerList(), handler.getConnection() );

            if ( handler.setState( CHECKED_IN, VALIDATION ) ) {
                if ( configuration.connectionValidator().isValid( handler.getConnection() ) ) {
                    handler.setState( CHECKED_IN );
                    //System.out.println( "Valid connection " + handler.getConnection() );
                } else {
                    handler.setState( FLUSH );
                    closeInvalidConnection( handler );
                    handler.setState( DESTROYED );
                    allConnections.remove( handler );
                }
            }
            else {
                if ( System.nanoTime() - handler.getLastAccess() > SECONDS.toNanos( LEAK_INTERVAL_S ) ) {
                    // Potential connection leak. Report.
                    WildFlyDataSourceListener.fireOnConnectionLeak( dataSource.listenerList(), handler.getConnection() );
                }
            }
        }

        private void closeInvalidConnection( ConnectionHandler connectionWrapper ) {
            try {
                connectionWrapper.closeUnderlyingConnection();
            } catch ( SQLException e ) {
                // Ignore
            }
            dataSource.metricsRegistry().afterConnectionClose();
        }

    }

    // --- reap //

    private class ReapMainTask implements Runnable {

        private static final long REAP_INTERVAL_MS = 20;

        @Override
        public void run() {
            int i = 0;
            try {
                for ( ConnectionHandler handler : allConnections ) {
                    housekeepingExecutor.schedule( new ReapTask( handler ), ++i * REAP_INTERVAL_MS, MILLISECONDS );
                }
            } finally {
                long timeOffset = ( ++i * REAP_INTERVAL_MS ) / 1000;
                housekeepingExecutor.schedule( this, timeOffset + configuration.connectionReapTimeout(), SECONDS );
            }
        }
    }

    private class ReapTask implements Runnable {

        private ConnectionHandler handler;

        public ReapTask(ConnectionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            if ( allConnections.size() > configuration.minSize() && handler.setState( CHECKED_IN, FLUSH ) ) {
                if ( System.nanoTime() - handler.getLastAccess() > SECONDS.toNanos( configuration.connectionReapTimeout() ) ) {

                    WildFlyDataSourceListener.fireOnConnectionTimeout( dataSource.listenerList(), handler.getConnection() );

                    closeIdleConnection( handler );
                    handler.setState( DESTROYED );
                    allConnections.remove( handler );
                }
                else {
                    handler.setState( CHECKED_IN );
//                    System.out.println( "Connection " + handler.getConnection() + " used recently. Do not reap!" );
                }
            }
        }

        private void closeIdleConnection( ConnectionHandler handler ) {
            try {
                handler.closeUnderlyingConnection();
            } catch ( SQLException e ) {
                // Ignore
            }
            dataSource.metricsRegistry().afterConnectionTimeout();
        }

    }

    // --- Synchronizer //

    private static class PoolSynchronizer {

        private final LongAdder counter = new LongAdder();

        private final AbstractQueuedLongSynchronizer sync = new AbstractQueuedLongSynchronizer() {

            @Override
            protected boolean tryAcquire(long value) {
                // Advance when counter is greater than value
                return counter.longValue() > value;

//                if ( counter.longValue() > value) {
//                    System.out.printf( "     >>>   %s got UNLOCKED!!  (%d > %d)%n", Thread.currentThread().getName(), counter.longValue(), value );
//                    System.out.flush();
//                }
//                else {
//                    System.out.printf( "  ------  %s got LOCKED on __ %d __ (current %d) %n", Thread.currentThread().getName(), value, counter.longValue() );
//                    System.out.flush();
//                }
//                return counter.longValue() > value;
            }

            @Override
            protected boolean tryRelease(long releases) {
                counter.add( releases );
//                System.out.printf( "  >>>     %s releases __ %d __%n", Thread.currentThread().getName(), counter.longValue() );
                return true;
            }

        };

        // --- //

        public int getQueueLength() {
            return sync.getQueueLength();
        }

        public boolean tryAcquire(long nanos) throws InterruptedException {
            return sync.tryAcquireNanos( counter.longValue(), nanos );
        }

        public void releaseConditional() {
            if (sync.hasQueuedThreads()) {
                sync.release( 1 );
            }
        }

    }

}

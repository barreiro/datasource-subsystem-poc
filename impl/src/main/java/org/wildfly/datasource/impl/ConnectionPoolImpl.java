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

package org.wildfly.datasource.impl;

import org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionPoolImpl implements AutoCloseable {

    private final ConnectionPoolConfiguration configuration;

    private final WildFlyDataSourceImpl dataSource;

    // allConnections collection should only be mutated by housekeeping threads

    private final BlockingPool<ConnectionHandler> connectionPool;
    private final List<ConnectionHandler> allConnections;
    private final LongAdder usedCounter;
    private final AtomicLong maxUsedCounter = new AtomicLong( 0 );

    private final ConnectionFactoryImpl connectionFactory;

    private final ScheduledExecutorService housekeepingExecutor;

    public ConnectionPoolImpl(ConnectionPoolConfiguration configuration, WildFlyDataSourceImpl dataSource) {
        this.configuration = configuration;
        this.dataSource = dataSource;

        connectionPool = ConnectionHandlerPoolFactory.create( configuration );
        allConnections = new CopyOnWriteArrayList<>();
        usedCounter = new LongAdder();

        connectionFactory = new ConnectionFactoryImpl( configuration.connectionFactoryConfiguration(), this );

        housekeepingExecutor = Executors.newSingleThreadScheduledExecutor( Executors.defaultThreadFactory() );
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

        if ( !configuration.validationTimeout().isZero() ) {
            housekeepingExecutor.schedule( new ValidationMainTask(), configuration.validationTimeout().toNanos(), TimeUnit.NANOSECONDS );
        }
        if ( !configuration.reapTimeout().isZero() ) {
            housekeepingExecutor.schedule( new ReapMainTask(), configuration.reapTimeout().toNanos(), TimeUnit.NANOSECONDS );
        }

    }

    private void fill(int size) {
        while ( allConnections.size() < size ) {
            housekeepingExecutor.submit( () -> {
                if ( allConnections.size() < size ) {
                    newConnectionHandler();
                }
            } );
        }
    }

    @Override
    public void close() {
        housekeepingExecutor.shutdownNow();
    }

    // --- //

    private void newConnectionHandler() {
        housekeepingExecutor.submit( () -> {
            if ( allConnections.size() >= configuration.maxSize() ) {
                return;
            }

            WildFlyDataSourceListenerHelper.fireBeforeConnectionCreated( dataSource.listenerList() );
            long metricsStamp = dataSource.metricsRegistry().beforeConnectionCreated();

            try {
                ConnectionHandler handler = connectionFactory.createHandler();

                WildFlyDataSourceListenerHelper.fireOnConnectionCreated( dataSource.listenerList(), handler.getConnection() );

                allConnections.add( handler );
                connectionPool.checkIn( handler );
                handler.setState( ConnectionHandler.State.CHECKED_IN );

                dataSource.metricsRegistry().afterConnectionCreated( metricsStamp );
            } catch ( SQLException e ) {
                // TODO: woops!
                // cache exception and interrupt pool
            }
        } );
    }

    // --- //

    public Connection getConnection() throws SQLException {
        int allConnectionsSize = allConnections.size();
        if ( usedCounter.longValue() >= allConnectionsSize && allConnectionsSize < configuration.maxSize() ) {
            newConnectionHandler();
        }
        WildFlyDataSourceListenerHelper.fireBeforeConnectionAcquire( dataSource.listenerList() );
        long metricsStamp = dataSource.metricsRegistry().beforeConnectionAcquire();

        ConnectionHandler handler;
        do {
            handler = connectionPool.checkOut();
        } while ( handler.getState() != ConnectionHandler.State.CHECKED_IN );

        prepareHandlerForCheckOut( handler );

        dataSource.metricsRegistry().afterConnectionAcquire( metricsStamp );
        WildFlyDataSourceListenerHelper.fireOnConnectionAcquired( dataSource.listenerList(), handler.getConnection() );

        return handler.getConnection();
    }

    private void prepareHandlerForCheckOut(ConnectionHandler handler) {
        usedCounter.increment();
        updateMaxUsedCounter();
        handler.setState( ConnectionHandler.State.CHECKED_OUT );
        handler.setLastAccess( System.nanoTime() );
        handler.setHoldingThread( Thread.currentThread() );
    }

    // --- //

    public void returnConnection(ConnectionHandler handler) {
        ConnectionHandler.State state = handler.getState();
        if ( state == ConnectionHandler.State.CHECKED_OUT ) {
            prepareHandlerForCheckIn( handler );
            connectionPool.checkIn( handler );
        }
        else {
            usedCounter.decrement();
        }
    }

    private void prepareHandlerForCheckIn(ConnectionHandler handler) {
        usedCounter.decrement();
        handler.setState( ConnectionHandler.State.CHECKED_IN );
        handler.setLastAccess( System.nanoTime() );
    }

    // --- //

    // Methods for statistics. Rely on usedCount (although it't not accurate) rather than iterate the connection list

    public long activeCount() {
        return usedCounter.longValue();
    }

    public long availableCount() {
        return allConnections.size() - usedCounter.longValue();
    }

    private void updateMaxUsedCounter() {
        long oldMax = maxUsedCounter.longValue();
        long value = usedCounter.longValue();
        while ( value > oldMax ) {
            if ( maxUsedCounter.compareAndSet( oldMax, value ) ) {
                break;
            }
            else {
                // concurrent modification -- retry
                oldMax = maxUsedCounter.longValue();
            }
        }
    }

    public long maxUsedCount() {
        return maxUsedCounter.longValue();
    }

    public void resetMaxUsedCount() {
        maxUsedCounter.set( 0 );
    }

    // --- validation + leak detection //

    private class ValidationMainTask implements Runnable {

        private static final long VALIDATION_INTERVAL_NS = 20 * 1_000_000;
        private static final long LEAK_INTERVAL_S = 1;

        @Override
        public void run() {
            int i = 0;
            try {
                for ( ConnectionHandler handler : allConnections ) {
                    if ( handler.getState() == ConnectionHandler.State.CHECKED_IN ) {
                        housekeepingExecutor.schedule( new ValidationTask( handler ), ++i * VALIDATION_INTERVAL_NS, TimeUnit.NANOSECONDS );
                    }
                    if ( handler.getState() == ConnectionHandler.State.CHECKED_OUT ) {
                        if ( System.nanoTime() - handler.getLastAccess() > TimeUnit.SECONDS.toNanos( LEAK_INTERVAL_S ) ) {
                            // Potential connection leak. Report.
                            WildFlyDataSourceListenerHelper.fireOnConnectionLeak( dataSource.listenerList(), handler.getConnection(), handler.getHoldingThread());
                        }
                    }
                }
            }
            finally {
                housekeepingExecutor.schedule( this, ++i * VALIDATION_INTERVAL_NS + configuration.validationTimeout().toNanos(), TimeUnit.NANOSECONDS );
            }
        }
    }

    private class ValidationTask implements Runnable {

        private ConnectionHandler handler;

        public ValidationTask(ConnectionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            WildFlyDataSourceListenerHelper.fireOnConnectionValidation( dataSource.listenerList(), handler.getConnection() );

            if ( handler.getState() == ConnectionHandler.State.CHECKED_IN ) {
                if ( configuration.connectionValidator().isValid( handler.getConnection() ) ) {
                    // TODO: all good!
                    //System.out.println( "Valid connection " + handler.getConnection() );
                } else {
                    handler.setState( ConnectionHandler.State.TO_DESTROY );
                    closeInvalidConnection( handler );
                    handler.setState( ConnectionHandler.State.DESTROYED );
                    allConnections.remove( handler );
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

        private static final long REAP_INTERVAL_NS = 20 * 1_000_000;

        @Override
        public void run() {
            int i = 0;
            try {
                for ( ConnectionHandler handler : allConnections ) {
                    if ( handler.getState() == ConnectionHandler.State.CHECKED_IN ) {
                        housekeepingExecutor.schedule( new ReapTask( handler ), ++i * REAP_INTERVAL_NS, TimeUnit.NANOSECONDS );
                    }
                }
            } finally {
                housekeepingExecutor.schedule( this, ++i * REAP_INTERVAL_NS + configuration.reapTimeout().toNanos(), TimeUnit.NANOSECONDS );
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
            if ( allConnections.size() > configuration.minSize() && handler.getState() == ConnectionHandler.State.CHECKED_IN ) {
                if ( System.nanoTime() - handler.getLastAccess() > configuration.reapTimeout().toNanos() ) {

                    WildFlyDataSourceListenerHelper.fireOnConnectionTimeout( dataSource.listenerList(), handler.getConnection() );

                    handler.setState( ConnectionHandler.State.TO_DESTROY );
                    closeIdleConnection( handler );
                    handler.setState( ConnectionHandler.State.DESTROYED );
                    allConnections.remove( handler );
                }
                else {
                    // TODO: all good!
                    //System.out.println( "In use connection " + handler.getConnection() );
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

}

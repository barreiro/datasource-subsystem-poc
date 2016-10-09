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

import org.wildfly.datasource.api.WildFlyDataSourceListener;
import org.wildfly.datasource.api.configuration.ConnectionPoolConfiguration;

import java.lang.reflect.Proxy;
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
            case OFF:
                break;
            case MIN:
                fill( configuration.minSize() );
                break;
            case MAX:
                fill( configuration.maxSize() );
                break;
        }

        if ( configuration.connectionValidationTimeout() > 0 ) {
            housekeepingExecutor.schedule( new ValidationMainTask(), configuration.connectionValidationTimeout(), TimeUnit.SECONDS );
        }
        if ( configuration.connectionReapTimeout() > 0 ) {
            housekeepingExecutor.schedule( new ReapMainTask(), configuration.connectionReapTimeout(), TimeUnit.SECONDS );
        }

    }

    private void fill(int size) {
        while ( allConnections.size() < size ) {
            housekeepingExecutor.submit( () -> {
                if ( allConnections.size() < size ) {
                    try {
                        newConnectionHandler();
                    } catch ( SQLException e ) {
                        // TODO: Log
                    }
                }
            } );
        }
    }

    @Override
    public void close() {
        housekeepingExecutor.shutdownNow();
    }

    // --- //

    private void newConnectionHandler() throws SQLException {
        housekeepingExecutor.submit( () -> {
            if ( allConnections.size() >= configuration.maxSize() ) {
                return;
            }

            WildFlyDataSourceListener.fireBeforeConnectionCreated( dataSource.listenerList() );
            long metricsStamp = dataSource.metricsRegistry().beforeConnectionCreated();

            try {
                ConnectionHandler handler = connectionFactory.createHandler();

                WildFlyDataSourceListener.fireOnConnectionCreated( dataSource.listenerList(), handler.getConnection() );

                allConnections.add( handler );
                connectionPool.checkIn( handler );
                handler.setState( ConnectionHandler.State.CHECKED_IN );

                dataSource.metricsRegistry().afterConnectionCreated( metricsStamp );
            } catch ( SQLException e ) {
                // TODO: woops!
            }
        } );
    }

    // --- //

    public Connection getConnection() throws SQLException {
        int allConnectionsSize = allConnections.size();
        if ( usedCounter.longValue() >= allConnectionsSize && allConnectionsSize < configuration.maxSize() ) {
            newConnectionHandler();
        }
        WildFlyDataSourceListener.fireBeforeConnectionAcquire( dataSource.listenerList() );
        long metricsStamp = dataSource.metricsRegistry().beforeConnectionAcquire();

        ConnectionHandler handler;
        do {
            handler = connectionPool.checkOut();
        } while ( handler.getState() != ConnectionHandler.State.CHECKED_IN );

        prepareHandlerForCheckOut( handler );

        dataSource.metricsRegistry().afterConnectionAcquire( metricsStamp );
        WildFlyDataSourceListener.fireOnConnectionAcquired( dataSource.listenerList(), handler.getConnection() );

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
        prepareHandlerForCheckIn( handler );
        if ( state == ConnectionHandler.State.CHECKED_OUT ) {
            connectionPool.checkIn( handler );
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
            } else {
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

        private static final long VALIDATION_INTERVAL_MS = 20;
        private static final long LEAK_INTERVAL_S = 1;

        @Override
        public void run() {
            int i = 0;
            try {
                for ( ConnectionHandler handler : allConnections ) {
                    if ( handler.getState() == ConnectionHandler.State.CHECKED_IN ) {
                        housekeepingExecutor.schedule( new ValidationTask( handler ), ++i * VALIDATION_INTERVAL_MS, TimeUnit.MILLISECONDS );
                    }
                    if ( handler.getState() == ConnectionHandler.State.CHECKED_OUT ) {
                        if ( System.nanoTime() - handler.getLastAccess() > TimeUnit.SECONDS.toNanos( LEAK_INTERVAL_S ) ) {
                            // Potential connection leak. Report.
                            WildFlyDataSourceListener.fireOnConnectionLeak( dataSource.listenerList(), handler.getConnection() );
                        }
                    }
                }
            }
            finally {
                long validationOffset = TimeUnit.MILLISECONDS.toSeconds( ++i * VALIDATION_INTERVAL_MS );
                housekeepingExecutor.schedule( this, validationOffset + configuration.connectionValidationTimeout(), TimeUnit.SECONDS );
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
            WildFlyDataSourceListener.fireOnConnectionValidation( dataSource.listenerList(), handler.getConnection() );

            if ( handler.getState() == ConnectionHandler.State.CHECKED_IN ) {
                if ( ! configuration.connectionValidator().isValid( handler.getConnection() ) ) {
                    handler.setState( ConnectionHandler.State.TO_DESTROY );
                    closeInvalidConnection( handler.getConnection() );
                    handler.setState( ConnectionHandler.State.DESTROYED );
                    allConnections.remove( handler );
                }
                else {
                    // TODO: all good!
                    //System.out.println( "Valid connection " + handler.getConnection() );
                }
            }
        }

        private void closeInvalidConnection( Connection connection ) {
            try {
                if ( connection instanceof Proxy ) {
                    connection.unwrap( Connection.class ).close();
                }
                else {
                    connection.close();
                }
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
                    if ( handler.getState() == ConnectionHandler.State.CHECKED_IN ) {
                        housekeepingExecutor.schedule( new ReapTask( handler ), ++i * REAP_INTERVAL_MS, TimeUnit.MILLISECONDS );
                    }
                }
            }
            finally {
                long timeOffset = ( ++i * REAP_INTERVAL_MS ) / 1000;
                housekeepingExecutor.schedule( this, timeOffset + configuration.connectionReapTimeout(), TimeUnit.SECONDS );
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
                if ( System.nanoTime() - handler.getLastAccess() > TimeUnit.SECONDS.toNanos( configuration.connectionReapTimeout() ) ) {

                    WildFlyDataSourceListener.fireOnConnectionTimeout( dataSource.listenerList(), handler.getConnection() );

                    handler.setState( ConnectionHandler.State.TO_DESTROY );
                    closeIdleConnection( handler.getConnection() );
                    handler.setState( ConnectionHandler.State.DESTROYED );
                    allConnections.remove( handler );
                }
                else {
                    // TODO: all good!
                    //System.out.println( "In use connection " + handler.getConnection() );
                }
            }
        }

        private void closeIdleConnection( Connection connection ) {
            try {
                if ( connection instanceof Proxy ) {
                    connection.unwrap( Connection.class ).close();
                }
                else {
                    connection.close();
                }
            } catch ( SQLException e ) {
                // Ignore
            }
            dataSource.metricsRegistry().afterConnectionTimeout();
        }

    }

}

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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionHandler {

    private final static AtomicReferenceFieldUpdater<ConnectionHandler, State> stateUpdater = AtomicReferenceFieldUpdater.newUpdater( ConnectionHandler.class, State.class, "state" );

    private final Connection connection;

    private ConnectionPool connectionPool;

    // Can use annotation to get a little better performance
    // @Contended
    private volatile State state;

    // for leak detection (only valid for CHECKED_OUT connections)
    private Thread holdingThread;

    // for expiration (CHECKED_IN connections) and leak detection (CHECKED_OUT connections)
    private long lastAccess;

    public ConnectionHandler(Connection connection) {
        this.connection = connection;
        state = State.NEW;
        lastAccess = System.currentTimeMillis();
    }

    public void setConnectionPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public Connection getConnection() {
        return connection;
    }

    public void closeConnection() throws SQLException {
        if ( state != State.FLUSH ) {
            throw new SQLException( "Closing connection in incorrect state" );
        }
        connection.close();
    }

    public void returnConnection() throws SQLException {
        connectionPool.returnConnection( this );
    }

    public boolean setState(State expected, State newState) {
        if ( expected == State.DESTROYED ) {
            throw new IllegalArgumentException( "Trying to move out of state DESTROYED" );
        }

        switch ( newState ) {
            default:
                throw new IllegalArgumentException( "Trying to set invalid state " + newState );
            case NEW:
                throw new IllegalArgumentException( "Trying to set invalid state NEW" );
            case CHECKED_IN:
            case CHECKED_OUT:
            case VALIDATION:
            case FLUSH:
            case DESTROYED:
                return stateUpdater.compareAndSet( this, expected, newState );
        }
    }

    public void setState(State newState) {
        // I believe we could use lazySet here, but there doesn't seem to be any performance advantage
        stateUpdater.set( this, newState );
    }

    public boolean isActive() {
        return stateUpdater.get( this ) == State.CHECKED_OUT;
    }

    public long getLastAccess() {
        return lastAccess;
    }

    public void setLastAccess(long lastAccess) {
        this.lastAccess = lastAccess;
    }

    public Thread getHoldingThread() {
        return holdingThread;
    }

    public void setHoldingThread(Thread holdingThread) {
        this.holdingThread = holdingThread;
    }

    // --- //

    public enum State {
        NEW, CHECKED_IN, CHECKED_OUT, VALIDATION, FLUSH, DESTROYED
    }

}
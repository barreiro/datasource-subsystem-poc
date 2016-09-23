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

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionHandler {

    public enum State {
        NEW, CHECKED_IN, CHECKED_OUT, TO_DESTROY, DESTROYED
    }

    private Connection connection;

    // state can be concurrently modified by housekeeping tasks
    private final AtomicReference<State> state;

    // for leak detection (only valid for CHECKED_OUT connections)
    private Thread holdingThread;

    // for expiration (CHECKED_IN connections) and leak detection (CHECKED_OUT connections)
    private long lastAccess;

    public ConnectionHandler(Connection connection) {
        this.connection = connection;
        state = new AtomicReference<>( State.NEW );
        lastAccess = System.currentTimeMillis();
    }

    // package private -- to be used only during initialization
    void setConnection(Connection connection) {
        if ( !( state.get() == State.NEW ) ) {
            throw new IllegalStateException( "" );
        }
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
    }

    public State getState() {
        return state.get();
    }

    public boolean setState(State newState) {
        State oldState = state.get();
        switch ( newState ) {
            default:
                throw new IllegalArgumentException( "Trying to set invalid state " + newState );
            case NEW:
                throw new IllegalArgumentException( "Trying to set invalid state NEW" );
            case CHECKED_IN:
                return ( oldState == State.NEW || oldState == State.CHECKED_OUT ) && state.compareAndSet( oldState, newState );
            case CHECKED_OUT:
                return ( oldState == State.CHECKED_IN ) && state.compareAndSet( oldState, newState );
            case TO_DESTROY:
                return ( oldState == State.NEW || oldState == State.CHECKED_IN || oldState == State.CHECKED_OUT ) && state.compareAndSet( oldState, newState );
            case DESTROYED:
                return ( oldState == State.TO_DESTROY ) && state.compareAndSet( oldState, newState );
        }
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

}
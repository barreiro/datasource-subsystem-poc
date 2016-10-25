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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ConnectionHandler implements Connection {

    public enum State {
        NEW, CHECKED_IN, CHECKED_OUT, TO_DESTROY, DESTROYED
    }

    private final ConnectionPoolImpl connectionPool;

    private final InterruptProtection interruptProtection;

    private final Connection wrappedConnection;

    // state can be concurrently modified by housekeeping tasks
    private final AtomicReference<State> state;

    // for leak detection (only valid for CHECKED_OUT connections)
    private Thread holdingThread;

    // for expiration (CHECKED_IN connections) and leak detection (CHECKED_OUT connections)
    private long lastAccess;

    public ConnectionHandler(ConnectionPoolImpl pool, InterruptProtection protection, Connection connection) {
        connectionPool = pool;
        interruptProtection = protection;
        wrappedConnection = connection;
        state = new AtomicReference<>( State.NEW );
        lastAccess = System.currentTimeMillis();
    }

    public Connection getConnection() {
        return this;
    }

    public void closeUnderlyingConnection() throws SQLException {
        if ( state.get() != State.TO_DESTROY ) {
            throw new SQLException( "Closing connection in incorrect state" );
        }
        wrappedConnection.close();
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

    // --- //

    private <T> T protect(InterruptProtection.SQLCallable<T> callable) throws SQLException {
        return interruptProtection.protect( callable );
    }

    private void protect(InterruptProtection.SQLRunnable runnable) throws SQLException {
        interruptProtection.protect( runnable );
    }

    // --- //

    @Override
    public void abort(Executor executor) throws SQLException {
        wrappedConnection.abort( executor );
    }

    @Override
    public void commit() throws SQLException {
        wrappedConnection.commit();
    }

    @Override
    public void close() throws SQLException {
        connectionPool.returnConnection( this );
    }

    @Override
    public void clearWarnings() throws SQLException {
        wrappedConnection.clearWarnings();
    }

    @Override
    public Clob createClob() throws SQLException {
        return wrappedConnection.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return wrappedConnection.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return wrappedConnection.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return wrappedConnection.createSQLXML();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return wrappedConnection.createArrayOf( typeName, elements );
    }

    @Override
    public Statement createStatement() throws SQLException {
        return wrappedConnection.createStatement();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return wrappedConnection.createStatement( resultSetType, resultSetConcurrency);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return wrappedConnection.createStatement( resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return wrappedConnection.createStruct( typeName, attributes );
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return wrappedConnection.getAutoCommit();
    }

    @Override
    public String getCatalog() throws SQLException {
        return wrappedConnection.getCatalog();
    }

    @Override
    public int getHoldability() throws SQLException {
        return wrappedConnection.getHoldability();
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return wrappedConnection.getClientInfo();
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return wrappedConnection.getClientInfo( name );
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return wrappedConnection.getMetaData();
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return wrappedConnection.getNetworkTimeout();
    }

    @Override
    public String getSchema() throws SQLException {
        return wrappedConnection.getSchema();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return wrappedConnection.getTypeMap();
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return wrappedConnection.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return wrappedConnection.getWarnings();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return wrappedConnection.isClosed();
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return wrappedConnection.isReadOnly();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return wrappedConnection.isValid( timeout );
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return wrappedConnection.nativeSQL( sql );
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return wrappedConnection.prepareCall( sql );
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return wrappedConnection.prepareCall( sql, resultSetType, resultSetConcurrency );
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return wrappedConnection.prepareCall( sql, resultSetType, resultSetConcurrency, resultSetHoldability );
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return wrappedConnection.prepareStatement( sql );
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return wrappedConnection.prepareStatement( sql, resultSetType, resultSetConcurrency );
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return wrappedConnection.prepareStatement( sql, resultSetType, resultSetConcurrency, resultSetHoldability );
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return wrappedConnection.prepareStatement( sql, autoGeneratedKeys );
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return wrappedConnection.prepareStatement( sql, columnIndexes );
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return wrappedConnection.prepareStatement( sql, columnNames );
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        wrappedConnection.releaseSavepoint( savepoint );
    }

    @Override
    public void rollback() throws SQLException {
        wrappedConnection.rollback();
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        wrappedConnection.rollback( savepoint );
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        wrappedConnection.setAutoCommit( autoCommit );
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        wrappedConnection.setCatalog( catalog );
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        wrappedConnection.setClientInfo( name, value );
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        wrappedConnection.setClientInfo( properties );
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        wrappedConnection.setReadOnly( readOnly );
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        wrappedConnection.setHoldability( holdability );
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return wrappedConnection.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return wrappedConnection.setSavepoint( name );
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        wrappedConnection.setNetworkTimeout( executor, milliseconds );
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        wrappedConnection.setTransactionIsolation( level );
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        wrappedConnection.setTypeMap( map );
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        wrappedConnection.setSchema( schema );
    }

    // --- //

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return wrappedConnection.unwrap( iface );
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return wrappedConnection.isWrapperFor( iface );
    }

}
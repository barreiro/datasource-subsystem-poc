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

package org.wildfly.datasource.narayana;

import org.wildfly.datasource.api.tx.TransactionSupport;
import org.wildfly.datasource.api.tx.TransactionalResource;

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class NarayanaTransactionSupport implements TransactionSupport {

    private final TransactionManager transactionManager;

    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    private final String key;

    public NarayanaTransactionSupport(TransactionManager transactionManager, TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionManager = transactionManager;
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
        this.key = UUID.randomUUID().toString();
    }

    public Connection getConnection() throws SQLException {
        try {
            Transaction transaction = transactionManager.getTransaction();
            if ( transaction != null &&
                    ( transaction.getStatus() == Status.STATUS_ACTIVE || transaction.getStatus() == Status.STATUS_MARKED_ROLLBACK ) ) {
                return (Connection) transactionSynchronizationRegistry.getResource( key );
            }
            return null;
        } catch ( Exception e ) {
            throw new SQLException( "Exception in getting existing transaction association", e );
        }
    }

    public void associate(Connection connection) throws SQLException {
        try {
            Transaction transaction = transactionManager.getTransaction();
            if ( transaction == null ) {
                return;
            }

            if ( transaction.getStatus() == Status.STATUS_ACTIVE || transaction.getStatus() == Status.STATUS_MARKED_ROLLBACK ) {
                transactionSynchronizationRegistry.putResource( key, connection );
                transactionSynchronizationRegistry.registerInterposedSynchronization( new Synchronization() {
                    @Override
                    public void beforeCompletion() {}

                    @Override
                    public void afterCompletion(int status) {
                        try { // Return connection to the pool
                            connection.close();
                        } catch ( SQLException ignore ) {}
                    }
                } );
                transaction.enlistResource( new LocalXAResource( (TransactionalResource) connection ) );
            } else {
                throw new SQLException( "Transaction not in ACTIVE state" );
            }
        } catch ( Exception e ) {
            throw new SQLException( "Exception in association of connection to existing transaction", e );
        }
    }

    public boolean disassociate(Connection connection) throws SQLException {
        try {
            transactionSynchronizationRegistry.putResource( key, null );
            return true;
        } catch ( Exception e ) {
//            throw new SQLException( "Exception in disassociation of connection to existing transaction", e );
            return true;
        }
    }

}
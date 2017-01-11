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

package org.wildfly.datasource.api.tx;


import org.wildfly.datasource.api.ConnectionHandler;

import javax.transaction.Status;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.sql.SQLException;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */

// TODO: Move to own module!

public class JTATransactionIntegration implements TransactionIntegration {

    private final TransactionManager transactionManager;

    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    public JTATransactionIntegration(TransactionManager transactionManager, TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionManager = transactionManager;
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
    }

    public ConnectionHandler getConnectionHandler() throws SQLException {
        try {
            Transaction transaction = transactionManager.getTransaction();
            return transaction == null ? null : (ConnectionHandler) transactionSynchronizationRegistry.getResource( transaction );
        } catch ( Exception e ) {
            throw new SQLException( "Exception in getting existing transaction association", e );
        }
    }

    public void associate(ConnectionHandler handler) throws SQLException {
        try {
            Transaction transaction = transactionManager.getTransaction();
            if ( transaction.getStatus() == Status.STATUS_ACTIVE ) {
                handler.incrementTransactionAssociation();
                transactionSynchronizationRegistry.putResource( transaction, handler );
            }
            else {
                throw new SQLException( "Transaction not in ACTIVE state" );
            }
        } catch ( Exception e ) {
            throw new SQLException( "Exception in association of connection to existing transaction", e );
        }
    }

    public boolean disasssociate(ConnectionHandler handler) throws SQLException {
        try {
            long associationCount = handler.decrementTransactionAssociation();
            if ( associationCount <= 0 ) {
                transactionSynchronizationRegistry.putResource( transactionManager.getTransaction(), null );
                return true;
            }
            return false;
        } catch ( Exception e ) {
            throw new SQLException( "Exception in association of connection to existing transaction", e );
        }
    }

}

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

import org.jboss.tm.ConnectableResource;
import org.jboss.tm.LastResource;
import org.jboss.tm.XAResourceWrapper;
import org.wildfly.datasource.api.tx.TransactionAware;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class LocalXAResource implements XAResource, ConnectableResource, LastResource, XAResourceWrapper {

    private final TransactionAware connection;

    private Xid currentXid;

    private String productName;

    private String productVersion;

    private String jndiName;

    public LocalXAResource(TransactionAware connection) {
        this.connection = connection;
    }

    @Override
    public void start(Xid xid, int flags) throws XAException {
        if ( currentXid != null ) {
            if ( flags != TMJOIN && flags != TMRESUME ) {
                throw new XAException( XAException.XAER_DUPID );
            }
        } else {
            if ( flags != TMNOFLAGS ) {
                throw new XAException( "Starting resource with wrong flags" );
            }
            try {
                connection.transactionBegin();
            } catch ( Throwable t ) {
                throw new XAException( "Error trying to start local transaction: " + t.getMessage() );
            }
            currentXid = xid;
        }
    }

    @Override
    public void commit(Xid xid, boolean onePhase) throws XAException {
        if ( xid == null || !xid.equals( currentXid ) ) {
            throw new XAException( "Invalid xid to transactionCommit" );
        }
        currentXid = null;

        try {
            connection.transactionCommit();
            connection.transactionEnd();
        } catch ( Throwable t ) {
            throw new XAException( "Error trying to transactionCommit local transaction: " + t.getMessage() );
        }
    }

    @Override
    public void rollback(Xid xid) throws XAException {
        if ( xid == null || !xid.equals( currentXid ) ) {
            throw new XAException( "Invalid xid to transactionCommit" );
        }
        currentXid = null;

        try {
            connection.transactionRollback();
            connection.transactionEnd();
        } catch ( Throwable t ) {
            throw new XAException( "Error trying to transactionRollback local transaction: " + t.getMessage() );
        }
    }

    @Override
    public void end(Xid xid, int flags) throws XAException {
    }

    @Override
    public void forget(Xid xid) throws XAException {
        throw new XAException( "Forget not supported in local XA resource" );
    }

    @Override
    public int getTransactionTimeout() throws XAException {
        return 0;
    }

    @Override
    public boolean isSameRM(XAResource xaResource) throws XAException {
        return this == xaResource;
    }

    @Override
    public int prepare(Xid xid) throws XAException {
        return XA_OK;
    }

    @Override
    public Xid[] recover(int flags) throws XAException {
        throw new XAException( "No recover in local XA resource" );
    }

    @Override
    public boolean setTransactionTimeout(int timeout) throws XAException {
        return false;
    }

    // --- XA Resource Wrapper //

    @Override
    public XAResource getResource() {
        return this;
    }

    @Override
    public String getProductName() {
        return productName;
    }

    @Override
    public String getProductVersion() {
        return productVersion;
    }

    @Override
    public String getJndiName() {
        return jndiName;
    }

    @Override
    public Object getConnection() throws Throwable {
        return connection;
    }
}

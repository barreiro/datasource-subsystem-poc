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

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */

// TODO: Move to own module!

public class LocalXAResource implements XAResource {

    private final ConnectionHandler connectionHandler;

    private Xid currentXid;

    private boolean autocommit;

    public LocalXAResource(ConnectionHandler handler) {
        this.connectionHandler = handler;
    }

    @Override
    public void start(Xid xid, int flags) throws XAException {
        if ( currentXid != null ) {
            if ( flags != TMJOIN && flags != TMRESUME ) {
                throw new XAException( XAException.XAER_DUPID );
            }
        }
        else {
            if ( flags != TMNOFLAGS ) {
                throw new XAException( "Starting resource with wrong flags" );
            }
            try {
                autocommit = connectionHandler.getConnection().getAutoCommit();
                connectionHandler.getConnection().setAutoCommit( false );
            }
            catch (Throwable t)             {
                throw new XAException( "Error trying to start local transaction" );
            }
            currentXid = xid;
        }
    }

    @Override
    public void commit(Xid xid, boolean onePhase) throws XAException {
        if ( xid == null || !xid.equals( currentXid ) ) {
            throw new XAException( "Invalid xid to commit" );
        }
        currentXid = null;

        try {
            connectionHandler.getConnection().commit();
        }
        catch (Throwable t)             {
            throw new XAException( "Error trying to commit local transaction" );
        }
    }

    @Override
    public void rollback(Xid xid) throws XAException {
        if ( xid == null || !xid.equals( currentXid ) ) {
            throw new XAException( "Invalid xid to commit" );
        }
        currentXid = null;

        try {
            connectionHandler.getConnection().rollback();
        }
        catch (Throwable t) {
            throw new XAException( "Error trying to rollback local transaction" );
        }
    }

    @Override
    public void end(Xid xid, int flags) throws XAException {
        try {
            connectionHandler.getConnection().setAutoCommit( autocommit );
        }
        catch (Throwable t)             {
            throw new XAException( "Error trying to re-set auto-commit" );
        }
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

}

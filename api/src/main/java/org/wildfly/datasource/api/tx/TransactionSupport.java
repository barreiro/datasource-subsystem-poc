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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public interface TransactionSupport {

    Connection getConnection() throws SQLException;

    void associate(Connection connection) throws SQLException;

    boolean disassociate(Connection connection) throws SQLException;

    // --- //

    static TransactionSupport noSupport() {
        return new TransactionSupport() {

            @Override
            public Connection getConnection() throws SQLException {
                return null;
            }

            @Override
            public void associate(Connection connection) throws SQLException {
            }

            @Override
            public boolean disassociate(Connection connection) throws SQLException {
                return true;
            }
        };
    }
}

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

import java.sql.Connection;
import java.util.List;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class WildFlyDataSourceListenerHelper {

    private WildFlyDataSourceListenerHelper() {}

    static void fireBeforeConnectionCreated(List<WildFlyDataSourceListener> listeners) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.beforeConnectionCreated();
        }
    }

    static void fireOnConnectionCreated(List<WildFlyDataSourceListener> listeners, Connection connection) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.onConnectionCreated( connection );
        }
    }

    static void fireBeforeConnectionAcquire(List<WildFlyDataSourceListener> listeners) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.beforeConnectionAcquire();
        }
    }

    static void fireOnConnectionAcquired(List<WildFlyDataSourceListener> listeners, Connection connection) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.onConnectionAcquired( connection );
        }
    }

    static void fireBeforeConnectionReturn(List<WildFlyDataSourceListener> listeners) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.beforeConnectionReturn();
        }
    }

    static void fireOnConnectionReturn(List<WildFlyDataSourceListener> listeners, Connection connection) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.onConnectionReturn( connection );
        }
    }

    static void fireOnConnectionValidation(List<WildFlyDataSourceListener> listeners, Connection connection) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.onConnectionValidation( connection );
        }
    }

    static void fireOnConnectionLeak(List<WildFlyDataSourceListener> listeners, Connection connection, Thread t) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.onConnectionLeak( connection, t );
        }
    }

    static void fireOnConnectionTimeout(List<WildFlyDataSourceListener> listeners, Connection connection) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.onConnectionTimeout( connection );
        }
    }

    static void fireOnConnectionClose(List<WildFlyDataSourceListener> listeners, Connection connection) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.onConnectionAcquired( connection );
        }
    }

    static void fireOnWarning(List<WildFlyDataSourceListener> listeners, Throwable throwable) {
        for ( WildFlyDataSourceListener listener : listeners ) {
            listener.onWarning( throwable );
        }
    }

}

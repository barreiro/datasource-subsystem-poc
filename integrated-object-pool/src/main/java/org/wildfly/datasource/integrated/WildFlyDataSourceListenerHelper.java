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

import org.wildfly.datasource.api.WildFlyDataSourceListener;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class WildFlyDataSourceListenerHelper {

    private WildFlyDataSourceListenerHelper() {
    }

    public static void fireBeforeConnectionCreated(WildFlyDataSourceIntegrated dataSource) {
        fire( dataSource.listenerList(), WildFlyDataSourceListener::beforeConnectionCreated );
    }

    public static void fireOnConnectionCreated(WildFlyDataSourceIntegrated dataSource, ConnectionHandler handler) {
        fire( dataSource.listenerList(), l -> l.onConnectionCreated( handler.getConnection() ) );
    }

    public static void fireBeforeConnectionAcquire(WildFlyDataSourceIntegrated dataSource) {
        fire( dataSource.listenerList(), WildFlyDataSourceListener::beforeConnectionAcquire );
    }

    public static void fireOnConnectionAcquired(WildFlyDataSourceIntegrated dataSource, ConnectionHandler handler) {
        fire( dataSource.listenerList(), l -> l.onConnectionAcquired( handler.getConnection() ) );
    }

    public static void fireBeforeConnectionReturn(WildFlyDataSourceIntegrated dataSource) {
        fire( dataSource.listenerList(), WildFlyDataSourceListener::beforeConnectionReturn );
    }

    public static void fireOnConnectionReturn(WildFlyDataSourceIntegrated dataSource, ConnectionHandler handler) {
        fire( dataSource.listenerList(), l -> l.onConnectionReturn( handler.getConnection() ) );
    }

    public static void fireOnConnectionValidation(WildFlyDataSourceIntegrated dataSource, ConnectionHandler handler) {
        fire( dataSource.listenerList(), l -> l.onConnectionValidation( handler.getConnection() ) );
    }

    public static void fireOnConnectionLeak(WildFlyDataSourceIntegrated dataSource, ConnectionHandler handler) {
        fire( dataSource.listenerList(), l -> l.onConnectionLeak( handler.getConnection(), handler.getHoldingThread() ) );
    }

    public static void fireOnConnectionTimeout(WildFlyDataSourceIntegrated dataSource, ConnectionHandler handler) {
        fire( dataSource.listenerList(), l -> l.onConnectionTimeout( handler.getConnection() ) );
    }

    public static void fireOnConnectionClose(WildFlyDataSourceIntegrated dataSource, ConnectionHandler handler) {
        fire( dataSource.listenerList(), l -> l.onConnectionClose( handler.getConnection() ) );
    }

    public static void fireOnWarning(WildFlyDataSourceIntegrated dataSource, Throwable throwable) {
        fire( dataSource.listenerList(), l -> l.onWarning( throwable ) );
    }

    private static void fire(List<WildFlyDataSourceListener> listeners, Consumer<WildFlyDataSourceListener> consumer) {
        try {
            for ( WildFlyDataSourceListener listener : listeners ) {
                consumer.accept( listener );
            }
        } catch ( Throwable ignore ) {
        }
    }
}

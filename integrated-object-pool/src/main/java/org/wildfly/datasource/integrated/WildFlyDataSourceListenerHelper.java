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

import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class WildFlyDataSourceListenerHelper {

    private WildFlyDataSourceListenerHelper() {}

    public static void fireBeforeConnectionCreated(List<WildFlyDataSourceListener> listeners) {
        fire( listeners, WildFlyDataSourceListener::beforeConnectionCreated );
    }

    public static void fireOnConnectionCreated(List<WildFlyDataSourceListener> listeners, Connection connection) {
        fire( listeners, l -> l.onConnectionCreated( connection ) );
    }

    public static void fireBeforeConnectionAcquire(List<WildFlyDataSourceListener> listeners) {
        fire( listeners, WildFlyDataSourceListener::beforeConnectionAcquire );
    }

    public static void fireOnConnectionAcquired(List<WildFlyDataSourceListener> listeners, Connection connection) {
        fire( listeners, l -> l.onConnectionAcquired( connection ) );
    }

    public static void fireBeforeConnectionReturn(List<WildFlyDataSourceListener> listeners) {
        fire( listeners, WildFlyDataSourceListener::beforeConnectionReturn );
    }

    public static void fireOnConnectionReturn(List<WildFlyDataSourceListener> listeners, Connection connection) {
        fire( listeners, l -> l.onConnectionReturn( connection ) );
    }

    public static void fireOnConnectionValidation(List<WildFlyDataSourceListener> listeners, Connection connection) {
        fire( listeners, l -> l.onConnectionValidation( connection ) );
    }

    public static void fireOnConnectionLeak(List<WildFlyDataSourceListener> listeners, Connection connection) {
        fire( listeners, l -> l.onConnectionLeak( connection ) );
    }

    public static void fireOnConnectionTimeout(List<WildFlyDataSourceListener> listeners, Connection connection) {
        fire( listeners, l -> l.onConnectionTimeout( connection ) );
    }

    public static void fireOnConnectionClose(List<WildFlyDataSourceListener> listeners, Connection connection) {
        fire( listeners, l -> l.onConnectionClose( connection ) );
    }

    public static void fireOnWarning(List<WildFlyDataSourceListener> listeners, Throwable throwable) {
        fire( listeners, l -> l.onWarning( throwable ) );
    }

    private static void fire(List<WildFlyDataSourceListener> listeners, Consumer<WildFlyDataSourceListener> consumer ) {
        try {
            for ( WildFlyDataSourceListener listener : listeners ) {
                consumer.accept( listener );
            }
        }
        catch ( Throwable t ) { // to be safe
        }
    }
    

}

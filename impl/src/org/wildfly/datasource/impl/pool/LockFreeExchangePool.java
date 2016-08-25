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

package org.wildfly.datasource.impl.pool;

import org.wildfly.datasource.impl.BlockingPool;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Exchanger;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class LockFreeExchangePool<T> implements BlockingPool<T> {

    private ConcurrentLinkedQueue<T> objectQueue;

    private ConcurrentLinkedQueue<Exchanger<T>> handOffQueue;

    public LockFreeExchangePool() {
        objectQueue = new ConcurrentLinkedQueue<>();
        handOffQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public T checkOut() {

        T object = objectQueue.poll();
        if ( object == null ) {
            Exchanger<T> exchanger = new Exchanger<>();
            handOffQueue.offer( exchanger );
            do {
                try {
                    return exchanger.exchange( null );
                } catch ( InterruptedException ignored ) {}
            } while ( true );
        }
        else {
            Exchanger<T> exchanger = handOffQueue.poll();
            if (exchanger != null) {
                do {
                    try {
                        exchanger.exchange( object );
                        break;
                    } catch ( InterruptedException ignored ) {
                    }
                } while ( true );
                return checkOut();
            }
            return object;
        }
    }

    @Override
    public void checkIn(T t) {
        Exchanger<T> exchanger = handOffQueue.poll();
        if (exchanger != null) {
            do {
                try {
                    exchanger.exchange( t );
                    return;
                } catch ( InterruptedException ignored ) {
                }
            } while ( true );
        }
        else {
            objectQueue.offer(t);
        }
    }

    @Override
    public void close() throws Exception {
        objectQueue.clear();
        handOffQueue.clear();
    }
    
}

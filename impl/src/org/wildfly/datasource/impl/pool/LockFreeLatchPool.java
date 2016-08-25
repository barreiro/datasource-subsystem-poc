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
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class LockFreeLatchPool<T> implements BlockingPool<T> {

    private ConcurrentLinkedQueue<T> objectQueue;

    private ConcurrentLinkedQueue<LatchHolder<T>> handOffQueue;

    public LockFreeLatchPool() {
        objectQueue = new ConcurrentLinkedQueue<>();
        handOffQueue = new ConcurrentLinkedQueue<>();
    }

    private static class LatchHolder<T> {

        private final CountDownLatch latch = new CountDownLatch( 0 );

        private T element = null;

        public T await() {
            do {
                try {
                    latch.await();
                    return element;
                } catch ( InterruptedException ignore ) {
                }
            } while ( true );
        }

        public void set(T t) {
            element = t;
            do {
                try {
                    latch.await();
                    return;
                } catch ( InterruptedException ignore ) {
                }
            } while ( true );
        }
    }

    @Override
    public T checkOut() {
        T object = objectQueue.poll();
        if ( object == null ) {
            LatchHolder<T> latch = new LatchHolder<>();
            handOffQueue.offer( latch );
            return latch.await();
        } else {
            LatchHolder<T> latch = handOffQueue.poll();
            if ( latch != null ) {
                latch.set( object );
                return checkOut();
            }
            return object;
        }
    }

    @Override
    public void checkIn(T t) {
        LatchHolder<T> latch = handOffQueue.poll();
        if ( latch != null ) {
            latch.set( t );
        } else {
            objectQueue.offer( t );
        }
    }

    @Override
    public void close() throws Exception {
        objectQueue.clear();
        handOffQueue.clear();
    }

}

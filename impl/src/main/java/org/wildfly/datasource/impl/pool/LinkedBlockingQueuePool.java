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

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class LinkedBlockingQueuePool<T> implements BlockingPool<T> {

    private LinkedBlockingQueue<T> queue;

    public LinkedBlockingQueuePool(){
        queue = new LinkedBlockingQueue<>();
    }

    @Override
    public T checkOut() {
        do {
            try {
                return queue.take();
            } catch ( InterruptedException ignored ) {
            }
        } while (true);
    }

    @Override
    public void checkIn(T t) {
        do {
            try {
                queue.put( t );
                return;
            } catch ( InterruptedException ignored ) {
            }
        } while (true);
    }

    @Override
    public void close() throws Exception {
        queue.clear();
    }
}

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

package org.wildfly.datasource.integrated.util;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class ExposedCopyOnWriteArrayList<T> extends CopyOnWriteArrayList<T> {

    private final T[] EMPTY;

    @SuppressWarnings( "unchecked" )
    public ExposedCopyOnWriteArrayList(Class<?> clazz) {
        EMPTY = (T[]) Array.newInstance( clazz, 0 );
    }

    public T[] getUnderlyingArray() {
        return super.toArray( EMPTY );
    }

    @Override
    public Iterator<T> iterator() {
        T[] array = getUnderlyingArray();

        return array.length == 0 ? EMPTY_ITERATOR : new Iterator<T>() {

            private int index;

            private T[] cache = getUnderlyingArray();

            @Override
            public boolean hasNext() {
                return index < cache.length;
            }

            @Override
            public T next() {
                return cache[index++];
            }
        };
    }

    private static final Iterator EMPTY_ITERATOR = new Iterator() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            throw new IndexOutOfBoundsException();
        }
    };

}

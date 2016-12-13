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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class SynchronizedCopyOnWriteArrayList<T> implements List<T> {

    private T[] data;

    @SuppressWarnings( "unchecked" )
    public SynchronizedCopyOnWriteArrayList(Class<? extends T> clazz) {
        this.data = (T[]) Array.newInstance( clazz, 0 );
    }

    // -- //

    public synchronized T[] getUnderlyingArray() {
        return data;
    }

    @Override
    public T get(int index) {
        return getUnderlyingArray()[index];
    }

    @Override
    public int size() {
        return getUnderlyingArray().length;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    // --- //

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException( );
    }

    @Override
    public synchronized boolean add(T element) {
        data = Arrays.copyOf( data, data.length + 1 );
        data[data.length - 1] = element;
        return true;
    }

    public synchronized T removeLast() {
        T element = data[data.length - 1];
        data = Arrays.copyOf( data, data.length - 1 );
        return element;
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public synchronized boolean remove(Object element) {
        for ( int index = data.length - 1; index >= 0; index-- ) {
            if ( element == data[index] ) {
                T[] newData = Arrays.copyOf( data, data.length - 1 );
                System.arraycopy( data, index + 1, newData, index, data.length - index - 1 );
                data = newData;
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void clear() {
         data = Arrays.copyOf( data, 0 );
    }

    @Override
    public synchronized T remove(int index) {
        T element = data[index];
        T[] newData = Arrays.copyOf( data, data.length - 1 );
        if ( data.length - index - 1 != 0 ) {
            System.arraycopy( data, index + 1, newData, index, data.length - index - 1 );
        }
        data = newData;
        return element;
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
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

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Override
    public Stream<T> stream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<T> parallelStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Spliterator<T> spliterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sort(Comparator<? super T> c) {
        throw new UnsupportedOperationException();
    }

}

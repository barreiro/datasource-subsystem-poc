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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class UncheckedArrayList<T> implements List<T> {

    private final Class<?> clazz;
    private T[] data;
    private int size;

    @SuppressWarnings( "unchecked" )
    public UncheckedArrayList(Class<?> clazz) {
        this(clazz, 4);
    }

    @SuppressWarnings( "unchecked" )
    public UncheckedArrayList(Class<?> clazz, int capacity) {
        this.data = (T[]) Array.newInstance( clazz, capacity );
        this.clazz = clazz;
        this.size = 0;
    }

    @Override
    public boolean add(T element) {
        if (size >= data.length ) {
            data = Arrays.copyOf( data, data.length << 1 );
        }
        data[size] = element;
        size++;
        return true;
    }

    @Override
    public T get(int index) {
        return data[index];
    }

    public T removeLast() {
        T element = data[--size];
        data[size] = null;
        return element;
    }

    @Override
    public boolean remove(Object element) {
        for ( int index = size - 1; index >= 0; index-- ) {
            if ( element == data[index] ) {
                int numMoved = size - index - 1;
                if ( numMoved > 0 ) {
                    System.arraycopy( data, index + 1, data, index, numMoved );
                }
                data[--size] = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        for ( int i = 0; i < size; i++ ) {
            data[i] = null;
        }
        size = 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public T set(int index, T element) {
        T old = data[index];
        data[index] = element;
        return old;
    }

    @Override
    public T remove(int index) {
        if ( size == 0 ) {
            return null;
        }
        T old = data[index];
        int moved = size - index - 1;
        if ( moved > 0 ) {
            System.arraycopy( data, index + 1, data, index, moved );
        }
        data[--size] = null;
        return old;
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public T next() {
                if ( index < size ) {
                    return data[index++];
                }
                throw new NoSuchElementException( "No more elements in FastList" );
            }
        };
    }

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

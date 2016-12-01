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
public class AtomicCopyOnWriteArrayList<T> implements List<T> {

    private volatile T[] data;

    private final Class<? extends T> clazz;
    private final AtomicArray<AtomicCopyOnWriteArrayList, T> atomizer;

    @SuppressWarnings( "unchecked" )
    public AtomicCopyOnWriteArrayList(Class<? extends T> clazz) {
        this.data = (T[]) Array.newInstance( clazz, 0 );
        this.clazz = clazz;
        this.atomizer = AtomicArray.create( AtomicReferenceFieldUpdater.newUpdater( AtomicCopyOnWriteArrayList.class, Object[].class, "data"), (Class) clazz );
    }

    // -- //

    public T[] getUnderlyingArray() {
        return data;
    }

    @Override
    public T get(int index) {
        return data[index];
    }

    @Override
    public int size() {
        return data.length;
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
    public boolean add(T element) {
        atomizer.add( this, element );
        return true;
    }

    public T removeLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public boolean remove(Object element) {
        return atomizer.remove( this, (T) element, true );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public void clear() {
        atomizer.clear( this );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public T remove(int index) {
         throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {

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

    @Override
    public Object clone() throws CloneNotSupportedException {
        super.clone();
        throw new CloneNotSupportedException();
    }

    // --- //

    /*
     * From JBoss Threads
     */
    public static final class AtomicArray<T, V> {

        private final AtomicReferenceFieldUpdater<T, V[]> updater;
        private final V[] emptyArray;
        private final Creator<V> creator;

        private AtomicArray(AtomicReferenceFieldUpdater<T, V[]> updater, Creator<V> creator) {
            this.updater = updater;
            this.creator = creator;
            emptyArray = creator.create(0);
        }

        public static <T, V> AtomicArray<T, V> create(AtomicReferenceFieldUpdater<T, V[]> updater, Class<V> componentType) {
            return new AtomicArray<T,V>(updater, new ReflectCreator<V>(componentType));
        }

        public static <T, V> AtomicArray<T, V> create(AtomicReferenceFieldUpdater<T, V[]> updater, Creator<V> creator) {
            return new AtomicArray<T,V>(updater, creator);
        }

        public void clear(T instance) {
            updater.set(instance, emptyArray);
        }


        @SuppressWarnings({ "unchecked" })
        private static <V> V[] copyOf(final AtomicArray.Creator<V> creator, V[] old, int newLen) {
            final V[] target = creator.create(newLen);
            System.arraycopy(old, 0, target, 0, Math.min(old.length, newLen));
            return target;
        }

        public void add(T instance, V value) {
            final AtomicReferenceFieldUpdater<T, V[]> updater = this.updater;
            for (;;) {
                final V[] oldVal = updater.get(instance);
                final int oldLen = oldVal.length;
                final V[] newVal = copyOf(creator, oldVal, oldLen + 1);
                newVal[oldLen] = value;
                if (updater.compareAndSet(instance, oldVal, newVal)) {
                    return;
                }
            }
        }

        public boolean remove(T instance, V value, boolean identity) {
            final AtomicReferenceFieldUpdater<T, V[]> updater = this.updater;
            for (;;) {
                final V[] oldVal = updater.get(instance);
                final int oldLen = oldVal.length;
                if (oldLen == 0) {
                    return false;
                } else {
                    int index = -1;
                    if (identity || value == null) {
                        for (int i = 0; i < oldLen; i++) {
                            if (oldVal[i] == value) {
                                index = i;
                                break;
                            }
                        }
                    } else {
                        for (int i = 0; i < oldLen; i++) {
                            if (value.equals(oldVal[i])) {
                                index = i;
                                break;
                            }
                        }
                    }
                    if (index == -1) {
                        return false;
                    }
                    final V[] newVal = creator.create(oldLen - 1);
                    System.arraycopy(oldVal, 0, newVal, 0, index);
                    System.arraycopy(oldVal, index + 1, newVal, index, oldLen - index - 1);
                    if (updater.compareAndSet(instance, oldVal, newVal)) {
                        return true;
                    }
                }
            }
        }

        public interface Creator<V> {
            V[] create(int len);
        }

        private static final class ReflectCreator<V> implements Creator<V> {
            private final Class<V> type;

            public ReflectCreator(final Class<V> type) {
                this.type = type;
            }

            @SuppressWarnings({ "unchecked" })
            public V[] create(final int len) {
                if (type == Object.class) {
                    return (V[]) new Object[len];
                } else {
                    return (V[]) Array.newInstance(type, len);
                }
            }
        }
    }

}

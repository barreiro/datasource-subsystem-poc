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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.AbstractQueuedLongSynchronizer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class FastBag<T> implements BlockingPool<T> {

    private final ConcurrentBag<T> bag;

    public FastBag(){
        bag = new ConcurrentBag<>();
    }

    @Override
    public T checkOut() {
        do {
            try {
                return bag.borrow().value();
            } catch ( InterruptedException ignored ) {
            }
        } while ( true );
    }

    @Override
    public void checkIn(T t) {
        bag.add( t );
    }

    @Override
    public void close() throws Exception {
        bag.close();
    }

    // --- //

    private final static class BagEntry<E> {
        private final AtomicInteger state;
        private final E value;

        private BagEntry(E value) {
            this.state = new AtomicInteger();
            this.value = value;
        }

        public int getState() {
            return state.get();
        }

        public boolean compareAndSet(int expect, int update) {
            return state.compareAndSet( expect, update );
        }

        public void lazySet(int update) {
            state.lazySet( update );
        }

        public E value(){
            return value;
        }

    }

    // --- //

    public static class ConcurrentBag<B> implements AutoCloseable {

        private static final int STATE_NOT_IN_USE = 0;
        private static final int STATE_IN_USE = 1;

        private final SequenceSynchronizer synchronizer;
        private final CopyOnWriteArrayList<BagEntry<B>> sharedList;
        private final ThreadLocal<FastList<BagEntry<B>>> threadList;
        private final ThreadLocal<BagEntry<B>> threadEntry;

        public ConcurrentBag() {
            this.sharedList = new CopyOnWriteArrayList<>();
            this.synchronizer = new SequenceSynchronizer();
            this.threadEntry = new ThreadLocal<>();
            this.threadList = ThreadLocal.withInitial( () -> new FastList<>( BagEntry.class, 16 ) );
        }

        public void close() {
            sharedList.clear();
        }

        public BagEntry<B> borrow() throws InterruptedException {
            // Try the thread-local list first
            FastList<BagEntry<B>> list = threadList.get();

            for ( int i = list.size() - 1; i >= 0; i-- ) {
                final BagEntry<B> bagEntry = list.remove( i );
                if ( bagEntry != null && bagEntry.compareAndSet( STATE_NOT_IN_USE, STATE_IN_USE ) ) {
                    return bagEntry;
                }
            }

            // Otherwise, scan the shared list
            long startSeq;
            do {
                do {
                    startSeq = synchronizer.currentSequence();
                    for ( BagEntry<B> bagEntry : sharedList ) {
                        if ( bagEntry.compareAndSet( STATE_NOT_IN_USE, STATE_IN_USE ) ) {
                            threadEntry.set( bagEntry );
                            return bagEntry;
                        }
                    }
                    Thread.yield();
                } while ( startSeq < synchronizer.currentSequence() );
            } while ( synchronizer.waitUntilSequenceExceeded( startSeq, Long.MAX_VALUE ) );

            return null;
        }

        public void add(final B entry) {
            BagEntry<B> bagEntry = threadEntry.get();
            if (bagEntry == null || bagEntry.value() != entry) {
                bagEntry = new BagEntry<>( entry );
                sharedList.add( bagEntry );
            }
            bagEntry.lazySet( STATE_NOT_IN_USE );
            threadList.get().add( bagEntry );
            synchronizer.signal();
        }

    }

    // --- //

    public static final class FastList<FT> extends ArrayList<FT> {
        private static final long serialVersionUID = -4598088075242913858L;

        private final Class<?> clazz;
        private FT[] elementData;
        private int size;

        @SuppressWarnings( "unchecked" )
        public FastList(Class<?> clazz) {
            this.elementData = (FT[]) Array.newInstance( clazz, 32 );
            this.clazz = clazz;
        }

        @SuppressWarnings( "unchecked" )
        public FastList(Class<?> clazz, int capacity) {
            this.elementData = (FT[]) Array.newInstance( clazz, capacity );
            this.clazz = clazz;
        }

        @Override
        public boolean add(FT element) {
            try {
                elementData[size++] = element;
            } catch ( ArrayIndexOutOfBoundsException e ) {
                // overflow-conscious code
                final int oldCapacity = elementData.length;
                final int newCapacity = oldCapacity << 1;
                @SuppressWarnings( "unchecked" )
                final FT[] newElementData = (FT[]) Array.newInstance( clazz, newCapacity );
                System.arraycopy( elementData, 0, newElementData, 0, oldCapacity );
                newElementData[size - 1] = element;
                elementData = newElementData;
            }
            return true;
        }

        @Override
        public FT get(int index) {
            return elementData[index];
        }

        public FT removeLast() {
            FT element = elementData[--size];
            elementData[size] = null;
            return element;
        }

        @Override
        public boolean remove(Object element) {
            for ( int index = size - 1; index >= 0; index-- ) {
                if ( element == elementData[index] ) {
                    final int numMoved = size - index - 1;
                    if ( numMoved > 0 ) {
                        System.arraycopy( elementData, index + 1, elementData, index, numMoved );
                    }
                    elementData[--size] = null;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void clear() {
            for ( int i = 0; i < size; i++ ) {
                elementData[i] = null;
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
        public FT set(int index, FT element) {
            FT old = elementData[index];
            elementData[index] = element;
            return old;
        }

        @Override
        public FT remove(int index) {
            if ( size == 0 ) {
                return null;
            }
            final FT old = elementData[index];
            final int numMoved = size - index - 1;
            if ( numMoved > 0 ) {
                System.arraycopy( elementData, index + 1, elementData, index, numMoved );
            }
            elementData[--size] = null;
            return old;
        }

        @Override
        public boolean contains(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<FT> iterator() {
            return new Iterator<FT>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                public FT next() {
                    if ( index < size ) {
                        return elementData[index++];
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
        public boolean addAll(Collection<? extends FT> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(int index, Collection<? extends FT> c) {
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
        public void add(int index, FT element) {
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
        public ListIterator<FT> listIterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListIterator<FT> listIterator(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FT> subList(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void trimToSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensureCapacity(int minCapacity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object clone() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void removeRange(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException();
        }

        public void forEach(Consumer<? super FT> action) {
            throw new UnsupportedOperationException();
        }

        public Spliterator<FT> spliterator() {
            throw new UnsupportedOperationException();
        }

        public boolean removeIf(Predicate<? super FT> filter) {
            throw new UnsupportedOperationException();
        }

        public void replaceAll(UnaryOperator<FT> operator) {
            throw new UnsupportedOperationException();
        }

        public void sort(Comparator<? super FT> c) {
            throw new UnsupportedOperationException();
        }

    }
    
    // --- //

    private static final class SequenceSynchronizer {

        private final LongAdder sequence;
        private final Synchronizer synchronizer;

        public SequenceSynchronizer() {
            this.synchronizer = new Synchronizer();
            this.sequence = new LongAdder();
        }

        public void signal() {
            synchronizer.releaseShared( 1 );
        }

        public long currentSequence() {
            return sequence.sum();
        }

        public boolean waitUntilSequenceExceeded(long sequence, long nanosTimeout) throws InterruptedException {
            return synchronizer.tryAcquireSharedNanos( sequence, nanosTimeout );
        }

        public boolean hasQueuedThreads() {
            return synchronizer.hasQueuedThreads();
        }

        public int getQueueLength() {
            return synchronizer.getQueueLength();
        }

        private final class Synchronizer extends AbstractQueuedLongSynchronizer {

            @Override
            protected long tryAcquireShared(long seq) {
                return sequence.sum() - ( seq + 1 );
            }

            @Override
            protected boolean tryReleaseShared(long unused) {
                sequence.increment();
                return true;
            }

        }

    }

}

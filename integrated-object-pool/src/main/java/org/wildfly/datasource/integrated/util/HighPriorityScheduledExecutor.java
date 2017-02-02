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

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author <a href="lbarreiro@redhat$.com">Luis Barreiro</a>
 */
public class HighPriorityScheduledExecutor extends ScheduledThreadPoolExecutor {

    private Queue<RunnableFuture<?>> highPriorityTasks = new LinkedList<>();

    public HighPriorityScheduledExecutor(int executorSize, String threadName) {
        super( executorSize, r -> {
            Thread housekeepingThread = new Thread( r, threadName );
            housekeepingThread.setDaemon( true );
            return housekeepingThread;
        } );
    }

    public Future<?> executeNow(Runnable highPriority) {
        RunnableFuture<?> future = new FutureTask<>( highPriority, null );
        highPriorityTasks.add( future );
        submit( () -> {
        } );
        return future;
    }

    @Override
    protected void beforeExecute(Thread thread, Runnable lowPriorityTask) {
        for ( RunnableFuture<?> priorityTask; ( priorityTask = highPriorityTasks.poll() ) != null; priorityTask.run() ) {
            // Run all high priority tasks in queue first, then low priority
        }
        super.beforeExecute( thread, lowPriorityTask );
    }
}

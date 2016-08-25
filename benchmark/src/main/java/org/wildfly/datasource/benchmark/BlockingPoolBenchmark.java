/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.wildfly.datasource.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatFactory;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;
import org.wildfly.datasource.impl.BlockingPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class BlockingPoolBenchmark {

    private BlockingPool<Object> pool;

    @Param( {
            "org.wildfly.datasource.impl.pool.LinkedBlockingQueuePool",
            "org.wildfly.datasource.impl.pool.LockFreeExchangePool",
            "org.wildfly.datasource.impl.pool.LockFreeSynchronousPool",
//            "org.wildfly.datasource.impl.pool.LockFreeLatchPool",
            "org.wildfly.datasource.impl.pool.LockFreeLocalBarrierPool",
            "org.wildfly.datasource.impl.pool.SemaphoreConcurrentLinkedQueuePool"
    } )
    public String poolClassName;

    @Param( "1" ) // to show the number of threads on the report
    public String threads;

    @Setup
    public void benchmarkSetup() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        pool = (BlockingPool<Object>) BlockingPoolBenchmark.class.getClassLoader().loadClass( poolClassName ).newInstance();
        for ( int i = 0; i < 10; i++ ) {
            pool.checkIn( new Object() );
        }
    }

    @Benchmark
    @BenchmarkMode( { Mode.Throughput, Mode.SampleTime } )
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkMain() {
        Object o = pool.checkOut();

        if (o == null) {
            throw new NullPointerException();
        }

        Thread.yield();

        Blackhole.consumeCPU(1000);

//        try {
//            Thread.sleep( 1 );
//        } catch ( InterruptedException ignore ) { }

        pool.checkIn(o);
    }

    @TearDown
    public void benchmarkTearDown() {
        try {
            pool.close();
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) throws RunnerException {
        int[] threadNumbers = new int[] { 5, 10, 20, 50, 100, 200 };

        Options baseOptions = new OptionsBuilder()
                .include(BlockingPoolBenchmark.class.getSimpleName())
                .verbosity( VerboseMode.NORMAL )
                .forks( 1 )
                .build();

        Collection<RunResult> results = new ArrayList<>();

        // This is a trick to use 'threadNumber' as a benchmark parameter
        for (int threadNumber : threadNumbers) {
            Options actualOptions = new OptionsBuilder().parent( baseOptions ).
                    threads( threadNumber )
                    .param( "threads", Integer.toString( threadNumber ) ).build();

            results.addAll( new Runner( actualOptions ).run() );
        }

        System.out.println("\n\n\n");
        ResultFormatFactory.getInstance( ResultFormatType.TEXT, System.out ).writeOut( results );
    }

}

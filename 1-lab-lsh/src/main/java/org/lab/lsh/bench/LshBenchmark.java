package org.lab.lsh.bench;

import org.lab.lsh.LSHIndex;
import org.lab.lsh.Vector3D;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class LshBenchmark {

    @Param({"12"})
    public int k;

    @Param({"50000"})
    public int datasetSize;

    private Vector3D[] dataset;
    private Vector3D[] queries;
    private LSHIndex populatedIndex;
    private int cursor;

    @Setup(Level.Trial)
    public void setupTrial() {
        Random rng = new Random(42L);
        dataset = new Vector3D[datasetSize];
        for (int i = 0; i < datasetSize; i++) {
            dataset[i] = new Vector3D(
                    rng.nextGaussian(),
                    rng.nextGaussian(),
                    rng.nextGaussian()
            );
        }

        queries = new Vector3D[1024];
        for (int i = 0; i < queries.length; i++) {
            queries[i] = new Vector3D(
                    rng.nextGaussian(),
                    rng.nextGaussian(),
                    rng.nextGaussian()
            );
        }

        populatedIndex = new LSHIndex(k, 123L);
        for (Vector3D v : dataset) {
            populatedIndex.add(v);
        }
    }

    @State(Scope.Thread)
    public static class ThreadCursor {
        int idx;
    }

    @Benchmark
    public void findDuplicatesSingleThread(ThreadCursor tc, Blackhole bh) {
        Vector3D q = queries[(tc.idx++) & 1023];
        List<Vector3D> res = populatedIndex.findDuplicates(q);
        bh.consume(res);
    }

    @Benchmark
    @Threads(4)
    public void findDuplicatesFourThreads(ThreadCursor tc, Blackhole bh) {
        Vector3D q = queries[(tc.idx++) & 1023];
        List<Vector3D> res = populatedIndex.findDuplicates(q);
        bh.consume(res);
    }

    @Benchmark
    @Measurement(iterations = 3, time = 1)
    @Warmup(iterations = 2, time = 1)
    public void buildIndex(Blackhole bh) {
        LSHIndex index = new LSHIndex(k, 123L);
        for (int i = 0; i < dataset.length; i++) {
            index.add(dataset[(cursor + i) % dataset.length]);
        }
        cursor = (cursor + 1) % dataset.length;
        bh.consume(index);
    }
}
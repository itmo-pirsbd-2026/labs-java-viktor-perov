package org.lab.lsh;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LSHIndex {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Vector3D[] planes;
    private final Map<Long, List<Vector3D>> buckets;

    public LSHIndex(int k) {
        this(k, System.nanoTime());
    }

    public LSHIndex(int k, long seed) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        int boundedK = Math.min(k, 63);

        Random random = new Random(seed);
        this.planes = new Vector3D[boundedK];
        for (int i = 0; i < boundedK; i++) {
            this.planes[i] = new Vector3D(
                    random.nextGaussian(),
                    random.nextGaussian(),
                    random.nextGaussian()
            );
        }
        this.buckets = new HashMap<>(1024);
    }

    private long hash(Vector3D v) {
        long res = 0L;
        for (int i = 0; i < planes.length; i++) {
            if (v.dot(planes[i]) >= 0.0) {
                res |= (1L << i);
            }
        }
        return res;
    }

    public void add(Vector3D v) {
        lock.writeLock().lock();
        try {
            long h = hash(v);
            buckets.computeIfAbsent(h, key -> new ArrayList<>()).add(v);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Vector3D> findDuplicates(Vector3D v) {
        lock.readLock().lock();
        try {
            long h = hash(v);
            List<Vector3D> bucket = buckets.get(h);
            return bucket == null ? List.of() : List.copyOf(bucket);
        } finally {
            lock.readLock().unlock();
        }
    }
}
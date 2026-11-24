import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lab.lsh.LSHIndex;
import org.lab.lsh.Vector3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LSHIndexTest {

    @ParameterizedTest(name = "[{index}] k={0}, seed={1}, v1={2}, v2={3} -> sameBucket={4}")
    @MethodSource("bucketPairsProvider")
    @DisplayName("Табличная проверка попадания пар векторов в один или разные бакеты")
    void tableBucketMembership(int k, long seed, Vector3D v1, Vector3D v2, boolean expectSameBucket) {
        LSHIndex index = new LSHIndex(k, seed);
        index.add(v1);

        List<Vector3D> found = index.findDuplicates(v2);
        boolean inSameBucket = found.contains(v1);

        assertEquals(expectSameBucket, inSameBucket);
    }

    static Stream<Arguments> bucketPairsProvider() {
        return Stream.of(
                Arguments.of(1, 42L, new Vector3D(1.0, 2.0, 3.0), new Vector3D(2.0, 4.0, 6.0), true),
                Arguments.of(1, 42L, new Vector3D(1.0, 2.0, 3.0), new Vector3D(-1.0, -2.0, -3.0), false),
                Arguments.of(8, 100L, new Vector3D(0.5, -0.2, 1.1), new Vector3D(0.51, -0.19, 1.09), true),
                Arguments.of(16, 100L, new Vector3D(1.0, 0.0, 0.0), new Vector3D(0.0, 1.0, 0.0), false),
                Arguments.of(63, 777L, new Vector3D(3.14, -2.71, 1.41), new Vector3D(3.14, -2.71, 1.41), true)
        );
    }

    @ParameterizedTest(name = "[{index}] invalid k={0}")
    @ValueSource(ints = {0, -1, -100})
    @DisplayName("Табличная проверка некорректного числа гиперплоскостей k <= 0")
    void tableInvalidK(int invalidK) {
        assertThrows(IllegalArgumentException.class, () -> new LSHIndex(invalidK, 1L));
    }

    @ParameterizedTest(name = "[{index}] requested k={0}, seed={1}")
    @CsvSource({
            "1, 1",
            "10, 42",
            "63, 12345",
            "128, 99999"
    })
    @DisplayName("Табличная проверка устойчивости индекса при разных k (включая k > 63)")
    void tableVariousKValues(int k, long seed) {
        LSHIndex index = new LSHIndex(k, seed);
        Vector3D base = new Vector3D(1.0, -1.0, 0.5);
        index.add(base);

        List<Vector3D> hits = index.findDuplicates(base);
        assertFalse(hits.isEmpty());
        assertEquals(base, hits.get(0));
    }

    @Test
    @DisplayName("Запрос несуществующего бакета возвращает пустой список")
    void emptyBucketReturnsEmptyList() {
        LSHIndex index = new LSHIndex(12, 42L);
        index.add(new Vector3D(1.0, 0.0, 0.0));

        List<Vector3D> res = index.findDuplicates(new Vector3D(-1.0, -1.0, -1.0));
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Параллельная вставка и чтение без исключений и потери элементов")
    void concurrentAddAndReadConsistency() throws InterruptedException, ExecutionException {
        final int k = 12;
        final long seed = 2026L;
        final int writers = 4;
        final int readers = 4;
        final int vectorsPerWriter = 2_500;

        LSHIndex index = new LSHIndex(k, seed);
        Vector3D anchor = new Vector3D(1.0, 1.0, 1.0);
        index.add(anchor);

        CountDownLatch startGate = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int w = 0; w < writers; w++) {
            final int writerId = w;
            tasks.add(() -> {
                Random rng = new Random(10_000L + writerId);
                startGate.await();
                int added = 0;
                for (int i = 0; i < vectorsPerWriter; i++) {
                    Vector3D v = new Vector3D(
                            rng.nextGaussian(),
                            rng.nextGaussian(),
                            rng.nextGaussian()
                    );
                    index.add(v);
                    added++;
                }
                return added;
            });
        }

        for (int r = 0; r < readers; r++) {
            final int readerId = r;
            tasks.add(() -> {
                Random rng = new Random(20_000L + readerId);
                startGate.await();
                int observedAnchorCount = 0;
                for (int i = 0; i < vectorsPerWriter; i++) {
                    Vector3D probe = (i % 5 == 0)
                            ? anchor
                            : new Vector3D(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian());
                    List<Vector3D> bucket = index.findDuplicates(probe);
                    assertNotNull(bucket);
                    if (i % 5 == 0 && bucket.contains(anchor)) {
                        observedAnchorCount++;
                    }
                }
                return observedAnchorCount;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(writers + readers);
        try {
            List<Future<Integer>> futures = new ArrayList<>(tasks.size());
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(task));
            }

            startGate.countDown();

            int totalAdded = 0;
            for (int i = 0; i < writers; i++) {
                totalAdded += futures.get(i).get();
            }
            for (int i = writers; i < futures.size(); i++) {
                futures.get(i).get();
            }

            assertEquals(writers * vectorsPerWriter, totalAdded);
            assertTrue(index.findDuplicates(anchor).contains(anchor));
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("Параллельная добавка идентичных векторов сохраняет их в одном бакете")
    void concurrentIdenticalVectorsAllPresent() throws InterruptedException, ExecutionException {
        final int threads = 8;
        final int perThread = 500;
        LSHIndex index = new LSHIndex(16, 555L);
        Vector3D target = new Vector3D(0.25, -0.75, 0.5);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>(threads);
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    for (int i = 0; i < perThread; i++) {
                        index.add(target);
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();

            for (Future<?> f : futures) {
                f.get();
            }

            List<Vector3D> bucket = index.findDuplicates(target);
            assertEquals(threads * perThread, bucket.size());
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
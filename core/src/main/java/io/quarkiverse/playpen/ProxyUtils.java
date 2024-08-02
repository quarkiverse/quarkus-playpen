package io.quarkiverse.playpen;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;

public class ProxyUtils {
    public static <T> T await(long timeout, Future<T> future) {
        return await(timeout, future, "");
    }

    public static void awaitAll(long timeout, Future... futures) {
        CountDownLatch latch = new CountDownLatch(futures.length);
        for (Future future : futures) {
            future.onComplete(event -> latch.countDown());
        }
        try {
            latch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted");
        }
    }

    public static void awaitAll(long timeout, List<Future> futures) {
        awaitAll(timeout, futures.toArray(new Future[futures.size()]));
    }

    public static <T> T await(long timeout, Future<T> future, String error) {
        CountDownLatch latch = new CountDownLatch(1);
        future.onComplete(event -> latch.countDown());
        try {
            latch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(error);
        }
        if (future.failed()) {
            throw new RuntimeException(error, future.cause());
        }
        return future.result();
    }
}

package io.quarkiverse.playpen.client;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.runtime.Shutdown;

@ApplicationScoped
public class OnShutdown {
    Object lock = new Object();
    List<Runnable> tasks = new ArrayList<>();

    public void await() {
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {

            }
        }
    }

    public void await(Runnable runnable) {
        tasks.add(runnable);
        await();
    }

    @Shutdown
    public void shutdown() {
        for (Runnable runnable : tasks) {
            try {
                runnable.run();
            } catch (Exception e) {

            }
        }
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}

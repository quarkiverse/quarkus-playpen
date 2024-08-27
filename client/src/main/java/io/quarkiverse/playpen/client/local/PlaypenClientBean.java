package io.quarkiverse.playpen.client.local;

import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.playpen.client.PlaypenClient;
import io.quarkiverse.playpen.client.PlaypenConnectionConfig;
import io.quarkus.runtime.Shutdown;
import io.vertx.core.Vertx;

@ApplicationScoped
public class PlaypenClientBean {
    PlaypenClient client;

    @Inject
    Vertx vertx;

    CountDownLatch running = new CountDownLatch(1);

    public boolean start(int localPort, PlaypenConnectionConfig config) throws Exception {
        client = PlaypenClient.create(vertx)
                .playpen(config)
                .service("localhost", localPort, false)
                .credentials(config.credentials)
                .build();
        if (!client.start()) {
            return false;
        }
        running = new CountDownLatch(1);
        running.await();
        return true;
    }

    @Shutdown
    public void stop() {
        running.countDown();
        if (client != null) {
            client.shutdown();
        }
        client = null;
    }
}

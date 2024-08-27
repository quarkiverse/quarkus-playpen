package io.quarkiverse.playpen.client.local;

import java.util.concurrent.CountDownLatch;

import io.quarkiverse.playpen.client.OnShutdown;
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

    @Inject
    OnShutdown shutdown;


    public boolean start(int localPort, PlaypenConnectionConfig config) throws Exception {
        client = PlaypenClient.create(vertx)
                .playpen(config)
                .service("localhost", localPort, false)
                .credentials(config.credentials)
                .build();
        if (!client.start()) {
            return false;
        }
        shutdown.await();
        return true;
    }

    @Shutdown
    public void stop() {
        try {
            if (client != null) {
                client.shutdown();
            }
        } finally {
            client = null;
        }
    }
}

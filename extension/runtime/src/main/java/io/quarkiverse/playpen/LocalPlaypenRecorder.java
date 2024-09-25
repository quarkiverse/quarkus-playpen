package io.quarkiverse.playpen;

import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.client.LocalPlaypenConnectionConfig;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

@Recorder
public class LocalPlaypenRecorder {
    private static final Logger log = Logger.getLogger(LocalPlaypenRecorder.class);

    static VirtualLocalPlaypenClient client;
    public static LocalPlaypenConnectionConfig config;
    static Vertx vertx;

    public void init(LaunchMode launchMode, Supplier<Vertx> vertx, ShutdownContext shutdown, LocalPlaypenConnectionConfig c,
            boolean delayConnect) {
        closeSession();
        config = c;
        LocalPlaypenRecorder.vertx = vertx.get();
        // LocalPlaypenHotReplacementSetup.close will be responsible
        // for closing session in development mode
        if (launchMode != LaunchMode.DEVELOPMENT) {
            shutdown.addShutdownTask(LocalPlaypenRecorder::closeSession);
        }
        if (!delayConnect) {
            startSession(LocalPlaypenRecorder.vertx, c);
        }
    }

    public static void startSession() {
        startSession(vertx, config);
    }

    public static void startSession(Vertx vertx, LocalPlaypenConnectionConfig config) {
        log.info("Starting playpen session");
        client = new VirtualLocalPlaypenClient();
        HttpClientOptions options = new HttpClientOptions();
        options.setDefaultHost(config.host);
        options.setDefaultPort(config.port);
        if (config.ssl) {
            options.setSsl(true);
            if (config.trustCert)
                options.setTrustAll(true);
        }
        client.setCredentials(config.credentials);
        client.setProxyClient(vertx.createHttpClient(options));
        client.vertx = vertx;
        client.initUri(config);
        client.start();
    }

    public static void closeSession() {
        if (client != null) {
            log.info("Closing playpen session");
            client.shutdown();
        }
        client = null;
    }
}

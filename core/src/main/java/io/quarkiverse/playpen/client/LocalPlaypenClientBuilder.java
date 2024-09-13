package io.quarkiverse.playpen.client;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

public class LocalPlaypenClientBuilder {
    private final LocalPlaypenClient playpenClient;
    private final Vertx vertx;
    private LocalPlaypenConnectionConfig config;

    LocalPlaypenClientBuilder(Vertx vertx) {
        this.playpenClient = new LocalPlaypenClient();
        this.vertx = vertx;
    }

    public LocalPlaypenClientBuilder playpen(String cli) {
        this.config = LocalPlaypenConnectionConfig.fromCli(cli);
        return this;
    }

    public LocalPlaypenClientBuilder playpen(LocalPlaypenConnectionConfig config) {
        this.config = config;
        return this;
    }

    public LocalPlaypenClientBuilder numPollers(int num) {
        playpenClient.numPollers = num;
        return this;
    }

    public LocalPlaypenClientBuilder service(String host, int port, boolean ssl) {
        HttpClientOptions options = new HttpClientOptions();
        if (ssl) {
            options.setSsl(true).setTrustAll(true);
        }
        return service(host, port, options);
    }

    public LocalPlaypenClientBuilder service(String host, int port, HttpClientOptions options) {
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        playpenClient.serviceClient = vertx.createHttpClient(options);
        return this;
    }

    public LocalPlaypenClientBuilder pollTimeoutMillis(long timeout) {
        playpenClient.setPollTimeoutMillis(timeout);
        return this;
    }

    public LocalPlaypenClientBuilder basicAuth(String user, String password) {
        playpenClient.setBasicAuth(user, password);
        return this;
    }

    public LocalPlaypenClientBuilder secretAuth(String secret) {
        playpenClient.setSecretAuth(secret);
        return this;
    }

    public LocalPlaypenClientBuilder credentials(String creds) {
        playpenClient.setCredentials(creds);
        return this;
    }

    public LocalPlaypenClient build() {
        HttpClientOptions options = new HttpClientOptions();
        options.setDefaultHost(config.host);
        options.setDefaultPort(config.port);
        if (config.ssl) {
            options.setSsl(true);
            if (config.trustCert)
                options.setTrustAll(true);
        }
        playpenClient.proxyClient = vertx.createHttpClient(options);
        playpenClient.initUri(config);
        return playpenClient;
    }
}

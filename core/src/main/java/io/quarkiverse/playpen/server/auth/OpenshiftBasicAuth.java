package io.quarkiverse.playpen.server.auth;

import java.net.URI;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

public class OpenshiftBasicAuth implements PlaypenAuth {
    final HttpClient client;

    public OpenshiftBasicAuth(Vertx vertx, String oauthUrl) {
        URI uri = URI.create(oauthUrl);
        HttpClientOptions options = new HttpClientOptions();
        boolean https = false;
        if (uri.getScheme().equals("https")) {
            https = true;
            options.setSsl(true).setTrustAll(true);
        }
        options.setDefaultHost(uri.getHost());
        int port = uri.getPort() == -1 ? (https ? 443 : 80) : uri.getPort();
        options.setDefaultPort(port);
        this.client = vertx.createHttpClient(options);
    }

    @Override
    public void authenticate(RoutingContext ctx, Runnable success) {
        String authorizationHeader = ctx.request().getHeader(AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BASIC)) {
            ctx.response().setStatusCode(401).putHeader(WWW_AUTHENTICATE, BASIC).end();
            return;
        }
        client.request(HttpMethod.GET, "/oauth/authorize?response_type=token&client_id=openshift-challenging-client", event -> {
            if (event.failed()) {
                ctx.response().setStatusCode(500).end();
                return;
            }
            HttpClientRequest request = event.result();
            request.putHeader(AUTHORIZATION, authorizationHeader)
                    .send().onComplete(result -> {
                        if (result.succeeded() && result.result().statusCode() == 302) {
                            success.run();
                        } else {
                            ctx.response().setStatusCode(500).end();
                        }
                    });
        });
    }
}

package io.quarkiverse.playpen.server.auth;

import io.vertx.ext.web.RoutingContext;

public class SecretAuth implements PlaypenAuth {
    private final String secret;

    public SecretAuth(String secret) {
        this.secret = secret;
    }

    @Override
    public void authenticate(RoutingContext ctx, Runnable success) {
        String authorizationHeader = ctx.request().getHeader(AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(SECRET)) {
            challenge(ctx);
            return;
        }
        int idx = authorizationHeader.indexOf("Secret");
        if (idx == -1) {
            ctx.response().setStatusCode(401).putHeader(WWW_AUTHENTICATE, SECRET).end();
            return;
        }
        String token = authorizationHeader.substring(idx + SECRET.length()).trim();
        if (!this.secret.equals(token)) {
            ctx.response().setStatusCode(401).putHeader(WWW_AUTHENTICATE, SECRET).end();
            return;
        }
        success.run();
    }

    @Override
    public void challenge(RoutingContext ctx) {
        ctx.response().setStatusCode(401).putHeader(WWW_AUTHENTICATE, "Secret").end();
    }
}

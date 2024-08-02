package io.quarkiverse.playpen.server.auth;

import io.quarkiverse.playpen.server.PlaypenServer;
import io.vertx.ext.web.RoutingContext;

public class NoAuth implements ProxySessionAuth {
    @Override
    public void authenticate(RoutingContext ctx, Runnable success) {
        success.run();
    }

    @Override
    public boolean authorized(RoutingContext ctx, PlaypenServer.ProxySession session) {
        return true;
    }

    @Override
    public void propagateToken(RoutingContext ctx, PlaypenServer.ProxySession session) {

    }
}

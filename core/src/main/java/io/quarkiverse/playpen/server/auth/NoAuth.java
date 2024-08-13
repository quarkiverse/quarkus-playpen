package io.quarkiverse.playpen.server.auth;

import io.quarkiverse.playpen.server.LocalDevPlaypenServer;
import io.vertx.ext.web.RoutingContext;

public class NoAuth implements PlaypenAuth {
    @Override
    public void authenticate(RoutingContext ctx, Runnable success) {
        success.run();
    }

    @Override
    public boolean authorized(RoutingContext ctx, LocalDevPlaypenServer.LocalDevPlaypen session) {
        return true;
    }

    @Override
    public void propagateToken(RoutingContext ctx, LocalDevPlaypenServer.LocalDevPlaypen session) {

    }
}

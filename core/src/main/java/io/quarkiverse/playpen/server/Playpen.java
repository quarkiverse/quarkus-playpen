package io.quarkiverse.playpen.server;

import io.vertx.ext.web.RoutingContext;

public interface Playpen {
    boolean isMatch(RoutingContext ctx);

    void route(RoutingContext ctx);

    boolean isRunning();

    String whoami();

    void close();
}

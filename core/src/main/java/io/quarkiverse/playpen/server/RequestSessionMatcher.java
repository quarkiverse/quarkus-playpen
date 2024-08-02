package io.quarkiverse.playpen.server;

import io.vertx.ext.web.RoutingContext;

/**
 *
 */
public interface RequestSessionMatcher {
    boolean matches(RoutingContext ctx);
}

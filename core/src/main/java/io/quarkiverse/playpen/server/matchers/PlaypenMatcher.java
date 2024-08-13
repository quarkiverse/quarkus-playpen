package io.quarkiverse.playpen.server.matchers;

import io.vertx.ext.web.RoutingContext;

/**
 *
 */
public interface PlaypenMatcher {
    boolean matches(RoutingContext ctx);
}

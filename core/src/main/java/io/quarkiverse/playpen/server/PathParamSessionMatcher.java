package io.quarkiverse.playpen.server;

import io.vertx.ext.web.RoutingContext;

public class PathParamSessionMatcher implements RequestSessionMatcher {
    private final String pattern;

    public PathParamSessionMatcher(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(RoutingContext ctx) {
        return ctx.normalizedPath().startsWith(pattern);
    }
}

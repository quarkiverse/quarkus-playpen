package io.quarkiverse.playpen.server.matchers;

import io.vertx.ext.web.RoutingContext;

public class PathParamMatcher implements PlaypenMatcher {
    private final String pattern;

    public PathParamMatcher(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(RoutingContext ctx) {
        return ctx.normalizedPath().startsWith(pattern);
    }
}

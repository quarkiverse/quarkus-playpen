package io.quarkiverse.playpen.server;

import io.vertx.ext.web.RoutingContext;

public class QueryParamSessionMatcher implements RequestSessionMatcher {
    private final String name;
    private final String value;

    public QueryParamSessionMatcher(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean matches(RoutingContext ctx) {
        return value.equals(ctx.queryParams().get(name));
    }
}

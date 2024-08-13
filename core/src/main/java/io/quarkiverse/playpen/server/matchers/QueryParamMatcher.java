package io.quarkiverse.playpen.server.matchers;

import io.vertx.ext.web.RoutingContext;

public class QueryParamMatcher implements PlaypenMatcher {
    private final String name;
    private final String value;

    public QueryParamMatcher(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean matches(RoutingContext ctx) {
        return value.equals(ctx.queryParams().get(name));
    }
}

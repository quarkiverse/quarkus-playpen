package io.quarkiverse.playpen.server;

import io.vertx.ext.web.RoutingContext;

public class ClientIpMatcher implements RequestSessionMatcher {

    final String address;

    public ClientIpMatcher(String address) {
        this.address = address;
    }

    @Override
    public boolean matches(RoutingContext ctx) {
        return address.equals(ctx.request().remoteAddress().hostAddress());
    }
}

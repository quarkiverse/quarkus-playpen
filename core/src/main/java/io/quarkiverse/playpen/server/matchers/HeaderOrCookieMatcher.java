package io.quarkiverse.playpen.server.matchers;

import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;

public class HeaderOrCookieMatcher implements PlaypenMatcher {
    private final String name;
    private final String value;

    public HeaderOrCookieMatcher(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean matches(RoutingContext ctx) {
        String sessionId = ctx.request().getHeader(name);
        if (sessionId == null) {
            Cookie cookie = ctx.request().getCookie(name);
            if (cookie != null) {
                sessionId = cookie.getValue();
            }
        }
        return value.equals(sessionId);
    }
}

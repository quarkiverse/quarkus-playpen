package io.quarkiverse.playpen.server.auth;

import io.quarkiverse.playpen.server.PlaypenServer;
import io.vertx.ext.web.RoutingContext;

public interface ProxySessionAuth {
    String BEARER_TOKEN_HEADER = "X-Bearer-Token";
    String WWW_AUTHENTICATE = "WWW-Authenticate";
    String AUTHORIZATION = "Authorization";
    String BASIC = "Basic";
    String SECRET = "Secret";

    // built in auth types
    String OPENSHIFT_BASIC_AUTH = "openshiftBasicAuth";
    String SECRET_AUTH = "secret";
    String NO_AUTH = "noAuth";

    /**
     * @param ctx
     * @param success code to execute on successful connect
     */
    void authenticate(RoutingContext ctx, Runnable success);

    /**
     * If false, then response is already set
     *
     * @param ctx
     * @param session
     * @return
     */
    default boolean authorized(RoutingContext ctx, PlaypenServer.ProxySession session) {
        return session.validateToken(ctx);
    }

    default void propagateToken(RoutingContext ctx, PlaypenServer.ProxySession session) {
        ctx.response().putHeader(BEARER_TOKEN_HEADER, "Bearer " + session.getToken());

    }
}

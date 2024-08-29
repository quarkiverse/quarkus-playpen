package io.quarkiverse.playpen;

import io.quarkiverse.playpen.server.PlaypenProxyConstants;
import io.quarkus.arc.Arc;
import io.vertx.core.http.HttpServerRequest;

public class Playpen {
    //protected static final Logger log = Logger.getLogger(Playpen.class);
    /**
     * Is server is involved within a playpen session request chain?
     * If so, return it. Basically looks for the playpen session header.
     *
     * FYI: global sessions don't need to propagate a session header.
     *
     * @return session name, null if no session exists
     */
    public static String current() {
        HttpServerRequest request = request();
        if (request == null) {
            //log.info("****** request was null");
            return null;
        }
        return request.getHeader(PlaypenProxyConstants.SESSION_HEADER);
    }

    private static HttpServerRequest request() {
        return Arc.container().instance(HttpServerRequest.class).get();
    }
}

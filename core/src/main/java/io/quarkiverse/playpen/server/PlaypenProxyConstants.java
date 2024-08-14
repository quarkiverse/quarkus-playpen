package io.quarkiverse.playpen.server;

public interface PlaypenProxyConstants {
    String API_PATH = "/_playpen/api";
    String LOCAL_API_PATH = "/local";
    String SESSION_HEADER = "X-Playpen-Session";
    String HEADER_FORWARD_PREFIX = "X-Playpen-Fwd-";
    String STATUS_CODE_HEADER = "X-Playpen-Status-Code";
    String METHOD_HEADER = "X-Playpen-Method";
    String URI_HEADER = "X-Playpen-Uri";
    String REQUEST_ID_HEADER = "X-Playpen-Request-Id";
    String RESPONSE_LINK = "X-Playpen-Response-Path";
    String POLL_LINK = "X-Playpen-Poll-Path";
    String POLL_TIMEOUT = "X-Playpen-Poll-Timeout";
    String APPLICATION_QUARKUS = "application/quarkus-live-reload";
    String REMOTE_API_PATH = "/remote";
    String DEPLOYMENT_PATH = "/deployment";
    String DEPLOYMENT_ZIP_PATH = "/deployment/zip";
    String CONNECT_PATH = "/connect";
}

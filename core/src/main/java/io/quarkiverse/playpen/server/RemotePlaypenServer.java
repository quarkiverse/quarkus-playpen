package io.quarkiverse.playpen.server;

import static io.quarkiverse.playpen.server.PlaypenServer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.quarkiverse.playpen.server.auth.NoAuth;
import io.quarkiverse.playpen.server.auth.ProxySessionAuth;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class RemotePlaypenServer {
    protected static final Logger log = Logger.getLogger(RemotePlaypenServer.class);
    public static final String APPLICATION_QUARKUS = "application/quarkus-live-reload";
    public static final String REMOTE_API_PATH = "/remote";

    class RemotePlaypenSession implements ProxyInterceptor {
        HttpClient client;
        HttpProxy sessionProxy;
        final String who;
        final String liveReloadPrefix;
        volatile boolean running = true;
        List<RequestSessionMatcher> matchers = new ArrayList<>();
        String host;
        int port;

        public RemotePlaypenSession(String host, int port, String who) {
            this.host = host;
            if (port == -1)
                port = 80;
            this.port = port;
            this.who = who;
            this.liveReloadPrefix = clientPathPrefix + "/remote/" + who;
            client = vertx.createHttpClient();
            sessionProxy = HttpProxy.reverseProxy(client);
            sessionProxy.addInterceptor(this);
            sessionProxy.origin(this.port, this.host);
            log.debugv("RemoteSession for {0}:{1}", host, port);
        }

        public RemotePlaypenSession(String who) {
            this(serviceName + "-playpen-" + who, 80, who);
        }

        public boolean isMatch(RoutingContext ctx) {
            for (RequestSessionMatcher matcher : matchers) {
                if (matcher.matches(ctx))
                    return true;
            }
            return false;
        }

        public void forward(RoutingContext ctx) {
            if (APPLICATION_QUARKUS.equals(ctx.request().getHeader(HttpHeaderNames.CONTENT_TYPE))) {
                log.error("Trying to send liveCode request through actual service is not allowed");
                ctx.response().setStatusCode(403).end();
            }
            sessionProxy.handle(ctx.request());
        }

        public void liveCoding(RoutingContext ctx) {
            if (!APPLICATION_QUARKUS.equals(ctx.request().getHeader(HttpHeaderNames.CONTENT_TYPE))) {
                log.error("Only live code requests are allowed");
                ctx.response().setStatusCode(403).end();
                return;
            }
            sessionProxy.handle(ctx.request());
        }

        /**
         * need to pull off path as RemoteSyncHandler expects basepaths
         */
        @Override
        public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {

            if (APPLICATION_QUARKUS.equals(context.request().headers().get(HttpHeaderNames.CONTENT_TYPE))) {
                String uri = context.request().getURI();
                String tmp = uri.replace(liveReloadPrefix, "");
                log.debugv("livecode change {0} to {1}", uri, tmp);
                context.request().setURI(tmp);
            }
            return context.sendRequest();
        }

        public void shutdown(boolean deleteUserPlaypen) {
            running = false;
            client.close();
        }
    }

    protected String serviceName;
    protected RemotePlaypenManager manager;
    protected ProxySessionAuth auth = new NoAuth();
    protected HttpClient proxyClient;
    protected HttpProxy proxy;
    protected Vertx vertx;
    protected String clientPathPrefix = "";
    protected String clientApiPath;
    protected Map<String, RemotePlaypenSession> sessions = new ConcurrentHashMap<>();
    protected volatile RemotePlaypenSession globalSession;
    protected String version = "unknown";

    public void setAuth(ProxySessionAuth auth) {
        this.auth = auth;
    }

    public void setClientPathPrefix(String clientPathPrefix) {
        this.clientPathPrefix = clientPathPrefix;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    protected ServiceConfig serviceConfig;

    public void init(Vertx vertx, Router proxyRouter, Router clientApiRouter, ServiceConfig config) {
        this.vertx = vertx;
        this.serviceConfig = config;
        proxyRouter.route().handler((context) -> {
            if (context.get("continue-sent") == null) {
                String expect = context.request().getHeader(HttpHeaderNames.EXPECT);
                if (expect != null && expect.equalsIgnoreCase("100-continue")) {
                    context.put("continue-sent", true);
                    context.response().writeContinue();
                }
            }
            context.next();
        });
        clientApiPath = clientPathPrefix + REMOTE_API_PATH + "/:who/_playpen_api";
        // CLIENT API
        clientApiRouter.route(clientApiPath + "/connect").method(HttpMethod.POST).handler(this::clientConnect);
        clientApiRouter.route(clientApiPath + "/connect").method(HttpMethod.DELETE).handler(this::deleteClientConnection);
        clientApiRouter.route(clientPathPrefix + REMOTE_API_PATH + "/:who" + "/*").handler(this::liveCode);

        // API routes
        proxyRouter.route(API_PATH + "/version").method(HttpMethod.GET)
                .handler((ctx) -> ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end(version));
        proxyRouter.route(API_PATH + "/clientIp").method(HttpMethod.GET)
                .handler((ctx) -> ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain")
                        .end("" + ctx.request().remoteAddress().hostAddress()));
        proxyRouter.route(API_PATH + "/cookie/set").method(HttpMethod.GET).handler(this::setCookieApi);
        proxyRouter.route(API_PATH + "/cookie/get").method(HttpMethod.GET).handler(this::getCookieApi);
        proxyRouter.route(API_PATH + "/cookie/remove").method(HttpMethod.GET).handler(this::removeCookieApi);
        proxyRouter.route(API_PATH + "/*").handler(routingContext -> routingContext.fail(404));
        proxyRouter.route().handler(this::proxy);

        // proxy to deployed services
        HttpClientOptions options = new HttpClientOptions();
        if (config.isSsl()) {
            options.setSsl(true).setTrustAll(true);
        }
        this.proxyClient = vertx.createHttpClient(options);
        this.proxy = HttpProxy.reverseProxy(proxyClient);
        proxy.origin(config.getPort(), config.getHost());
    }

    public void liveCode(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        RemotePlaypenSession session = sessions.get(who);
        if (session == null && isGlobal(who)) {
            session = globalSession;
        }
        if (session == null) {
            log.debugv("livecode {0} session not found", who);
            ctx.response().setStatusCode(404).end();
        } else {
            if (!who.equals(session.who)) {
                log.debugv("livecode {0} who does not match", who);
                ctx.response().setStatusCode(403).end();
                return;
            }
            session.liveCoding(ctx);
        }
    }

    public void proxy(RoutingContext ctx) {
        log.debugv("entered proxy {0} {1}", ctx.request().method().toString(), ctx.request().uri());

        RemotePlaypenSession found = null;
        for (RemotePlaypenSession session : sessions.values()) {
            if (session.isMatch(ctx)) {
                found = session;
                break;
            }
        }
        if (found == null) {
            found = globalSession;
        }

        if (found != null && found.running) {
            log.debug("Found session");
            found.forward(ctx);
        } else {
            proxy.handle(ctx.request());
        }
    }

    public void clientConnect(RoutingContext ctx) {
        // TODO: add security 401 protocol

        log.debug("Connect: " + ctx.request().absoluteURI());
        String who = ctx.pathParam("who");
        log.debugv("Establish connection for {0}", who);
        List<RequestSessionMatcher> matchers = new ArrayList<>();
        boolean isGlobal = false;
        String host = null;
        int port = -1;
        for (Map.Entry<String, String> entry : ctx.queryParams()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("query".equals(key)) {
                String query = value;
                String qvalue = who;
                int idx = value.indexOf('=');
                if (idx > 0) {
                    query = value.substring(0, idx);
                    qvalue = value.substring(idx + 1);
                }
                matchers.add(new QueryParamSessionMatcher(query, qvalue));
            } else if ("path".equals(key)) {
                matchers.add(new PathParamSessionMatcher(value));
            } else if ("header".equals(key)) {
                String header = value;
                int idx = value.indexOf('=');
                String hvalue = who;
                if (idx > 0) {
                    header = value.substring(0, idx);
                    hvalue = value.substring(idx + 1);
                }
                matchers.add(new HeaderOrCookieSessionMatcher(header, hvalue));
            } else if ("clientIp".equals(key)) {
                String ip = value;
                if (ip == null) {
                    ip = ctx.request().remoteAddress().hostAddress();
                }
            } else if ("global".equals(key) && "true".equals(value)) {
                isGlobal = true;
            } else if ("host".equals(key)) {
                host = value;
                int idx = host.indexOf(':');
                if (idx > -1) {
                    host = value.substring(0, idx);
                    String p = value.substring(idx + 1);
                    port = Integer.parseInt(p);
                }
            }
        }
        log.debugv("Is global session: {0}", isGlobal);
        log.debugv("Auth type: {0}", auth.getClass().getSimpleName());
        synchronized (this) {
            RemotePlaypenSession session = null;
            if (isGlobal) {
                session = globalSession;
            } else {
                session = sessions.get(who);
            }
            if (session != null) {
                if (isGlobal && !who.equals(session.who)) {
                    log.errorv("Failed Client Connect for global session: Existing connection {0}", who);
                    ctx.response().setStatusCode(409).putHeader("Content-Type", "text/plain").end(session.who);
                    return;
                }
                auth.authenticate(ctx, () -> {
                    ctx.response().setStatusCode(204).end();
                });
            } else {
                boolean finalIsGlobal = isGlobal;
                String finalHost = host;
                int finalPort = port;
                auth.authenticate(ctx, () -> {
                    RemotePlaypenSession newSession;
                    if (finalHost == null) {
                        newSession = new RemotePlaypenSession(who);
                    } else {
                        newSession = new RemotePlaypenSession(finalHost, finalPort, who);
                    }
                    newSession.matchers = matchers;
                    if (finalIsGlobal) {
                        globalSession = newSession;
                    } else {
                        newSession.matchers.add(new HeaderOrCookieSessionMatcher(SESSION_HEADER, who));
                        sessions.put(who, newSession);
                    }
                    ctx.response().setStatusCode(204).end();
                });
            }
        }
    }

    public void deleteClientConnection(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        log.debugv("Attempt Shutdown session {0}", who);
        List<String> removePen = ctx.queryParam("removePen");
        boolean remove = !removePen.isEmpty() && removePen.get(0).equals("true");
        RemotePlaypenSession session = sessions.remove(who);
        if (session == null && isGlobal(who)) {
            log.debug("Deleting global session");
            session = globalSession;
            globalSession = null;
        }
        if (session != null) {
            // TODO do we need to authenticate again?  Who cares if somebody deletes session?
            log.debugv("Shutdown session {0}", who);
            session.shutdown(remove);
            ctx.response().setStatusCode(204).end();
        } else {
            ctx.response().setStatusCode(404).end();
        }
    }

    private boolean isGlobal(String who) {
        return globalSession != null && globalSession.who.equals(who);
    }

    public void setCookieApi(RoutingContext ctx) {
        String session = ctx.queryParams().get("session");
        if (session == null) {
            ctx.response().setStatusCode(400).putHeader("Content-Type", "text/plain")
                    .end("You must specify a session query param in url to set session");
        } else {
            ctx.response()
                    .setStatusCode(200)
                    .addCookie(Cookie.cookie(SESSION_HEADER, session).setPath("/"))
                    .putHeader("Content-Type", "text/plain")
                    .end("Session cookie set for session: " + session);
        }
    }

    public void removeCookieApi(RoutingContext ctx) {
        ctx.response()
                .setStatusCode(200)
                .addCookie(Cookie.cookie(SESSION_HEADER, "null").setPath("/").setMaxAge(0))
                .putHeader("Content-Type", "text/plain")
                .end("Session cookie removed");

    }

    public void getCookieApi(RoutingContext ctx) {
        String sessionId = null;
        Cookie cookie = ctx.request().getCookie(SESSION_HEADER);
        if (cookie != null) {
            sessionId = cookie.getValue();
        } else {
            sessionId = "NONE";
        }
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "text/plain")
                .end("Session cookie: " + sessionId);
    }
}

package io.quarkiverse.playpen.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.quarkiverse.playpen.ProxyUtils;
import io.quarkiverse.playpen.server.auth.NoAuth;
import io.quarkiverse.playpen.server.auth.ProxySessionAuth;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.Pipe;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.HttpProxy;

public class PlaypenServer {

    public static AutoCloseable create(Vertx vertx, ServiceConfig config, int proxyPort, int clientApiPort) {
        HttpServer proxy = vertx.createHttpServer();
        HttpServer clientApi = vertx.createHttpServer();
        PlaypenServer proxyServer = new PlaypenServer();
        Router proxyRouter = Router.router(vertx);
        Router clientApiRouter = Router.router(vertx);
        proxyServer.init(vertx, proxyRouter, clientApiRouter, config);
        ProxyUtils.await(1000, proxy.requestHandler(proxyRouter).listen(proxyPort));
        ProxyUtils.await(1000, clientApi.requestHandler(clientApiRouter).listen(clientApiPort));
        return new AutoCloseable() {

            @Override
            public void close() throws Exception {
                ProxyUtils.await(1000, proxy.close());
                ProxyUtils.await(1000, clientApi.close());
            }
        };
    }

    public class Poller {
        final ProxySession session;
        final RoutingContext pollerCtx;
        final AtomicBoolean closed = new AtomicBoolean();
        final long timestamp = System.currentTimeMillis();
        final long timeout;
        boolean enqueued;

        public Poller(ProxySession session, RoutingContext pollerCtx, long timeout) {
            this.session = session;
            this.pollerCtx = pollerCtx;
            this.timeout = timeout;
        }

        private void enqueuePoller() {
            HttpServerResponse pollResponse = pollerCtx.response();
            pollResponse.closeHandler(v1 -> connectionFailure());
            pollResponse.exceptionHandler(v1 -> connectionFailure());
            pollerCtx.request().connection().closeHandler(v -> connectionFailure());
            pollerCtx.request().connection().exceptionHandler(v -> connectionFailure());
            enqueued = true;
        }

        public boolean isTimedOut() {
            if (closed.get()) {
                return true;
            }
            if (System.currentTimeMillis() - timestamp >= timeout) {
                closed.set(true);
            }
            return closed.get();
        }

        private void connectionFailure() {
            closed.set(true);
            if (!enqueued)
                return;
            synchronized (session.pollLock) {
                session.awaitingPollers.remove(this);
            }
            session.pollDisconnect();
        }

        public void closeSession() {
            if (closed.get())
                return;
            pollerCtx.response().setStatusCode(404).end();
        }

        public void forwardRequestToPollerClient(RoutingContext proxiedCtx) {
            log.debugv("Forward request to poller client {0}", session.who);
            enqueued = false;
            HttpServerRequest proxiedRequest = proxiedCtx.request();
            HttpServerResponse pollResponse = pollerCtx.response();
            session.pollProcessing();
            pollResponse.setStatusCode(200);
            proxiedRequest.headers().forEach((key, val) -> {
                if (key.equalsIgnoreCase("Content-Length")) {
                    return;
                }
                pollResponse.headers().add(HEADER_FORWARD_PREFIX + key, val);
            });
            String requestId = session.queueResponse(proxiedCtx);
            pollResponse.putHeader(REQUEST_ID_HEADER, requestId);
            String responsePath = clientApiPath + "/" + session.who + "/push/" + requestId;
            pollResponse.putHeader(RESPONSE_LINK, responsePath);
            pollResponse.putHeader(METHOD_HEADER, proxiedRequest.method().toString());
            pollResponse.putHeader(URI_HEADER, proxiedRequest.uri());
            sendBody(proxiedRequest, pollResponse);
        }
    }

    public class ProxySession {
        final ConcurrentHashMap<String, RoutingContext> responsePending = new ConcurrentHashMap<>();
        final ServiceProxy proxy;
        final long timerId;
        final String who;
        final String token = UUID.randomUUID().toString();
        final Deque<RoutingContext> awaiting = new LinkedList<>();
        final Deque<Poller> awaitingPollers = new LinkedList<>();
        final Object pollLock = new Object();
        long pollTimeout = defaultPollTimeout;

        List<RequestSessionMatcher> matchers = new ArrayList<>();

        volatile boolean running = true;
        volatile long lastPoll;
        AtomicLong requestId = new AtomicLong(System.currentTimeMillis());

        ProxySession(ServiceProxy proxy, String who) {
            timerId = vertx.setPeriodic(timerPeriod, this::timerCallback);
            this.proxy = proxy;
            this.who = who;
        }

        void timerCallback(Long t) {
            List<Poller> idlePollers = null;
            synchronized (pollLock) {
                Iterator<Poller> it = awaitingPollers.iterator();
                while (it.hasNext()) {
                    Poller poller = it.next();
                    if (poller.isTimedOut()) {
                        if (idlePollers == null)
                            idlePollers = new ArrayList<>();
                        idlePollers.add(poller);
                        it.remove();
                    }
                }
            }
            if (idlePollers != null) {
                for (Poller poller : idlePollers)
                    poller.pollerCtx.response().setStatusCode(408).end();
            }
            checkIdle();
        }

        void checkIdle() {
            if (!running)
                return;
            if (System.currentTimeMillis() - lastPoll > idleTimeout) {
                log.warnv("Shutting down session {0} due to idle timeout.", who);
                shutdown();
            }
        }

        String queueResponse(RoutingContext ctx) {
            String requestId = Long.toString(this.requestId.incrementAndGet());
            responsePending.put(requestId, ctx);
            return requestId;
        }

        public String getToken() {
            return token;
        }

        public boolean validateToken(RoutingContext ctx) {
            String header = ctx.request().getHeader("Authorization");
            if (header == null) {
                log.error("Authorization failed: no Authorization header");
                ctx.response().setStatusCode(401).end();
                return false;
            }
            int idx = header.indexOf("Bearer");
            if (idx == -1) {
                log.error("Authorization failed: bad Authorization header");
                ctx.response().setStatusCode(401).end();
                return false;
            }
            String token = header.substring(idx + "Bearer".length()).trim();
            if (!this.token.equals(token)) {
                log.error("Authorization failed: bad session token");
                ctx.response().setStatusCode(401).end();
                return false;
            }
            return true;
        }

        RoutingContext dequeueResponse(String requestId) {
            return responsePending.remove(requestId);
        }

        void shutdown() {
            if (!running)
                return;
            running = false;
            vertx.cancelTimer(timerId);

            synchronized (pollLock) {
                while (!awaiting.isEmpty()) {
                    RoutingContext proxiedCtx = awaiting.poll();
                    if (proxiedCtx != null) {
                        proxy.proxy.handle(proxiedCtx.request());
                    }
                }
                awaitingPollers.stream().forEach((poller) -> {
                    poller.closeSession();
                });
            }
        }

        void pollStarted() {
            lastPoll = System.currentTimeMillis();
        }

        void pollProcessing() {
            lastPoll = System.currentTimeMillis();
        }

        void pollEnded() {
            lastPoll = System.currentTimeMillis();
        }

        void pollDisconnect() {
            if (!running) {
                return;
            }
            checkIdle();
        }

        public void poll(RoutingContext pollingCtx) {
            this.pollStarted();
            RoutingContext proxiedCtx = null;
            Poller poller = new Poller(this, pollingCtx, pollTimeout);
            synchronized (pollLock) {
                proxiedCtx = awaiting.poll();
                if (proxiedCtx == null) {
                    poller.enqueuePoller();
                    awaitingPollers.add(poller);
                    return;
                }
            }
            poller.forwardRequestToPollerClient(proxiedCtx);
        }

        public void handleProxiedRequest(RoutingContext ctx) {
            log.debug("handleProxiedRequest");
            ctx.request().pause();
            Poller poller = null;
            synchronized (pollLock) {
                poller = awaitingPollers.poll();
                if (poller == null) {
                    log.debug("No pollers, enqueueing");
                    awaiting.add(ctx);
                    return;
                }
                poller.enqueued = false;
            }
            poller.forwardRequestToPollerClient(ctx);
        }
    }

    public class ServiceProxy {
        final HttpClient client;

        public ServiceProxy(ServiceConfig service) {
            this.config = service;
            HttpClientOptions options = new HttpClientOptions();
            if (service.isSsl()) {
                options.setSsl(true).setTrustAll(true);
            }
            this.client = vertx.createHttpClient(options);
            this.proxy = HttpProxy.reverseProxy(client);
            proxy.origin(service.getPort(), service.getHost());
        }

        final ServiceConfig config;
        final HttpProxy proxy;
        final Map<String, ProxySession> sessions = new ConcurrentHashMap<>();
        volatile ProxySession globalSession;

        void shutdown() {
            for (ProxySession session : sessions.values()) {
                session.shutdown();
            }
            ProxySession tmp = globalSession;
            if (tmp != null) {
                globalSession = null;
                tmp.shutdown();
            }
        }
    }

    public static final String API_PATH = "/_playpen/api";
    public static final String CLIENT_API_PATH = "/_playpen/client";
    public static final String LOCAL_API_PATH = "/local";
    public static final String GLOBAL_PROXY_SESSION = "_playpen_global";
    public static final String SESSION_HEADER = "X-Playpen-Session";
    public static final String HEADER_FORWARD_PREFIX = "X-Playpen-Fwd-";
    public static final String STATUS_CODE_HEADER = "X-Playpen-Status-Code";
    public static final String METHOD_HEADER = "X-Playpen-Method";
    public static final String URI_HEADER = "X-Playpen-Uri";
    public static final String REQUEST_ID_HEADER = "X-Playpen-Request-Id";
    public static final String RESPONSE_LINK = "X-Playpen-Response-Path";
    public static final String POLL_LINK = "X-Playpen-Poll-Path";
    public static final String POLL_TIMEOUT = "X-Playpen-Poll-Timeout";

    protected long idleTimeout = 60000;
    protected long defaultPollTimeout = 5000;
    protected long timerPeriod = 1000;
    protected static final Logger log = Logger.getLogger(PlaypenServer.class);
    protected ServiceProxy service;
    protected Vertx vertx;
    protected ProxySessionAuth auth = new NoAuth();
    protected String clientPathPrefix;
    protected String clientApiPath = LOCAL_API_PATH;
    protected String version = "unknown";

    public void setVersion(String version) {
        this.version = version;
    }

    public void setTimerPeriod(long timerPeriod) {
        this.timerPeriod = timerPeriod;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public void setPollTimeout(long pollTimeout) {
        this.defaultPollTimeout = pollTimeout;
    }

    public void setAuth(ProxySessionAuth auth) {
        this.auth = auth;
    }

    public void setClientPathPrefix(String clientPathPrefix) {
        this.clientPathPrefix = clientPathPrefix;
    }

    public void init(Vertx vertx, Router proxyRouter, Router clientApiRouter, ServiceConfig config) {
        this.vertx = vertx;
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
        if (clientPathPrefix != null) {
            clientApiPath = clientPathPrefix + LOCAL_API_PATH;
        }
        // CLIENT API
        clientApiRouter.route(clientApiPath + "/version").method(HttpMethod.GET)
                .handler((ctx) -> ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end(version));
        clientApiRouter.route(clientApiPath + "/:who/poll").method(HttpMethod.POST).handler(this::pollNext);
        clientApiRouter.route(clientApiPath + "/:who/connect").method(HttpMethod.POST).handler(this::clientConnect);
        clientApiRouter.route(clientApiPath + "/:who/connect").method(HttpMethod.DELETE).handler(this::deleteClientConnection);
        clientApiRouter.route(clientApiPath + "/:who/push/:request")
                .method(HttpMethod.POST)
                .handler(this::pushResponse);
        clientApiRouter.route(clientApiPath + "/:who/push/:request")
                .method(HttpMethod.DELETE)
                .handler(this::deletePushResponse);
        clientApiRouter.route(clientApiPath + "/*").handler(routingContext -> routingContext.fail(404));

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
        service = new ServiceProxy(config);
    }

    static void error(RoutingContext ctx, int status, String msg) {
        ctx.response().setStatusCode(status).putHeader("ContentType", "text/plain").end(msg);

    }

    static Boolean isChunked(MultiMap headers) {
        List<String> te = headers.getAll("transfer-encoding");
        if (te != null) {
            boolean chunked = false;
            for (String val : te) {
                if (val.equals("chunked")) {
                    chunked = true;
                } else {
                    return null;
                }
            }
            return chunked;
        } else {
            return false;
        }
    }

    private static void sendBody(HttpServerRequest source, HttpServerResponse destination) {
        long contentLength = -1L;
        String contentLengthHeader = source.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            try {
                contentLength = Long.parseLong(contentLengthHeader);
            } catch (NumberFormatException e) {
                // Ignore ???
            }
        }
        Body body = Body.body(source, contentLength);
        long len = body.length();
        if (len >= 0) {
            destination.putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
        } else {
            Boolean isChunked = PlaypenServer.isChunked(source.headers());
            destination.setChunked(len == -1 && Boolean.TRUE == isChunked);
        }
        Pipe<Buffer> pipe = body.stream().pipe();
        pipe.endOnComplete(true);
        pipe.endOnFailure(false);
        pipe.to(destination, ar -> {
            if (ar.failed()) {
                log.debug("Failed to pipe response on poll");
                destination.reset();
            }
        });
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

    public void proxy(RoutingContext ctx) {
        log.debugv("*** entered proxy {0} {1}", ctx.request().method().toString(), ctx.request().uri());

        ProxySession found = null;
        find: for (ProxySession session : service.sessions.values()) {
            for (RequestSessionMatcher matcher : session.matchers) {
                if (matcher.matches(ctx)) {
                    found = session;
                    break find;
                }
            }
        }
        if (found == null) {
            found = service.globalSession;
            if (found != null) {
                log.debug("forward to global session");
            }
        } else {
            log.debugv("forward to session {0}", found.who);
        }

        if (found != null && found.running) {
            found.handleProxiedRequest(ctx);
        } else {
            service.proxy.handle(ctx.request());
        }
    }

    public void clientConnect(RoutingContext ctx) {
        // TODO: add security 401 protocol
        String who = ctx.pathParam("who");
        boolean isGlobal = false;
        List<RequestSessionMatcher> matchers = new ArrayList<>();
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
                log.debug("********** Adding PathParam " + value);
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
                matchers.add(new ClientIpMatcher(ip));
            } else if ("global".equals(key) && "true".equals(value)) {
                isGlobal = true;
            }
        }
        synchronized (this) {
            ProxySession session = service.sessions.get(who);
            if (session != null) {
                if (auth.authorized(ctx, session)) {
                    ctx.response().setStatusCode(204)
                            .putHeader(POLL_TIMEOUT, Long.toString(session.pollTimeout))
                            .putHeader(POLL_LINK, getPollLink(who))
                            .end();
                }
            } else {
                boolean finalIsGlobal = isGlobal;
                auth.authenticate(ctx, () -> {
                    ProxySession newSession = new ProxySession(service, who);
                    if (finalIsGlobal) {
                        service.globalSession = newSession;
                    } else {
                        matchers.add(new HeaderOrCookieSessionMatcher(SESSION_HEADER, who));
                        newSession.matchers = matchers;
                        service.sessions.put(who, newSession);
                    }

                    auth.propagateToken(ctx, newSession);
                    ctx.response().setStatusCode(204)
                            .putHeader(POLL_TIMEOUT, Long.toString(newSession.pollTimeout))
                            .putHeader(POLL_LINK, getPollLink(who))
                            .end();
                });

            }
        }
    }

    public void deleteClientConnection(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        ProxySession session = getProxySession(who);
        if (session != null) {
            if (!auth.authorized(ctx, session))
                return;
            service.sessions.remove(who);
            log.debugv("Shutdown session {0}", who);
            session.shutdown();
            ctx.response().setStatusCode(204).end();
        } else {
            ctx.response().setStatusCode(404).end();
        }
    }

    private ProxySession getProxySession(String who) {
        ProxySession session = service.sessions.get(who);
        if (session == null && service.globalSession != null && service.globalSession.who.equals(who)) {
            session = service.globalSession;
        }
        return session;
    }

    public void pushResponse(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        String requestId = ctx.pathParam("request");
        String kp = ctx.queryParams().get("keepAlive");
        boolean keepAlive = kp == null ? true : Boolean.parseBoolean(kp);

        ProxySession session = getProxySession(who);
        if (session == null) {
            log.error("Push response could not find service " + service.config.getName() + " session ");
            PlaypenServer.error(ctx, 404, "Session not found for service " + service.config.getName());
            return;
        }
        if (!auth.authorized(ctx, session))
            return;
        RoutingContext proxiedCtx = session.dequeueResponse(requestId);
        if (proxiedCtx == null) {
            log.error("Push response could not request " + requestId + " for service " + service.config.getName() + " session "
                    + who);
            ctx.response().putHeader(POLL_LINK, getPollLink(who));
            PlaypenServer.error(ctx, 404, "Request " + requestId + " not found");
            return;
        }
        HttpServerResponse proxiedResponse = proxiedCtx.response();
        HttpServerRequest pushedResponse = ctx.request();
        String status = pushedResponse.getHeader(STATUS_CODE_HEADER);
        if (status == null) {
            log.error("Failed to get status header");
            PlaypenServer.error(proxiedCtx, 500, "Failed");
            PlaypenServer.error(ctx, 400, "Failed to get status header");
            return;
        }
        proxiedResponse.setStatusCode(Integer.parseInt(status));
        pushedResponse.headers().forEach((key, val) -> {
            int idx = key.indexOf(HEADER_FORWARD_PREFIX);
            if (idx == 0) {
                String headerName = key.substring(HEADER_FORWARD_PREFIX.length());
                proxiedResponse.headers().add(headerName, val);
            }
        });
        sendBody(pushedResponse, proxiedResponse);
        if (keepAlive) {
            log.debugv("Keep alive {0} {1}", service.config.getName(), who);
            session.pollProcessing();
            session.poll(ctx);
        } else {
            log.debugv("End polling {0} {1}", service.config.getName(), who);
            session.pollEnded();
            ctx.response().setStatusCode(204).end();
        }
    }

    private String getPollLink(String who) {
        return clientApiPath + "/" + who + "/poll";
    }

    public void deletePushResponse(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        String requestId = ctx.pathParam("request");

        ProxySession session = getProxySession(who);
        if (session == null) {
            log.error("Delete push response could not find service " + service.config.getName() + " session ");
            PlaypenServer.error(ctx, 404, "Session not found for service " + service.config.getName());
            return;
        }
        if (!auth.authorized(ctx, session))
            return;
        RoutingContext proxiedCtx = session.dequeueResponse(requestId);
        if (proxiedCtx == null) {
            log.error("Delete push response could not find request " + requestId + " for service " + service.config.getName()
                    + " session " + who);
            PlaypenServer.error(ctx, 404, "Request " + requestId + " not found");
            return;
        }
        proxiedCtx.fail(500);
        ctx.response().setStatusCode(204).end();
    }

    public void pollNext(RoutingContext ctx) {
        String who = ctx.pathParam("who");

        ProxySession session = getProxySession(who);
        if (session == null) {
            log.error("Poll next could not find service " + service.config.getName() + " session " + who);
            PlaypenServer.error(ctx, 404, "Session not found for service " + service.config.getName());
            return;
        }
        if (!auth.authorized(ctx, session))
            return;
        log.debugv("pollNext {0} {1}", service.config.getName(), who);
        session.poll(ctx);
    }
}

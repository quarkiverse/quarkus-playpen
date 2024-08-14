package io.quarkiverse.playpen.server;

import static io.quarkiverse.playpen.server.PlaypenProxyConstants.CONNECT_PATH;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.server.matchers.ClientIpMatcher;
import io.quarkiverse.playpen.server.matchers.HeaderOrCookieMatcher;
import io.quarkiverse.playpen.server.matchers.PathParamMatcher;
import io.quarkiverse.playpen.server.matchers.PlaypenMatcher;
import io.quarkiverse.playpen.server.matchers.QueryParamMatcher;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.Pipe;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.Body;

public class LocalDevPlaypenServer {

    public class Poller {
        final LocalDevPlaypen session;
        final RoutingContext pollerCtx;
        final AtomicBoolean closed = new AtomicBoolean();
        final long timestamp = System.currentTimeMillis();
        final long timeout;
        boolean enqueued;

        public Poller(LocalDevPlaypen session, RoutingContext pollerCtx, long timeout) {
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
                pollResponse.headers().add(PlaypenProxyConstants.HEADER_FORWARD_PREFIX + key, val);
            });
            String requestId = session.queueResponse(proxiedCtx);
            pollResponse.putHeader(PlaypenProxyConstants.REQUEST_ID_HEADER, requestId);
            String responsePath = clientApiPath + "/" + session.who + "/push/" + requestId;
            pollResponse.putHeader(PlaypenProxyConstants.RESPONSE_LINK, responsePath);
            pollResponse.putHeader(PlaypenProxyConstants.METHOD_HEADER, proxiedRequest.method().toString());
            pollResponse.putHeader(PlaypenProxyConstants.URI_HEADER, proxiedRequest.uri());
            sendBody(proxiedRequest, pollResponse);
        }
    }

    public class LocalDevPlaypen implements Playpen {
        final ConcurrentHashMap<String, RoutingContext> responsePending = new ConcurrentHashMap<>();
        final long timerId;
        final String who;
        final String token = UUID.randomUUID().toString();
        final Deque<RoutingContext> awaiting = new LinkedList<>();
        final Deque<Poller> awaitingPollers = new LinkedList<>();
        final Object pollLock = new Object();
        long pollTimeout = config.defaultPollTimeout;

        List<PlaypenMatcher> matchers = new ArrayList<>();

        volatile boolean running = true;
        volatile long lastPoll;
        AtomicLong requestId = new AtomicLong(System.currentTimeMillis());

        LocalDevPlaypen(String who) {
            timerId = vertx.setPeriodic(config.timerPeriod, this::timerCallback);
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
            if (System.currentTimeMillis() - lastPoll > config.idleTimeout) {
                log.warnv("Shutting down session {0} due to idle timeout.", who);
                close();
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

        @Override
        public void close() {
            if (!running)
                return;
            running = false;
            vertx.cancelTimer(timerId);

            synchronized (pollLock) {
                while (!awaiting.isEmpty()) {
                    RoutingContext proxiedCtx = awaiting.poll();
                    if (proxiedCtx != null) {
                        master.proxy.handle(proxiedCtx.request());
                    }
                }
                awaitingPollers.stream().forEach((poller) -> {
                    poller.closeSession();
                });
            }
        }

        @Override
        public boolean isMatch(RoutingContext ctx) {
            for (PlaypenMatcher matcher : matchers) {
                if (matcher.matches(ctx))
                    return true;
            }
            return false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public String whoami() {
            return who;
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

        @Override
        public void route(RoutingContext ctx) {
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

    protected static final Logger log = Logger.getLogger(LocalDevPlaypenServer.class);
    PlaypenProxy master;
    PlaypenProxyConfig config;
    Vertx vertx;
    String clientApiPath = PlaypenProxyConstants.LOCAL_API_PATH;

    public void init(PlaypenProxy master, Router clientApiRouter) {
        this.master = master;
        this.config = master.config;
        this.vertx = master.vertx;

        if (config.clientPathPrefix != null) {
            clientApiPath = config.clientPathPrefix + PlaypenProxyConstants.LOCAL_API_PATH;
        }
        clientApiRouter.route(clientApiPath + "/:who/poll").method(HttpMethod.POST).handler(this::pollNext);
        clientApiRouter.route(clientApiPath + "/:who" + CONNECT_PATH).method(HttpMethod.POST).handler(this::clientConnect);
        clientApiRouter.route(clientApiPath + "/:who" + CONNECT_PATH).method(HttpMethod.DELETE)
                .handler(this::deleteClientConnection);
        clientApiRouter.route(clientApiPath + "/:who/push/:request")
                .method(HttpMethod.POST)
                .handler(this::pushResponse);
        clientApiRouter.route(clientApiPath + "/:who/push/:request")
                .method(HttpMethod.DELETE)
                .handler(this::deletePushResponse);
        clientApiRouter.route(clientApiPath + "/*").handler(routingContext -> routingContext.fail(404));
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
            Boolean isChunked = isChunked(source.headers());
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

    public void clientConnect(RoutingContext ctx) {
        // TODO: add security 401 protocol
        String who = ctx.pathParam("who");
        boolean isGlobal = false;
        List<PlaypenMatcher> matchers = new ArrayList<>();
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
                matchers.add(new QueryParamMatcher(query, qvalue));
            } else if ("path".equals(key)) {
                log.debug("********** Adding PathParam " + value);
                matchers.add(new PathParamMatcher(value));
            } else if ("header".equals(key)) {
                String header = value;
                int idx = value.indexOf('=');
                String hvalue = who;
                if (idx > 0) {
                    header = value.substring(0, idx);
                    hvalue = value.substring(idx + 1);
                }
                matchers.add(new HeaderOrCookieMatcher(header, hvalue));
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
        // We close existing connections with same who if they are not LocalDevPlaypens
        // or authorization fails.
        if (isGlobal) {
            if (master.globalSession != null) {
                if (!who.equals(master.globalSession.whoami())) {
                    log.errorv("Failed Client Connect for global session: Existing connection {0}", who);
                    ctx.response().setStatusCode(409).putHeader("Content-Type", "text/plain")
                            .end(master.globalSession.whoami());
                    return;
                }
                if (!(master.globalSession instanceof LocalDevPlaypen)) {
                    Playpen tmp = master.globalSession;
                    master.globalSession = null;
                    tmp.close();
                } else {
                    LocalDevPlaypen session = (LocalDevPlaypen) master.globalSession;
                    if (master.auth.authorized(ctx, session)) {
                        ctx.response().setStatusCode(204)
                                .putHeader(PlaypenProxyConstants.POLL_TIMEOUT, Long.toString(session.pollTimeout))
                                .putHeader(PlaypenProxyConstants.POLL_LINK, getPollLink(who))
                                .end();
                        return;
                    } else {
                        Playpen tmp = master.globalSession;
                        master.globalSession = null;
                        tmp.close();
                    }
                }
            }
        } else {
            Playpen playpen = master.sessions.get(who);
            if (playpen != null) {
                if (!(playpen instanceof LocalDevPlaypen)) {
                    master.sessions.remove(who);
                    playpen.close();
                } else {
                    LocalDevPlaypen session = (LocalDevPlaypen) playpen;
                    if (master.auth.authorized(ctx, session)) {
                        ctx.response().setStatusCode(204)
                                .putHeader(PlaypenProxyConstants.POLL_TIMEOUT, Long.toString(session.pollTimeout))
                                .putHeader(PlaypenProxyConstants.POLL_LINK, getPollLink(who))
                                .end();
                        return;
                    } else {
                        master.sessions.remove(who);
                        playpen.close();
                    }
                }
            }
        }
        boolean finalIsGlobal = isGlobal;
        master.auth.authenticate(ctx, () -> {
            LocalDevPlaypen newSession = new LocalDevPlaypen(who);
            if (finalIsGlobal) {
                master.globalSession = newSession;
            } else {
                matchers.add(new HeaderOrCookieMatcher(PlaypenProxyConstants.SESSION_HEADER, who));
                newSession.matchers = matchers;
                master.sessions.put(who, newSession);
            }

            master.auth.propagateToken(ctx, newSession);
            ctx.response().setStatusCode(204)
                    .putHeader(PlaypenProxyConstants.POLL_TIMEOUT, Long.toString(newSession.pollTimeout))
                    .putHeader(PlaypenProxyConstants.POLL_LINK, getPollLink(who))
                    .end();
        });
    }

    public void deleteClientConnection(RoutingContext ctx) {
        master.deleteConnection(ctx);
    }

    private LocalDevPlaypen getPlaypen(String who) {
        Playpen session = master.sessions.get(who);
        if (session == null && master.globalSession != null && master.globalSession.whoami().equals(who)) {
            session = master.globalSession;
        }
        if (session instanceof LocalDevPlaypen) {
            return (LocalDevPlaypen) session;
        } else {
            return null;
        }
    }

    public void pushResponse(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        String requestId = ctx.pathParam("request");
        String kp = ctx.queryParams().get("keepAlive");
        boolean keepAlive = kp == null ? true : Boolean.parseBoolean(kp);

        LocalDevPlaypen session = getPlaypen(who);
        if (session == null) {
            log.error("Push response could not find service " + master.config.service + " session ");
            error(ctx, 404, "Session not found for service " + master.config.service);
            return;
        }
        if (!master.auth.authorized(ctx, session))
            return;
        RoutingContext proxiedCtx = session.dequeueResponse(requestId);
        if (proxiedCtx == null) {
            log.error("Push response could not request " + requestId + " for service " + master.config.service + " session "
                    + who);
            ctx.response().putHeader(PlaypenProxyConstants.POLL_LINK, getPollLink(who));
            error(ctx, 404, "Request " + requestId + " not found");
            return;
        }
        HttpServerResponse proxiedResponse = proxiedCtx.response();
        HttpServerRequest pushedResponse = ctx.request();
        String status = pushedResponse.getHeader(PlaypenProxyConstants.STATUS_CODE_HEADER);
        if (status == null) {
            log.error("Failed to get status header");
            error(proxiedCtx, 500, "Failed");
            error(ctx, 400, "Failed to get status header");
            return;
        }
        proxiedResponse.setStatusCode(Integer.parseInt(status));
        pushedResponse.headers().forEach((key, val) -> {
            int idx = key.indexOf(PlaypenProxyConstants.HEADER_FORWARD_PREFIX);
            if (idx == 0) {
                String headerName = key.substring(PlaypenProxyConstants.HEADER_FORWARD_PREFIX.length());
                proxiedResponse.headers().add(headerName, val);
            }
        });
        sendBody(pushedResponse, proxiedResponse);
        if (keepAlive) {
            log.debugv("Keep alive {0} {1}", master.config.service, who);
            session.pollProcessing();
            session.poll(ctx);
        } else {
            log.debugv("End polling {0} {1}", master.config.service, who);
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

        LocalDevPlaypen session = getPlaypen(who);
        if (session == null) {
            log.error("Delete push response could not find service " + master.config.service + " session ");
            error(ctx, 404, "Session not found for service " + master.config.service);
            return;
        }
        if (!master.auth.authorized(ctx, session))
            return;
        RoutingContext proxiedCtx = session.dequeueResponse(requestId);
        if (proxiedCtx == null) {
            log.error("Delete push response could not find request " + requestId + " for service " + master.config.service
                    + " session " + who);
            error(ctx, 404, "Request " + requestId + " not found");
            return;
        }
        proxiedCtx.fail(500);
        ctx.response().setStatusCode(204).end();
    }

    public void pollNext(RoutingContext ctx) {
        String who = ctx.pathParam("who");

        LocalDevPlaypen session = getPlaypen(who);
        if (session == null) {
            log.error("Poll next could not find service " + master.config.service + " session " + who);
            error(ctx, 404, "Session not found for service " + master.config.service);
            return;
        }
        if (!master.auth.authorized(ctx, session))
            return;
        log.debugv("pollNext {0} {1}", master.config.service, who);
        session.poll(ctx);
    }

}

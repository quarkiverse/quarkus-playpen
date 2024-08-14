package io.quarkiverse.playpen.server;

import static io.quarkiverse.playpen.server.PlaypenProxyConstants.API_PATH;
import static io.quarkiverse.playpen.server.PlaypenProxyConstants.SESSION_HEADER;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.server.auth.NoAuth;
import io.quarkiverse.playpen.server.auth.PlaypenAuth;
import io.quarkiverse.playpen.utils.ProxyUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.HttpProxy;

public class PlaypenProxy {
    protected static final Logger log = Logger.getLogger(PlaypenProxy.class);

    protected Vertx vertx;
    protected PlaypenProxyConfig config;
    protected HttpClient client;
    protected HttpProxy proxy;
    protected Map<String, Playpen> sessions = new ConcurrentHashMap<>();
    protected volatile Playpen globalSession;
    protected PlaypenAuth auth = new NoAuth();
    protected LocalDevPlaypenServer local = new LocalDevPlaypenServer();
    protected RemoteDevPlaypenServer remote = new RemoteDevPlaypenServer();

    public static AutoCloseable create(Vertx vertx, PlaypenProxyConfig config, int proxyPort, int clientApiPort) {
        HttpServer proxy = vertx.createHttpServer();
        HttpServer clientApi = vertx.createHttpServer();
        PlaypenProxy proxyServer = new PlaypenProxy();
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

    public LocalDevPlaypenServer getLocal() {
        return local;
    }

    public RemoteDevPlaypenServer getRemote() {
        return remote;
    }

    public PlaypenProxyConfig getConfig() {
        return config;
    }

    public void init(Vertx vertx, Router proxyRouter, Router clientApiRouter, PlaypenProxyConfig config) {
        this.vertx = vertx;
        this.config = config;

        // API routes
        proxyRouter.route(API_PATH + "/version").method(HttpMethod.GET)
                .handler(
                        (ctx) -> ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end(config.version));
        proxyRouter.route(API_PATH + "/clientIp").method(HttpMethod.GET)
                .handler((ctx) -> ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain")
                        .end("" + ctx.request().remoteAddress().hostAddress()));
        proxyRouter.route(API_PATH + "/cookie/set").method(HttpMethod.GET).handler(this::setCookieApi);
        proxyRouter.route(API_PATH + "/cookie/get").method(HttpMethod.GET).handler(this::getCookieApi);
        proxyRouter.route(API_PATH + "/cookie/remove").method(HttpMethod.GET).handler(this::removeCookieApi);
        proxyRouter.route(API_PATH + "/*").handler(routingContext -> routingContext.fail(404));
        proxyRouter.route().handler(this::proxy);

        clientApiRouter.route("/version").method(HttpMethod.GET)
                .handler(
                        (ctx) -> ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end(config.version));
        clientApiRouter.route("/version").method(HttpMethod.GET)
                .handler(this::challenge);

        local.init(this, clientApiRouter);
        remote.init(this, clientApiRouter);

        // proxy to deployed services
        HttpClientOptions options = new HttpClientOptions();
        if (config.ssl) {
            options.setSsl(true).setTrustAll(true);
        }
        this.client = vertx.createHttpClient(options);
        this.proxy = HttpProxy.reverseProxy(client);
        proxy.origin(config.servicePort, config.serviceHost);

    }

    public void setAuth(PlaypenAuth auth) {
        this.auth = auth;
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

        Playpen found = null;
        for (Playpen session : sessions.values()) {
            if (session.isMatch(ctx)) {
                found = session;
                break;
            }
        }
        if (found == null) {
            found = globalSession;
            if (found != null) {
                log.debug("forward to global session");
            }
        } else {
            log.debugv("forward to session {0}", found.whoami());
        }

        if (found != null && found.isRunning()) {
            found.route(ctx);
        } else {
            proxy.handle(ctx.request());
        }
    }

    public void deleteConnection(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        Playpen playpen = null;
        if (globalSession != null && globalSession.whoami().equals(who)) {
            playpen = globalSession;
            globalSession = null;
        }
        if (playpen == null) {
            playpen = sessions.remove(who);
        }
        if (playpen == null) {
            ctx.response().setStatusCode(404).end();
            return;
        }
        log.debugv("Shutdown session {0}", who);
        playpen.close();
        ctx.response().setStatusCode(204).end();
    }

    public void challenge(RoutingContext ctx) {
        auth.challenge(ctx);
    }

}

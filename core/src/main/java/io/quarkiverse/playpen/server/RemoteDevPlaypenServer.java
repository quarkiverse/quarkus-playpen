package io.quarkiverse.playpen.server;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.quarkiverse.playpen.server.matchers.HeaderOrCookieMatcher;
import io.quarkiverse.playpen.server.matchers.PathParamMatcher;
import io.quarkiverse.playpen.server.matchers.PlaypenMatcher;
import io.quarkiverse.playpen.server.matchers.QueryParamMatcher;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.streams.Pipe;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class RemoteDevPlaypenServer {

    class RemoteDevPlaypen implements ProxyInterceptor, Playpen {
        HttpClient client;
        HttpProxy sessionProxy;
        final String who;
        final String liveReloadPrefix;
        volatile boolean running = true;
        List<PlaypenMatcher> matchers = new ArrayList<>();
        String host;
        int port;

        public RemoteDevPlaypen(String host, int port, String who) {
            this.host = host;
            if (port == -1)
                port = 80;
            this.port = port;
            this.who = who;
            this.liveReloadPrefix = master.config.clientPathPrefix + "/remote/" + who;
            client = vertx.createHttpClient();
            sessionProxy = HttpProxy.reverseProxy(client);
            sessionProxy.addInterceptor(this);
            sessionProxy.origin(this.port, this.host);
            log.debugv("RemoteSession for {0}:{1}", host, port);
        }

        public RemoteDevPlaypen(String who) {
            this(master.config.service + "-playpen-" + who, 80, who);
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
        public void route(RoutingContext ctx) {
            if (PlaypenProxyConstants.APPLICATION_QUARKUS.equals(ctx.request().getHeader(HttpHeaderNames.CONTENT_TYPE))) {
                log.error("Trying to send liveCode request through actual service is not allowed");
                ctx.response().setStatusCode(403).end();
            }
            sessionProxy.handle(ctx.request());
        }

        public void liveCoding(RoutingContext ctx) {
            if (!PlaypenProxyConstants.APPLICATION_QUARKUS.equals(ctx.request().getHeader(HttpHeaderNames.CONTENT_TYPE))) {
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

            if (PlaypenProxyConstants.APPLICATION_QUARKUS
                    .equals(context.request().headers().get(HttpHeaderNames.CONTENT_TYPE))) {
                String uri = context.request().getURI();
                String tmp = uri.replace(liveReloadPrefix, "");
                log.debugv("livecode change {0} to {1}", uri, tmp);
                context.request().setURI(tmp);
            }
            return context.sendRequest();
        }

        @Override
        public void close() {
            running = false;
            client.close();
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public String whoami() {
            return who;
        }
    }

    protected static final Logger log = Logger.getLogger(RemoteDevPlaypenServer.class);
    PlaypenProxy master;
    PlaypenProxyConfig config;
    Vertx vertx;
    String clientApiPath = PlaypenProxyConstants.REMOTE_API_PATH;
    RemotePlaypenManager manager = new MockRemotePlaypenManager();

    public void setManager(RemotePlaypenManager manager) {
        this.manager = manager;
    }

    public void init(PlaypenProxy master, Router clientApiRouter) {
        this.master = master;
        this.config = master.config;
        this.vertx = master.vertx;

        clientApiPath = master.config.clientPathPrefix + PlaypenProxyConstants.REMOTE_API_PATH + "/:who/_playpen_api";

        // CLIENT API
        clientApiRouter.route(clientApiPath + PlaypenProxyConstants.QUARKUS_DEPLOYMENT_PATH).method(HttpMethod.POST)
                .handler(this::createDeployment);
        clientApiRouter.route(clientApiPath + PlaypenProxyConstants.DEPLOYMENT_PATH).method(HttpMethod.GET)
                .blockingHandler(this::deployment);
        clientApiRouter.route(clientApiPath + PlaypenProxyConstants.DEPLOYMENT_PATH).method(HttpMethod.DELETE)
                .blockingHandler(this::deleteDeployment);
        clientApiRouter.route(clientApiPath + PlaypenProxyConstants.DEPLOYMENT_ZIP_PATH).method(HttpMethod.GET)
                .handler(this::deploymentFiles);
        clientApiRouter.route(clientApiPath + PlaypenProxyConstants.CONNECT_PATH).method(HttpMethod.POST)
                .handler(this::clientConnect);
        clientApiRouter.route(clientApiPath + PlaypenProxyConstants.CONNECT_PATH).method(HttpMethod.DELETE)
                .handler(this::deleteClientConnection);
        clientApiRouter.route(clientApiPath + "/*").handler(routingContext -> routingContext.fail(404));
        clientApiRouter.route(master.config.clientPathPrefix + PlaypenProxyConstants.REMOTE_API_PATH + "/:who" + "/*")
                .handler(this::liveCode);
    }

    public void deployment(RoutingContext ctx) {

        String who = ctx.pathParam("who");
        if (manager.exists(who)) {
            ctx.response().setStatusCode(204).end();
        } else {
            ctx.response().setStatusCode(404).end();
        }

    }

    public void deleteDeployment(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        Path path = Paths.get(master.config.basePlaypenDirectory).resolve(who);
        Path zip = path.resolve("project.zip");
        vertx.fileSystem().delete(zip.toString());
        manager.delete(who);

    }

    public void deploymentFiles(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        log.debugv("deploymentFiles {0}", who);
        Path path = Paths.get(master.config.basePlaypenDirectory).resolve(who);
        Path zip = path.resolve("project.zip");
        vertx.fileSystem().exists(zip.toString())
                .onFailure(event -> ctx.response().setStatusCode(500).end())
                .onSuccess(exists -> {
                    if (!exists.booleanValue()) {
                        ctx.response().setStatusCode(404).end();
                        return;
                    }
                    vertx.fileSystem().open(zip.toString(), new OpenOptions().setWrite(false).setRead(true))
                            .onFailure(event1 -> {
                                log.error("Could not open zip");
                                ctx.response().setStatusCode(500).end();

                            })
                            .onSuccess(async -> {
                                async.pause();
                                ctx.response().setStatusCode(200);
                                ctx.response().setChunked(true);

                                Body body = Body.body(async, -1);
                                Pipe<Buffer> pipe = body.stream().pipe();
                                pipe.endOnComplete(true);
                                pipe.endOnFailure(false);
                                pipe.to(ctx.response(), (ar) -> {
                                    if (ar.failed()) {
                                        log.error("Failed to pipe file upload");
                                        ctx.response().setStatusCode(500).end();
                                    }
                                });
                            });

                });

    }

    public void createDeployment(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        boolean manualPod = !ctx.queryParam("manual").isEmpty();
        log.debugv("createDeployment {0} manual = ", who, manualPod);
        /*
         * TODO
         * if (manager.exists(who)) {
         * ctx.response().setStatusCode(204).end();
         * return;
         * }
         *
         */
        ctx.request().pause();
        Path path = Paths.get(master.config.basePlaypenDirectory).resolve(who);
        Path zip = path.resolve("project.zip");
        log.debugv("Creating path: " + path.toString());
        vertx.fileSystem().mkdirs(path.toString())
                .onFailure(event -> {
                    log.error("Could not create deployment directories", event);
                    // for some reason, have to reset.  Probably because client is still sending and request was paused?
                    ctx.response().setStatusCode(500).reset();
                })
                .onSuccess(event -> {
                    log.debug("Made directories");
                    vertx.fileSystem().open(zip.toString(), new OpenOptions().setTruncateExisting(true).setWrite(true))
                            .onFailure(event1 -> {
                                log.error("Could not open zip", event1);
                                // for some reason, have to reset.  Probably because client is still sending and request was paused?
                                ctx.response().setStatusCode(500).reset();
                            })
                            .onSuccess(async -> {
                                log.debug("Opened async file to write");
                                long contentLength = -1L;
                                String contentLengthHeader = ctx.request().getHeader(HttpHeaders.CONTENT_LENGTH);
                                if (contentLengthHeader != null) {
                                    try {
                                        contentLength = Long.parseLong(contentLengthHeader);
                                    } catch (NumberFormatException var6) {
                                    }
                                }

                                Body body = Body.body(ctx.request(), contentLength);
                                Pipe<Buffer> pipe = body.stream().pipe();
                                pipe.endOnComplete(true);
                                pipe.endOnFailure(false);
                                pipe.to(async, (ar) -> {
                                    if (ar.failed()) {
                                        log.error("Failed to pipe file upload");
                                        ctx.response().reset();
                                        vertx.fileSystem().delete(zip.toString());
                                    } else {
                                        if (manualPod) {
                                            ctx.response().setStatusCode(201).end();
                                        } else {
                                            createQuarkusDeployment(ctx, who);
                                        }
                                    }
                                });
                            });

                });
    }

    private void createQuarkusDeployment(RoutingContext ctx, String who) {
        vertx.executeBlocking(() -> {
            manager.create(who);
            ctx.response().setStatusCode(201).end();
            return null;
        });
    }

    public void liveCode(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        Playpen playpen = master.sessions.get(who);
        if (playpen == null && isGlobal(who)) {
            playpen = master.globalSession;
        }
        if (!(playpen instanceof RemoteDevPlaypen)) {
            playpen = null;
        }
        if (playpen == null || !playpen.isRunning()) {
            log.debugv("livecode {0} session not found", who);
            ctx.response().setStatusCode(404).end();
        } else {
            ((RemoteDevPlaypen) playpen).liveCoding(ctx);
        }
    }

    public void clientConnect(RoutingContext ctx) {
        // TODO: add security 401 protocol

        log.debug("Connect: " + ctx.request().absoluteURI());
        String who = ctx.pathParam("who");
        log.debugv("Establish connection for {0}", who);
        List<PlaypenMatcher> matchers = new ArrayList<>();
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
                matchers.add(new QueryParamMatcher(query, qvalue));
            } else if ("path".equals(key)) {
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
                if (!(master.globalSession instanceof RemoteDevPlaypen)) {
                    Playpen tmp = master.globalSession;
                    master.globalSession = null;
                    tmp.close();
                } else {
                    log.debugv("connecting to existing global session");
                    master.auth.authenticate(ctx, () -> {
                        ctx.response().setStatusCode(204).end();
                    });
                    return;
                }
            }
        } else {
            Playpen playpen = master.sessions.get(who);
            if (playpen != null) {
                if (!(playpen instanceof RemoteDevPlaypen)) {
                    master.sessions.remove(who);
                    playpen.close();
                } else {
                    log.debugv("connecting to existing session");
                    master.auth.authenticate(ctx, () -> {
                        ctx.response().setStatusCode(204).end();
                    });
                    return;
                }
            }
        }
        boolean finalIsGlobal = isGlobal;
        String finalHost = host;
        int finalPort = port;
        log.debugv("Creating new session: {0} {1} {2}", isGlobal, finalHost, finalPort);
        master.auth.authenticate(ctx, () -> {
            RemoteDevPlaypen newSession;
            if (finalHost == null) {
                newSession = new RemoteDevPlaypen(who);
            } else {
                newSession = new RemoteDevPlaypen(finalHost, finalPort, who);
            }
            newSession.matchers = matchers;
            if (finalIsGlobal) {
                master.globalSession = newSession;
            } else {
                newSession.matchers.add(new HeaderOrCookieMatcher(PlaypenProxyConstants.SESSION_HEADER, who));
                master.sessions.put(who, newSession);
            }
            ctx.response().setStatusCode(204).end();
        });
    }

    public void deleteClientConnection(RoutingContext ctx) {
        master.deleteConnection(ctx);
    }

    private boolean isGlobal(String who) {
        return master.globalSession != null && master.globalSession.whoami().equals(who);
    }

}

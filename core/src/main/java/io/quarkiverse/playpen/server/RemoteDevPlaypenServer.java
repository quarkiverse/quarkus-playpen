package io.quarkiverse.playpen.server;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
import io.vertx.core.http.HttpClientOptions;
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
    public static class HostPort {
        public final String host;
        public final int port;

        public HostPort(String val) {
            int idx = val.indexOf(':');
            int p = -1;
            if (idx > -1) {
                this.host = val.substring(0, idx);
                String pStr = val.substring(idx + 1);
                p = Integer.parseInt(pStr);
            } else {
                this.host = val;
            }
            if (p == -1)
                p = 80;
            this.port = p;
        }
    }

    public static void parseHost(String val, BiConsumer<String, Integer> values) {
        String host;
        int port;
        int idx = val.indexOf(':');
        int p = -1;
        if (idx > -1) {
            host = val.substring(0, idx);
            String pStr = val.substring(idx + 1);
            p = Integer.parseInt(pStr);
        } else {
            host = val;
        }
        if (p == -1)
            p = 80;
        port = p;
        values.accept(host, port);
    }

    class RemoteDevPlaypen implements ProxyInterceptor, Playpen {
        HttpClient client;
        HttpProxy sessionProxy;
        final String who;
        final String liveReloadPrefix;
        volatile boolean running = true;
        List<PlaypenMatcher> matchers = new ArrayList<>();
        String host;
        int port = -1;
        final boolean deleteOnShutdown;
        long timerId;
        volatile long lastRequest = System.currentTimeMillis();

        public RemoteDevPlaypen(String host, String who, boolean deleteOnShutdown) {
            this.deleteOnShutdown = deleteOnShutdown;
            parseHost(host, (s, integer) -> {
                this.host = s;
                this.port = integer;
            });
            this.who = who;
            this.liveReloadPrefix = master.config.clientPathPrefix + "/remote/" + who;
            client = vertx.createHttpClient();
            sessionProxy = HttpProxy.reverseProxy(client);
            sessionProxy.addInterceptor(this);
            sessionProxy.origin(this.port, this.host);
            log.debugv("RemoteSession for {0}:{1}", this.host, port);
            timerId = vertx.setPeriodic(config.timerPeriod, this::timerCallback);
        }

        void timerCallback(Long t) {
            if (!running)
                return;
            if (System.currentTimeMillis() - lastRequest > config.idleTimeout) {
                log.warnv("Shutting down remote playpen {0} due to idle timeout.", who);
                master.getPlaypenForDeletion(who);
                close();
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
        public void route(RoutingContext ctx) {
            if (PlaypenProxyConstants.APPLICATION_QUARKUS.equals(ctx.request().getHeader(HttpHeaderNames.CONTENT_TYPE))) {
                log.error("Trying to send liveCode request through actual service is not allowed");
                ctx.response().setStatusCode(403).end();
            }
            lastRequest = System.currentTimeMillis();
            sessionProxy.handle(ctx.request());
        }

        public void liveCoding(RoutingContext ctx) {
            if (!PlaypenProxyConstants.APPLICATION_QUARKUS.equals(ctx.request().getHeader(HttpHeaderNames.CONTENT_TYPE))) {
                log.error("Only live code requests are allowed");
                ctx.response().setStatusCode(403).end();
                return;
            }
            lastRequest = System.currentTimeMillis();
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
            } else {
                // add session header to request if it is not already there
                // and we are not part of a global session
                if (master.globalSession != this
                        && !context.request().headers().contains(PlaypenProxyConstants.SESSION_HEADER)) {
                    context.request().headers().add(PlaypenProxyConstants.SESSION_HEADER, who);
                }
            }
            return context.sendRequest();
        }

        @Override
        public void close() {
            close(null);
        }

        public void close(Runnable callback) {
            if (running) {
                running = false;
                client.close();
                vertx.cancelTimer(timerId);
                if (deleteOnShutdown) {
                    deleteDeployment(who, callback);
                    return;
                }
            }
            if (callback != null)
                callback.run();

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
        boolean exists = !ctx.queryParam("exists").isEmpty();
        if (exists) {
            vertx.executeBlocking(() -> {
                String host = manager.get(who);
                if (host != null) {
                    ctx.response().setStatusCode(204).end();
                } else {
                    ctx.response().setStatusCode(404).end();
                }
                return null;
            });

        } else {
            vertx.executeBlocking(() -> {
                String host = manager.get(who);
                if (host != null) {
                    ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end(host);
                } else {
                    ctx.response().setStatusCode(404).end();
                }
                return null;
            });

        }
    }

    public void deleteDeployment(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        deleteDeployment(who, () -> ctx.response().setStatusCode(204).end());
    }

    private void deleteDeployment(String who, Runnable callback) {
        Path path = Paths.get(master.config.basePlaypenDirectory).resolve(who);
        Path zip = path.resolve("project.zip");
        vertx.fileSystem().delete(zip.toString());
        vertx.executeBlocking(() -> {
            try {
                manager.delete(who);
            } finally {
                if (callback != null)
                    callback.run();
            }
            return null;
        });
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
        List<String> copy = ctx.queryParam("copy-env");
        boolean copyEnv = true;
        if (!copy.isEmpty()) {
            copyEnv = Boolean.parseBoolean(copy.get(0));
        }
        boolean finalCopyEnv = copyEnv;
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
                                            createQuarkusDeployment(ctx, who, finalCopyEnv);
                                        }
                                    }
                                });
                            });

                });
    }

    private void createQuarkusDeployment(RoutingContext ctx, String who, boolean copyEnv) {
        log.debugv("Create quarkus deployment {0}", who);
        vertx.executeBlocking(() -> {
            try {
                manager.create(who, copyEnv);
                waitForHost(ctx, who, 0);
            } catch (Exception e) {
                log.error("Failed to create remote playpen", e);
                ctx.response().setStatusCode(500).end();
            }
            return null;
        });
    }

    private void waitForHost(RoutingContext ctx, String who, int count) {
        String host = manager.get(who);
        if (host == null) {
            if (count + 1 > 30) {
                log.error("Timeout trying to get new remote playpen");
                deleteDeployment(who, () -> ctx.response().setStatusCode(500).end());
                return;
            }
            vertx.setTimer(2000, event -> {
                vertx.executeBlocking(() -> {
                    waitForHost(ctx, who, count + 1);
                    return null;
                });
            });
            return;
        }
        parseHost(host, (s, integer) -> {
            HttpClientOptions options = new HttpClientOptions();
            options.setDefaultHost(s).setDefaultPort(integer);
            HttpClient client = vertx.createHttpClient(options);
            isUpYet(ctx, who, client, 0);
        });

    }

    private void isUpYet(RoutingContext ctx, String who, HttpClient client, int count) {
        // just send a dummy request, 404 is fine
        client.request(HttpMethod.GET, "/_nonsense_")
                .onFailure(event -> {
                    continueIsUpYet(ctx, who, client, count);
                })
                .onSuccess(req -> {
                    req.send()
                            .onSuccess(event -> {
                                ctx.response().setStatusCode(201).end();
                            })
                            .onFailure(event -> {
                                continueIsUpYet(ctx, who, client, count);
                            });

                });
    }

    private void continueIsUpYet(RoutingContext ctx, String who, HttpClient client, int count) {
        if (count + 1 > 30) {
            failIsUpYet(ctx, who, client);
            return;
        }
        log.debugv("Remote playpen is not up yet {0}", count);
        vertx.setTimer(2000, event -> {
            isUpYet(ctx, who, client, count + 1);
        });
    }

    private void failIsUpYet(RoutingContext ctx, String who, HttpClient client) {
        log.error("Timeout trying to ping new remote playpen");
        client.close();
        deleteDeployment(who, () -> ctx.response().setStatusCode(500).end());
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
        boolean cleanup = false;
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
            } else if ("cleanup".equals(key)) {
                cleanup = Boolean.parseBoolean(value);
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
        boolean finalCleanup = cleanup;
        String finalHost = host;
        log.debugv("Creating new session: {0} {1}", isGlobal, finalHost);
        master.auth.authenticate(ctx, () -> {
            vertx.executeBlocking(() -> {
                if (finalHost == null) {
                    try {
                        // we are expecting that a pod was created with temporary remote playpen
                        log.debugv("Find playpen for session {0}", who);
                        String theHost = manager.get(who);
                        if (theHost == null) {
                            log.warnv("Remote playpen {0} does not exist", who);
                            ctx.response().setStatusCode(400).putHeader("Content-Type", "text/plain")
                                    .end("Cannot resolve remote playpen endpoint");
                        }
                        setupSession(ctx, theHost, who, matchers, finalIsGlobal, finalCleanup);
                    } catch (Exception e) {
                        log.error("Failed to setup session", e);
                        ctx.response().setStatusCode(500).end();
                    }
                    return null;
                } else {
                    try {
                        log.debugv("Resolve playpen for session {0}", finalHost);
                        String clusterHost = manager.getHost(finalHost);
                        log.debugv("clusterHost: " + clusterHost);
                        setupSession(ctx, clusterHost, who, matchers, finalIsGlobal, false);
                    } catch (IllegalArgumentException ill) {
                        log.error("Cannot resolve host: " + ill.getMessage());
                        ctx.response().setStatusCode(400).putHeader("Content-Type", "text/plain")
                                .end(ill.getMessage());

                    } catch (Exception e) {
                        log.error("Failed to setup session", e);
                        ctx.response().setStatusCode(500).end();
                    }
                    return null;
                }
            });
        });
    }

    private void setupSession(RoutingContext ctx, String host, String who, List<PlaypenMatcher> matchers, boolean finalIsGlobal,
            boolean deleteOnClose) {
        RemoteDevPlaypen newSession = new RemoteDevPlaypen(host, who, deleteOnClose);
        newSession.matchers = matchers;
        if (finalIsGlobal) {
            master.globalSession = newSession;
        } else {
            newSession.matchers.add(new HeaderOrCookieMatcher(PlaypenProxyConstants.SESSION_HEADER, who));
            master.sessions.put(who, newSession);
        }
        ctx.response().setStatusCode(204).end();
    }

    public void deleteClientConnection(RoutingContext ctx) {
        String who = ctx.pathParam("who");
        Playpen playpen = master.getPlaypenForDeletion(who);
        if (playpen == null) {
            ctx.response().setStatusCode(404).end();
            return;
        }
        log.debugv("Shutdown connection {0}", who);
        if (playpen instanceof RemoteDevPlaypen) {
            ((RemoteDevPlaypen) playpen).close(() -> {
                ctx.response().setStatusCode(204).end();
            });
        } else {
            playpen.close();
            ctx.response().setStatusCode(204).end();
        }
    }

    private boolean isGlobal(String who) {
        return master.globalSession != null && master.globalSession.whoami().equals(who);
    }

}

package io.quarkiverse.playpen.utils;

import org.jboss.logging.Logger;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.streams.Pipe;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.Body;

public class ReverseProxy {
    protected static final Logger log = Logger.getLogger(ReverseProxy.class);
    protected HttpClient client;

    public ReverseProxy(HttpClient client) {
        this.client = client;
    }

    public void proxyContext(RoutingContext ctx, int port, String host, String uri) {
        log.debugv("proxyContext {0} {1}", host, uri);
        ctx.request().pause();
        client.request(ctx.request().method(), port, host, uri)
                .onSuccess(req -> {
                    log.debugv("connected {0} {1}", host, uri);
                    req.response().onSuccess(res -> {
                        log.debugv("responding {0} {1}", host, uri);
                        handleProxyResponse(ctx, req, res);
                    })
                            .onFailure(event -> {
                                log.error("live coding response failed");
                                ctx.response().setStatusCode(500).end();
                            });
                    ctx.request().headers().forEach((s, s2) -> req.putHeader(s, s2));
                    long contentLength = -1L;
                    String contentLengthHeader = ctx.request().getHeader(HttpHeaders.CONTENT_LENGTH);
                    if (contentLengthHeader != null) {
                        try {
                            contentLength = Long.parseLong(contentLengthHeader);
                        } catch (NumberFormatException var6) {
                        }
                    }

                    Body body = Body.body(ctx.request(), contentLength);
                    long len = body.length();
                    if (len >= 0L) {
                        req.putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
                    } else {
                        req.setChunked(true);
                    }
                    Pipe<Buffer> pipe = body.stream().pipe();
                    pipe.endOnComplete(true);
                    pipe.endOnFailure(false);
                    pipe.to(req, (ar) -> {
                        if (ar.failed()) {
                            log.error("Failed to pipe live code request");
                            req.reset();
                        }

                    });
                })
                .onFailure(event -> {
                    log.error("Failed to connect to " + host, event);
                    ctx.response().setStatusCode(500).end();
                });
    }

    private void handleProxyResponse(RoutingContext ctx, HttpClientRequest req, HttpClientResponse res) {
        //log.debug("live coding response: " + res.statusCode());
        ctx.response().setStatusCode(res.statusCode());
        //log.debug("headers:");
        res.headers().forEach((key, val) -> {
            //log.debugv("{0}: {1}", key, val);
            ctx.response().headers().add(key, val);

        });
        long contentLength = -1L;
        String contentLengthHeader = res.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            try {
                contentLength = Long.parseLong(contentLengthHeader);
            } catch (NumberFormatException var14) {
            }
        }
        Body body = Body.body(res, contentLength);
        long len = body.length();
        if (len >= 0L) {
            ctx.response().putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
        } else {
            ctx.response().setChunked(true);
        }

        Pipe<Buffer> pipe = body.stream().pipe();
        pipe.endOnSuccess(true);
        pipe.endOnFailure(false);
        pipe.to(ctx.response(), (ar) -> {
            if (ar.failed()) {
                log.error("Failed to send proxy response back");
                req.reset();
                ctx.response().reset();
            }
        });
    }

}

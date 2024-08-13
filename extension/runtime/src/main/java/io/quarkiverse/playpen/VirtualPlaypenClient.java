package io.quarkiverse.playpen;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.jboss.logging.Logger;

import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.quarkiverse.playpen.client.AbstractPlaypenClient;
import io.quarkiverse.playpen.server.PlaypenProxyConstants;
import io.quarkus.netty.runtime.virtual.VirtualClientConnection;
import io.quarkus.netty.runtime.virtual.VirtualResponseHandler;
import io.quarkus.vertx.http.runtime.QuarkusHttpHeaders;
import io.quarkus.vertx.http.runtime.VertxHttpRecorder;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.core.streams.impl.InboundBuffer;

public class VirtualPlaypenClient extends AbstractPlaypenClient {
    protected static final Logger log = Logger.getLogger(VirtualPlaypenClient.class);

    protected Vertx vertx;

    private class NettyResponseHandler implements VirtualResponseHandler, ReadStream<Buffer> {

        final String responsePath;
        InboundBuffer<Buffer> queue;
        static Buffer end = Buffer.buffer();
        Handler<Void> endHandler;
        VirtualClientConnection connection;

        public void setConnection(VirtualClientConnection connection) {
            this.connection = connection;
        }

        private void write(Buffer buf) {
            vertx.runOnContext((v) -> queue.write(buf));
        }

        public NettyResponseHandler(String responsePath, Vertx vertx) {
            this.responsePath = responsePath;
        }

        @Override
        public ReadStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
            queue.exceptionHandler(handler);
            return this;
        }

        @Override
        public ReadStream<Buffer> handler(@Nullable Handler<Buffer> handler) {
            if (handler == null) {
                if (queue != null)
                    queue.handler(null);
                return this;
            }
            log.debug("NettyResponseHandler: set handler");
            queue.handler((buf) -> {
                log.debug("NettyResponseHandler: handler");
                if (buf == end) {
                    log.debug("NettyResponseHandler: calling end");
                    connection.close();
                    if (endHandler != null) {
                        endHandler.handle(null);
                    }
                } else {
                    log.debug("NettyResponseHandler: handler.handle(buf)");
                    handler.handle(buf);
                }
            });
            return this;
        }

        @Override
        public ReadStream<Buffer> pause() {
            log.debug("NettyResponseHandler: pause");
            queue.pause();
            return this;
        }

        @Override
        public ReadStream<Buffer> resume() {
            log.debug("NettyResponseHandler: resume");
            boolean result = queue.resume();
            log.debug("NettyResponseHandler: resume returned: " + result);
            return this;
        }

        @Override
        public ReadStream<Buffer> fetch(long amount) {
            log.debug("NettyResponseHandler: fetch");
            queue.fetch(amount);
            return this;
        }

        @Override
        public ReadStream<Buffer> endHandler(@Nullable Handler<Void> endHandler) {
            this.endHandler = endHandler;
            return this;
        }

        @Override
        public void handleMessage(Object msg) {
            log.debugv("NettyResponseHandler: handleMessage({0})", msg.getClass().getName());
            if (msg instanceof HttpResponse) {
                queue = new InboundBuffer<>(vertx.getOrCreateContext());
                queue.pause();
                HttpResponse res = (HttpResponse) msg;
                proxyClient.request(HttpMethod.POST, responsePath + "?keepAlive=" + running)
                        .onFailure(exc -> {
                            logError("Proxy handle response failure: " + exc.getMessage());
                            workerOffline();
                        })
                        .onSuccess(pushRequest -> {
                            log.debug("NettyResponseHandler connect accepted for pushResponse");
                            setToken(pushRequest);
                            pushRequest.setTimeout(pollTimeoutMillis);
                            pushRequest.putHeader(PlaypenProxyConstants.STATUS_CODE_HEADER,
                                    Integer.toString(res.status().code()));

                            for (String name : res.headers().names()) {
                                final List<String> allForName = res.headers().getAll(name);
                                if (allForName == null || allForName.isEmpty()) {
                                    continue;
                                }

                                for (Iterator<String> valueIterator = allForName.iterator(); valueIterator.hasNext();) {
                                    String val = valueIterator.next();
                                    if (name.equalsIgnoreCase("Transfer-Encoding")
                                            && val.equals("chunked")) {
                                        continue; // ignore transfer encoding, chunked screws up message and response
                                    }
                                    pushRequest.headers().add(PlaypenProxyConstants.HEADER_FORWARD_PREFIX + name, val);
                                }
                            }
                            pushRequest.send(this)
                                    .onFailure(exc -> {
                                        if (exc instanceof TimeoutException) {
                                            poll();
                                        } else {
                                            logError("Failed to push service response: " + exc.getMessage());
                                            workerOffline();
                                        }
                                    })
                                    .onSuccess(VirtualPlaypenClient.this::handlePoll); // a successful push restarts poll
                        });
            }
            if (msg instanceof HttpContent) {
                log.debug("NettyResponseHandler: write HttpContent");
                write(BufferImpl.buffer(((HttpContent) msg).content()));
            }
            if (msg instanceof FileRegion) {
                log.error("FileRegion not supported yet");
                throw new RuntimeException("FileRegion not supported yet");
            }
            if (msg instanceof LastHttpContent) {
                log.debug("NettyResponseHandler: write LastHttpContent");
                write(end);
            }
        }

        @Override
        public void close() {

        }
    }

    private class NettyWriteStream implements WriteStream<Buffer> {
        VirtualClientConnection connection;

        public NettyWriteStream(VirtualClientConnection connection) {
            this.connection = connection;
        }

        private void writeHttpContent(Buffer data) {
            log.debug("NettyWriteStream: writeHttpContent");
            // todo getByteBuf copies the underlying byteBuf
            DefaultHttpContent content = new DefaultHttpContent(data.getByteBuf());
            connection.sendMessage(content);

        }

        @Override
        public WriteStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
            return this;
        }

        @Override
        public Future<Void> write(Buffer data) {
            writeHttpContent(data);
            Promise<Void> promise = Promise.promise();
            write(data, promise);
            return promise.future();
        }

        @Override
        public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
            writeHttpContent(data);
            handler.handle(Future.succeededFuture());
        }

        @Override
        public void end(Handler<AsyncResult<Void>> handler) {
            log.debug("NettyWriteStream: end");
            connection.sendMessage(LastHttpContent.EMPTY_LAST_CONTENT);
            handler.handle(Future.succeededFuture());
        }

        @Override
        public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return false;
        }

        @Override
        public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
            return this;
        }
    }

    protected void processPoll(HttpClientResponse pollResponse) {
        log.debug("Unpack poll request");
        String method = pollResponse.getHeader(PlaypenProxyConstants.METHOD_HEADER);
        String uri = pollResponse.getHeader(PlaypenProxyConstants.URI_HEADER);
        String responsePath = pollResponse.getHeader(PlaypenProxyConstants.RESPONSE_LINK);
        NettyResponseHandler handler = new NettyResponseHandler(responsePath, vertx);
        VirtualClientConnection connection = VirtualClientConnection.connect(handler, VertxHttpRecorder.VIRTUAL_HTTP,
                null);
        handler.setConnection(connection);

        QuarkusHttpHeaders quarkusHeaders = new QuarkusHttpHeaders();
        // add context specific things
        io.netty.handler.codec.http.HttpMethod httpMethod = io.netty.handler.codec.http.HttpMethod.valueOf(method);

        DefaultHttpRequest nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                httpMethod, uri,
                quarkusHeaders);
        pollResponse.headers().forEach((key, val) -> {
            log.debugv("Poll response header: {0} : {1}", key, val);
            int idx = key.indexOf(PlaypenProxyConstants.HEADER_FORWARD_PREFIX);
            if (idx == 0) {
                String headerName = key.substring(PlaypenProxyConstants.HEADER_FORWARD_PREFIX.length());
                nettyRequest.headers().add(headerName, val);
            } else if (key.equalsIgnoreCase("Content-Length")) {
                nettyRequest.headers().add("Content-Length", val);
            }
        });
        if (!nettyRequest.headers().contains(HttpHeaderNames.HOST)) {
            nettyRequest.headers().add(HttpHeaderNames.HOST, "localhost");
        }

        log.debug("send initial nettyRequest");
        connection.sendMessage(nettyRequest);
        pollResponse.pipeTo(new NettyWriteStream(connection));
    }
}

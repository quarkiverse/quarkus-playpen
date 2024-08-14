package io.quarkiverse.playpen.server;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkiverse.playpen.server.auth.NoAuth;
import io.quarkiverse.playpen.server.auth.OpenshiftBasicAuth;
import io.quarkiverse.playpen.server.auth.PlaypenAuth;
import io.quarkiverse.playpen.server.auth.SecretAuth;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

@ApplicationScoped
public class QuarkusPlaypenServer {
    protected static final Logger log = Logger.getLogger(QuarkusPlaypenServer.class);

    @Inject
    @ConfigProperty(name = "service.name")
    protected String serviceName;

    @Inject
    @ConfigProperty(name = "service.host")
    protected String serviceHost;

    @Inject
    @ConfigProperty(name = "service.port")
    protected int servicePort;

    @Inject
    @ConfigProperty(name = "service.ssl", defaultValue = "false")
    protected boolean serviceSsl;

    @Inject
    @ConfigProperty(name = "client.api.port")
    protected int clientApiPort;

    @Inject
    @ConfigProperty(name = "idle.timeout", defaultValue = "60000")
    protected int idleTimeout;

    @Inject
    @ConfigProperty(name = "poll.timeout", defaultValue = "5000")
    protected int pollTimeout;

    @Inject
    @ConfigProperty(name = "authentication.type", defaultValue = "none")
    protected String authType;

    @Inject
    @ConfigProperty(name = "oauth.url", defaultValue = "oauth-openshift.openshift-authentication.svc.cluster.local")
    protected String oauthUrl;

    @Inject
    @ConfigProperty(name = "secret", defaultValue = "badsecret")
    protected String secret;

    @Inject
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    protected String version;

    @Inject
    @ConfigProperty(name = "client.path.prefix")
    protected Optional<String> clientPathPrefix;

    protected PlaypenProxy proxyServer;
    private HttpServer clientApi;

    public PlaypenProxy getProxyServer() {
        return proxyServer;
    }

    public void start(@Observes StartupEvent start, Vertx vertx, Router proxyRouter) {

        proxyServer = new PlaypenProxy();
        PlaypenProxyConfig config = new PlaypenProxyConfig();
        config.service = serviceName;
        config.serviceHost = serviceHost;
        config.servicePort = servicePort;
        config.ssl = serviceSsl;
        config.idleTimeout = idleTimeout;
        config.defaultPollTimeout = pollTimeout;
        config.version = version;

        if (PlaypenAuth.OPENSHIFT_BASIC_AUTH.equalsIgnoreCase(authType)) {
            log.info("Openshift Basic Auth: " + oauthUrl);
            proxyServer.setAuth(new OpenshiftBasicAuth(vertx, oauthUrl));
        } else if (PlaypenAuth.SECRET_AUTH.equalsIgnoreCase(authType)) {
            log.info("Secret auth");
            proxyServer.setAuth(new SecretAuth(secret));
        } else {
            log.info("no auth");
            proxyServer.setAuth(new NoAuth());
        }
        if (clientPathPrefix.isPresent()) {
            log.info("Client Path Prefix: " + clientPathPrefix.get());
            config.clientPathPrefix = clientPathPrefix.get();
        }
        clientApi = vertx.createHttpServer();
        Router clientApiRouter = Router.router(vertx);
        proxyServer.init(vertx, proxyRouter, clientApiRouter, config);
        clientApi.requestHandler(clientApiRouter).listen(clientApiPort);
    }

    @Shutdown
    public void stop() {
        clientApi.close();
    }
}

package io.quarkiverse.playpen.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import java.util.concurrent.TimeUnit;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.playpen.LocalPlaypenRecorder;
import io.quarkiverse.playpen.server.PlaypenProxy;
import io.quarkiverse.playpen.server.PlaypenProxyConfig;
import io.quarkiverse.playpen.server.PlaypenProxyConstants;
import io.quarkiverse.playpen.utils.ProxyUtils;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.ext.web.Router;

public class PlaypenServerSessionTest {

    public static final int SERVICE_PORT = 9091;
    public static final int PROXY_PORT = 9092;
    public static final int CLIENT_API_PORT = 9093;

    private static final String APP_PROPS = "" +
            "quarkus.playpen.uri=http://localhost:9093/local/john?path=/users/john&query=user=john\n"
            + "quarkus.log.category.\"io.quarkiverse.playpen\".level=DEBUG\n"
            + "quarkus.playpen.manual-start=true\n";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource(new StringAsset(APP_PROPS), "application.properties")
                    .addClasses(RouteProducer.class));

    public static AutoCloseable proxy;

    static HttpServer myService;

    @Singleton
    public static class RouteProducer {
        void observeRouter(@Observes Router router) {
            router.route().handler(
                    request -> {
                        System.out.println("************ CALLED LOCAL SERVER **************");
                        request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("local");
                    });
        }

    }

    static Vertx vertx;

    @BeforeAll
    public static void before() {
        vertx = new VertxBuilder()
                .threadFactory(new VertxThreadFactory() {
                    public VertxThread newVertxThread(Runnable target, String name, boolean worker, long maxExecTime,
                            TimeUnit maxExecTimeUnit) {
                        return new VertxThread(target, "TEST-VERTX." + name, worker, maxExecTime, maxExecTimeUnit);
                    }
                }).init().vertx();
        myService = vertx.createHttpServer();
        ProxyUtils.await(1000, myService.requestHandler(request -> {
            request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("my-service");
        }).listen(SERVICE_PORT));

        PlaypenProxyConfig config = new PlaypenProxyConfig();
        config.service = "my-service";
        config.serviceHost = "localhost";
        config.servicePort = SERVICE_PORT;
        proxy = PlaypenProxy.create(vertx, config, PROXY_PORT, CLIENT_API_PORT);
    }

    @AfterAll
    public static void after() throws Exception {
        System.out.println(" -------    CLEANUP TEST ------");
        if (vertx != null) {
            ProxyUtils.await(1000, myService.close());
            System.out.println(" -------    Cleaned up my-service ------");
            proxy.close();
            System.out.println(" -------    Cleaned up proxy ------");
            ProxyUtils.await(1000, vertx.close());
            System.out.println(" -------    Cleaned up test vertx ------");
        }
    }

    @Test
    public void testSession() throws Exception {

        try {
            LocalPlaypenRecorder.startSession();
            System.out.println("-------------------- Query GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/yo?user=john")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("-------------------- Path GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/users/john/stuff")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("-------------------- Header GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .header(PlaypenProxyConstants.SESSION_HEADER, "john")
                    .get("/stuff")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("-------------------- Cookie GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .cookie(PlaypenProxyConstants.SESSION_HEADER, "john")
                    .get("/stuff")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("------------------ No session ---------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/yo")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/yo?user=jen")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/users/jen")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .port(PROXY_PORT)
                    .header(PlaypenProxyConstants.SESSION_HEADER, "jen")
                    .get("/stuff")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
        } finally {
            LocalPlaypenRecorder.closeSession();
        }
        System.out.println("-------------------- After Shutdown GET REQUEST --------------------");
        given()
                .when()
                .port(PROXY_PORT)
                .get("/yo")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("my-service"));
        given()
                .when()
                .port(PROXY_PORT)
                .get("/yo?user=john")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("my-service"));
    }
}

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

import io.quarkiverse.playpen.PlaypenRecorder;
import io.quarkiverse.playpen.ProxyUtils;
import io.quarkiverse.playpen.server.PlaypenServer;
import io.quarkiverse.playpen.server.ServiceConfig;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.ext.web.Router;

public class PlaypenServerTest {

    public static final int SERVICE_PORT = 9091;
    public static final int PROXY_PORT = 9092;

    private static final String APP_PROPS = "" +
            "quarkus.playpen.uri=http://localhost:9092?who=bill\n"
            + "quarkus.playpen.manual-start=true\n";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource(new StringAsset(APP_PROPS), "application.properties")
                    .addClasses(RouteProducer.class));

    public static PlaypenServer proxyServer;
    public static HttpServer proxy;

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

        proxy = vertx.createHttpServer();
        proxyServer = new PlaypenServer();
        Router proxyRouter = Router.router(vertx);
        ServiceConfig config = new ServiceConfig("my-service", "localhost", SERVICE_PORT);
        proxyServer.init(vertx, proxyRouter, proxyRouter, config);
        ProxyUtils.await(1000, proxy.requestHandler(proxyRouter).listen(PROXY_PORT));
    }

    @AfterAll
    public static void after() {
        System.out.println(" -------    CLEANUP TEST ------");
        if (vertx != null) {
            ProxyUtils.await(1000, myService.close());
            System.out.println(" -------    Cleaned up my-service ------");
            ProxyUtils.await(1000, proxy.close());
            System.out.println(" -------    Cleaned up proxy ------");
            ProxyUtils.await(1000, vertx.close());
            System.out.println(" -------    Cleaned up test vertx ------");
        }
    }

    @Test
    public void testNothing() {

    }

    @Test
    public void testProxy() {
        // invoke service directly
        given()
                .when()
                .port(SERVICE_PORT)
                .get("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        given()
                .when()
                .port(SERVICE_PORT)
                .body("hello")
                .contentType("text/plain")
                .post("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        // invoke local directly
        /*
         * given()
         * .when()
         * .get("/yo")
         * .then()
         * .statusCode(200)
         * .body(equalTo("local"));
         * given()
         * .when()
         * .body("hello")
         * .contentType("text/plain")
         * .post("/yo")
         * .then()
         * .statusCode(200)
         * .body(equalTo("local"));
         *
         */
        // invoke proxy
        given()
                .when()
                .port(PROXY_PORT)
                .get("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        given()
                .when()
                .port(PROXY_PORT)
                .body("hello")
                .contentType("text/plain")
                .post("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
    }

    @Test
    public void testGlobalSession() throws Exception {

        try {
            PlaypenRecorder.startSession();
            System.out.println("------------------ POST REQUEST BODY ---------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .contentType("text/plain")
                    .body("hello")
                    .post("/hey")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("-------------------- GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/yo")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("------------------ POST REQUEST NO BODY ---------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .post("/nobody")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
        } finally {
            PlaypenRecorder.closeSession();
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
    }
}

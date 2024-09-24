package io.quarkiverse.playpen.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.playpen.server.PlaypenProxy;
import io.quarkiverse.playpen.server.PlaypenProxyConfig;
import io.quarkiverse.playpen.server.PlaypenProxyConstants;
import io.quarkiverse.playpen.test.util.command.PlaypenCli;
import io.quarkiverse.playpen.utils.ProxyUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;

public class CliSessionTest {
    public static final int SERVICE_PORT = 9091;
    public static final int PROXY_PORT = 9092;
    public static final int CLIENT_API_PORT = 9093;

    public static AutoCloseable proxy;

    static HttpServer myService;

    static Vertx vertx;
    static HttpServer localService;

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
        localService = vertx.createHttpServer();
        localService.requestHandler(request -> {
            request.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/plain")
                    .end("local");
        }).listen(8080);

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
            ProxyUtils.await(1000, localService.close());
            proxy.close();
            ProxyUtils.await(1000, vertx.close());
        }
    }

    @Test
    public void testSession() throws Exception {
        PlaypenCli cli = new PlaypenCli()
                .executeAsync(
                        "local connect http://localhost:" + CLIENT_API_PORT
                                + " -who john --query=user=john -path /users/john");
        try {
            String found = cli.waitForStdout("Control-C", "[ERROR]");
            if (!found.equals("Control-C")) {
                throw new RuntimeException("Failed to start CLI");
            }
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
            cli.exit();
        }
        Thread.sleep(100);
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

package io.quarkiverse.playpen.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfigSpec;
import io.quarkiverse.playpen.test.util.command.PlaypenCli;
import io.quarkiverse.playpen.utils.ProxyUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;

public abstract class BaseCliLocalTest {
    public static String nodeHost;
    public static String greetingService;
    public static KubernetesClient client;
    public static Vertx vertx;
    public static HttpServer localService;

    @BeforeAll
    public static void init() {
        String defaultHost = "devcluster";
        if (System.getProperty("openshift") != null) {
            defaultHost = "apps-crc.testing";
        }
        nodeHost = System.getProperty("node.host", defaultHost);
        greetingService = nodeHost + ":30607";
        vertx = new VertxBuilder()
                .threadFactory(new VertxThreadFactory() {
                    public VertxThread newVertxThread(Runnable target, String name, boolean worker, long maxExecTime,
                            TimeUnit maxExecTimeUnit) {
                        return new VertxThread(target, "TEST-" + name, worker, maxExecTime, maxExecTimeUnit);
                    }
                }).init().vertx();
        localService = vertx.createHttpServer();
        localService.requestHandler(request -> {
            request.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/plain")
                    .end("local");
        }).listen(8080);
        client = new KubernetesClientBuilder().build();
    }

    @AfterAll
    public static void cleanup() {
        if (localService != null)
            ProxyUtils.await(1000, localService.close());
        if (vertx != null)
            ProxyUtils.await(1000, vertx.close());

    }

    public static PlaypenConfig createConfig(String configName) {
        PlaypenConfig config = new PlaypenConfig();
        config.getMetadata().setName(configName);
        config.setSpec(new PlaypenConfigSpec());
        config.getSpec().setLogLevel("DEBUG");
        return config;
    }

    public void test(String cmd) throws Exception {
        System.out.println("Testing playpen connected: " + "http://" + greetingService);
        given()
                .baseUri("http://" + greetingService)
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello"));

        System.out.println("playpen " + cmd);

        PlaypenCli cli = new PlaypenCli()
                .executeAsync(cmd);
        try {
            String found = cli.waitForStdout("Control-C", "[ERROR]", "Usage");
            if (!found.equals("Control-C")) {
                throw new RuntimeException("Failed to start CLI");
            }
        } catch (Exception e) {
            cli.exit();
            throw e;
        }
        System.out.println("Test local session");
        try {
            given()
                    .baseUri("http://" + greetingService)
                    .when().get("/hello")
                    .then()
                    .statusCode(200)
                    .body(containsString("local"));
        } finally {
            cli.exit();
        }
        Thread.sleep(100);
        System.out.println("Test after disconnection");
        given()
                .baseUri("http://" + greetingService)
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello"));
    }

}

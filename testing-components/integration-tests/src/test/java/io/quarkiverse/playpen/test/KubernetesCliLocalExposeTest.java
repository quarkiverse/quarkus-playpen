package io.quarkiverse.playpen.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfigSpec;
import io.quarkiverse.playpen.test.util.PlaypenUtil;
import io.quarkiverse.playpen.test.util.command.CommandExec;
import io.quarkiverse.playpen.test.util.command.PlaypenCli;
import io.quarkiverse.playpen.utils.ProxyUtils;
import io.restassured.RestAssured;
import io.restassured.config.ConnectionConfig;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;

@EnabledIfSystemProperty(named = "k8sit", matches = "true")
public class KubernetesCliLocalExposeTest {
    static String nodeHost;
    static String greetingService;
    private static CommandExec mvn;
    static KubernetesClient client;
    public static Vertx vertx;

    static HttpServer localService;

    @BeforeAll
    public static void init() {
        nodeHost = System.getProperty("node.host", "devcluster");
        //nodeHost = System.getProperty("node.host", "apps-crc.testing");
        greetingService = nodeHost + ":30607";
        /*
         * mvn = new CommandExec()
         * .workDir(System.getProperty("user.dir") + "/../greeting")
         * .executeAsync("mvn quarkus:dev");
         * try {
         * String wait = mvn.waitForStdout("Installed features", "ERROR");
         * if (!wait.equals("Installed features")) {
         * throw new RuntimeException("Failed to start maven");
         * }
         * } catch (Exception e) {
         * mvn.exit();
         * throw e;
         * }
         *
         */
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
        if (mvn != null) {
            mvn.exit();
        }
        if (localService != null)
            ProxyUtils.await(1000, localService.close());
        if (vertx != null)
            ProxyUtils.await(1000, vertx.close());

    }

    @Test
    public void testExposeByPortForward() throws Exception {
        System.out.println("---------- PORT FORWARD");
        String configName = "expose-none-auth-none";
        PlaypenConfig config = createConfig(configName);
        client.resource(config).create();
        Thread.sleep(100);

        try {
            PlaypenUtil.createPlaypen(client, "greeting", configName);
            String cmd = "local connect greeting -who bill -global";
            for (int i = 0; i < 3; i++) {
                test(cmd);
            }
            PlaypenUtil.deletePlaypen(client, "greeting");
        } finally {

            client.resource(config).delete();
            Thread.sleep(1000);

        }
    }

    @Test
    public void testExposeByIngressHost() throws Exception {
        System.out.println("---------- INGRESS HOST");
        String configName = "expose-ingress-host-auth-none";
        PlaypenConfig config = createConfig(configName);
        config.getSpec().setIngress(new PlaypenConfigSpec.PlaypenIngress());
        config.getSpec().getIngress().setHost("devcluster");
        client.resource(config).create();
        Thread.sleep(100);
        System.out.println("Creating playpen greeting");
        PlaypenUtil.createPlaypen(client, "greeting", config.getMetadata().getName());
        try {
            System.out.println("Waiting for ingress...");
            for (int i = 0; i < 50; i++) {
                Ingress ingress = client.network().v1().ingresses().withName("greeting-playpen").get();
                if (ingress == null)
                    continue;
                if (ingress.getStatus() == null)
                    continue;
                if (ingress.getStatus().getLoadBalancer() == null)
                    continue;
                Thread.sleep(100);
            }

            String cmd = "local connect http://" + nodeHost + "/greeting-playpen-it -who bill -global";
            test(cmd);
            Thread.sleep(100);
            PlaypenUtil.deletePlaypen(client, "greeting");
        } finally {
            client.resource(config).delete();
            Thread.sleep(1000);
        }
    }

    @Test
    public void testExposeByIngressDomain() throws Exception {
        System.out.println("---------- INGRESS Domain");
        String configName = "expose-ingress-domain-auth-none";
        PlaypenConfig config = createConfig(configName);
        config.getSpec().setIngress(new PlaypenConfigSpec.PlaypenIngress());
        config.getSpec().getIngress().setDomain("devcluster");
        client.resource(config).create();
        Thread.sleep(100);
        System.out.println("Creating playpen greeting");
        PlaypenUtil.createPlaypen(client, "greeting", config.getMetadata().getName());
        try {
            System.out.println("Waiting for ingress...");
            for (int i = 0; i < 50; i++) {
                Ingress ingress = client.network().v1().ingresses().withName("greeting-playpen").get();
                if (ingress == null)
                    continue;
                if (ingress.getStatus() == null)
                    continue;
                if (ingress.getStatus().getLoadBalancer() == null)
                    continue;
                Thread.sleep(100);
            }

            String cmd = "local connect http://greeting-playpen-it." + nodeHost + " -who bill -global";
            test(cmd);
            Thread.sleep(100);
            PlaypenUtil.deletePlaypen(client, "greeting");
        } finally {
            client.resource(config).delete();
            Thread.sleep(1000);
        }
    }

    private static PlaypenConfig createConfig(String configName) {
        PlaypenConfig config = new PlaypenConfig();
        config.getMetadata().setName(configName);
        config.setSpec(new PlaypenConfigSpec());
        config.getSpec().setLogLevel("DEBUG");
        return config;
    }

    public void test(String cmd) throws Exception {
        System.out.println("Testing playpen connected: " + "http://" + greetingService);
        var config = RestAssured.config()
                .connectionConfig(new ConnectionConfig().closeIdleConnectionsAfterEachResponse());
        given()
                .config(config)
                .baseUri("http://" + greetingService)
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello"));

        System.out.println("playpen " + cmd);

        PlaypenCli cli = new PlaypenCli()
                .executeAsync(cmd);
        try {
            String found = cli.waitForStdout("Control-C", "[ERROR]", "[WARN]");
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
                    .config(config)
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
                .config(config)
                .baseUri("http://" + greetingService)
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello"));
    }
}

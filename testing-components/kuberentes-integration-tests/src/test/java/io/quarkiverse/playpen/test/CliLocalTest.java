package io.quarkiverse.playpen.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.kubernetes.crds.Playpen;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfigSpec;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenSpec;
import io.quarkiverse.playpen.test.util.CommandExec;
import io.quarkiverse.playpen.test.util.PlaypenCli;
import io.restassured.RestAssured;
import io.restassured.config.ConnectionConfig;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;

@EnabledIfSystemProperty(named = "k8sit", matches = "true")
public class CliLocalTest {
    static String nodeHost;
    static String greetingService;
    private static CommandExec mvn;
    static KubernetesClient client;
    public static Vertx vertx;

    static HttpServer myService;

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
    }

    static void createPlaypen(String service, String config) throws Exception {
        Playpen playpen = new Playpen();
        playpen.getMetadata().setName(service);
        if (config != null) {
            playpen.setSpec(new PlaypenSpec());
            playpen.getSpec().setConfig(config);
        }
        client.resource(playpen).create();
        waitForPLaypen(service);
    }

    static void waitForPLaypen(String service) throws InterruptedException {
        // Waiting for stuff to be ready is just so unpredictable
        // have to put in huge sleeps or tests won't run
        Deployment deployment = null;
        for (int i = 0; i < 20; i++) {
            deployment = client.apps().deployments().withName(service + "-playpen").get();
            if (deployment != null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        Assertions.assertNotNull(deployment);
        System.out.println("Deployment ready...");
        Pod pod = null;
        for (int i = 0; i < 20; i++) {
            for (Pod p : client.pods().list().getItems()) {
                if (p.getMetadata().getName().startsWith("greeting-playpen")) {
                    pod = p;
                    break;
                }
            }
            if (pod != null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        Assertions.assertNotNull(pod);
        System.out.println("Waiting for pod to be ready");
        try {
            client.pods().resource(pod).waitUntilReady(20, TimeUnit.SECONDS);
            System.out.println("Pod is ready!");
        } catch (Exception e) {
            System.out.println("Pod not ready. Waiting until ready timed out...");
            pod = client.pods().withName(pod.getMetadata().getName()).get();
            if (pod.getStatus().toString().contains("ImagePullBackOff")) {
                throw new RuntimeException("Failed to start playpen pod: ImagePullBackOff");
            }
            System.out.println("Pod status: " + pod.getStatus());
            Thread.sleep(5000);
        }
    }

    static void deletePlaypen(String service) {
        try {
            System.out.println("Deleting playpen " + service);
            Pod pod = null;
            for (Pod p : client.pods().list().getItems()) {
                if (p.getMetadata().getName().startsWith("greeting-playpen")) {
                    pod = p;
                    break;
                }
            }
            String podName = pod == null ? "nada" : pod.getMetadata().getName();

            Playpen playpen = client.resources(Playpen.class).withName(service).get();
            //System.out.println("Delete Version: " + playpen.getVersion());
            client.resource(playpen).delete();
            Thread.sleep(100);
            System.out.println("waiting for deployment to terminate");
            for (int i = 0; i < 10; i++) {
                Deployment deployment = client.apps().deployments().withName(service + "-playpen").get();
                if (deployment == null) {
                    break;
                }
                Thread.sleep(1000);
            }
            System.out.println("waiting for pod to terminate");
            for (int i = 0; i < 30 && client.pods().withName(podName).get() != null; i++) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    @Test
    public void testExposeByPortForward() throws Exception {
        System.out.println("---------- PORT FORWARD");
        String configName = "expose-none-auth-none";
        PlaypenConfig config = createConfig(configName);
        client.resource(config).create();
        Thread.sleep(100);

        try {
            createPlaypen("greeting", configName);
            String cmd = "local connect greeting -who bill -global";
            for (int i = 0; i < 10; i++) {
                test(cmd);
            }
            deletePlaypen("greeting");
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
        createPlaypen("greeting", config.getMetadata().getName());
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
            deletePlaypen("greeting");
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

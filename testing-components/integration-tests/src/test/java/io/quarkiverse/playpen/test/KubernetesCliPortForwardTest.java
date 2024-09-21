package io.quarkiverse.playpen.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfigSpec;
import io.quarkiverse.playpen.test.util.PlaypenUtil;
import io.quarkiverse.playpen.test.util.command.CommandExec;
import io.quarkiverse.playpen.test.util.command.PlaypenCli;

@EnabledIfSystemProperty(named = "k8s", matches = "true")
public class KubernetesCliPortForwardTest {
    static String nodeHost;
    static KubernetesClient client;
    static String meetingService;

    @BeforeAll
    public static void init() {
        String defaultHost = "devcluster";
        if (System.getProperty("openshift") != null) {
            defaultHost = "apps-crc.testing";
        }
        nodeHost = System.getProperty("node.host", defaultHost);
        meetingService = nodeHost + ":30609";
        client = new KubernetesClientBuilder().build();
    }

    @Test
    public void testPortForwards() throws Exception {

        String configName = "expose-none-auth-none";
        PlaypenConfig config = createConfig(configName);
        client.resource(config).create();
        Thread.sleep(100);

        try {
            PlaypenUtil.createPlaypen(client, "meeting", configName);

            CommandExec mvn = new CommandExec()
                    .workDir(System.getProperty("user.dir") + "/../meeting")
                    .executeAsync(
                            "mvn quarkus:dev -Dgreeting.service=http://localhost:9090 -Dfarewell.service=http://localhost:9091");
            try {
                String wait = mvn.waitForStdout("Installed features", "ERROR");
                if (!wait.equals("Installed features")) {
                    throw new RuntimeException("Failed to start maven");
                }
            } catch (Exception e) {
                mvn.exit();
                throw e;
            }

            try {
                String cmd = "local connect meeting -who bill -global -pf greeting::9090 -pf farewell::9091";

                System.out.println("Testing playpen connected: " + "http://" + meetingService);
                given()
                        .baseUri("http://" + meetingService)
                        .when().get("/meet")
                        .then()
                        .statusCode(200)
                        .body(equalTo("Hello developer Goodbye developer cluster"));

                System.out.println("playpen " + cmd);
                PlaypenCli cli = new PlaypenCli()
                        .executeAsync(cmd);
                try {
                    String found = cli.waitForStdout("Control-C", "[ERROR]", "[WARN]", "Usage");
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
                            .baseUri("http://" + meetingService)
                            .when().get("/meet")
                            .then()
                            .statusCode(200)
                            .body(equalTo("Hello developer Goodbye developer"));
                } finally {
                    cli.exit();
                }
                Thread.sleep(100);
                System.out.println("Test after disconnection");
                given()
                        .baseUri("http://" + meetingService)
                        .when().get("/meet")
                        .then()
                        .statusCode(200)
                        .body(equalTo("Hello developer Goodbye developer cluster"));
            } finally {
                mvn.exit();
            }

        } finally {
            PlaypenUtil.deletePlaypen(client, "meeting");
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
}

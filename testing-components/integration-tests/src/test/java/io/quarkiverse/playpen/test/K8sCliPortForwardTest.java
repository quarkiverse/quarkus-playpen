package io.quarkiverse.playpen.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.test.util.PlaypenUtil;
import io.quarkiverse.playpen.test.util.command.CommandExec;
import io.quarkiverse.playpen.test.util.command.PlaypenCli;

@EnabledIfSystemProperty(named = "k8s", matches = "true")
public class K8sCliPortForwardTest extends BaseK8sTest {
    static String meetingService;

    @BeforeAll
    public static void setService() {
        meetingService = nodeHost + ":30609";

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
                            "mvn quarkus:dev");
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
                String cmd = "local connect meeting -who bill -hijack -pf greeting::9090";

                System.out.println("Testing playpen connected: " + "http://" + meetingService);
                given()
                        .baseUri("http://" + meetingService)
                        .when().get("/meet")
                        .then()
                        .statusCode(200)
                        .body(startsWith("<h1>Hello developer cluster</h1>"));

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
                            .baseUri("http://" + meetingService)
                            .when().get("/meet")
                            .then()
                            .statusCode(200)
                            .body(startsWith("<h1>Hello developer</h1>"));
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
                        .body(startsWith("<h1>Hello developer cluster</h1>"));
            } finally {
                mvn.exit();
            }

        } finally {
            PlaypenUtil.deletePlaypen(client, "meeting");
            client.resource(config).delete();
            Thread.sleep(1000);
        }
    }
}

package io.quarkiverse.playpen.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkiverse.playpen.test.util.CommandExec;
import io.quarkiverse.playpen.test.util.PlaypenCli;

@EnabledIfSystemProperty(named = "k8sit", matches = "true")
public class CliLocalTest {
    static String nodeHost;
    static String greetingService;
    private static CommandExec mvn;

    @BeforeAll
    public static void init() {
        nodeHost = System.getProperty("node.host", "192.168.130.11");
        greetingService = nodeHost + ":30607";
        mvn = new CommandExec()
                .workDir(System.getProperty("user.dir") + "/../greeting")
                .executeAsync("mvn quarkus:dev");
        try {
            mvn.waitForStdout("Installed features");
        } catch (Exception e) {
            mvn.exit();
            throw e;
        }
    }

    @AfterAll
    public static void cleanup() {
        if (mvn != null) {
            mvn.exit();
        }
    }

    @Test
    @Order(0)
    public void testBasicLocal() throws Exception {
        given()
                .baseUri("http://" + greetingService)
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello"));

        PlaypenCli cli = new PlaypenCli()
                .executeAsync("local connect greeting -who bill -global");
        try {
            cli.waitForStdout("Control-C");
        } catch (Exception e) {
            cli.exit();
            throw e;
        }
        try {
            given()
                    .baseUri("http://" + greetingService)
                    .when().get("/hello")
                    .then()
                    .statusCode(200)
                    .body(containsString("Greetings"));
        } finally {
            cli.exit();
        }
        given()
                .baseUri("http://" + greetingService)
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello"));
    }
}

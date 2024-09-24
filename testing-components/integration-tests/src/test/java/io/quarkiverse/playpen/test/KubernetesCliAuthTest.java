package io.quarkiverse.playpen.test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.fabric8.kubernetes.api.model.Secret;
import io.quarkiverse.playpen.kubernetes.crds.AuthenticationType;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.test.util.PlaypenUtil;
import io.quarkiverse.playpen.test.util.command.PlaypenCli;

@EnabledIfSystemProperty(named = "k8s", matches = "true")
public class KubernetesCliAuthTest extends BaseCliLocalTest {
    @Test
    public void testSecretAuth() throws Exception {
        System.out.println("---------- Secret Auth Test");
        String configName = "expose-none-auth-secret";
        PlaypenConfig config = createConfig(configName);
        config.getSpec().setAuthType(AuthenticationType.secret.name());
        client.resource(config).create();
        Thread.sleep(1000);

        try {
            PlaypenUtil.createPlaypen(client, "greeting", configName);
            Thread.sleep(2000);
            Secret secret = client.secrets().withName("greeting-playpen-auth").get();
            String encoded = secret.getData().get("password");
            String secretValue = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

            System.out.println("Test no secret specified...");
            String cmd = "local connect greeting -who bill -hijack";
            PlaypenCli cli = new PlaypenCli()
                    .executeAsync(cmd);
            try {

                String found = cli.waitForStdout("Could not authenticate", "Control-C", "[ERROR]", "[WARN]", "Usage");
                if (!found.equals("Could not authenticate")) {
                    throw new RuntimeException("Failed to start CLI");
                }
            } catch (Exception e) {
                throw e;
            } finally {
                cli.exit();
                Thread.sleep(1000);
            }
            System.out.println("Test with secret...");
            cmd = cmd + " -credentials " + secretValue;
            test(cmd);
        } finally {
            PlaypenUtil.deletePlaypen(client, "greeting");
            client.resource(config).delete();
            Thread.sleep(1000);

        }
    }
}

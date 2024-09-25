package io.quarkiverse.playpen.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkiverse.playpen.kubernetes.crds.AuthenticationType;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.test.util.PlaypenUtil;
import io.quarkiverse.playpen.test.util.command.PlaypenCli;

@EnabledIfSystemProperty(named = "openshift", matches = "true")
public class OpenshiftCliAuthTest extends K8sCliAuthTest {
    @Test
    public void testOpenshiftOAuth() throws Exception {
        System.out.println("---------- Openshift Auth Test");
        String configName = "expose-none-auth-openshift-pw";
        PlaypenConfig config = createConfig(configName);
        config.getSpec().setAuthType(AuthenticationType.openshiftBasicAuth.name());
        client.resource(config).create();
        Thread.sleep(1000);

        try {
            PlaypenUtil.createPlaypen(client, "greeting", configName);
            Thread.sleep(4000);

            System.out.println("Test no pw specified...");
            String cmd = "local connect greeting -who bill -hijack";
            PlaypenCli cli = new PlaypenCli()
                    .executeAsync(cmd);
            try {

                String found = cli.waitForStdout("Could not authenticate", "Control-C", "[ERROR]", "Usage");
                if (!found.equals("Could not authenticate")) {
                    throw new RuntimeException("Failed to start CLI");
                }
            } catch (Exception e) {
                throw e;
            } finally {
                cli.exit();
                Thread.sleep(1000);
            }
            System.out.println("Test with username password...");
            // Yes, I have checked in code with my password to my local crc instance :-p
            String user = System.getProperty("ocp.user", "kubeadmin");
            String pw = System.getProperty("ocp.pw", "xnfWZ-fLqZ6-z9ti8-vaDjx");
            cmd = cmd + " -credentials " + user + ":" + pw;
            test(cmd);
        } finally {
            PlaypenUtil.deletePlaypen(client, "greeting");
            client.resource(config).delete();
            Thread.sleep(1000);

        }
    }
}

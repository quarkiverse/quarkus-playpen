package io.quarkiverse.playpen.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkiverse.playpen.kubernetes.crds.ExposePolicy;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.test.util.PlaypenUtil;

@EnabledIfSystemProperty(named = "openshift", matches = "true")
public class OpenshiftCliLocalExposeTest extends BaseCliLocalTest {
    @Test
    public void testExposeByPortForward() throws Exception {
        System.out.println("---------- PORT FORWARD");
        String configName = "expose-none-auth-none";
        PlaypenConfig config = createConfig(configName);
        client.resource(config).create();
        Thread.sleep(100);

        try {
            PlaypenUtil.createPlaypen(client, "greeting", configName);
            Thread.sleep(4000);
            String cmd = "local connect greeting -who bill -hijack";
            test(cmd);
            // Test with no -who set that it defaults to username
            cmd = "local connect greeting -hijack";
            test(cmd);
        } finally {
            PlaypenUtil.deletePlaypen(client, "greeting");
            client.resource(config).delete();
            Thread.sleep(1000);

        }
    }

    @Test
    public void testExposeByRoute() throws Exception {
        System.out.println("---------- Route");
        String configName = "expose-route-auth-none";
        PlaypenConfig config = createConfig(configName);
        config.getSpec().setExposePolicy(ExposePolicy.route.name());
        client.resource(config).create();
        Thread.sleep(100);

        try {
            PlaypenUtil.createPlaypen(client, "greeting", configName);
            Thread.sleep(4000);
            String cmd = "local connect http://greeting-playpen-it." + nodeHost + " -who bill -hijack";
            test(cmd);
        } finally {
            PlaypenUtil.deletePlaypen(client, "greeting");
            client.resource(config).delete();
            Thread.sleep(1000);

        }
    }

    @Test
    public void testExposeBySecureRoute() throws Exception {
        System.out.println("---------- Secure Route");
        String configName = "expose-secure-route-auth-none";
        PlaypenConfig config = createConfig(configName);
        config.getSpec().setExposePolicy(ExposePolicy.secureRoute.name());
        client.resource(config).create();
        Thread.sleep(100);

        try {
            PlaypenUtil.createPlaypen(client, "greeting", configName);
            Thread.sleep(4000);
            String cmd = "local connect https://greeting-playpen-it." + nodeHost + " -who bill -hijack -trustCert";
            test(cmd);
        } finally {
            PlaypenUtil.deletePlaypen(client, "greeting");
            client.resource(config).delete();
            Thread.sleep(1000);

        }
    }
}

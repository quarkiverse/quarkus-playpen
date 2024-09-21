package io.quarkiverse.playpen.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfigSpec;
import io.quarkiverse.playpen.test.util.PlaypenUtil;

@EnabledIfSystemProperty(named = "k8s", matches = "true")
public class KubernetesCliLocalExposeTest extends BaseCliLocalTest {
    @Test
    public void testExposeByPortForward() throws Exception {
        System.out.println("---------- PORT FORWARD");
        String configName = "expose-none-auth-none";
        PlaypenConfig config = createConfig(configName);
        client.resource(config).create();
        Thread.sleep(100);

        try {
            PlaypenUtil.createPlaypen(client, "greeting", configName);
            Thread.sleep(2000);
            String cmd = "local connect greeting -who bill -global";
            for (int i = 0; i < 2; i++) {
                test(cmd);
            }
        } finally {
            PlaypenUtil.deletePlaypen(client, "greeting");
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
        config.getSpec().getIngress().setHost(nodeHost);
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
        } finally {
            PlaypenUtil.deletePlaypen(client, "greeting");
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
        config.getSpec().getIngress().setDomain(nodeHost);
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
        } finally {
            PlaypenUtil.deletePlaypen(client, "greeting");
            client.resource(config).delete();
            Thread.sleep(1000);
        }
    }

}

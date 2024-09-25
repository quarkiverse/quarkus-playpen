package io.quarkiverse.playpen.test;

import org.junit.jupiter.api.BeforeAll;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfig;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenConfigSpec;

abstract public class BaseK8sTest {
    protected static String nodeHost;
    protected static KubernetesClient client;

    @BeforeAll
    public static void init() {
        String defaultHost = "devcluster";
        if (System.getProperty("openshift") != null) {
            defaultHost = "apps-crc.testing";
        }
        nodeHost = System.getProperty("node.host", defaultHost);
        client = new KubernetesClientBuilder().build();
    }

    public static PlaypenConfig createConfig(String configName) {
        PlaypenConfig config = new PlaypenConfig();
        config.getMetadata().setName(configName);
        config.setSpec(new PlaypenConfigSpec());
        config.getSpec().setLogLevel("DEBUG");
        return config;
    }
}

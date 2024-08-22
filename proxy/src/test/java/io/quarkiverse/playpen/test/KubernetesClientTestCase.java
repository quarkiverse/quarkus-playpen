package io.quarkiverse.playpen.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public class KubernetesClientTestCase {
    //@Test
    public void testFindDeployment() {
        KubernetesClient client = new KubernetesClientBuilder().build();
        Deployment dep = client.apps().deployments().withName("demo-rest-service").get();
        Assertions.assertNotNull(dep);
        System.out.println(dep.getMetadata().getName());
        System.out.println(dep.getSpec().getSelector());
        dep.getSpec().getSelector().getMatchLabels().forEach((s, s2) -> System.out.println(s + ":" + s2));

        System.out.println("---------------");

        LabelSelector selector = new LabelSelector();
        Map<String, String> map = new HashMap<>();
        map.put("app.kubernetes.io/name", "demo-rest-service");
        map.put("app.kubernetes.io/version", "1.0.0-SNAPSHOT");
        selector.setMatchLabels(map);
        List<Deployment> list = client.apps().deployments().withLabelSelector(selector).list().getItems();
        for (Deployment dep2 : list) {
            System.out.println(dep2.getMetadata().getName());
        }

    }
}

package io.quarkiverse.playpen.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.client.PortForward;

public class PortForwardTest {

    @Test
    public void testConstruction() {
        PortForward pf = new PortForward("greeting");
        Assertions.assertEquals(PortForward.Type.service, pf.getType());
        Assertions.assertEquals("greeting", pf.getName());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertNull(pf.getNamespace());

        pf = new PortForward("service/greeting");
        Assertions.assertEquals(PortForward.Type.service, pf.getType());
        Assertions.assertEquals("greeting", pf.getName());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertNull(pf.getNamespace());

    }

    //@Test
    public void testPortForward() {
        PortForward pf = new PortForward("greeting-playpen");
        KubernetesClient client = new KubernetesClientBuilder().build();
        pf.forward(client, 0);
    }
}

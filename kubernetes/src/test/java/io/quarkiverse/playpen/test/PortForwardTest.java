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
        Assertions.assertEquals(PortForward.Type.unknown, pf.getType());
        Assertions.assertEquals("greeting", pf.getName());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertNull(pf.getNamespace());

        pf = new PortForward("service/greeting");
        Assertions.assertEquals(PortForward.Type.service, pf.getType());
        Assertions.assertEquals("greeting", pf.getName());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertNull(pf.getNamespace());

        pf = new PortForward("pod/greeting");
        Assertions.assertEquals(PortForward.Type.pod, pf.getType());
        Assertions.assertEquals("greeting", pf.getName());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertNull(pf.getNamespace());

        pf = new PortForward("samples/greeting");
        Assertions.assertEquals(PortForward.Type.unknown, pf.getType());
        Assertions.assertEquals("greeting", pf.getName());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertEquals("samples", pf.getNamespace());

        pf = new PortForward("pod/samples/greeting");
        Assertions.assertEquals(PortForward.Type.pod, pf.getType());
        Assertions.assertEquals("greeting", pf.getName());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertEquals("samples", pf.getNamespace());

        pf = new PortForward("service/samples/greeting");
        Assertions.assertEquals(PortForward.Type.service, pf.getType());
        Assertions.assertEquals("greeting", pf.getName());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertEquals("samples", pf.getNamespace());

    }

    //@Test
    public void testPortForward() {
        PortForward pf = new PortForward("greeting-playpen");
        KubernetesClient client = new KubernetesClientBuilder().build();
        pf.forward(client, 0);
    }
}

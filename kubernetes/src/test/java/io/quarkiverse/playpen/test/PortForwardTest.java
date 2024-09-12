package io.quarkiverse.playpen.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.client.PortForward;

public class PortForwardTest {

    @Test
    void testProblem() {
        PortForward pf = new PortForward("service/samples/greeting:8080");
        Assertions.assertEquals("service/samples/greeting:8080", pf.toString());
        Assertions.assertEquals(8080, pf.getPort());
        Assertions.assertEquals(0, pf.getLocalPort());

    }

    @Test
    public void testConstruction() {
        PortForward pf = new PortForward("greeting");
        Assertions.assertEquals("greeting", pf.toString());

        pf = new PortForward("service/greeting");
        Assertions.assertEquals("service/greeting", pf.toString());

        pf = new PortForward("pod/greeting");
        Assertions.assertEquals("pod/greeting", pf.toString());

        pf = new PortForward("samples/greeting");
        Assertions.assertEquals("samples/greeting", pf.toString());

        pf = new PortForward("pod/samples/greeting");
        Assertions.assertEquals("pod/samples/greeting", pf.toString());

        pf = new PortForward("service/samples/greeting");
        Assertions.assertEquals("service/samples/greeting", pf.toString());

        pf = new PortForward("service/samples/greeting:8080:9999");
        Assertions.assertEquals("service/samples/greeting:8080:9999", pf.toString());
        Assertions.assertEquals(8080, pf.getPort());
        Assertions.assertEquals(9999, pf.getLocalPort());

        pf = new PortForward("service/samples/greeting:8080");
        Assertions.assertEquals("service/samples/greeting:8080", pf.toString());
        Assertions.assertEquals(8080, pf.getPort());
        Assertions.assertEquals(0, pf.getLocalPort());

        pf = new PortForward("service/samples/greeting::9999");
        Assertions.assertEquals("service/samples/greeting::9999", pf.toString());
        Assertions.assertEquals(-1, pf.getPort());
        Assertions.assertEquals(9999, pf.getLocalPort());
    }

    @Test
    public void testPortForward() {
        PortForward pf = new PortForward("greeting");
        KubernetesClient client = new KubernetesClientBuilder().build();
        pf.forward(client, 0);
    }
}

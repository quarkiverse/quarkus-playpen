package io.quarkiverse.playpen.test.util;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.playpen.kubernetes.crds.Playpen;
import io.quarkiverse.playpen.kubernetes.crds.PlaypenSpec;

public class PlaypenUtil {
    public static void createPlaypen(KubernetesClient client, String service, String config) throws Exception {
        Playpen playpen = new Playpen();
        playpen.getMetadata().setName(service);
        if (config != null) {
            playpen.setSpec(new PlaypenSpec());
            playpen.getSpec().setConfig(config);
        }
        client.resource(playpen).create();
        waitForPlaypen(client, service);
    }

    public static void waitForPlaypen(KubernetesClient client, String service) throws Exception {
        // Waiting for stuff to be ready is just so unpredictable
        // have to put in huge sleeps or tests won't run
        Deployment deployment = null;
        for (int i = 0; i < 20; i++) {
            deployment = client.apps().deployments().withName(service + "-playpen").get();
            if (deployment != null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        String selector = deployment.getSpec().getSelector().getMatchLabels().get("run");
        Assertions.assertNotNull(selector);
        Assertions.assertNotNull(deployment);
        System.out.println("Deployment ready...");
        Pod pod = null;
        for (int i = 0; i < 20; i++) {
            for (Pod p : client.pods().withLabel("run", selector).list().getItems()) {
                if (p.getMetadata().getName().startsWith(service + "-playpen")) {
                    pod = p;
                    break;
                }
            }
            if (pod != null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        Assertions.assertNotNull(pod);
        System.out.println("Waiting for pod to be ready");
        try {
            client.pods().resource(pod).waitUntilReady(20, TimeUnit.SECONDS);
            System.out.println("Pod is ready!");
        } catch (Exception e) {
            System.out.println("Pod not ready. Waiting until ready timed out...");
            pod = client.pods().withName(pod.getMetadata().getName()).get();
            if (pod.getStatus().toString().contains("ImagePullBackOff")) {
                throw new RuntimeException("Failed to start playpen pod: ImagePullBackOff");
            }
            System.out.println("Pod status: " + pod.getStatus());
            Thread.sleep(5000);
        }
    }

    public static void deletePlaypen(KubernetesClient client, String service) {
        try {
            System.out.println("Deleting playpen " + service);
            Deployment deployment = client.apps().deployments().withName(service + "-playpen").get();
            String selector = deployment.getSpec().getSelector().getMatchLabels().get("run");

            Pod pod = null;
            for (Pod p : client.pods().withLabel("run", selector).list().getItems()) {
                if (p.getMetadata().getName().startsWith(service + "-playpen")) {
                    pod = p;
                    break;
                }
            }
            String podName = pod == null ? "nada" : pod.getMetadata().getName();

            Playpen playpen = client.resources(Playpen.class).withName(service).get();
            //System.out.println("Delete Version: " + playpen.getVersion());
            client.resource(playpen).delete();
            Thread.sleep(100);
            System.out.println("waiting for deployment to terminate");
            for (int i = 0; i < 10; i++) {
                deployment = client.apps().deployments().withName(service + "-playpen").get();
                if (deployment == null) {
                    break;
                }
                Thread.sleep(1000);
            }
            System.out.println("waiting for pod to terminate");
            for (int i = 0; i < 30 && client.pods().withName(podName).get() != null; i++) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
    }
}

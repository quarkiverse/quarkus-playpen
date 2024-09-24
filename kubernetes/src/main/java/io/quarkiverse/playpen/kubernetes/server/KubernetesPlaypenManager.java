package io.quarkiverse.playpen.kubernetes.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.playpen.kubernetes.client.KubernetesHostEndpoint;
import io.quarkiverse.playpen.server.PlaypenProxyConfig;
import io.quarkiverse.playpen.server.PlaypenProxyConstants;
import io.quarkiverse.playpen.server.RemotePlaypenManager;

public class KubernetesPlaypenManager implements RemotePlaypenManager {
    protected static final Logger log = Logger.getLogger(KubernetesPlaypenManager.class);
    protected final KubernetesClient client;
    protected final PlaypenProxyConfig config;

    public KubernetesPlaypenManager(KubernetesClient client, PlaypenProxyConfig config) {
        this.client = client;
        this.config = config;
    }

    private String getPodName(String who) {
        return config.service + "-playpen-" + who;
    }

    @Override
    public boolean exists(String who) {
        String name = getPodName(who);
        Pod pod = client.pods().withName(name).get();
        return pod != null;
    }

    @Override
    public void create(String who, boolean copyEnv) {
        createQuarkus(who, copyEnv);

    }

    public void createQuarkus(String who, boolean copyEnv) {
        Map<String, String> env = Map.of("QUARKUS_LAUNCH_DEVMODE", "true");

        create(who, config.remotePlaypenImage, config.remotePlaypenImagePolicy, copyEnv, env);
    }

    static ObjectMeta createMetadata(String name) {
        return new ObjectMetaBuilder()
                .withName(name)
                .withLabels(Map.of("app.kubernetes.io/name", name))
                .build();
    }

    private List<EnvVar> getCurrentEnv() {
        Service service = client.services().withName(config.serviceHost).get();
        LabelSelector selector = new LabelSelector();
        selector.setMatchLabels(service.getSpec().getSelector());
        List<Deployment> list = client.apps().deployments().withLabelSelector(selector).list().getItems();
        if (list == null || list.isEmpty()) {
            throw new RuntimeException("Origin deployment not found");
        }
        Deployment dep = list.get(0);
        if (dep.getSpec().getTemplate().getSpec().getContainers().size() > 1) {
            throw new RuntimeException("Deployment had more than one container");
        }
        return dep.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
    }

    public void create(String who, String image, String imagePullPolicy, boolean copyEnv, Map<String, String> env) {
        String name = getPodName(who);
        if (client.pods().withName(name).get() != null) {
            log.warn("Pod already exists.  Deleting it");
            client.pods().withName(name).delete();
            for (int i = 0; i < 30; i++) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {

                }
                if (client.pods().withName(name).get() == null) {
                    break;
                }
            }

        }
        String url = "http://" + config.service + "-playpen" + config.clientPathPrefix + PlaypenProxyConstants.REMOTE_API_PATH
                + "/" + who + "/_playpen_api" + PlaypenProxyConstants.DEPLOYMENT_ZIP_PATH;

        var container = new PodBuilder()
                .withMetadata(createMetadata(name))
                .withNewSpec()
                .addNewContainer()
                .withImage(image)
                .withImagePullPolicy(imagePullPolicy)
                .withName(name)
                .addNewEnv().withName("PLAYPEN_CODE_URL")
                .withValue(url).endEnv()
                .addNewPort().withName("http").withContainerPort(8080).withProtocol("TCP").endPort();
        if (env != null) {
            env.forEach((s, s2) -> {
                container.addNewEnv().withName(s).withValue(s2).endEnv();
            });
        }
        if (copyEnv) {
            List<EnvVar> curr = getCurrentEnv();
            if (curr != null) {
                container.addAllToEnv(curr);
            }
        }
        Pod pod = container.endContainer().endSpec().build();
        client.pods().resource(pod).create();
        client.pods().withName(name).waitUntilReady(1, TimeUnit.MINUTES);
    }

    @Override
    public String get(String who) {
        String name = getPodName(who);
        Pod pod = client.pods().withName(name).get();
        if (pod == null)
            return null;
        if (pod.getStatus() == null) {
            return null;
        }
        if (pod.getStatus().getPodIP() == null) {
            return null;
        }
        return pod.getStatus().getPodIP() + ":8080";
    }

    @Override
    public String getHost(String host) {
        KubernetesHostEndpoint binding = new KubernetesHostEndpoint(host);
        binding.locateBinding(client);
        return binding.getClusterHostname() + ":" + binding.getPort();
    }

    @Override
    public void delete(String who) {
        String podName = getPodName(who);
        log.debugv("Deleting pod {0}", podName);
        try {
            client.pods().withName(podName).delete();
            log.debugv("Deleted pod {0}", podName);
        } catch (Exception e) {
            log.error("Failed to delete", e);
        }
        for (int i = 0; i < 30 && client.pods().withName(podName).get() != null; i++) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}

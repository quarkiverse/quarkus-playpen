package io.quarkiverse.playpen.server;

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

public class KubernetesPlaypenManager implements RemotePlaypenManager {
    protected static final Logger log = Logger.getLogger(KubernetesPlaypenManager.class);
    protected final KubernetesClient client;
    protected final PlaypenProxyConfig config;

    public KubernetesPlaypenManager(KubernetesClient client, PlaypenProxyConfig config) {
        this.client = client;
        this.config = config;
    }

    private String getDeploymentName(String who) {
        return config.service + "-playpen-" + who;
    }

    @Override
    public boolean exists(String who) {
        String name = getDeploymentName(who);
        Pod pod = client.pods().withName(name).get();
        return pod != null;
    }

    @Override
    public void create(String who) {
        createQuarkus(who);

    }

    public void createQuarkus(String who) {
        Map<String, String> env = Map.of("QUARKUS_LAUNCH_DEVMODE", "true");

        create(who, config.remotePlaypenImage, config.remotePlaypenImagePolicy, true, env);
    }

    static ObjectMeta createMetadata(String name) {
        return new ObjectMetaBuilder()
                .withName(name)
                .withLabels(Map.of("app.kubernetes.io/name", name))
                .build();
    }

    private List<EnvVar> getCurrentEnv() {
        Service service = client.services().withName(config.service + "-origin").get();
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
        String name = getDeploymentName(who);

        var container = new PodBuilder()
                .withMetadata(createMetadata(name))
                .withNewSpec()
                .addNewContainer()
                .withImage(image)
                .withImagePullPolicy(imagePullPolicy)
                .withName(name)
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
        container.addNewEnv().withName("PLAYPEN_CODE_URL")
                .withValue("http://" + config.service + "-playpen" + PlaypenProxyConstants.DEPLOYMENT_ZIP_PATH);
        Pod pod = container.endContainer().endSpec().build();
        client.pods().resource(pod).serverSideApply();
        client.pods().withName(name).waitUntilReady(1, TimeUnit.MINUTES);
    }

    @Override
    public String get(String who) {
        String name = getDeploymentName(who);
        Pod pod = client.pods().withName(name).get();
        return pod.getStatus().getPodIP() + ":8080";
    }

    @Override
    public void delete(String who) {
        client.pods().withName(getDeploymentName(who)).delete();
    }
}

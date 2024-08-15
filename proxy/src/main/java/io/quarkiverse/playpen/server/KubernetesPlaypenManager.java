package io.quarkiverse.playpen.server;


import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ServiceResource;

import java.util.Map;

public class KubernetesPlaypenManager implements RemotePlaypenManager {
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
        ServiceResource<Service> serviceResource = client.services()
                .withName(name);
        Service service = serviceResource.get();
        return service != null;
    }

    @Override
    public void create(String who) {

    }
    static ObjectMeta createMetadata(String name) {
        return new ObjectMetaBuilder()
                .withName(name)
                .withLabels(Map.of("app.kubernetes.io/name", name))
                .build();
    }

    public void create(String who, String image, String imagePullPolicy, boolean copyEnv, Map<String, String> env) {
        String name = getDeploymentName(who);
        var container = new DeploymentBuilder()
                .withMetadata(createMetadata(name))
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                .withMatchLabels(Map.of("run", name))
                .endSelector()
                .withNewTemplate().withNewMetadata().addToLabels(Map.of("run", name)).endMetadata()
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
        Deployment deployment = container.endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        client.apps().deployments().resource(deployment).serverSideApply();
    }

    @Override
    public void delete(String who) {

    }
}

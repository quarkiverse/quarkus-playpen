package io.quarkiverse.playpen.kubernetes.client;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.quarkiverse.playpen.client.DefaultLocalPlaypenClientManager;
import io.quarkiverse.playpen.client.LocalPlaypenClientManager;
import io.quarkiverse.playpen.client.LocalPlaypenConnectionConfig;

public class KubernetesLocalPlaypenClientManager extends DefaultLocalPlaypenClientManager implements LocalPlaypenClientManager {
    final KubernetesClient client;
    private PortForward playpenForward;

    public KubernetesLocalPlaypenClientManager(LocalPlaypenConnectionConfig config, KubernetesClient client) {
        super(config);
        this.client = client;
    }

    public PortForward portForward() throws IllegalArgumentException {
        playpenForward = new PortForward(config.connection);
        playpenForward.setName(playpenForward.getName() + "-playpen");
        LocalPortForward forward = playpenForward.forward(client, 0);

        config.host = "localhost";
        config.port = forward.getLocalPort();
        return playpenForward;
    }

    public KubernetesClient getClient() {
        return client;
    }

    public PortForward getPlaypenForward() {
        return playpenForward;
    }
}

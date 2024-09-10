package io.quarkiverse.playpen.client;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;

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

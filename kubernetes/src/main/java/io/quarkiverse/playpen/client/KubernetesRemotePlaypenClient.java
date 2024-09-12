package io.quarkiverse.playpen.client;

import java.io.Closeable;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;

public class KubernetesRemotePlaypenClient extends RemotePlaypenClient implements Closeable {
    final KubernetesClient client;
    private PortForward playpenForward;
    private String kubeConnectionUrl;
    private int localPort = 0;

    public KubernetesRemotePlaypenClient(KubernetesClient client, RemotePlaypenConnectionConfig config) {
        super(config);
        this.client = client;
        if (config.connection == null) {
            throw new IllegalArgumentException("Play connection locatio not defined");
        }
        if (config.connection.startsWith("http")) {
            throw new IllegalArgumentException("Cannot use remote kubernetes playpen if connection is http");
        }
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public KubernetesClient getClient() {
        return client;
    }

    @Override
    public Boolean isSelfSigned() {
        return false;
    }

    @Override
    public String getBasePlaypenUrl() {
        return kubeConnectionUrl;
    }

    public boolean init() throws IllegalArgumentException {
        playpenForward = new PortForward(config.connection);
        playpenForward.setName(playpenForward.getName() + "-playpen");
        LocalPortForward forward = playpenForward.forward(client, localPort);
        kubeConnectionUrl = "http://localhost:" + forward.getLocalPort();

        return true;
    }

    public PortForward getPlaypenForward() {
        return playpenForward;
    }

    public void close() {
        super.close();
        if (playpenForward != null) {
            playpenForward.close();
        }
    }
}

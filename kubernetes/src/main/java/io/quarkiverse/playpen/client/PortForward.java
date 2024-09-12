package io.quarkiverse.playpen.client;

import java.io.Closeable;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.quarkiverse.playpen.utils.ProxyUtils;

public class PortForward extends KubernetesEndpoint implements Closeable {
    private static final Logger log = Logger.getLogger(PortForward.class);

    protected int localPort;
    protected LocalPortForward portForward;

    public PortForward(String location) throws IllegalArgumentException {
        super(location);
    }

    @Override
    protected void parsePort() {
        int idx = name.indexOf(':');
        if (idx > 0) {
            String tmp = name.substring(idx + 1);
            this.name = name.substring(0, idx);
            idx = tmp.indexOf(':');
            if (idx < 0) {
                port = Integer.parseInt(tmp);
            } else if (idx == 0) {
                tmp = tmp.substring(idx + 1);
                port = -1;
                if (!tmp.isEmpty()) {
                    localPort = Integer.parseInt(tmp);
                }
            } else {
                String portString = tmp.substring(0, idx);
                port = Integer.parseInt(portString);
                tmp = tmp.substring(idx + 1);
                if (!tmp.isEmpty()) {
                    localPort = Integer.parseInt(tmp);
                }
            }
        }
    }

    public LocalPortForward forward(KubernetesClient client, int localPort) throws IllegalArgumentException {
        this.localPort = localPort;
        return forward(client);
    }

    public LocalPortForward forward(KubernetesClient client) {
        locateBinding(client);
        if (pod != null) {
            setPortFromPod();
            this.portForward = client.pods().resource(pod).portForward(port, this.localPort);
        } else if (service != null) {
            if (port == -1) {
                if (service.getSpec().getPorts().size() != 1) {
                    String ports = "";
                    for (ServicePort p : service.getSpec().getPorts()) {
                        ports += " " + p.getTargetPort().getIntVal();
                    }
                    throw new IllegalArgumentException(
                            "Please choose a service port from [" + ports + " ] for endpoint " + this);
                }
                port = service.getSpec().getPorts().get(0).getTargetPort().getIntVal();
            }
            this.portForward = client.services().resource(service).portForward(port, this.localPort);
            this.localPort = this.portForward.getLocalPort();
        }
        return this.portForward;
    }

    @Override
    public void close() {
        ProxyUtils.safeClose(this.portForward);
    }

    public int getLocalPort() {
        return localPort;
    }

    public LocalPortForward getPortForward() {
        return portForward;
    }

    @Override
    public String toString() {
        String msg = (type == Type.unknown ? "" : type.name() + "/") +
                (namespace == null ? "" : namespace + "/") + name;
        if (localPort > 0) {
            msg = msg + (port == -1 ? ":" : ":" + port) + ":" + localPort;
        } else {
            msg = msg + (port == -1 ? "" : ":" + port);
        }
        return msg;
    }
}

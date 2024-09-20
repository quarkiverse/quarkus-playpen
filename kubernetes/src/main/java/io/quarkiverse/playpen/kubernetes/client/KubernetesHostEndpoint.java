package io.quarkiverse.playpen.kubernetes.client;

import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;

public class KubernetesHostEndpoint extends KubernetesEndpoint {
    public KubernetesHostEndpoint(String location) {
        super(location);
    }

    @Override
    public void locateBinding(KubernetesClient client) throws IllegalArgumentException {
        super.locateBinding(client);
        if (pod != null) {
            setPortFromPod();
        } else if (service != null) {
            if (port == -1) {
                if (service.getSpec().getPorts().size() != 1) {
                    String ports = "";
                    for (ServicePort p : service.getSpec().getPorts()) {
                        ports += " " + p.getPort();
                    }
                    throw new IllegalArgumentException("Please choose a port [" + ports + " ] for endpoint " + this);
                }
                port = service.getSpec().getPorts().get(0).getTargetPort().getIntVal();
            }
        }
    }

    protected void parsePort() {
        int idx = name.indexOf(':');
        if (idx > 0) {
            String tmp = name.substring(idx + 1);
            port = Integer.parseInt(tmp);
            this.name = name.substring(0, idx);
        }
    }

    @Override
    public String toString() {
        return (type == Type.unknown ? "" : type.name() + "/") +
                (namespace == null ? "" : namespace + "/") + name +
                (port == -1 ? "" : ":" + port);
    }
}

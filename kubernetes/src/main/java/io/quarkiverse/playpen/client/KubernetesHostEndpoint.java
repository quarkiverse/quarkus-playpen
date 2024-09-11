package io.quarkiverse.playpen.client;

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
                    throw new IllegalArgumentException("Please chose a port [" + ports + " ] for endpoint " + location);
                }
                port = service.getSpec().getPorts().get(0).getTargetPort().getIntVal();
            }
        }
    }

    @Override
    public String toString() {
        return "{" +
                "type=" + type +
                ", namespace='" + namespace + '\'' +
                ", name='" + name + '\'' +
                ", port=" + port +
                '}';
    }
}

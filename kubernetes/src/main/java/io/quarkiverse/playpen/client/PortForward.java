package io.quarkiverse.playpen.client;

import java.io.Closeable;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.quarkiverse.playpen.utils.ProxyUtils;

public class PortForward extends KubernetesEndpoint implements Closeable {
    private static final Logger log = Logger.getLogger(PortForward.class);

    protected LocalPortForward portForward;

    public PortForward(String location) throws IllegalArgumentException {
        super(location);
    }

    public LocalPortForward forward(KubernetesClient client, int localPort) throws IllegalArgumentException {
        locateBinding(client);
        if (pod != null) {
            setPortFromPod();
            this.portForward = client.pods().resource(pod).portForward(port, localPort);
        } else if (service != null) {
            if (port == -1) {
                if (service.getSpec().getPorts().size() != 1) {
                    String ports = "";
                    for (ServicePort p : service.getSpec().getPorts()) {
                        ports += " " + p.getTargetPort().getIntVal();
                    }
                    throw new IllegalArgumentException("Please choose a port [" + ports + " ] for endpoint " + location);
                }
                port = service.getSpec().getPorts().get(0).getTargetPort().getIntVal();
            }
            this.portForward = client.services().resource(service).portForward(port, localPort);
        }
        return this.portForward;
    }

    @Override
    public void close() {
        ProxyUtils.safeClose(this.portForward);
    }

    public LocalPortForward getPortForward() {
        return portForward;
    }

    @Override
    public String toString() {
        return "{" +
                "type=" + type +
                ", name='" + name + '\'' +
                (namespace == null ? "" : " , namespace='" + namespace + '\'') +
                ", port=" + port +
                (portForward == null ? "" : ", localPort=" + portForward.getLocalPort()) +
                '}';
    }
}

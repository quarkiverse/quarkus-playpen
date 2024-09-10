package io.quarkiverse.playpen.client;

import java.io.Closeable;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.quarkiverse.playpen.utils.ProxyUtils;

public class PortForward implements Closeable {
    private static final Logger log = Logger.getLogger(PortForward.class);

    public enum Type {
        pod,
        service
    }

    private final String location;
    private String name;
    private String namespace;
    private int port = -1;
    private Type type;
    private LocalPortForward portForward;

    public PortForward(String location) throws IllegalArgumentException {
        this.location = location;
        String[] tokens = location.split("/");
        if (tokens.length == 1) {
            type = Type.service;
            name = tokens[0];
        } else if (tokens.length == 2) {
            if (tokens[0].equals("service")) {
                type = Type.service;
                name = tokens[1];
            } else if (tokens[0].equals("pod")) {
                type = Type.pod;
                name = tokens[1];
            } else {
                namespace = tokens[0];
                name = tokens[1];
            }
        } else if (tokens.length == 3) {
            type = Type.valueOf(tokens[0]);
            namespace = tokens[1];
            name = tokens[2];
        } else {
            throw new IllegalArgumentException("Unknown port forward type: " + location);
        }
        int idx = name.indexOf(':');
        if (idx > 0) {
            String tmp = name.substring(idx + 1);
            port = Integer.parseInt(tmp);
            this.name = name.substring(0, idx);
        }
    }

    public LocalPortForward forward(KubernetesClient client, int localPort) throws IllegalArgumentException {
        log.debugv("Attempting to forward {0}", this.toString());
        if (type == Type.pod) {
            Pod pod = null;
            if (namespace == null) {
                pod = client.pods().withName(name).get();
            } else {
                pod = client.pods().inNamespace(namespace).withName(name).get();
            }
            if (pod == null) {
                throw new IllegalArgumentException("Could not find pod: " + location);
            }
            if (port == -1) {
                String ports = "";
                int count = 0;
                for (Container container : pod.getSpec().getContainers()) {
                    for (ContainerPort cp : container.getPorts()) {
                        if (cp.getContainerPort() != null) {
                            port = cp.getContainerPort();
                            count++;
                            ports += " " + port;
                        }
                    }
                }
                if (count > 1) {
                    throw new IllegalArgumentException("Please chose a port [" + ports + " ] for " + location);

                }
            }
            log.debugv("pod forward {0}", this.toString());
            this.portForward = client.pods().resource(pod).portForward(port, localPort);
        } else if (type == Type.service) {
            Service service = null;
            if (namespace == null) {
                service = client.services().withName(name).get();
            } else {
                service = client.services().inNamespace(namespace).withName(name).get();
            }
            if (service == null) {
                throw new IllegalArgumentException("Could not find service: " + location);
            }
            if (port == -1) {
                if (service.getSpec().getPorts().size() != 1) {
                    String ports = "";
                    for (ServicePort p : service.getSpec().getPorts()) {
                        ports += " " + p.getTargetPort().getIntVal();
                    }
                    throw new IllegalArgumentException("Please chose a port [" + ports + " ] for " + location);
                }
                port = service.getSpec().getPorts().get(0).getTargetPort().getIntVal();
            }
            log.debugv("service forward {0}", this.toString());
            this.portForward = client.services().resource(service).portForward(port, localPort);

        }
        return this.portForward;
    }

    @Override
    public void close() {
        ProxyUtils.safeClose(this.portForward);
    }

    public String getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public int getPort() {
        return port;
    }

    public Type getType() {
        return type;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public LocalPortForward getPortForward() {
        return portForward;
    }

    @Override
    public String toString() {
        return "{" +
                ", type=" + type +
                ", name='" + name + '\'' +
                (namespace == null ? "" : " , namespace='" + namespace + '\'') +
                ", port=" + port +
                (portForward == null ? "" : ", localPort=" + portForward.getLocalPort()) +
                '}';
    }
}

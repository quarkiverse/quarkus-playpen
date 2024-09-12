package io.quarkiverse.playpen.client;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;

public abstract class KubernetesEndpoint {
    private static final Logger log = Logger.getLogger(KubernetesEndpoint.class);

    public enum Type {
        pod,
        service,
        unknown
    }

    protected final String endpoint;
    protected String name;
    protected String namespace;
    protected int port = -1;
    protected Type type = Type.unknown;
    protected Pod pod;
    protected Service service;
    protected String clusterHostname;

    public KubernetesEndpoint(String endpoint) {
        this.endpoint = endpoint;
        String[] tokens = endpoint.split("/");
        if (tokens.length == 1) { // <name>
            name = tokens[0];
        } else if (tokens.length == 2) {
            if (tokens[0].equals("service")) {
                type = Type.service;
                name = tokens[1];
            } else if (tokens[0].equals("pod")) {
                type = Type.pod;
                name = tokens[1];
            } else { // <name>/<namespace>
                namespace = tokens[0];
                name = tokens[1];
            }
        } else if (tokens.length == 3) { // pod|service/<name>/<namespace>
            type = Type.valueOf(tokens[0]);
            namespace = tokens[1];
            name = tokens[2];
        } else {
            throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        }
        parsePort();
    }

    protected abstract void parsePort();

    protected void setPortFromPod() {
        if (pod == null)
            return;
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
                throw new IllegalArgumentException("Please choose a container port from [" + ports + " ] for " + this);
            }
        }
    }

    public void locateBinding(KubernetesClient client) throws IllegalArgumentException {
        switch (type) {
            case pod:
                setPod(client);
                break;
            case service:
                setService(client);
                break;
            case unknown:
                setPod(client);
                if (pod == null)
                    setService(client);
                break;
        }
        if (clusterHostname == null) {
            throw new IllegalArgumentException("Could not resolve endpoint: " + this);

        }
    }

    private void setService(KubernetesClient client) {
        if (namespace == null) {
            service = client.services().withName(name).get();
            if (service != null) {
                clusterHostname = name;
                type = Type.service;
            }
        } else {
            service = client.services().inNamespace(namespace).withName(name).get();
            if (service != null) {
                clusterHostname = name + "." + namespace;
                type = Type.service;
            }
        }
    }

    private void setPod(KubernetesClient client) {
        if (namespace == null) {
            pod = client.pods().withName(name).get();
        } else {
            pod = client.pods().inNamespace(namespace).withName(name).get();
        }
        if (pod != null) {
            clusterHostname = pod.getStatus().getPodIP();
            type = Type.pod;
        }
    }

    public String getClusterHostname() {
        return clusterHostname;
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

}

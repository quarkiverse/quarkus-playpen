package io.quarkiverse.playpen.kubernetes.crds;

public enum ExposePolicy {
    none,
    route,
    secureRoute,
    nodePort,
    ingress
}

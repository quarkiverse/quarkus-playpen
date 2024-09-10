package io.quarkiverse.playpen.kubernetes.crds;

public enum ExposePolicy {
    defaultPolicy,
    manual,
    route,
    secureRoute,
    nodePort,
    ingress
}

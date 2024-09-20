package io.quarkiverse.playpen.kubernetes.crds;

import java.util.Map;

public class PlaypenConfigSpec {
    public static class PlaypenIngress {
        private String domain;
        private String host;
        private Map<String, String> annotations;

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(Map<String, String> annotations) {
            this.annotations = annotations;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }
    }

    public static class PlaypenRoute {
        private Map<String, String> annotations;

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(Map<String, String> annotations) {
            this.annotations = annotations;
        }

    }

    private String authType;
    private Integer pollTimeoutSeconds;
    private Integer idleTimeoutSeconds;
    private String logLevel;
    private PlaypenIngress ingress;
    private PlaypenRoute route;
    private PlaypenRoute secureRoute;
    /**
     * manual
     * ingress
     * route
     * secure-route
     * nodePort;
     */
    private String exposePolicy;

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getExposePolicy() {
        return exposePolicy;
    }

    public void setExposePolicy(String exposePolicy) {
        this.exposePolicy = exposePolicy;
    }

    public Integer getPollTimeoutSeconds() {
        return pollTimeoutSeconds;
    }

    public Integer getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(Integer idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public void setPollTimeoutSeconds(Integer pollTimeoutSeconds) {
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }

    public PlaypenIngress getIngress() {
        return ingress;
    }

    public void setIngress(PlaypenIngress ingress) {
        this.ingress = ingress;
    }

    public PlaypenRoute getRoute() {
        return route;
    }

    public void setRoute(PlaypenRoute route) {
        this.route = route;
    }

    public PlaypenRoute getSecureRoute() {
        return secureRoute;
    }

    public void setSecureRoute(PlaypenRoute secureRoute) {
        this.secureRoute = secureRoute;
    }

    public AuthenticationType toAuthenticationType() {
        if (authType == null)
            return AuthenticationType.secret;
        return AuthenticationType.valueOf(authType);
    }

    public ExposePolicy toExposePolicy() {
        if (exposePolicy == null && ingress != null) {
            return ExposePolicy.ingress;
        }
        if (exposePolicy == null && route != null) {
            return ExposePolicy.route;
        }
        if (exposePolicy == null && secureRoute != null) {
            return ExposePolicy.route;
        }
        if (exposePolicy == null) {
            return ExposePolicy.none;
        }
        return ExposePolicy.valueOf(exposePolicy);
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

}

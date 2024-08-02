package io.quarkiverse.playpen.operator;

public class PlaypenSpec {
    private String config;
    private String configNamespace;
    private Integer nodePort;
    private String logLevel;

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public Integer getNodePort() {
        return nodePort;
    }

    public void setNodePort(Integer nodePort) {
        this.nodePort = nodePort;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public String getConfigNamespace() {
        return configNamespace;
    }

    public void setConfigNamespace(String configNamespace) {
        this.configNamespace = configNamespace;
    }
}
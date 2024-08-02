package io.quarkiverse.playpen.server;

public class ServiceConfig {
    private String name;
    private String host;
    private int port;
    private boolean ssl;

    public ServiceConfig() {
    }

    public ServiceConfig(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
    }

    public ServiceConfig(String name, String host, int port, boolean ssl) {
        this.name = name;
        this.host = host;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }
}

package io.quarkiverse.playpen.server;

public class PlaypenProxyConfig {
    public String service;
    public String serviceHost;
    public int servicePort;
    public boolean ssl;
    public long idleTimeout = 60000;
    public long defaultPollTimeout = 5000;
    public long timerPeriod = 1000;
    public String clientPathPrefix = "";
    public String version = "UNKNOWN";
}

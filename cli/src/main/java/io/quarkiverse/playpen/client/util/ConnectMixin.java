package io.quarkiverse.playpen.client.util;

import static picocli.CommandLine.Help.Visibility.NEVER;

import java.util.List;

import io.quarkiverse.playpen.client.BasePlaypenConnectionConfig;
import picocli.CommandLine;

public class ConnectMixin {
    @CommandLine.Option(names = { "-w", "--w", "-who", "--who" }, required = true)
    public String who;

    @CommandLine.Option(names = { "-q", "--q",
            "-query", "--query" }, description = "route by query param")
    public List<String> queries;

    @CommandLine.Option(names = { "-header",
            "--header" }, description = "route by header")
    public List<String> headers;

    @CommandLine.Option(names = { "-p", "--p",
            "-path", "--path" }, description = "route by path prefix")
    public List<String> paths;

    @CommandLine.Option(names = { "-ip", "--ip", "-clientIp",
            "--clientIp" }, arity = "0..1", fallbackValue = "UNSET", description = "route by client ip")
    public String clientIp;

    @CommandLine.Option(names = { "-c", "--c",
            "-credentials", "--credentials" }, description = "user:password or secret", showDefaultValue = NEVER)
    public String credentials;

    @CommandLine.Option(names = {
            "-trustCert", "--trustCert" }, description = "Trust any connection https cert")
    public boolean trustCert;

    @CommandLine.Option(names = {
            "-hijack", "--hijack" }, defaultValue = "false", description = "route all requests to playpen")
    public boolean hijack;

    @CommandLine.Parameters(index = "0", description = "location of playpen server")
    public String uri;

    public void setConfig(BasePlaypenConnectionConfig config) {
        config.connection = uri;
        config.paths = paths;
        config.headers = headers;
        config.queries = queries;
        config.hijack = hijack;
        config.trustCert = trustCert;
        config.credentials = credentials;
        config.useClientIp = clientIp != null;
        config.who = who;
        if (!"UNSET".equals(clientIp)) {
            config.clientIp = clientIp;
        }

    }
}

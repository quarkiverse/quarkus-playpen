package io.quarkiverse.playpen.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class PlaypenConnectionConfig {
    public String host;
    public int port;
    public boolean ssl;
    public List<String> headers;
    public List<String> paths;
    public List<String> queries;
    public String error;
    public boolean useClientIp;
    public String clientIp;
    public String credentials;
    public String prefix;
    public boolean isGlobal;
    public boolean trustCert;

    public static PlaypenConnectionConfig fromUri(String uriString) {
        PlaypenConnectionConfig playpen = new PlaypenConnectionConfig();
        URI uri = null;
        try {
            uri = new URI(uriString);
        } catch (Exception e) {
            playpen.error = "playpen URI value is bad";
            return playpen;
        }
        playpen.host = uri.getHost();
        playpen.ssl = "https".equalsIgnoreCase(uri.getScheme());
        playpen.port = uri.getPort() == -1 ? (playpen.ssl ? 443 : 80) : uri.getPort();

        if (uri.getQuery() != null) {
            for (String pair : uri.getQuery().split("&")) {
                int idx = pair.indexOf("=");
                String key = pair.substring(0, idx);
                String value = idx == -1 ? null : pair.substring(idx + 1);
                if ("query".equals(key)) {
                    if (playpen.queries == null)
                        playpen.queries = new ArrayList<>();
                    playpen.queries.add(value);
                } else if ("path".equals(key)) {
                    if (playpen.paths == null)
                        playpen.paths = new ArrayList<>();
                    playpen.paths.add(value);
                } else if ("header".equals(key)) {
                    if (playpen.headers == null)
                        playpen.headers = new ArrayList<>();
                    playpen.headers.add(value);
                } else if ("clientIp".equals(key)) {
                    playpen.useClientIp = true;
                    playpen.clientIp = value;
                } else if ("global".equals(key)) {
                    if (value == null)
                        value = "true";
                    if ("true".equals(value)) {
                        playpen.isGlobal = true;
                    }
                }
            }
        }
        if (uri.getRawPath() != null) {
            playpen.prefix = uri.getRawPath();
        }
        return playpen;

    }

    @Override
    public String toString() {
        return "PlaypenConnectionConfig{" +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", ssl=" + ssl +
                ", headers=" + headers +
                ", paths=" + paths +
                ", queries=" + queries +
                ", error='" + error + '\'' +
                ", useClientIp=" + useClientIp +
                ", clientIp='" + clientIp + '\'' +
                ", credentials='" + credentials + '\'' +
                ", prefix='" + prefix + '\'' +
                '}';
    }
}

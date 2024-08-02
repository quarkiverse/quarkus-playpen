package io.quarkiverse.playpen.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class PlaypenConnectionConfig {
    public String who;
    public String host;
    public int port;
    public boolean ssl;
    public List<String> headers;
    public List<String> paths;
    public List<String> queries;
    public String session;
    public String error;
    public boolean useClientIp;
    public String clientIp;
    public String credentials;
    public String prefix;

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

        boolean needSession = false;
        if (uri.getQuery() != null) {
            for (String pair : uri.getQuery().split("&")) {
                int idx = pair.indexOf("=");
                String key = pair.substring(0, idx);
                String value = idx == -1 ? null : pair.substring(idx + 1);
                if ("session".equals(key)) {
                    playpen.session = value;
                } else if ("who".equals(key)) {
                    playpen.who = value;
                } else if ("query".equals(key)) {
                    if (playpen.queries == null)
                        playpen.queries = new ArrayList<>();
                    playpen.queries.add(value);
                    needSession = true;
                } else if ("path".equals(key)) {
                    if (playpen.paths == null)
                        playpen.paths = new ArrayList<>();
                    playpen.paths.add(value);
                    needSession = true;
                } else if ("header".equals(key)) {
                    if (playpen.headers == null)
                        playpen.headers = new ArrayList<>();
                    playpen.headers.add(value);
                    needSession = true;
                } else if ("clientIp".equals(key)) {
                    playpen.useClientIp = true;
                    playpen.clientIp = value;
                }
            }
        }
        if (uri.getRawPath() != null) {
            playpen.prefix = uri.getRawPath();
        }
        if (playpen.who == null) {
            playpen.error = "playpen uri is missing who parameter";
        }
        if (needSession && playpen.session == null) {
            playpen.error = "playpen uri is missing session parameter";
        }
        return playpen;

    }

    @Override
    public String toString() {
        return "PlaypenConnectionConfig{" +
                "who='" + who + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", ssl=" + ssl +
                ", headers=" + headers +
                ", paths=" + paths +
                ", queries=" + queries +
                ", session='" + session + '\'' +
                ", error='" + error + '\'' +
                ", useClientIp=" + useClientIp +
                ", clientIp='" + clientIp + '\'' +
                ", credentials='" + credentials + '\'' +
                ", prefix='" + prefix + '\'' +
                '}';
    }
}

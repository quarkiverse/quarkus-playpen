package io.quarkiverse.playpen.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class LocalPlaypenConnectionConfig extends BasePlaypenConnectionConfig {
    public String host;
    public int port = -1;
    public boolean ssl;
    public String prefix;

    public static LocalPlaypenConnectionConfig fromCli(String cli) {
        return fromCli(cli, null);
    }

    public static LocalPlaypenConnectionConfig fromCli(String cli, BiConsumer<String, List<String>> extension) {
        LocalPlaypenConnectionConfig config = new LocalPlaypenConnectionConfig();
        parse(config, cli, extension);
        if (config.error != null) {
            return config;
        } else {
            setHttpLocation(config);
        }
        return config;
    }

    protected static void setHttpLocation(LocalPlaypenConnectionConfig playpen) {
        String target = playpen.connection;
        if (target.startsWith("http")) {
            String uriString = target;
            URI uri = null;
            try {
                uri = new URI(uriString);
            } catch (Exception e) {
                playpen.error = "playpen URI value is bad";
            }
            playpen.host = uri.getHost();
            playpen.ssl = "https".equalsIgnoreCase(uri.getScheme());
            playpen.port = uri.getPort() == -1 ? (playpen.ssl ? 443 : 80) : uri.getPort();
            if (uri.getRawPath() != null) {
                playpen.prefix = uri.getRawPath();
            }
        }

    }

    public static LocalPlaypenConnectionConfig fromUri(String uriString) {
        LocalPlaypenConnectionConfig playpen = new LocalPlaypenConnectionConfig();
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

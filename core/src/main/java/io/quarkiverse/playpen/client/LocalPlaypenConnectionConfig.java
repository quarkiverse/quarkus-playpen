package io.quarkiverse.playpen.client;

import java.net.URI;
import java.util.List;
import java.util.function.BiConsumer;

public class LocalPlaypenConnectionConfig extends BasePlaypenConnectionConfig {
    public String host;
    public int port = -1;
    public boolean ssl;
    public String prefix;
    public boolean onPoll;

    public static LocalPlaypenConnectionConfig fromCli(String cli) {
        LocalPlaypenConnectionConfig config = new LocalPlaypenConnectionConfig();
        return fromCli(config, cli, null);
    }

    public static LocalPlaypenConnectionConfig fromCli(LocalPlaypenConnectionConfig config, String cli) {
        return fromCli(config, cli, null);
    }

    public static LocalPlaypenConnectionConfig fromCli(LocalPlaypenConnectionConfig config, String cli,
            BiConsumer<String, List<String>> extension) {
        parse(config, cli, (key, val) -> {
            if (key.equals("onPoll")) {
                config.onPoll = val.isEmpty() || val.get(0).equals("true");
            } else if (extension != null) {
                extension.accept(key, val);
            }
        });
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

    @Override
    public String connectionQueryParams() {
        String queryParams = super.connectionQueryParams();
        if (onPoll) {
            queryParams = addQueryParam(queryParams, "onPoll=true");
        }
        return queryParams;
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

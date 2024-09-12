package io.quarkiverse.playpen.client;

import java.util.List;
import java.util.function.BiConsumer;

public class RemotePlaypenConnectionConfig extends BasePlaypenConnectionConfig {
    public String host;
    public Boolean cleanup;

    @Override
    public String connectionQueryParams() {
        String queryParams = super.connectionQueryParams();
        if (host != null) {
            queryParams = addQueryParam(queryParams, "host=" + host);
        }
        if (cleanup != null) {
            queryParams = addQueryParam(queryParams, "cleanup=" + cleanup.booleanValue());
        }
        return queryParams;
    }

    public static RemotePlaypenConnectionConfig fromCli(String cli) {
        return fromCli(new RemotePlaypenConnectionConfig(), cli, null);
    }

    public static RemotePlaypenConnectionConfig fromCli(RemotePlaypenConnectionConfig config, String cli) {
        return fromCli(config, cli, null);
    }

    public static RemotePlaypenConnectionConfig fromCli(RemotePlaypenConnectionConfig config, String cli,
            BiConsumer<String, List<String>> extension) {
        parse(config, cli, (key, val) -> {
            if (key.equals("host")) {
                if (!val.isEmpty())
                    config.host = val.get(0);
            } else if (key.equals("cleanup")) {
                if (val.isEmpty()) {
                    config.cleanup = true;
                } else {
                    config.cleanup = "true".equalsIgnoreCase(val.get(0));
                }
            } else if (extension != null) {
                extension.accept(key, val);
            }
        });
        return config;
    }
}

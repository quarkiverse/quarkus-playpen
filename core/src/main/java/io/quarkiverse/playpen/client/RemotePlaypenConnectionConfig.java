package io.quarkiverse.playpen.client;

import java.util.ArrayList;
import java.util.List;

public class RemotePlaypenConnectionConfig {
    public String host;
    public List<String> headers;
    public List<String> paths;
    public List<String> queries;
    public boolean useClientIp;
    public String clientIp;
    public boolean global;

    /**
     * from name value pairs separated by ';'
     *
     * @param string
     * @return
     */
    public static RemotePlaypenConnectionConfig fromNameValue(String string) {
        RemotePlaypenConnectionConfig playpen = new RemotePlaypenConnectionConfig();
        for (String pair : string.split(";")) {
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
            } else if ("host".equals(key)) {
                playpen.host = value;
            } else if ("globalSession".equals(key)) {
                playpen.global = true;
            }
        }
        return playpen;
    }
}

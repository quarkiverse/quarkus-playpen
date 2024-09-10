package io.quarkiverse.playpen.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class BasePlaypenConnectionConfig {
    public String connection;
    public List<String> headers;
    public List<String> paths;
    public List<String> queries;
    public String error;
    public boolean useClientIp;
    public String clientIp;
    public String credentials;
    public boolean isGlobal;
    public boolean trustCert;
    public String who;

    public static String addQueryParam(String curr, String add) {
        if (curr == null) {
            return "?" + add;
        } else {
            return curr + "&" + add;
        }
    }

    public String connectionQueryParams() {
        String queryParams = null;
        if (queries != null) {
            for (String query : queries) {
                queryParams = addQueryParam(queryParams, "query=" + query);
            }
        }
        if (headers != null) {
            for (String header : headers) {
                queryParams = addQueryParam(queryParams, "header=" + header);
            }
        }
        if (paths != null) {
            for (String path : paths) {
                queryParams = addQueryParam(queryParams, "path=" + path);
            }
        }
        if (isGlobal) {
            queryParams = addQueryParam(queryParams, "global=true");
        }
        if (useClientIp) {
            String param = "clientIp";
            if (clientIp != null) {
                param = param + "=" + clientIp;
            }
            queryParams = addQueryParam(queryParams, param);
        }
        return queryParams;
    }

    protected static Map<String, List<String>> cliParams(String[] tokens, int i) {

        Map<String, List<String>> params = new HashMap<>();
        for (; i < tokens.length; i++) {
            if (tokens[i].startsWith("--")) {
                if (tokens[i].length() < 3)
                    continue;
                int idx = tokens[i].indexOf('=');
                if (idx == 3)
                    continue; // '=' is first char

                String name = null;
                List<String> vals = new ArrayList<>();
                if (idx < 0) {
                    name = tokens[i].substring(2);
                } else {
                    name = tokens[i].substring(2, idx);
                    String val = tokens[i].substring(idx + 1);
                    for (String v : val.split(",")) {
                        vals.add(v);
                    }
                }
                params.put(name, vals);
            } else if (tokens[i].startsWith("-")) {
                if (tokens[i].length() < 2)
                    continue;
                String name = tokens[i].substring(1);
                List<String> vals = new ArrayList<>();
                params.put(name, vals);
                if (i + 1 == tokens.length)
                    continue;
                String val = tokens[++i];
                for (String v : val.split(",")) {
                    vals.add(v);
                }
            }
        }
        return params;
    }

    protected static void parse(BasePlaypenConnectionConfig playpen, String cli, BiConsumer<String, List<String>> extension) {
        String[] tokens = cli.split("\\s+");
        int i = 0;
        if (!tokens[0].startsWith("-")) {
            playpen.connection = tokens[0];
            i++;
        }
        Map<String, List<String>> params = cliParams(tokens, i);
        params.forEach((key, val) -> {
            if (key.equals("q") || key.equals("query")) {
                if (playpen.queries == null) {
                    playpen.queries = new ArrayList<>();
                }
                playpen.queries.addAll(val);
            } else if (key.equals("header")) {
                if (playpen.headers == null) {
                    playpen.headers = new ArrayList<>();
                }
                playpen.headers.addAll(val);
            } else if (key.equals("p") || key.equals("path")) {
                if (playpen.paths == null) {
                    playpen.paths = new ArrayList<>();
                }
                playpen.paths.addAll(val);
            } else if (key.equals("clientIp") || key.equals("ip")) {
                playpen.useClientIp = true;

                if (!val.isEmpty()) {
                    playpen.clientIp = val.get(0);
                }
            } else if (key.equals("global") || key.equals("g")) {
                if (val.isEmpty()) {
                    playpen.isGlobal = true;
                } else {
                    playpen.isGlobal = "true".equalsIgnoreCase(val.get(0));
                }
            } else if (key.equals("trustCert")) {
                if (val.isEmpty()) {
                    playpen.trustCert = true;
                } else {
                    playpen.trustCert = "true".equalsIgnoreCase(val.get(0));
                }
            } else if (key.equals("w") || key.equals("who")) {
                if (!val.isEmpty()) {
                    playpen.who = val.get(0);
                }
            } else if (key.equals("c") || key.equals("credentials")) {
                if (!val.isEmpty()) {
                    playpen.credentials = val.get(0);
                }
            } else if (extension != null) {
                extension.accept(key, val);
            }
        });
    }
}

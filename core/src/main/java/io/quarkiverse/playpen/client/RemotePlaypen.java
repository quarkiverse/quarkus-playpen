package io.quarkiverse.playpen.client;

import java.net.HttpURLConnection;
import java.net.URL;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.server.auth.ProxySessionAuth;

public class RemotePlaypen {
    protected static final Logger log = Logger.getLogger(RemotePlaypen.class);

    public static boolean connect(String url, String secret, String configString) throws Exception {
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        url += "/_playpen_api/connect?" + configString;

        log.info("Sending connect " + url);
        URL httpUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.addRequestProperty(ProxySessionAuth.AUTHORIZATION, "Secret " + secret);
            int responseCode = connection.getResponseCode();
            if (responseCode == 204) {
                log.info("Successfully set up playpen session.");
                return true;
            } else {
                log.error("Failed to connect to remote playpen: " + responseCode);
                return false;
            }
        } finally {
            try {
                connection.disconnect();
            } catch (Exception e) {

            }
        }
    }

    public static boolean disconnect(String url, String secret) throws Exception {
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        url += "/_playpen_api/connect";

        log.info("Sending disconnect " + url);
        URL httpUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("DELETE");
            connection.addRequestProperty(ProxySessionAuth.AUTHORIZATION, "Secret " + secret);
            int responseCode = connection.getResponseCode();
            if (responseCode == 204) {
                log.info("Playpen disconnect succeeded.");
                return true;
            } else {
                log.error("Failed to connect to remote playpen: " + responseCode);
                return false;
            }
        } finally {
            try {
                connection.disconnect();
            } catch (Exception e) {

            }
        }
    }
}

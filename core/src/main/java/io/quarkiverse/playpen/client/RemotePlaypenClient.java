package io.quarkiverse.playpen.client;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.server.PlaypenProxyConstants;
import io.quarkiverse.playpen.server.auth.PlaypenAuth;

public class RemotePlaypenClient {
    protected static final Logger log = Logger.getLogger(RemotePlaypenClient.class);

    protected String url;
    protected String credentials;
    protected String configString;
    protected String authHeader;

    public RemotePlaypenClient(String url, String credentials, String configString) {
        this.url = url;
        this.credentials = credentials;
        this.configString = configString;
    }

    public boolean isConnectingToExistingHost() {
        return configString.contains("host=");
    }

    public void challenge() throws IOException {
        int idx = url.indexOf(PlaypenProxyConstants.REMOTE_API_PATH);
        if (idx < 0) {
            throw new RuntimeException("Illegal Url: " + url);
        }
        String challenge = url.substring(0, idx) + "/challenge";
        URL httpUrl = new URL(challenge);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode == 401) {
                String wwwAuthenticate = connection.getHeaderField(PlaypenAuth.WWW_AUTHENTICATE);
                if (wwwAuthenticate == null) {
                    throw new RuntimeException("No www-authenticate header");
                } else if (wwwAuthenticate.startsWith("Basic")) {
                    setBasicAuth(credentials);
                } else if (wwwAuthenticate.startsWith("Secret")) {
                    setSecretAuth(credentials);
                }

            } else if (responseCode >= 400) {
                throw new RuntimeException("Failed to challenge: " + responseCode);
            }

        } finally {
            try {
                connection.disconnect();
            } catch (Exception e) {

            }
        }
    }

    public void setSecretAuth(String secret) {
        if (secret == null) {
            throw new RuntimeException("Credentials not set, must be username:password string");
        }
        this.authHeader = "Secret " + secret;
    }

    public void setBasicAuth(String username, String password) {
        String valueToEncode = username + ":" + password;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    }

    public void setBasicAuth(String creds) {
        if (creds == null) {
            throw new RuntimeException("Credentials not set, must be username:password string");
        }
        int idx = creds.indexOf(':');
        if (idx < 0) {
            throw new RuntimeException("Credentials not set, must be username:password string");
        }
        setBasicAuth(creds.substring(0, idx), creds.substring(idx + 1));
    }

    private void setAuth(HttpURLConnection connection) {
        if (authHeader != null) {
            connection.addRequestProperty(PlaypenAuth.AUTHORIZATION, authHeader);
        }
    }

    public boolean connect() throws Exception {
        String connectUrl = apiUrl("connect");
        connectUrl = connectUrl + "?" + configString;

        log.info("Sending connect " + connectUrl);
        URL httpUrl = new URL(connectUrl);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("POST");
            setAuth(connection);
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

    private String apiUrl(String action) {
        action = action.startsWith("/") ? action.substring(1) : action;
        String connectUrl = url;
        connectUrl = stripSlash(connectUrl);
        connectUrl += "/_playpen_api/" + action;
        return connectUrl;
    }

    private static String stripSlash(String url) {
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return url;
    }

    public boolean disconnect() throws Exception {
        String connectUrl = apiUrl("connect");

        log.info("Sending disconnect " + connectUrl);
        URL httpUrl = new URL(connectUrl);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("DELETE");
            setAuth(connection);
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

    public boolean remotePlaypenExists() throws Exception {
        String connectUrl = apiUrl(PlaypenProxyConstants.DEPLOYMENT_PATH);
        connectUrl = connectUrl + "?exists";
        URL httpUrl = new URL(connectUrl);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("GET");
            setAuth(connection);

            int responseCode = connection.getResponseCode();
            return responseCode != 204;
        } finally {
            try {
                connection.disconnect();
            } catch (Exception e) {

            }
        }
    }

    public String get() throws Exception {
        String connectUrl = apiUrl(PlaypenProxyConstants.DEPLOYMENT_PATH);
        URL httpUrl = new URL(connectUrl);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("GET");
            setAuth(connection);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                for (int result = bis.read(); result != -1; result = bis.read()) {
                    buf.write((byte) result);
                }
                return buf.toString("UTF-8");
            }
            return null;
        } finally {
            try {
                connection.disconnect();
            } catch (Exception e) {

            }
        }
    }

    /**
     * For testing purposes gets uploaded zip from creation of playpen
     *
     * @param zip
     * @return
     * @throws Exception
     */
    public boolean download(Path zip) throws Exception {
        String connectUrl = apiUrl(PlaypenProxyConstants.DEPLOYMENT_ZIP_PATH);

        log.info("Getting deployment " + connectUrl);
        URL httpUrl = new URL(connectUrl);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("GET");
            setAuth(connection);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                log.info("Got deployment, saving to disk");
                Files.copy(connection.getInputStream(), zip, StandardCopyOption.REPLACE_EXISTING);
                try {
                    connection.getInputStream().close();
                } catch (IOException e) {
                }
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

    public boolean create(Path zip, boolean manual) throws Exception {
        String connectUrl = apiUrl(PlaypenProxyConstants.QUARKUS_DEPLOYMENT_PATH);
        if (manual) {
            connectUrl = connectUrl + "?manual=true";
        }

        log.info("Sending deployment " + connectUrl);
        URL httpUrl = new URL(connectUrl);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("POST");
            setAuth(connection);
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            Files.copy(zip, os);
            os.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == 201) {
                log.info("Successfully set up remote container.");
                return true;
            } else {
                log.error("Failed to create to remote container: " + responseCode);
                return false;
            }
        } catch (Exception ex) {
            log.error("Failure sending request");
            return false;
        } finally {
            try {
                connection.disconnect();
            } catch (Exception e) {

            }
        }
    }

    public boolean delete() throws Exception {
        String connectUrl = apiUrl(PlaypenProxyConstants.DEPLOYMENT_PATH);
        log.info("Sending delete " + connectUrl);
        URL httpUrl = new URL(connectUrl);
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        try {
            connection.setRequestMethod("DELETE");
            setAuth(connection);
            int responseCode = connection.getResponseCode();
            if (responseCode == 204) {
                log.info("Successfully deleted remote container.");
                return true;
            } else {
                log.error("Failed to delete remote container: " + responseCode);
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

package io.quarkiverse.playpen.utils;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class InsecureSsl {

    /**
     * Check to see if certs used in https url connection are self-signed.
     * Returns false if not https.
     * makes a call with https, if fails, tries to trust all, if success
     * then it is self-signed.
     *
     * @param urlString
     * @return null if failed to invoke
     */
    public static Boolean isSelfSigned(String urlString) {
        if (!urlString.startsWith("https"))
            return false;
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            return null;
        }

        boolean success = false;
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            success = true;
        } catch (Exception ex) {
        }
        if (success)
            return false;

        HostnameVerifier oldVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        SSLSocketFactory oldFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        try {
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            SSLSocketFactory socketFactory = getInstance().getSocketFactory();
            HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            success = true;
        } catch (Exception e) {
        } finally {
            HttpsURLConnection.setDefaultHostnameVerifier(oldVerifier);
            HttpsURLConnection.setDefaultSSLSocketFactory(oldFactory);
        }
        if (success)
            return true;
        return null;
    }

    // Create a trust manager that does NOT validate certificate chains
    private static TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
    };

    // A hostname verifier for which ALL hosts valid
    public static HostnameVerifier allHostsValid = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    static boolean defaultsSet = false;
    static SSLSocketFactory socketFactory;

    public static void trustAllByDefault() {
        try {
            if (defaultsSet)
                return;
            defaultsSet = true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            socketFactory = getInstance().getSocketFactory();
            HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static SSLContext getInstance() throws KeyManagementException, NoSuchAlgorithmException {
        return getInstance("TLS");
    }

    //get a 'Relaxed' SSLContext with no trust store (all certificates are valid)
    private static SSLContext getInstance(String protocol) throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext sc = SSLContext.getInstance(protocol);
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }
}

package io.quarkiverse.playpen.deployment;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface PlaypenKubernetesClientBuildConfig {

    /**
     * Whether the client should trust a self-signed certificate if so presented by the API server
     */
    Optional<Boolean> trustCerts();

    /**
     * URL of the Kubernetes API server
     */
    Optional<String> masterUrl();

    /**
     * Default namespace to use
     */
    Optional<String> namespace();

    /**
     * CA certificate file
     */
    Optional<String> caCertFile();

    /**
     * CA certificate data
     */
    Optional<String> caCertData();

    /**
     * Client certificate file
     */
    Optional<String> clientCertFile();

    /**
     * Client certificate data
     */
    Optional<String> clientCertData();

    /**
     * Client key file
     */
    Optional<String> clientKeyFile();

    /**
     * Client key data
     */
    Optional<String> clientKeyData();

    /**
     * Client key algorithm
     */
    Optional<String> clientKeyAlgo();

    /**
     * Client key passphrase
     */
    Optional<String> clientKeyPassphrase();

    /**
     * Kubernetes auth username
     */
    Optional<String> username();

    /**
     * Kubernetes auth password
     */
    Optional<String> password();

    /**
     * Kubernetes oauth token
     */
    Optional<String> token();

    /**
     * Watch reconnect interval
     */
    @WithDefault("PT1S") // default lifted from Kubernetes Client
    Duration watchReconnectInterval();

    /**
     * Maximum reconnect attempts in case of watch failure
     * By default there is no limit to the number of reconnect attempts
     */
    @WithDefault("-1") // default lifted from Kubernetes Client
    int watchReconnectLimit();

    /**
     * Maximum amount of time to wait for a connection with the API server to be established
     */
    @WithDefault("PT10S") // default lifted from Kubernetes Client
    Duration connectionTimeout();

    /**
     * Maximum amount of time to wait for a request to the API server to be completed
     */
    @WithDefault("PT10S") // default lifted from Kubernetes Client
    Duration requestTimeout();

    /**
     * Maximum number of retry attempts for API requests that fail with an HTTP code of >= 500
     */
    @WithDefault("0") // default lifted from Kubernetes Client
    Integer requestRetryBackoffLimit();

    /**
     * Time interval between retry attempts for API requests that fail with an HTTP code of >= 500
     */
    @WithDefault("PT1S") // default lifted from Kubernetes Client
    Duration requestRetryBackoffInterval();

    /**
     * HTTP proxy used to access the Kubernetes API server
     */
    Optional<String> httpProxy();

    /**
     * HTTPS proxy used to access the Kubernetes API server
     */
    Optional<String> httpsProxy();

    /**
     * Proxy username
     */
    Optional<String> proxyUsername();

    /**
     * Proxy password
     */
    Optional<String> proxyPassword();

    /**
     * IP addresses or hosts to exclude from proxying
     */
    Optional<List<String>> noProxy();
}

package io.quarkiverse.playpen.deployment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eclipse.microprofile.config.ConfigProvider;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public class KubernetesClientUtils {

    private KubernetesClientUtils() {
    }

    static void invoke(Object target, String name, Object param) {
        boolean invoked = false;
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name)) {
                try {
                    if (m.getParameterCount() != 1)
                        continue;
                    m.invoke(target, param);
                    invoked = true;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (!invoked)
            throw new RuntimeException("No such method: " + name);
    }

    public static Config createConfig(PlaypenKubernetesClientBuildConfig buildConfig) {
        boolean globalTrustAll = ConfigProvider.getConfig().getOptionalValue("quarkus.tls.trust-all", Boolean.class)
                .orElse(false);
        Config base = Config.autoConfigure(null);
        boolean trustAll = buildConfig.trustCerts().isPresent() ? buildConfig.trustCerts().get() : globalTrustAll;
        ConfigBuilder builder = new ConfigBuilder();

        builder
                .withTrustCerts(trustAll)
                .withWatchReconnectInterval((int) buildConfig.watchReconnectInterval().toMillis())
                .withWatchReconnectLimit(buildConfig.watchReconnectLimit())
                .withConnectionTimeout((int) buildConfig.connectionTimeout().toMillis())
                .withRequestTimeout((int) buildConfig.requestTimeout().toMillis())
                .withCaCertFile(buildConfig.caCertFile().orElse(base.getCaCertFile()))
                .withCaCertData(buildConfig.caCertData().orElse(base.getCaCertData()))
                .withClientCertFile(buildConfig.clientCertFile().orElse(base.getClientCertFile()))
                .withClientCertData(buildConfig.clientCertData().orElse(base.getClientCertData()))
                .withClientKeyFile(buildConfig.clientKeyFile().orElse(base.getClientKeyFile()))
                .withClientKeyData(buildConfig.clientKeyData().orElse(base.getClientKeyData()))
                .withClientKeyPassphrase(buildConfig.clientKeyPassphrase().orElse(base.getClientKeyPassphrase()))
                .withClientKeyAlgo(buildConfig.clientKeyAlgo().orElse(base.getClientKeyAlgo()))
                .withHttpProxy(buildConfig.httpProxy().orElse(base.getHttpProxy()))
                .withHttpsProxy(buildConfig.httpsProxy().orElse(base.getHttpsProxy()))
                .withProxyUsername(buildConfig.proxyUsername().orElse(base.getProxyUsername()))
                .withProxyPassword(buildConfig.proxyPassword().orElse(base.getProxyPassword()))
                .withNoProxy(buildConfig.noProxy().map(list -> list.toArray(new String[0])).orElse(base.getNoProxy()))
                .withHttp2Disable(base.isHttp2Disable())
                .withRequestRetryBackoffInterval((int) buildConfig.requestRetryBackoffInterval().toMillis())
                .withRequestRetryBackoffLimit(buildConfig.requestRetryBackoffLimit())
                .withMasterUrl(buildConfig.masterUrl().orElse(base.getMasterUrl()))
                .withNamespace(buildConfig.namespace().orElse(base.getNamespace()))
                .withUsername(buildConfig.username().orElse(base.getUsername()))
                .withPassword(buildConfig.password().orElse(base.getPassword()))
                .withOauthToken(buildConfig.token().orElse(base.getOauthToken()));

        return builder.build();
    }

    public static Config createConfigReflection(PlaypenKubernetesClientBuildConfig buildConfig) {
        boolean globalTrustAll = ConfigProvider.getConfig().getOptionalValue("quarkus.tls.trust-all", Boolean.class)
                .orElse(false);
        Config base = Config.autoConfigure(null);
        boolean trustAll = buildConfig.trustCerts().isPresent() ? buildConfig.trustCerts().get() : globalTrustAll;
        ConfigBuilder builder = new ConfigBuilder();

        invoke(builder, "withTrustCerts", trustAll);
        invoke(builder, "withWatchReconnectInterval", ((int) buildConfig.watchReconnectInterval().toMillis()));
        invoke(builder, "withWatchReconnectLimit", buildConfig.watchReconnectLimit());
        invoke(builder, "withConnectionTimeout", (int) buildConfig.connectionTimeout().toMillis());
        invoke(builder, "withRequestTimeout", (int) buildConfig.requestTimeout().toMillis());
        invoke(builder, "withCaCertFile", buildConfig.caCertFile().orElse(base.getCaCertFile()));
        invoke(builder, "withCaCertData", buildConfig.caCertData().orElse(base.getCaCertData()));
        invoke(builder, "withClientCertFile", buildConfig.clientCertFile().orElse(base.getClientCertFile()));
        invoke(builder, "withClientCertData", buildConfig.clientCertData().orElse(base.getClientCertData()));
        invoke(builder, "withClientKeyFile", buildConfig.clientKeyFile().orElse(base.getClientKeyFile()));
        invoke(builder, "withClientKeyData", buildConfig.clientKeyData().orElse(base.getClientKeyData()));
        invoke(builder, "withClientKeyPassphrase", buildConfig.clientKeyPassphrase().orElse(base.getClientKeyPassphrase()));
        invoke(builder, "withClientKeyAlgo", buildConfig.clientKeyAlgo().orElse(base.getClientKeyAlgo()));
        invoke(builder, "withHttpProxy", buildConfig.httpProxy().orElse(base.getHttpProxy()));
        invoke(builder, "withHttpsProxy", buildConfig.httpsProxy().orElse(base.getHttpsProxy()));
        invoke(builder, "withProxyUsername", buildConfig.proxyUsername().orElse(base.getProxyUsername()));
        invoke(builder, "withProxyPassword", buildConfig.proxyPassword().orElse(base.getProxyPassword()));
        invoke(builder, "withNoProxy",
                buildConfig.noProxy().map(list -> list.toArray(new String[0])).orElse(base.getNoProxy()));
        invoke(builder, "withHttp2Disable", base.isHttp2Disable());
        invoke(builder, "withRequestRetryBackoffInterval", (int) buildConfig.requestRetryBackoffInterval().toMillis());
        invoke(builder, "withRequestRetryBackoffLimit", buildConfig.requestRetryBackoffLimit());
        invoke(builder, "withMasterUrl", buildConfig.masterUrl().orElse(base.getMasterUrl()));
        invoke(builder, "withNamespace", buildConfig.namespace().orElse(base.getNamespace()));
        invoke(builder, "withUsername", buildConfig.username().orElse(base.getUsername()));
        invoke(builder, "withPassword", buildConfig.password().orElse(base.getPassword()));
        invoke(builder, "withOauthToken", buildConfig.token().orElse(base.getOauthToken()));

        return builder.build();
    }

    public static KubernetesClient createClient(PlaypenKubernetesClientBuildConfig buildConfig) {
        return new KubernetesClientBuilder().withConfig(createConfigReflection(buildConfig)).build();
    }
}

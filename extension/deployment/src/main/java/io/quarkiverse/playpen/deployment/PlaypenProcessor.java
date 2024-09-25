package io.quarkiverse.playpen.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.playpen.LocalPlaypenRecorder;
import io.quarkiverse.playpen.client.DefaultLocalPlaypenClientManager;
import io.quarkiverse.playpen.client.LocalPlaypenConnectionConfig;
import io.quarkiverse.playpen.kubernetes.client.KubernetesLocalPlaypenClientManager;
import io.quarkiverse.playpen.kubernetes.client.PortForward;
import io.quarkiverse.playpen.utils.ProxyUtils;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.quarkus.vertx.http.deployment.RequireVirtualHttpBuildItem;

public class PlaypenProcessor {
    private static final Logger log = Logger.getLogger(PlaypenProcessor.class);

    @BuildStep(onlyIfNot = { IsNormal.class, IsAnyRemoteDev.class })
    public RequireVirtualHttpBuildItem requestVirtualHttp(PlaypenConfig config) throws BuildException {
        // always turn on virtual http in test/dev mode just in case somebody wants to manually start
        // server
        return RequireVirtualHttpBuildItem.MARKER;
    }

    static Map<String, PortForward> portForwards = new HashMap<>();
    static String lastConnect;
    static String lastEndpoint;
    static PortForward playpenPortForward;

    static void addPortForward(KubernetesClient client, CuratedApplicationShutdownBuildItem shutdown, String endpoint) {
        if (portForwards.isEmpty()) {
            shutdown.addCloseTask(() -> {
                portForwards.values().forEach(ProxyUtils::safeClose);
                portForwards.clear();
            }, true);
        }
        try {
            if (portForwards.containsKey(endpoint)) {
                log.infov("Reusing port forward {0}", portForwards.get(endpoint));
                return;
            }
            PortForward pf = new PortForward(endpoint);
            pf.forward(client);
            log.infov("Established port forward {0}", pf.toString());
            portForwards.put(endpoint, pf);
        } catch (RuntimeException e) {
            log.error("Could not establish port forward: " + endpoint);
            log.error(e.getMessage());
            throw e;
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIfNot = { IsNormal.class, IsAnyRemoteDev.class })
    public void recordProxy(CoreVertxBuildItem vertx,
            List<ServiceStartBuildItem> orderServicesFirst, // try to order this after service recorders
            ShutdownContextBuildItem shutdown,
            PlaypenConfig config,
            LaunchModeBuildItem launchModeBuildItem,
            LocalPlaypenRecorder proxy,
            CuratedApplicationShutdownBuildItem curatedShutdown) {
        KubernetesClient client = null;
        if (config.local().portForwards().isPresent()) {
            if (client == null)
                client = KubernetesClientUtils.createClient(config.kubernetesClient());
            for (String pf : config.local().portForwards().get()) {
                addPortForward(client, curatedShutdown, pf);
            }
        }

        if (config.local().connect().isPresent()) {
            if (launchModeBuildItem.getLaunchMode() == LaunchMode.DEVELOPMENT) {
                if (lastConnect != null && lastConnect.equals(config.local().connect().get())) {
                    if ((lastEndpoint == null && !config.endpoint().isPresent())
                            || (lastEndpoint != null && lastEndpoint.equals(config.endpoint().orElse(null)))) {
                        // Connection will already be set up
                        // On a hot reload quarkus may restart the server and
                        // the playpen client will be in process of forwarding request
                        return;
                    }
                }
            }
            LocalPlaypenConnectionConfig playpenConfig = new LocalPlaypenConnectionConfig();
            lastEndpoint = config.endpoint().orElse(null);
            if (config.endpoint().isPresent()) {
                LocalPlaypenConnectionConfig.fromCli(playpenConfig, lastEndpoint);
            }

            lastConnect = config.local().connect().get();
            if (!RemotePlaypenProcessor.isPropertyBlank(lastConnect)) {
                LocalPlaypenConnectionConfig.fromCli(playpenConfig, lastConnect);
            }
            if (playpenConfig.who == null) {
                String username = System.getProperty("user.name");
                if (username != null && !username.isEmpty()) {
                    log.warn(
                            "Your login username is being used as a session id.  Use playpen.local.connect -who to set it to a different value");
                    playpenConfig.who = username;
                } else {
                    log.error("playpen.local.connect -who must be set");
                    System.exit(1);
                }
            }
            if (playpenConfig.connection.startsWith("http")) {
                DefaultLocalPlaypenClientManager manager = new DefaultLocalPlaypenClientManager(playpenConfig);
                if (!manager.checkHttpsCerts()) {
                    System.exit(1);
                }
            } else {
                if (client == null)
                    client = KubernetesClientUtils.createClient(config.kubernetesClient());
                KubernetesLocalPlaypenClientManager manager = new KubernetesLocalPlaypenClientManager(playpenConfig,
                        client);
                if (playpenPortForward == null) {
                    curatedShutdown.addCloseTask(() -> {
                        if (playpenPortForward != null) {
                            ProxyUtils.safeClose(playpenPortForward);
                        }
                    }, true);
                }
                if (playpenPortForward != null) {
                    if (!playpenConfig.connection.equals(playpenPortForward.getEndpoint())) {
                        ProxyUtils.safeClose(playpenPortForward);
                        playpenPortForward = null;
                    } else {
                        log.info("Reusing playpen port forward");
                        playpenConfig.host = "localhost";
                        playpenConfig.port = playpenPortForward.getLocalPort();
                    }
                }
                if (playpenPortForward == null) {
                    try {
                        PortForward pf = manager.portForward();
                        log.infov("Established playpen port forward {0}", pf.toString());
                        playpenPortForward = pf;
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to establish playpen: " + e.getMessage());
                        log.error("Maybe playpen does not exist?");
                        System.exit(1);
                    }
                }
                if (playpenConfig.portForwards != null) {
                    for (String pf : playpenConfig.portForwards) {
                        addPortForward(client, curatedShutdown, pf);
                    }
                }
            }
            proxy.init(launchModeBuildItem.getLaunchMode(), vertx.getVertx(), shutdown, playpenConfig,
                    config.local().manualStart());
        }
    }

}

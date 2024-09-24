package io.quarkiverse.playpen.deployment;

import java.util.HashMap;
import java.util.LinkedList;
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
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
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

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIfNot = { IsNormal.class, IsAnyRemoteDev.class })
    public void recordProxy(CoreVertxBuildItem vertx,
            List<ServiceStartBuildItem> orderServicesFirst, // try to order this after service recorders
            ShutdownContextBuildItem shutdown,
            PlaypenConfig config,
            LocalPlaypenRecorder proxy,
            CuratedApplicationShutdownBuildItem curatedShutdown) {
        if (config.local().connect().isPresent()) {
            LocalPlaypenConnectionConfig playpenConfig = new LocalPlaypenConnectionConfig();
            if (config.endpoint().isPresent()) {
                LocalPlaypenConnectionConfig.fromCli(playpenConfig, config.endpoint().get());
            }
            String cli = config.local().connect().get();

            // don't reload if -Dplaypen.local.connect and no value
            if (!RemotePlaypenProcessor.isPropertyBlank(cli)) {
                LocalPlaypenConnectionConfig.fromCli(playpenConfig, cli);
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
                KubernetesClient client = KubernetesClientUtils.createClient(config.kubernetesClient());
                KubernetesLocalPlaypenClientManager manager = new KubernetesLocalPlaypenClientManager(playpenConfig,
                        client);
                PortForward ppf = portForwards.get(playpenConfig.connection);
                if (ppf == null) {
                    try {
                        PortForward playpenPortForward = manager.portForward();
                        log.infov("Established playpen port forward {0}", playpenPortForward.toString());
                        portForwards.put(playpenConfig.connection, playpenPortForward);
                    } catch (IllegalArgumentException e) {
                        log.error(e.getMessage());
                        log.error("Maybe playpen does not exist?");
                        System.exit(1);
                    }
                } else {
                    log.info("Reusing playpen port forward");
                    playpenConfig.host = "localhost";
                    playpenConfig.port = ppf.getLocalPort();
                }
                curatedShutdown.addCloseTask(() -> {
                    portForwards.values().forEach(ProxyUtils::safeClose);
                    portForwards.clear();
                }, true);
                List<String> pfs = new LinkedList<>();
                if (config.local().portForwards().isPresent()) {
                    pfs.addAll(config.local().portForwards().get());
                }
                if (playpenConfig.portForwards != null) {
                    pfs.addAll(playpenConfig.portForwards);
                }
                pfs.forEach(s -> {
                    try {
                        if (portForwards.containsKey(s)) {
                            log.infov("Reusing port forward {0}", portForwards.get(s));
                            return;
                        }
                        PortForward pf = new PortForward(s);
                        pf.forward(client);
                        log.infov("Established port forward {0}", pf.toString());
                        portForwards.put(s, pf);
                    } catch (RuntimeException e) {
                        log.error("Could not establish port forward: " + s);
                        log.error(e.getMessage());
                        throw e;
                    }
                });

            }
            proxy.init(vertx.getVertx(), shutdown, playpenConfig, config.local().manualStart());
        }
    }

}

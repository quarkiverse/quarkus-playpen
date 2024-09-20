package io.quarkiverse.playpen.deployment;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

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

    static List<Closeable> closeables = new ArrayList<>();

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIfNot = { IsNormal.class, IsAnyRemoteDev.class })
    public void recordProxy(CoreVertxBuildItem vertx,
            List<ServiceStartBuildItem> orderServicesFirst, // try to order this after service recorders
            ShutdownContextBuildItem shutdown,
            PlaypenConfig config,
            LocalPlaypenRecorder proxy,
            CuratedApplicationShutdownBuildItem curatedShutdown) {
        if (config.local().connect().isPresent()) {
            LocalPlaypenConnectionConfig local = new LocalPlaypenConnectionConfig();
            if (config.endpoint().isPresent()) {
                LocalPlaypenConnectionConfig.fromCli(local, config.endpoint().get());
            }
            String cli = config.local().connect().get();

            // don't reload if -Dplaypen.local.connect and no value
            if (!RemotePlaypenProcessor.isPropertyBlank(cli)) {
                LocalPlaypenConnectionConfig.fromCli(local, cli);
            }
            if (local.who == null) {
                log.error("playpen.local.connect -who must be set");
                System.exit(1);
            }
            if (local.connection.startsWith("http")) {
                DefaultLocalPlaypenClientManager manager = new DefaultLocalPlaypenClientManager(local);
                if (!manager.checkHttpsCerts()) {
                    System.exit(1);
                }
            } else {
                KubernetesClient client = KubernetesClientUtils.createClient(config.kubernetesClient());
                KubernetesLocalPlaypenClientManager manager = new KubernetesLocalPlaypenClientManager(local, client);
                PortForward portForward = null;
                try {
                    portForward = manager.portForward();
                } catch (IllegalArgumentException e) {
                    log.error(e.getMessage());
                    log.error("Maybe playpen does not exist?");
                    System.exit(1);

                }
                log.infov("Established playpen port forward {0}", portForward.toString());
                closeables.add(portForward);
                curatedShutdown.addCloseTask(() -> {
                    closeables.forEach(ProxyUtils::safeClose);
                }, true);
                if (config.local().portForwards().isPresent()) {
                    config.local().portForwards().get().forEach(s -> {
                        try {
                            PortForward pf = new PortForward(s);
                            pf.forward(client);
                            log.infov("Established port forward {0}", pf.toString());
                            closeables.add(pf);
                        } catch (IllegalArgumentException e) {
                            log.error("Could not establish port forward");
                            log.error(e.getMessage());
                        }
                    });
                }
            }
            proxy.init(vertx.getVertx(), shutdown, local, config.local().manualStart());
        }
    }

}

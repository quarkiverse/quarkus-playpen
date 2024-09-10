package io.quarkiverse.playpen.deployment;

import java.util.List;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.playpen.LocalPlaypenRecorder;
import io.quarkiverse.playpen.client.DefaultLocalPlaypenClientManager;
import io.quarkiverse.playpen.client.KubernetesLocalPlaypenClientManager;
import io.quarkiverse.playpen.client.LocalPlaypenConnectionConfig;
import io.quarkiverse.playpen.client.PortForward;
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

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIfNot = { IsNormal.class, IsAnyRemoteDev.class })
    public void recordProxy(CoreVertxBuildItem vertx,
            List<ServiceStartBuildItem> orderServicesFirst, // try to order this after service recorders
            ShutdownContextBuildItem shutdown,
            PlaypenConfig config,
            LocalPlaypenRecorder proxy,
            CuratedApplicationShutdownBuildItem curatedShutdown) {
        if (config.local().isPresent() && !config.command().isPresent()) {
            LocalPlaypenConnectionConfig local = LocalPlaypenConnectionConfig.fromCli(config.local().get());
            if (local.connection.startsWith("http")) {
                DefaultLocalPlaypenClientManager manager = new DefaultLocalPlaypenClientManager(local);
                if (!manager.checkHttpsCerts()) {
                    System.exit(1);
                }
            } else {
                KubernetesClient client = KubernetesClientUtils.createClient(config.kubernetesClient());
                KubernetesLocalPlaypenClientManager manager = new KubernetesLocalPlaypenClientManager(local, client);
                try {
                    PortForward portForward = manager.portForward();
                    log.infov("Established port forward {0}", portForward.toString());
                    curatedShutdown.addCloseTask(() -> {
                        ProxyUtils.safeClose(portForward);
                    }, true);
                } catch (IllegalArgumentException e) {
                    log.error("Failed to set up port forward to playpen: " + local.connection);
                    log.error(e.getMessage());
                    System.exit(1);
                }
            }
            proxy.init(vertx.getVertx(), shutdown, local, config.manualStart());
        }
    }

}

package io.quarkiverse.playpen.deployment;

import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.LocalPlaypenRecorder;
import io.quarkiverse.playpen.client.PlaypenConnectionConfig;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
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
            LocalPlaypenRecorder proxy) {
        if (config.local().isPresent() && !config.command().isPresent()) {
            PlaypenConnectionConfig playpen = PlaypenConnectionConfig.fromUri(config.local().get());
            if (playpen.error != null) {
                throw new RuntimeException(playpen.error);
            }
            if (config.credentials().isPresent()) {
                playpen.credentials = config.credentials().get();
            }
            proxy.init(vertx.getVertx(), shutdown, playpen, config.manualStart());
        }
    }

}
